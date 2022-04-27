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

  private val getJobsQuery = Compiled((skip: ConstColumn[Long], take: ConstColumn[Long]) =>
    backgroundJobs.sortBy(_.id.desc).drop(skip).take(take)
  )

  private val getJobsReadyForStartQuery = Compiled((
    desiredNumberOfJobs: ConstColumn[Long],
    maxAcceptableAttemptsCount: Rep[Int],
    now: Rep[Date],
    lastAttemptAfter: Rep[Date]
  ) => {
      backgroundJobs
        .filter { job =>
          (job.status === Status.Pending && job.shouldRunAt < now) ||
            (job.status === Status.Failed
              && job.tryCount < maxAcceptableAttemptsCount
              && job.finishedAtOpt < lastAttemptAfter) // one attempt for some period of time
        }
        .sortBy { job => (job.priority.asc, job.createdAt.asc) }
        .take(desiredNumberOfJobs)
  })

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

  private val markJobAsStartedSelectQuery = Compiled((bgJobId: Rep[Long]) =>
    backgroundJobs
      .filter(job => job.id === bgJobId && job.status =!= Status.Started)
      .map(job => (job.status, job.startedAtOpt, job.initiatedAtOpt, job.tryCount))
  )

  private val maintainExpiredJobsInitiatedQuery = Compiled((upperBoundTime: Rep[Date]) =>
    backgroundJobs
      .filter(job => job.status === Status.Initiated && job.initiatedAtOpt < upperBoundTime)
      .map(job => (job.status, job.error, job.finishedAtOpt))
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
    db.run(getJobsQuery(skip, take).result)
  }

  /**
   * @inheritdoc
   */
  override def getJobsReadyForStart(
    desiredNumberOfJobs: Long,
    maxAcceptableAttemptsCount: Int,
    now: Date,
    betweenAttemptInterval: FiniteDuration
  ): Future[Seq[BackgroundJob]] = {
    val lastAttemptAfter = now.sub(betweenAttemptInterval)

    db.run(getJobsReadyForStartQuery(desiredNumberOfJobs, maxAcceptableAttemptsCount, now, lastAttemptAfter).result)
  }

  /**
   * @inheritdoc
   */
  override def getJobReadyForStart(
    maxAcceptableAttemptsCount: Int,
    now: Date,
    betweenAttemptInterval: FiniteDuration
  ): Future[Option[BackgroundJob]] = {
    getJobsReadyForStart(1, maxAcceptableAttemptsCount, now, betweenAttemptInterval)
      .map(_.headOption)
  }

  /**
   * @inheritdoc
   */
  override def maintainExpiredJobs(jobRunTimeout: FiniteDuration, now: Date): Future[Int] = {
    val upperBoundTime = now.sub(jobRunTimeout)

    for {
      notStarted <- db.run {
        // this part of the method is deprecated because we don't use Initiated any more
        // this block is left for compatibility
        maintainExpiredJobsInitiatedQuery(upperBoundTime)
          .update((Status.Pending, "Timeout (from Initiated)", Some(now)))
      }
      startedButNotFinished <- db.run {
        maintainExpiredJobsStartedQuery(upperBoundTime)
          .update((Status.Failed, "Timeout (from Started)", Some(now)))
      }
    } yield notStarted + startedButNotFinished
  }

  /**
   * @inheritdoc
   */
  override def markJobAsStarted(bgJobId: Long, newTryCount: Int, updateDate: Date): Future[Boolean] = {
    db
      .run {
        markJobAsStartedSelectQuery(bgJobId).update((Status.Started, Some(updateDate), Some(updateDate), newTryCount))
      }
      .map(_ == 1)
  }

  /**
   * @inheritdoc
   */
  override def markJobAsSucceed(bgJobId: Long, updateDate: Date): Future[Unit] = {
    db
      .run(markJobAsSucceededSelectQuery(bgJobId).update((Status.Succeeded, Some(updateDate))))
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
