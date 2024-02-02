package givers.moonlight.scheduled.quartz

import com.codahale.metrics.MetricRegistry
import givers.moonlight.scheduled.ScheduledJob
import givers.moonlight.scheduled.quartz.QuartzScheduledJob._
import givers.moonlight.util.Metrics
import org.quartz.{Job, JobExecutionContext}
import play.api.Logger

import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

/**
 * Quartz Scheduled job. It's used like a mediator between Quartz and ScheduledJob
 */
class QuartzScheduledJob extends Job {

  private[this] val logger = Logger(this.getClass)
  override def execute(context: JobExecutionContext): Unit = {
    val runId = UUID.randomUUID()
    val start = System.currentTimeMillis()

    def took: Double = (System.currentTimeMillis() - start).toDouble / 1000

    // get data populated by scheduler
    val job = context.getMergedJobDataMap.get(JOB_RUNNER_ARG).asInstanceOf[ScheduledJob]
    val data = context.getMergedJobDataMap.get(JOB_DATA_IN_ARG).asInstanceOf[job.IN]
    val timeout = context.getMergedJobDataMap.get(JOB_TIMEOUT).asInstanceOf[Duration]
    val metricRegistry = context.getMergedJobDataMap.get(METRIC_REGISTRY).asInstanceOf[MetricRegistry]

    val jobType = formatJobType(job)

    logger.info(s"Scheduled job started (Run id: $runId, type: $jobType)")

    metricRegistry.counter(Metrics.scheduled.started(jobType)).inc()
    val timer = metricRegistry.timer(Metrics.scheduled.duration(jobType)).time()

    val jobRun = job.run(data)
    val jobTimeout = new AtomicBoolean(false)

    // Double check on complete in case of timeout to log result
    jobRun.onComplete { res =>
      timer.stop()

      res match {
        case Success(_) if jobTimeout.get() =>
          logger.info(
            s"Scheduled job successfully finished after timeout (Run id: $runId, took: $took seconds)"
          )
        case Failure(ex) if jobTimeout.get() =>
          logger.error(s"Scheduled job failed after timeout (Run id: $runId, took: $took seconds)", ex)
        case _ =>
      }
    }(job.executionContext)

    // Await needed since quartz jobs are supposed to be synchronous
    Try(Await.result(jobRun, timeout)) match {
      case Success(_) =>
        logger.info(s"Scheduled job successfully finished (Run id: $runId, took: $took seconds)")
        metricRegistry.counter(Metrics.scheduled.succeeded(jobType)).inc()
      case Failure(_: TimeoutException) =>
        jobTimeout.set(true)
        logger.error(s"Scheduled job timeout (Run id: $runId, took: $took seconds)")
        metricRegistry.counter(Metrics.scheduled.timeout(jobType)).inc()
      case Failure(ex) =>
        logger.error(s"Scheduled job failed (Run id: $runId, took $took seconds)", ex)
        metricRegistry.counter(Metrics.scheduled.failed(jobType)).inc()
    }
  }
}

object QuartzScheduledJob {
  // job object
  val JOB_RUNNER_ARG = "job"
  // scheduled job parameters map key name for input params
  val JOB_DATA_IN_ARG = "in"
  // job single execution timeout
  val JOB_TIMEOUT = "timeout"
  val METRIC_REGISTRY = "metricRegistry"

  private val jobNameRegex = "[^A-Za-z0-9]+"
  private val jobNameReplace = "_"
  def formatJobType(job: ScheduledJob) = {
    job.getClass.getName.replaceAll(jobNameRegex, jobNameReplace)
  }
}
