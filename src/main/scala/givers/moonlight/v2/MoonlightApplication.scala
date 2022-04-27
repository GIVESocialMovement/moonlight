package givers.moonlight.v2

import akka.Done
import akka.actor.CoordinatedShutdown
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

    runLoop.onComplete {
      res =>
        logger.info(s"app is completed with result $res. Stopping ...")
        Play.stop(app)
    }

    app.injector.instanceOf[CoordinatedShutdown]
      .addTask(CoordinatedShutdown.PhaseServiceUnbind, "stop-main-loop") {
        () =>
          logger.info("cancelling run loop on shutdown")
          runLoopControl.cancel()

          runLoop.map(_ => Done)
      }
  }
}

object MoonlightApplication extends MoonlightApplicationBase {
  def main(args: Array[String]): Unit = {
    logger.info("starting the app")
    runApp(args)
  }
}