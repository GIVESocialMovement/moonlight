package executors

import com.codahale.metrics.{MetricAttribute, MetricFilter, MetricRegistry}
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import com.google.inject.{Inject, Provider}
import givers.moonlight.v2.MoonlightSettings
import play.api.inject.Binding
import play.api.{Configuration, Environment}

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._
import scala.jdk.CollectionConverters.SetHasAsJava

class MoonlightSettingsLocalModule extends play.api.inject.Module {
  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
      val graphite = new Graphite(new InetSocketAddress("localhost", 2003))

      val registry = new MetricRegistry()
      val reporter = GraphiteReporter
        .forRegistry(registry)
        .disabledMetricAttributes(
          Set(
            // list of metrics, not used right now
            MetricAttribute.MAX,
            MetricAttribute.MEAN,
            MetricAttribute.MIN,
            MetricAttribute.STDDEV,
            MetricAttribute.P50,
            MetricAttribute.P75,
            // MetricAttribute.P95,
            MetricAttribute.P98,
            // MetricAttribute.P99,
            MetricAttribute.P999,
            // MetricAttribute.COUNT,
            MetricAttribute.M1_RATE,
            MetricAttribute.M5_RATE,
            MetricAttribute.M15_RATE,
            MetricAttribute.MEAN_RATE
          ).asJava
        )
        .prefixedWith(s"giveasia.dev.test-project")
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .filter(MetricFilter.ALL)
        .build(graphite)

      reporter.start(1000, TimeUnit.MILLISECONDS)
    Seq(
      bind[MetricRegistry].toInstance(registry),
      bind[MoonlightSettings].toProvider[MoonlightSettingsLocalProvider]
    )
  }
}

object MoonlightSettingsLocalProvider {
  var settings: AtomicReference[MoonlightSettings] = new AtomicReference(null)
}

class MoonlightSettingsLocalProvider @Inject()(simpleExecutor: SimpleExecutor) extends Provider[MoonlightSettings] {
  def get(): MoonlightSettings = {
    if(MoonlightSettingsLocalProvider.settings.get() == null) {
      MoonlightSettingsLocalProvider.settings.compareAndSet(null, new MoonlightSettings(
        parallelism = Runtime.getRuntime.availableProcessors(),
        pauseDurationWhenNoJobs = 10.seconds,
        maintenanceInterval = 1.minutes,
        betweenRunAttemptInterval = 10.minutes,
        countMetricsCollectionInterval = 1.minute,
        maxJobRetries = 3,
        jobRunTimeout = 10.seconds,
        completedJobsTtl = (24 * 30).hours,
        executors = Seq(simpleExecutor)
      ))
    }

    MoonlightSettingsLocalProvider.settings.get()
  }
}
