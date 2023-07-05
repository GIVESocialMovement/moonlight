package givers.moonlight.util

/**
 * Registry of all metric names
 */
object Metrics {
  object jobDispatcher {
    val jobsReadyToStart = "job-dispatcher.jobs.ready-to-start"
    val jobsOverall = "job-dispatcher.jobs.overall"

    val maintenanceOldJobs = "job-dispatcher.maintenance.old-jobs"
    val maintenanceOldJobsErrors = "job-dispatcher.maintenance.old-jobs.errors"

    val maintenanceUnstuck = "job-dispatcher.maintenance.unstuck"
    val maintenanceUnstuckErrors = "job-dispatcher.maintenance.unstuck.errors"
    val concurrentFail = s"job-dispatcher.concurrent-fail"
  }

  object executor {
    def duration(jobType: String) = s"executor.${jobType.toLowerCase}.execution"
    def succeeded(jobType: String) = s"executor.${jobType.toLowerCase}.succeeded"
    def failed(jobType: String) = s"executor.${jobType.toLowerCase}.failed"
  }
}
