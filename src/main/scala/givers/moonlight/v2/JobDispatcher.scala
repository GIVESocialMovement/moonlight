package givers.moonlight.v2

import akka.actor.Cancellable
import akka.actor.typed.Scheduler
import com.google.inject.Singleton
import givers.moonlight.BackgroundJob
import givers.moonlight.util.DateTimeFactory
import givers.moonlight.util.RichFuture.TimeoutAwareFuture
import givers.moonlight.v2.repository.{BackgroundJobRepository, JobReadyForStartCheckParams}
import play.api.Logger
import play.api.inject.Injector

import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Random, Success}

case class JobExecutionError(realCause: Throwable) extends Exception("Job execution error", realCause)

@Singleton
/**
 * Dispatches jobs
 *
 * @param bgJobRepo background jobs repository
 * @param settings moonlight settings
 * @param dateTimeFactory date/time factory
 * @param executionContext execution context
 * @param injector DI system injector
 * @param scheduler task scheduler
 * @param random randomizer for duration randomization
 */
class JobDispatcher @Inject()(bgJobRepo: BackgroundJobRepository,
                    settings: MoonlightSettings,
                    dateTimeFactory: DateTimeFactory
                   )(implicit executionContext: ExecutionContext,
                     injector: Injector,
                     scheduler: Scheduler,
                     random: Random) {
  private[this] val logger = Logger(this.getClass)

  /**
   * Schedules maintenance task that will "unstuck" jobs
   *
   * @return
   */
  private def scheduleMaintenance(): Cancellable = {
    scheduler.scheduleAtFixedRate(0.minutes, settings.maintenanceInterval)(() => {
      val _ = bgJobRepo.maintainExpiredJobs(settings.jobRunTimeout, dateTimeFactory.now).onComplete {
        case Success(0) =>
        case Success(numberOfMaintainedJobs) =>
          logger.warn(s"$numberOfMaintainedJobs expired jobs were maintained")
        case Failure(e) =>
          logger.error("maintenance error", e)
      }
    })
  }

  /**
   * Dispatcher main loop
   * Picks tasks and runs it, pauses if no tasks
   * 
   * @return loop cancel control, future that will be finished after cancel
   */
  def runLoop(): (Cancellable, Future[Unit]) = {
    val runPromise = Promise[Unit]
    val untilRunning = new AtomicBoolean(true)

    def runForever(threadSeqId: Int): Unit = {
      Option.when(untilRunning.get())(()) match {
        case Some(_) =>
          bgJobRepo
            .getJobReadyForStart(
              JobReadyForStartCheckParams(
                settings.maxJobRetries,
                dateTimeFactory.now,
                settings.betweenRunAttemptInterval
              )
            )
            .onComplete {
              case Success(Some(job)) =>
                runJob(job, threadSeqId).foreach(_ => runForever(threadSeqId))
              // maybe db error
              case Failure(e) =>
                logger.error("can't get job", e)
                scheduler.scheduleOnce(settings.pauseDurationWhenNoJobsRandomized, () => runForever(threadSeqId))
              // no jobs for start
              case _ =>
                scheduler.scheduleOnce(settings.pauseDurationWhenNoJobsRandomized, () => runForever(threadSeqId))
            }
        case _ =>
          logger.info("finishing run loop")
          runPromise.success(())
      }
    }

    // respect required parallelism
    (1 to settings.parallelism)
      .foreach {
        threadSeqId =>
          scheduler.scheduleOnce(JobDispatcher.jobStartDelay, () => runForever(threadSeqId))
      }

    val maintenanceCancelControl = scheduleMaintenance()

    val loopCancelControl = new Cancellable {
      override def cancel(): Boolean = {
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
   * @param job background job
   * @param threadSeqId number from 1 to <parallelism>
   * @return
   */
  private def runJob(job: BackgroundJob, threadSeqId: Int): Future[Unit] = {
    val startInMillis = Instant.now().toEpochMilli

    val startResult = for {
      worker <- Future(settings.getWorkerByJobType(job.jobType))
      isStarted <- bgJobRepo.tryMarkJobAsStarted(job.id, job.tryCount + 1, dateTimeFactory.now,
        JobReadyForStartCheckParams(
          settings.maxJobRetries,
          dateTimeFactory.now,
          settings.betweenRunAttemptInterval
        ))
    } yield (isStarted, worker)

    startResult
      .flatMap {
        case (false, _) =>
          logger.warn(s"#$threadSeqId job ${job.id} can't be started because it's started by someone else")
          Future.successful(())
        case (_, worker) =>
          logger.info(s"#$threadSeqId starting worker ${worker.getClass.getSimpleName} for job ${job.id}")
          val runFuture = for {
            _ <- worker.runAsync(job).withTimeout(settings.jobRunTimeout)
            _ <- bgJobRepo.markJobAsSucceed(job.id, dateTimeFactory.now)
          } yield {
            logger.info(s"#$threadSeqId worker ${worker.getClass.getSimpleName} with job ${job.id} finished successfully")
          }

          runFuture.onComplete {
            _ =>
              val duration = Instant.now().toEpochMilli - startInMillis
              logger.info(s"#$threadSeqId job ${job.id} took $duration millis")
          }

          runFuture.recover(e => throw JobExecutionError(e))
      }
      // this "recover" will try to mark job as failed
      .recoverWith {
        case JobExecutionError(e) =>
          logger.error(s"#$threadSeqId error occurred while running the job " +
            s"(id=${job.id}, type=${job.jobType}, params=${job.paramsInJsonString}, tryCount=${job.tryCount}).", e)
          bgJobRepo.markJobAsFailed(job.id, e, dateTimeFactory.now)
        case e: WorkerSearchException =>
          logger.error(s"#$threadSeqId error occurred while searching for worker " +
            s"(id=${job.id}, type=${job.jobType}, params=${job.paramsInJsonString}, tryCount=${job.tryCount})", e)
          bgJobRepo.markJobAsFailed(job.id, e, dateTimeFactory.now)
      }
      // in case of other error like db failure
      .recover {
        case e =>
          logger.error(s"#$threadSeqId critical failure job $job failure", e)
          ()
      }
  }
}

object JobDispatcher {
  val jobStartDelay: FiniteDuration = 1.milli
}