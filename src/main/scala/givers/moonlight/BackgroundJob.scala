package givers.moonlight

import java.util.Date

import slick.jdbc.PostgresProfile.api._

object BackgroundJob {
  object Status extends Enumeration {
    type Status = Value
    val Pending, Started, Succeeded, Failed = Value
  }

  def statusToSqlType(status: Status.Value) = status.toString
  implicit val StatusColumnType = MappedColumnType.base[Status.Value, String](statusToSqlType, Status.withName)

  def dateToSqlType(date: Date) = date.getTime
  implicit val DateColumnType = MappedColumnType.base[Date, Long](dateToSqlType, { t => new Date(t) })
}

case class BackgroundJob(
  id: Long,
  createdAt: Date,
  shouldRunAt: Date,
  startedAtOpt: Option[Date],
  finishedAtOpt: Option[Date],
  status: BackgroundJob.Status.Value,
  error: String,
  tryCount: Int,
  jobType: String,
  paramsInJsonString: String
)

class BackgroundJobTable(tag: Tag) extends Table[BackgroundJob](tag, "background_jobs") {

  import BackgroundJob._

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def createdAt = column[Date]("created_at")
  def shouldRunAt = column[Date]("should_run_at")
  def startedAtOpt = column[Option[Date]]("started_at_opt")
  def finishedAtOpt = column[Option[Date]]("finished_at_opt")
  def status = column[BackgroundJob.Status.Value]("status")
  def error = column[String]("error")
  def tryCount = column[Int]("try_count")
  def jobType = column[String]("job_type")
  def paramsInJsonString = column[String]("params_in_json_string")

  def * = (
    id,
    createdAt,
    shouldRunAt,
    startedAtOpt,
    finishedAtOpt,
    status,
    error,
    tryCount,
    jobType,
    paramsInJsonString
  ) <> ((BackgroundJob.apply _).tupled, BackgroundJob.unapply)
}
