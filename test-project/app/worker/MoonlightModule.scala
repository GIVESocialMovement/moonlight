package worker

import com.google.inject.{Inject, Provider}
import givers.moonlight.{Config, Moonlight}
import play.api.libs.json.Json
import play.api.{Configuration, Environment}
import play.api.libs.ws.WSClient

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.sys.process.Process

class MoonlightModule extends play.api.inject.Module {
  def bindings(environment: Environment, configuration: Configuration)  = {

    Seq(
      bind[Moonlight].toProvider[MoonlightProvider]
    )
  }
}

class MoonlightProvider @Inject()(
  ws: WSClient,
) extends Provider[Moonlight] {
  def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)

  def get(): Moonlight = {
    new Moonlight(
      Config(maxErrorCountToKillOpt = Some(3)),
      Seq(SimpleWorkerSpec),
      Some({ job =>
        var status = 422

        while (status == 422) {
          val result = await(ws
            .url("https://api.heroku.com/apps/moonlight-test/dynos")
            .withHttpHeaders(
              "Accept" -> "application/vnd.heroku+json; version=3",
              "Authorization" -> s"Bearer ${sys.env.apply("HEROKU_API_KEY")}",
              "Content-Type" -> "application/json"
            )
            .post(Json.obj(
              "attach" -> false,
              "command" ->  s"./target/universal/stage/bin/test-project -Dconfig.resource=heroku.conf -main givers.moonlight.Main -- prod work ${job.id}",
              "env" -> Json.obj(),
              "size" ->  "free",
              "type" -> "run",
              "time_to_live" -> 1800
            )))

          status = result.status

          if (status == 200) {
            // do nothing
          } else if (status == 422) {
            println("Go over the heroku's one-off dyno limit. Wait and retry.")
            println(result.body)
            Thread.sleep(10000)
          } else {
            throw new Exception(s"Error trying to start a one-off dyno. status=${result.status}, body=${result.body}")
          }
        }

      })
    )
  }
}
