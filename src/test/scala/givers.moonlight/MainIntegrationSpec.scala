package givers.moonlight

import java.util.Date

import helpers._
import play.api.inject.Module
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Configuration, Environment, Mode}
import utest._

import scala.concurrent.Future

object MainIntegrationSpec extends BaseSpec {

  val tests = Tests {
    resetDatabase()
    val app = new GuiceApplicationBuilder()
      .configure(Configuration.from(Map(
        "slick.dbs.default.db.properties.url" -> BaseSpec.DATABASE_URL,
        "slick.dbs.default.profile" -> "slick.jdbc.PostgresProfile$",
        "slick.dbs.default.db.dataSourceClass" -> "slick.jdbc.DatabaseUrlDataSource",
        "slick.dbs.default.db.properties.driver" -> "org.postgresql.Driver"
      )))
      .bindings(new Module {
        override def bindings(environment: Environment, configuration: Configuration) = Seq(
          bind[Moonlight].toInstance(new Moonlight(SimpleWorker))
        )
      })
      .in(Mode.Test)
      .build()
    val backgroundJobService = app.injector.instanceOf[BackgroundJobService]
    val main = app.injector.instanceOf[Main]

    "Queue and run" - {
      val job = await(backgroundJobService.queue(new Date(), SimpleWorker.Job("some data")))

      Future {
        while (!await(backgroundJobService.getById(job.id)).map(_.status).contains(BackgroundJob.Status.Succeeded)) {
          Thread.sleep(100)
        }

        main.running.set(false)
      }

      main.run(Array.empty)

      val finishedOpt = await(backgroundJobService.getById(job.id))
      assert(finishedOpt.map(_.status).contains(BackgroundJob.Status.Succeeded))
    }
  }
}
