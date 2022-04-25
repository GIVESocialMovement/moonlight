package givers.moonlight.persistence.table

import com.google.inject.ImplementedBy
import givers.moonlight.BackgroundJob
import givers.moonlight.BackgroundJob.Status
import givers.moonlight.persistence.SlickJdbcProfile
import givers.moonlight.persistence.table.postgres.PgBackgroundJobTableComponent

import java.util.Date

@ImplementedBy(classOf[PgBackgroundJobTableComponent])
trait BackgroundJobTableComponent extends SlickJdbcProfile {
  import profile.api._

  def statusToSqlType(status: Status.Value): String = status.toString
  implicit val StatusColumnType: BaseColumnType[Status.Value] = MappedColumnType.base[Status.Value, String](statusToSqlType, Status.withName)

  def dateToSqlType(date: Date): Long = date.getTime
  implicit val DateColumnType: BaseColumnType[Date] = MappedColumnType.base[Date, Long](dateToSqlType, { t => new Date(t) })

  class BackgroundJobTable(tag: Tag) extends Table[BackgroundJob](tag, "background_jobs") {

    import BackgroundJob._

    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def createdAt = column[Date]("created_at")
    def shouldRunAt = column[Date]("should_run_at")
    def initiatedAtOpt = column[Option[Date]]("initiated_at_opt")
    def startedAtOpt = column[Option[Date]]("started_at_opt")
    def finishedAtOpt = column[Option[Date]]("finished_at_opt")
    def status = column[Status.Value]("status")
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

  val backgroundJobs = TableQuery[BackgroundJobTable]
}
