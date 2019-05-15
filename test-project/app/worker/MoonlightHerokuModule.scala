package worker

import com.google.inject.{Inject, Provider}
import givers.moonlight.{Config, Moonlight, StartJobResult}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.{Configuration, Environment}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class MoonlightHerokuModule extends play.api.inject.Module {
  def bindings(environment: Environment, configuration: Configuration)  = Seq(
    bind[Moonlight].toProvider[MoonlightHerokuProvider]
  )
}

class MoonlightHerokuProvider @Inject()(
  ws: WSClient,
) extends Provider[Moonlight] {
  def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)

  def get(): Moonlight = {
    new Moonlight(
      Config(maxErrorCountToKillOpt = Some(3), timeoutInMillis = 60L * 60L * 1000L),
      Seq(SimpleWorkerSpec),
      Some({ job =>
        try {
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
          println(result.status)
          println(result.body)
          StartJobResult(started = true)
        } catch {
          case e: Exception => StartJobResult(started = false)
        }
      })
    )
  }
}
