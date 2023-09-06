package givers.moonlight.v2

import akka.actor.Cancellable
import akka.actor.typed.Scheduler
import com.codahale.metrics.MetricRegistry
import com.google.inject.Singleton
import givers.moonlight.util.Metrics.MetricRegistryOps
import givers.moonlight.{BackgroundJob, JobExecutor}
import givers.moonlight.util.{DateTimeFactory, Metrics}
import givers.moonlight.util.RichDate._
import givers.moonlight.util.RichFuture.TimeoutAwareFuture
import givers.moonlight.v2.repository.BackgroundJobRepository
import play.api.Logger
import play.api.inject.Injector

import java.time.Instant
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import javax.inject.Inject
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NoStackTrace
import scala.util.{Failure, Random, Success, Try}

case class JobExecutionError(realCause: Throwable) extends Exception("Job execution error", realCause)
case class JobTypeExecutorAlreadyExists(typeId: String) extends Exception(s"Job executor for $typeId already exists")

case class JobExecutorNotFound(typeId: String)
    extends Exception(s"Job executor for $typeId not found")
    with NoStackTrace

@Singleton
/**
 * Dispatches jobs
 *
 * @param bgJobRepo
 *   background jobs repository
 * @param settings
 *   moonlight settings
 * @param dateTimeFactory
 *   date/time factory
 * @param executionContext
 *   execution context
 * @param injector
 *   DI system injector
 * @param scheduler
 *   task scheduler
 * @param random
 *   randomizer for duration randomization
 */
class JobDispatcher @Inject() (
  bgJobRepo: BackgroundJobRepository,
  settings: MoonlightSettings,
  dateTimeFactory: DateTimeFactory,
  metricRegistry: MetricRegistry
)(implicit executionContext: ExecutionContext, injector: Injector, scheduler: Scheduler, random: Random) {
  private[this] val logger = Logger(this.getClass)

  private val activeExecutorsCount = metricRegistry.settableGauge[Int](Metrics.jobDispatcher.activeExecutorsCount)
  private val readyToStartCount = metricRegistry.settableGauge[Int](Metrics.jobDispatcher.jobsReadyToStart)
  private val jobsOverall = metricRegistry.settableGauge[Int](Metrics.jobDispatcher.jobsOverall)

  private val maintenanceDeleteOldTimer = metricRegistry.timer(Metrics.jobDispatcher.maintenanceOldJobs)
  private val maintenanceDeleteOldErrorCount = metricRegistry.counter(Metrics.jobDispatcher.maintenanceOldJobsErrors)

  private val maintenanceUnstuckTimer = metricRegistry.timer(Metrics.jobDispatcher.maintenanceUnstuck)
  private val maintenanceUnstuckErrorCount = metricRegistry.counter(Metrics.jobDispatcher.maintenanceUnstuckErrors)

  private val concurrentFailCount = metricRegistry.counter(Metrics.jobDispatcher.concurrentFail)

  val typeExecutors: Map[String, JobExecutor[_]] = {
    settings.executors.foldLeft(Map.empty[String, JobExecutor[_]]) { case (current, executor) =>
      current.updatedWith(executor.jobType.id) {
        case Some(_) => throw JobTypeExecutorAlreadyExists(executor.jobType.id)
        case _ => Some(executor)
      }
    }
  }

  /**
   * Schedules 2 maintenance tasks
   *   - first will delete old jobs
   *   - second will "unstuck" jobs
   *
   * @return
   */
  private def scheduleMaintenance(): Cancellable = {
    scheduler.scheduleAtFixedRate(0.minutes, settings.maintenanceInterval)(() => {
      val oldJobsDeleteTimer = maintenanceDeleteOldTimer.time()
      bgJobRepo
        .deleteJobsSucceededOrFailedBefore(dateTimeFactory.now.sub(settings.completedJobsTtl).midnight)
        .onComplete {
          case Success(0) =>
            oldJobsDeleteTimer.stop()
          case Success(numberOfDeletedJobs) =>
            oldJobsDeleteTimer.stop()
            logger.warn(s"$numberOfDeletedJobs succeeded/failed jobs were removed")
          case Failure(e) =>
            oldJobsDeleteTimer.stop()
            maintenanceDeleteOldErrorCount.inc()
            logger.error("maintenance (delete task) error", e)
        }

      val unstuckTimer = maintenanceUnstuckTimer.time()
      val _ = bgJobRepo.maintainExpiredJobs(settings.jobRunTimeout, dateTimeFactory.now).onComplete {
        case Success(0) =>
          unstuckTimer.stop()
        case Success(numberOfMaintainedJobs) =>
          unstuckTimer.stop()
          logger.warn(s"$numberOfMaintainedJobs expired jobs were maintained")
        case Failure(e) =>
          unstuckTimer.stop()
          maintenanceUnstuckErrorCount.inc()
          logger.error("maintenance (unstuck task) error", e)
      }
    })
  }

  private def scheduleMetrics(): Cancellable = {
    scheduler.scheduleAtFixedRate(0.minutes, settings.countMetricsCollectionInterval)(() => {
      bgJobRepo
        .countPendingJobReadyForStart(dateTimeFactory.now)
        .onComplete {
          case Success(count) =>
            readyToStartCount.setValue(count)
          case Failure(e) =>
            readyToStartCount.setValue(-1)
            logger.error("maintenance (delete task) error", e)
        }

      bgJobRepo.count
        .onComplete {
          case Success(count) =>
            jobsOverall.setValue(count)
          case Failure(e) =>
            jobsOverall.setValue(-1)
            logger.error("maintenance (delete task) error", e)
        }
    })
  }

  /**
   * Get job that can be started now. First checks pending jobs, if there is no any then checks failed jobs that can be
   * retried
   *
   * @return
   */
  private def getJobReadyForStart: Future[Option[BackgroundJob]] = {
    val now = dateTimeFactory.now
    val lastAttemptAfter = now.sub(settings.betweenRunAttemptInterval)

    bgJobRepo.getPendingJobReadyForStart(now).flatMap {
      case None => bgJobRepo.getFailedJobReadyForRetry(settings.maxJobRetries, lastAttemptAfter)
      case res => Future.successful(res)
    }
  }

  /**
   * Dispatcher main loop Picks tasks and runs it, pauses if no tasks
   *
   * @return
   *   loop cancel control, future that will be finished after cancel
   */
  def runLoop(): (Cancellable, Future[Unit]) = {
    val runPromise = Promise[Unit]
    val untilRunning = new AtomicBoolean(true)

    // the idea is to complete runPromise only when last executor is closed
    val executorsLeft = new AtomicInteger(settings.parallelism)
    val activeExecutors = new AtomicInteger(0)

    def runForever(threadSeqId: Int): Unit = {
      Option.when(untilRunning.get())(()) match {
        case Some(_) =>
          getJobReadyForStart
            .onComplete {
              case Success(Some(job)) =>
                activeExecutorsCount.setValue(activeExecutors.incrementAndGet())
                runJob(job, threadSeqId)
                  .foreach { _ =>
                    activeExecutorsCount.setValue(activeExecutors.decrementAndGet())
                    runForever(threadSeqId)
                  }
              // maybe db error
              case Failure(e) =>
                logger.error("can't get job", e)
                scheduler.scheduleOnce(settings.pauseDurationWhenNoJobsRandomized, () => runForever(threadSeqId))
              // no jobs for start
              case _ =>
                scheduler.scheduleOnce(settings.pauseDurationWhenNoJobsRandomized, () => runForever(threadSeqId))
            }
        case _ =>
          logger.info(s"$threadSeqId closing executor")
          if (executorsLeft.decrementAndGet() == 0) {
            logger.info(s"$threadSeqId finishing run loop")
            runPromise.trySuccess(())
          }
      }
    }

    // respect required parallelism
    (1 to settings.parallelism)
      .foreach { threadSeqId =>
        scheduler.scheduleOnce(JobDispatcher.jobStartDelay, () => runForever(threadSeqId))
      }

    val maintenanceCancelControl = scheduleMaintenance()
    val metricsCancelControl = scheduleMetrics()

    val loopCancelControl = new Cancellable {
      override def cancel(): Boolean = {
        metricsCancelControl.cancel()
        maintenanceCancelControl.cancel()
        untilRunning.set(false)

        true
      }

      override def isCancelled: Boolean = !untilRunning.get() && maintenanceCancelControl.isCancelled
    }

    (loopCancelControl, runPromise.future)
  }

  /**
   * Runs specified background job
   *
   * @param job
   *   background job
   * @param threadSeqId
   *   number from 1 to <parallelism>
   * @return
   */
  private def runJob(job: BackgroundJob, threadSeqId: Int): Future[Unit] = {
    val startInMillis = Instant.now().toEpochMilli
    def duration: Long = Instant.now().toEpochMilli - startInMillis

    metricRegistry.counter(Metrics.executor.started(job.jobType)).inc()

    val startResult = for {
      executor <- Future.fromTry(
        Try(
          typeExecutors.getOrElse(
            job.jobType,
            throw JobExecutorNotFound(job.jobType)
          )
        )
      )
      isStarted <- bgJobRepo.tryMarkJobAsStarted(
        job.id,
        job.tryCount + 1,
        dateTimeFactory.now,
        settings.maxJobRetries,
        dateTimeFactory.now,
        settings.betweenRunAttemptInterval
      )
    } yield (isStarted, executor)

    startResult
      .flatMap {
        case (false, _) =>
          logger.warn(s"#$threadSeqId job ${job.id} can't be started because it's started by someone else")
          concurrentFailCount.inc()
          Future.successful(())
        case (_, executor) =>
          logger.info(s"#$threadSeqId starting executor ${executor.getClass.getSimpleName} for job ${job.id}")
          val timer = metricRegistry.timer(Metrics.executor.duration(job.jobType)).time()

          val runFuture = for {
            _ <- executor.run(job.paramsInJsonString).withTimeout(settings.jobRunTimeout)
            _ <- bgJobRepo.markJobAsSucceed(job.id, dateTimeFactory.now)
          } yield {
            logger.info(
              s"#$threadSeqId executor ${executor.getClass.getSimpleName} with job " +
                s"${job.id} finished successfully. Took $duration millis"
            )
          }

          runFuture.onComplete { res =>
            timer.stop()

            res.foreach(_ => metricRegistry.counter(Metrics.executor.succeeded(job.jobType)).inc())
            res.failed.foreach(_ => metricRegistry.counter(Metrics.executor.failed(job.jobType)).inc())
          }

          runFuture.recover(e => throw JobExecutionError(e))
      }
      // this "recover" will try to mark job as failed
      .recoverWith {
        case JobExecutionError(e) =>
          logger.error(
            s"#$threadSeqId error occurred while running the job " +
              s"(id=${job.id}, type=${job.jobType}, params=${job.paramsInJsonString}, tryCount=${job.tryCount}). " +
              s"Took $duration millis",
            e
          )
          bgJobRepo.markJobAsFailed(job.id, e, dateTimeFactory.now)
        case e: JobExecutorNotFound =>
          logger.error(
            s"#$threadSeqId error occurred while searching for executor " +
              s"(id=${job.id}, type=${job.jobType}, params=${job.paramsInJsonString}, tryCount=${job.tryCount}). " +
              s"Took $duration millis",
            e
          )
          bgJobRepo.markJobAsFailed(job.id, e, dateTimeFactory.now)
      }
      // in case of other error like db failure
      .recover { case e =>
        logger.error(s"#$threadSeqId critical failure job $job failure. Took $duration millis", e)
        ()
      }
  }
}

object JobDispatcher {
  val jobStartDelay: FiniteDuration = 1.milli
}
