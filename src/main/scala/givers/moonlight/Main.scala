package givers.moonlight

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import com.google.inject.Inject
import play.api._
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}


class Moonlight(val workers: WorkerSpec*)

object Main {
  private[this] val logger = Logger(this.getClass)

  def main(args: Array[String]): Unit = {
    val mode = args.head match {
      case "prod" => Mode.Prod
      case "dev" => Mode.Dev
      case "test" => Mode.Test
    }
    val realArgs = args.drop(1)

    logger.info(s"Start moonlight.Main ($mode)")
    val app = GuiceApplicationBuilder(environment = Environment.simple(mode = mode)).build()
    try {
      Play.start(app)

      app.injector.instanceOf[Main].run(realArgs)
    } catch { case e: Throwable =>
      logger.error("Error", e)
      System.exit(1) // force terminating all hanging threads.
    } finally  {
      logger.info(s"Finished moonlight.Main ($mode)")
      Play.stop(app)
      System.exit(0) // force terminating all hanging threads.
    }
  }
}

class Main @Inject()(
  app: Application,
  moonlight: Moonlight,
  backgroundJobService: BackgroundJobService
)(
  implicit ec: ExecutionContext
) {

  private[this] val DEFAULT_FUTURE_TIMEOUT = Duration.apply(5, TimeUnit.MINUTES)

  private[this] val logger = Logger(this.getClass)

  def run(args: Array[String]): Unit = {
    val running = new AtomicBoolean(true)

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        running.set(false)
      }
    })

    while (running.get()) {
      runOneJob(running)
    }

    logger.info("Exit")
  }

  def getWorker(job: BackgroundJob): Worker[_] = {
    val applicableWorkers = moonlight.workers.filter { worker =>
      worker.identifier == job.jobType || worker.previousIdentifiers.contains(job.jobType)
    }.toList

    applicableWorkers match {
      case Nil => throw new Exception(s"Unrecognized job type '${job.jobType}'.")
      case one :: Nil => app.injector.instanceOf(one.classTag)
      case multiple =>
        val names = multiple.map(_.classTag.getClass.getCanonicalName).mkString(", ")
        throw new Exception(s"Ambiguous job type '${job.jobType}'. Multiple workers ($names) are defined to process this job type.")
    }
  }

  def runOneJob(running: AtomicBoolean): Unit = {
    try {
      Await.ready(backgroundJobService.updateTimeoutJobs(), DEFAULT_FUTURE_TIMEOUT)

      Await.result(backgroundJobService.get(), DEFAULT_FUTURE_TIMEOUT) match {
        case Some(job) =>
          backgroundJobService.start(job.id, job.tryCount + 1)

          val runnable = getWorker(job)

          try {
            logger.info(s"Started ${runnable.getClass.getSimpleName}")
            runnable.run(job)
            backgroundJobService.succeed(job.id)
            logger.info(s"Succeed ${runnable.getClass.getSimpleName}")
          } catch {
            case e: InterruptedException => throw e
            case e: Throwable =>
              backgroundJobService.fail(job.id, e)
              logger.error(s"Error occurred while running job (id=${job.id}, type=${job.jobType}, params=${job
                .paramsInJsonString}.", e)
          }
        case None =>
          var count = 0
          while (running.get() && count < 10) {
            Thread.sleep(1000)
            count += 1
          }
      }

    } catch {
      case _: InterruptedException =>
        logger.info("Interrupted.")
        running.set(false)
      case e: Throwable =>
        logger.error("Error occurred while getting a background job.", e)
        running.set(false)
    }
  }
}
