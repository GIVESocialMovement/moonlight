package worker

import com.google.inject.{Inject, Provider}
import givers.moonlight.{Config, Moonlight, StartJobResult}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.{Configuration, Environment}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.sys.process.Process

class MoonlightLocalModule extends play.api.inject.Module {
  def bindings(environment: Environment, configuration: Configuration)  = Seq(
    bind[Moonlight].toProvider[MoonlightLocalProvider]
  )
}

class MoonlightLocalProvider @Inject()(
  ws: WSClient,
) extends Provider[Moonlight] {
  def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)

  def get(): Moonlight = {
    new Moonlight(
      Config(maxErrorCountToKillOpt = Some(3), timeoutInMillis = 60L * 60L * 1000L),
      Seq(SimpleWorkerSpec),
      Some({ job =>
        Process(
          Seq(
            "sbt",
            s"runMain givers.moonlight.Main dev work ${job.id}"
          )
        ).!
        StartJobResult(started = false)
      }),
      Some({ () => true })
    )
  }
}
