package givers.moonlight.util

import com.codahale.metrics.{DefaultSettableGauge, MetricRegistry, NoopMetricRegistry, SettableGauge}

/**
 * Registry of all metric names
 */
object Metrics {
  object jobDispatcher {
    val activeExecutorsCount = "job-dispatcher.jobs.active-executors-count"
    val jobsReadyToStart = "job-dispatcher.jobs.ready-to-start"
    val jobsOverall = "job-dispatcher.jobs.overall"

    val maintenanceOldJobs = "job-dispatcher.maintenance.old-jobs"
    val maintenanceOldJobsErrors = "job-dispatcher.maintenance.old-jobs.errors"

    val maintenanceUnstuck = "job-dispatcher.maintenance.unstuck"
    val maintenanceUnstuckErrors = "job-dispatcher.maintenance.unstuck.errors"
    val concurrentFail = s"job-dispatcher.concurrent-fail"
  }

  object executor {
    def duration(jobType: String) = s"executor.$jobType.execution"
    def started(jobType: String) = s"executor.$jobType.started"
    def succeeded(jobType: String) = s"executor.$jobType.succeeded"
    def failed(jobType: String) = s"executor.$jobType.failed"
  }

  implicit class MetricRegistryOps(val registry: MetricRegistry) extends AnyVal {
    def settableGauge[T](name: String): SettableGauge[T] = {
      registry match {
        case _: NoopMetricRegistry => new DefaultSettableGauge[T]
        case other => other.gauge[SettableGauge[T]](name)
      }
    }
  }
}
