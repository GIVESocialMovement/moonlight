package givers.moonlight

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
}
