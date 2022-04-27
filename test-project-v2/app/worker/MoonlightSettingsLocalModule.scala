package worker

import com.google.inject.{Inject, Provider}
import givers.moonlight.v2.MoonlightSettings
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.{Configuration, Environment}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.sys.process.Process

class MoonlightSettingsLocalModule extends play.api.inject.Module {
  def bindings(environment: Environment, configuration: Configuration)  = Seq(
    bind[MoonlightSettings].toProvider[MoonlightSettingsLocalProvider]
  )
}

class MoonlightSettingsLocalProvider extends Provider[MoonlightSettings] {
  def get(): MoonlightSettings = {
    new MoonlightSettings(
      parallelism = Runtime.getRuntime().availableProcessors(),
      pauseDurationWhenNoJobs = 10.seconds,
      maintenanceInterval = 1.minutes,
      betweenRunAttemptInterval = 10.minutes,
      maxJobRetries = 3,
      jobRunTimeout = 10.seconds,
      workerSpecs = Seq(SimpleWorkerSpec)
    )
  }
}
