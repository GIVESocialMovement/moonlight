package givers.moonlight

import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import com.google.inject.Inject
import givers.moonlight.BackgroundJob.Status
import play.api._
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}


case class Config(
  maxErrorCountToKillOpt: Option[Int]
)

class Moonlight(
  val config: Config,
  val workers: Seq[WorkerSpec],
  val startJobOpt: Option[BackgroundJob => Unit]
)

object Main {
  private[this] val logger = Logger(this.getClass)

  def main(args: Array[String]): Unit = try {
    val mode = args.head match {
      case "prod" => Mode.Prod
      case "dev" => Mode.Dev
      case "test" => Mode.Test
    }

    val app = GuiceApplicationBuilder(environment = Environment.simple(mode = mode)).build()

    try {
      Play.start(app)

      val runner = args(1) match {
        case "run" => app.injector.instanceOf[Run]
        case "coordinate" => app.injector.instanceOf[Coordinate]
        case "work" => app.injector.instanceOf[Work]
      }

      logger.info(s"Start moonlight.Main ($mode, ${runner.getClass})")
      runner.run(args.drop(2))
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

sealed abstract class Main {
  def run(args: Array[String]): Unit

  private[this] val DEFAULT_FUTURE_TIMEOUT = Duration.apply(5, TimeUnit.MINUTES)
  protected[this] def await[T](future: Future[T]): T = {
    Await.result(future, DEFAULT_FUTURE_TIMEOUT)
  }
}

abstract class BaseCoordinate extends Main {
  def app: Application
  def moonlight: Moonlight
  def backgroundJobService: BackgroundJobService
  def runJob(job: BackgroundJob): Unit

  private[this] val logger = Logger(this.getClass)

  val errorCount = new AtomicInteger(0)

  private[moonlight] var sleep: Long => Unit = Thread.sleep
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
      pickAndRunJob(running)
    }
  }

  def pickAndRunJob(running: AtomicBoolean): Unit = {
    try {
      await(backgroundJobService.updateTimeoutJobs())

      await(backgroundJobService.get()) match {
        case Some(job) =>
          try {
            await(backgroundJobService.start(job.id, job.tryCount + 1))
            runJob(job)
          } catch {
            case e: InterruptedException => throw e
            case _: Throwable =>
              errorCount.incrementAndGet()

              moonlight.config.maxErrorCountToKillOpt.foreach { maxErrorCountToKill =>
                if (maxErrorCountToKill <= errorCount.get) {
                  logger.warn(s"Too many errors (maxErrorCountToKill = $maxErrorCountToKill, currentErrorCount = ${errorCount.get}). Exit")
                  running.set(false)
                }
              }
          }
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

}

class Coordinate @Inject()(
  val app: Application,
  val moonlight: Moonlight,
  val backgroundJobService: BackgroundJobService,
)(
  implicit ec: ExecutionContext
) extends BaseCoordinate {
  private[this] val logger = Logger(this.getClass)

  def runJob(job: BackgroundJob): Unit = {
    logger.info(s"Coordinate starts the job (id=${job.id})")
    moonlight.startJobOpt.get.apply(job)
  }
}

class Work @Inject()(
  app: Application,
  moonlight: Moonlight,
  backgroundJobService: BackgroundJobService,
)(
  implicit ec: ExecutionContext
) extends Main {
  private[this] val logger = Logger(this.getClass)

  def run(args: Array[String]): Unit = {
    val id = args.head.toLong
    assert(args.size == 1)

    val job = await(backgroundJobService.getById(id)).getOrElse {
      throw new Exception(s"The background job (id=$id) doesn't exist.")
    }

    if (job.status != Status.Started) {
      throw new Exception(s"The background job's status isn't 'Started'; it is ${job.status}")
    }

    runJob(job)
  }

  def runJob(job: BackgroundJob): Unit = {
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
        throw e
    }
    val duration = Instant.now().toEpochMilli - startInMillis
    logger.info(s"The job (id=${job.id}) took $duration millis")
  }

  private[moonlight] def getWorker(jobType: String): Worker[_] = {
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
}

class Run @Inject()(
  val app: Application,
  val moonlight: Moonlight,
  val backgroundJobService: BackgroundJobService,
  work: Work
)(
  implicit ec: ExecutionContext
) extends BaseCoordinate {

  override def runJob(job: BackgroundJob): Unit = {
    work.runJob(job)
  }
}
