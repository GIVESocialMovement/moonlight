package helpers

import givers.moonlight.BackgroundJob

import java.util.Date

trait BackgroundJobFixture {
  val anyJob: BackgroundJob = BackgroundJob(
    id = -1,
    createdAt = new Date(),
    shouldRunAt = new Date(),
    initiatedAtOpt = None,
    startedAtOpt = None,
    finishedAtOpt = None,
    status = BackgroundJob.Status.Pending,
    error = "",
    tryCount = 0,
    jobType = "",
    paramsInJsonString = "{}",
    priority = 1
  )

  def jobOfType(tp: String, id: Long): BackgroundJob = anyJob.copy(id = id, jobType = tp)
}
