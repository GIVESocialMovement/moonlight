package executors

import com.codahale.metrics.{MetricRegistry, NoopMetricRegistry}
import com.google.inject.{Inject, Provider}
import givers.moonlight.scheduled.SchedulerInput
import givers.moonlight.scheduled.CronSchedule.fromValidCronExpression
import givers.moonlight.scheduled.quartz.QuartzCronExpression.cronExpression
import givers.moonlight.v2.MoonlightSettings
import play.api.inject.Binding
import play.api.{Configuration, Environment}

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.duration._

class MoonlightSettingsLocalModule extends play.api.inject.Module {
  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] = {
    Seq(
      bind[MetricRegistry].toInstance(new NoopMetricRegistry),
      bind[MoonlightSettings].toProvider[MoonlightSettingsLocalProvider]
    )
  }
}

object MoonlightSettingsLocalProvider {
  var settings: AtomicReference[MoonlightSettings] = new AtomicReference(null)
}

class MoonlightSettingsLocalProvider @Inject() (simpleExecutor: SimpleExecutor) extends Provider[MoonlightSettings] {
  def get(): MoonlightSettings = {
    if (MoonlightSettingsLocalProvider.settings.get() == null) {
      val atomic = new AtomicInteger()
      MoonlightSettingsLocalProvider.settings.compareAndSet(
        null,
        new MoonlightSettings(
          parallelism = Runtime.getRuntime.availableProcessors(),
          pauseDurationWhenNoJobs = 10.seconds,
          maintenanceInterval = 1.minutes,
          betweenRunAttemptInterval = 10.minutes,
          countMetricsCollectionInterval = 1.minute,
          maxJobRetries = 3,
          jobRunTimeout = 10.seconds,
          completedJobsTtl = (24 * 30).hours,
          executors = Seq(simpleExecutor),
          schedulerInputs = Seq(
            // each minute
            SchedulerInput((atomic, "job1"), cronExpression("0 * * * * ? *"), 10.seconds).forJob[SimpleScheduledJob],
            // each 3 minutes
            SchedulerInput((atomic, "job2"), cronExpression("0 */3 * * * ? *"), 10.seconds).forJob[SimpleScheduledJob]
          )
        )
      )
    }

    MoonlightSettingsLocalProvider.settings.get()
  }
}
