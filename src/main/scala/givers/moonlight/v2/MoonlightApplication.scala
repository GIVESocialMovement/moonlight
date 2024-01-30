package givers.moonlight.v2

import akka.Done
import akka.actor.CoordinatedShutdown
import givers.moonlight.scheduled.quartz.QuartzScheduler
import play.api._
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.ExecutionContext

/**
 * Moonlight application v2 main
 */
trait MoonlightApplicationBase {
  protected val logger: Logger = Logger(this.getClass)

  def runApp(args: Array[String]): Unit = {

    val mode = args.head match {
      case "prod" => Mode.Prod
      case "dev" => Mode.Dev
      case "test" => Mode.Test
    }

    val app = GuiceApplicationBuilder(environment = Environment.simple(mode = mode)).build()

    implicit val executionContext: ExecutionContext = app.actorSystem.dispatcher

    Play.start(app)

    logger.info("app is started")

    val dispatcher = app.injector.instanceOf[JobDispatcher]

    // main app loop
    val (runLoopControl, runLoop) = dispatcher.runLoop()

    runLoop.onComplete { res =>
      logger.info(s"app is completed with result $res. Stopping ...")
      Play.stop(app)
    }

    val scheduler = app.injector.instanceOf[QuartzScheduler]
    val cancelScheduler = scheduler.run()

    app.injector
      .instanceOf[CoordinatedShutdown]
      .addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "stop-main-loop") { () =>
        logger.info("finishing run loop on app shutdown")
        runLoopControl.cancel()

        logger.info("finishing scheduler on app shutdown")
        val schedulerShutdown = cancelScheduler()

        for {
          _ <- runLoop
          _ = logger.info("run loop finished")
          _ <- schedulerShutdown
          _ = logger.info("scheduler finished")
        } yield Done
      }
  }
}

object MoonlightApplication extends MoonlightApplicationBase {
  def main(args: Array[String]): Unit = {
    logger.info("starting the app")
    runApp(args)
  }
}
