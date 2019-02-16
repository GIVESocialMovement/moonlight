package worker

import givers.moonlight.{Config, Moonlight}
import play.api.{Configuration, Environment}

class MoonlightModule extends play.api.inject.Module {
  def bindings(environment: Environment, configuration: Configuration)  = Seq(
    bind[Moonlight].toInstance(new Moonlight(Config(maxErrorCountToKillOpt = Some(3)), Seq(SimpleWorkerSpec)))
  )
}
