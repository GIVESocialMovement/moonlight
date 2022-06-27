package givers.moonlight.v2.repository

import com.google.inject.Inject
import givers.moonlight.BackgroundJob
import givers.moonlight.BackgroundJob._
import givers.moonlight.persistence.table.BackgroundJobTableComponent
import givers.moonlight.util.RichDate.RichDate
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import java.util.Date
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
 * Background job repository
 *
 * @param dbConfigProvider db config provider
 * @param table table component
 * @param ec execution context
 */
class BackgroundJobJdbcRepository @Inject()(
  val dbConfigProvider: DatabaseConfigProvider,
  table: BackgroundJobTableComponent
)(
  implicit ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] with BackgroundJobRepository {

  import dbConfig.profile.api._
  import table._

  private val markJobAsFailedSelectQuery = Compiled((bgJobId: Rep[Long]) =>
    backgroundJobs
      .filter(_.id === bgJobId)
      .map(job => (job.status, job.finishedAtOpt, job.error))
  )

  private val markJobAsSucceededSelectQuery = Compiled((bgJobId: Rep[Long]) =>
    backgroundJobs
      .filter(_.id === bgJobId)
      .map(job => (job.status, job.finishedAtOpt))
  )

  private val markJobAsStartedSelectQuery = Compiled((
      bgJobId: Rep[Long],
      maxAcceptableAttemptsCount: Rep[Int],
      now: Rep[Date],
      lastAttemptAfter: Rep[Date]
    ) =>
      backgroundJobs
        .filter(job => job.id === bgJobId && jobCanBeStartedConditions(job, maxAcceptableAttemptsCount, now, lastAttemptAfter))
        .map(job => (job.status, job.startedAtOpt, job.initiatedAtOpt, job.tryCount))
  )

  private val maintainExpiredJobsStartedQuery = Compiled((upperBoundTime: Rep[Date]) =>
    backgroundJobs
      .filter(q => q.status === Status.Started && q.startedAtOpt < upperBoundTime)
      .map(job => (job.status, job.error, job.finishedAtOpt))
  )

  /**
   * @inheritdoc
   */
  override def enqueue(bgJob: BackgroundJob): Future[BackgroundJob] = {
    db
      .run((backgroundJobs returning backgroundJobs.map(_.id)) += bgJob)
      .map(insertedId => bgJob.copy(id = insertedId))
  }

  /**
   * @inheritdoc
   */
  override def getJobs(skip: Long, take: Long): Future[Seq[BackgroundJob]] = {
    db.run(backgroundJobs.sortBy(_.id.desc).drop(skip).take(take).result)
  }

  /**
   * Conditions for job to be started
   *
   * @param job background job table
   * @param maxAcceptableAttemptsCount "try count" upper bound
   * @param now current date
   * @param lastAttemptAfter when the job after failed finish can be tried again
   *
   * @return
   */
  private def jobCanBeStartedConditions(
    job: BackgroundJobTable,
    maxAcceptableAttemptsCount: Rep[Int],
    now: Rep[Date],
    lastAttemptAfter: Rep[Date]
  ): Rep[Option[Boolean]] = {
    (job.status === Status.Pending && job.shouldRunAt <= now) ||
      (job.status === Status.Failed
        && job.tryCount < maxAcceptableAttemptsCount
        && job.finishedAtOpt < lastAttemptAfter) // one attempt for some period of time
  }

  /**
   * @inheritdoc
   */
  override def getJobsReadyForStart(
    desiredNumberOfJobs: Long,
    checkParams: JobReadyForStartCheckParams
  ): Future[Seq[BackgroundJob]] = {

    db.run(
      // this request can't be compiled because of inSet
      backgroundJobs
        .filter(
          jobCanBeStartedConditions(_, checkParams.maxAcceptableAttemptsCount, checkParams.now, checkParams.lastAttemptAfter)
        )
        .sortBy { job => (job.priority.asc, job.createdAt.asc) }
        .take(desiredNumberOfJobs)
        .result
    )
  }

  /**
   * @inheritdoc
   */
  override def getJobReadyForStart(
    checkParams: JobReadyForStartCheckParams
  ): Future[Option[BackgroundJob]] = {
    getJobsReadyForStart(1, checkParams).map(_.headOption)
  }

  /**
   * @inheritdoc
   */
  override def maintainExpiredJobs(jobRunTimeout: FiniteDuration, now: Date): Future[Int] = {
    val upperBoundTime = now.sub(jobRunTimeout)

    db.run {
      maintainExpiredJobsStartedQuery(upperBoundTime).update((Status.Failed, "Timeout (from Started)", Some(now)))
    }
  }

  /**
   * @inheritdoc
   */
  override def tryMarkJobAsStarted(
    bgJobId: Long,
    newTryCount: Int,
    updateDate: Date,
    checkParams: JobReadyForStartCheckParams
  ): Future[Boolean] = {
    db
      .run(
        markJobAsStartedSelectQuery(
          bgJobId, checkParams.maxAcceptableAttemptsCount, checkParams.now, checkParams.lastAttemptAfter
        ).update((Status.Started, Some(updateDate), Some(updateDate), newTryCount))
      )
      .map(_ == 1)
  }

  /**
   * @inheritdoc
   */
  override def markJobAsSucceed(bgJobId: Long, updateDate: Date): Future[Unit] = {
    db
      .run(
        markJobAsSucceededSelectQuery(bgJobId).update((Status.Succeeded, Some(updateDate)))
      )
      .map(_ => ())
  }

  /**
   * @inheritdoc
   */
  override def markJobAsFailed(bgJobId: Long, cause: Throwable, updateDate: Date): Future[Unit] = {
    val error = s"${cause.getMessage}\n${cause.getStackTrace.mkString("\n")}"

    db
      .run(markJobAsFailedSelectQuery(bgJobId).update((Status.Failed, Some(updateDate), error)))
      .map(_ => ())
  }
}
