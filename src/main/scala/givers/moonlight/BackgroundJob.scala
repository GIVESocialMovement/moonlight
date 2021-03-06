package givers.moonlight

import java.util.Date

import slick.jdbc.PostgresProfile.api._

object BackgroundJob {
  object Status extends Enumeration {
    type Status = Value
    val Pending, Initiated, Started, Succeeded, Failed = Value
  }

  def statusToSqlType(status: Status.Value) = status.toString
  implicit val StatusColumnType: BaseColumnType[Status.Value] = MappedColumnType.base[Status.Value, String](statusToSqlType, Status.withName)

  def dateToSqlType(date: Date) = date.getTime
  implicit val DateColumnType: BaseColumnType[Date] = MappedColumnType.base[Date, Long](dateToSqlType, { t => new Date(t) })
}

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

class BackgroundJobTable(tag: Tag) extends Table[BackgroundJob](tag, "background_jobs") {

  import BackgroundJob._

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def createdAt = column[Date]("created_at")
  def shouldRunAt = column[Date]("should_run_at")
  def initiatedAtOpt = column[Option[Date]]("initiated_at_opt")
  def startedAtOpt = column[Option[Date]]("started_at_opt")
  def finishedAtOpt = column[Option[Date]]("finished_at_opt")
  def status = column[BackgroundJob.Status.Value]("status")
  def error = column[String]("error")
  def tryCount = column[Int]("try_count")
  def jobType = column[String]("job_type")
  def paramsInJsonString = column[String]("params_in_json_string")
  def priority = column[Int]("priority")

  def * = (
    id,
    createdAt,
    shouldRunAt,
    initiatedAtOpt,
    startedAtOpt,
    finishedAtOpt,
    status,
    error,
    tryCount,
    jobType,
    paramsInJsonString,
    priority
  ) <> ((BackgroundJob.apply _).tupled, BackgroundJob.unapply)
}
