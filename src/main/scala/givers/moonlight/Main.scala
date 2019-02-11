package givers.moonlight

import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import com.google.inject.Inject
import play.api._
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}


class Moonlight(val workers: WorkerSpec*)

object Main {
  private[this] val logger = Logger(this.getClass)

  def main(args: Array[String]): Unit = try {
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
    } finally  {
      logger.info(s"Finished moonlight.Main ($mode)")
      Play.stop(app)
    }
  } catch { case e: Throwable =>
    logger.error("Error", e)
    System.exit(1) // force terminating all hanging threads. This prevents a hang when there's an exception.
  } finally {
    System.exit(0) // force terminating all hanging threads.
  }
}

class Main @Inject()(
  app: Application,
  moonlight: Moonlight,
  backgroundJobService: BackgroundJobService,
)(
  implicit ec: ExecutionContext
) {

  private[this] val DEFAULT_FUTURE_TIMEOUT = Duration.apply(5, TimeUnit.MINUTES)
  private[this] val logger = Logger(this.getClass)

  var sleep: Long => Unit = Thread.sleep
  val running = new AtomicBoolean(true)

  def run(args: Array[String]): Unit = {
    running.set(true)

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        logger.info("Run the shutdown hook.")
        running.set(false)
      }
    })

    while (running.get()) {
      runOneJob(running)
    }

    logger.info("Exit")
  }

  def getWorker(jobType: String): Worker[_] = {
    val applicableWorkers = moonlight.workers.filter { worker =>
      worker.identifier == jobType || worker.previousIdentifiers.contains(jobType)
    }.toList

    applicableWorkers match {
      case Nil => throw new Exception(s"Unrecognized job type '$jobType'.")
      case one :: Nil => app.injector.instanceOf(one.classTag)
      case multiple =>
        val names = multiple.map(_.classTag.getClass.getCanonicalName).mkString(", ")
        throw new Exception(s"Ambiguous job type '$jobType'. Multiple workers ($names) are defined to process this job type.")
    }
  }

  def runOneJob(running: AtomicBoolean): Unit = {
    try {
      await(backgroundJobService.updateTimeoutJobs())

      await(backgroundJobService.get()) match {
        case Some(job) =>
          await(backgroundJobService.start(job.id, job.tryCount + 1))

          val runnable = getWorker(job.jobType)

          val startInMillis = Instant.now().toEpochMilli
          try {
            logger.info(s"Started ${runnable.getClass.getSimpleName} (id=${job.id})")
            runnable.run(job)
            await(backgroundJobService.succeed(job.id))
            logger.info(s"Finished ${runnable.getClass.getSimpleName} (id=${job.id}) successfully")
          } catch {
            case e: InterruptedException => throw e
            case e: Throwable =>
              await(backgroundJobService.fail(job.id, e))
              logger.error(s"Error occurred while running ${runnable.getClass.getSimpleName} (id=${job.id}, type=${job.jobType}, params=${job.paramsInJsonString}.", e)
              logger.info(s"Finished ${runnable.getClass.getSimpleName} (id=${job.id}) with the above error")
          }
          val duration = Instant.now().toEpochMilli - startInMillis
          logger.info(s"The job (id=${job.id}) took $duration millis")
        case None =>
          var count = 0
          while (running.get() && count < 10) {
            sleep(1000)
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

  private[this] def await[T](future: Future[T]): T = {
    Await.result(future, DEFAULT_FUTURE_TIMEOUT)
  }
}
