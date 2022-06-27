package givers.moonlight


import play.api.libs.json.OFormat

import java.util.Date

case class BackgroundJob(
  id: Long,
  createdAt: Date,
  shouldRunAt: Date,
  initiatedAtOpt: Option[Date],
  startedAtOpt: Option[Date],
  finishedAtOpt: Option[Date],
  status: BackgroundJob.Status.Value,
  error: String,
  tryCount: Int,
  jobType: String,
  paramsInJsonString: String,
  priority: Int
)

object BackgroundJob {
  object Status extends Enumeration {
    type Status = Value
    val Pending, Started, Succeeded, Failed = Value
  }

  def forEnqueue[T <: Job](shouldRunAt: Date, priority: Int, param: T)
                     (implicit jsonFormat: OFormat[T], id: JobId[T]): BackgroundJob = {
    BackgroundJob(
      id = -1,
      createdAt = new Date(),
      shouldRunAt = shouldRunAt,
      initiatedAtOpt = None,
      startedAtOpt = None,
      finishedAtOpt = None,
      status = BackgroundJob.Status.Pending,
      error = "",
      tryCount = 0,
      jobType = id.value,
      paramsInJsonString = jsonFormat.writes(param).toString,
      priority = priority
    )
  }
}

