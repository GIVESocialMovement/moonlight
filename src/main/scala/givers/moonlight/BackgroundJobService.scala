package givers.moonlight

import java.util.Date
import java.util.concurrent.TimeUnit

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.OFormat
import slick.jdbc.JdbcProfile

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object BackgroundJobService {
  val TEN_MINUTES_IN_MILLIS = 10L * 60L * 1000L
  val FIVE_MINUTES_IN_MILLIS = 5L * 60L * 1000L
}

@Singleton
class BackgroundJobService @Inject()(
  val dbConfigProvider: DatabaseConfigProvider
)(
  implicit ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import dbConfig.profile.api._

  val query = TableQuery[BackgroundJobTable]

  private[this] val logger = Logger(this.getClass)

  def queue[T <: Job](
    shouldRunAt: Date,
    param: T
  )(
    implicit jsonFormat: OFormat[T],
    id: JobId[T]
  ): Future[BackgroundJob] = {
    val job = BackgroundJob(
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
      paramsInJsonString = jsonFormat.writes(param).toString
    )

    db.run {
      (query returning query.map(_.id)) += job
    }.map { insertedId =>
      job.copy(id = insertedId)
    }
  }

  def getAll(limit: Int): Future[Seq[BackgroundJob]] = {
    import BackgroundJob._
    db.run {
      query.sortBy(_.createdAt.desc).take(limit).result
    }
  }

  def get(): Future[Option[BackgroundJob]] = {
    import BackgroundJob._

    val now = new Date()
    val oneHourInMillis = 60L * 60L * 1000L
    val oneHourAgo = new Date(now.getTime - oneHourInMillis)

    db.run {
      query
        .filter { q =>
          (q.status === BackgroundJob.Status.Pending && q.shouldRunAt < now) ||
            (q.status === BackgroundJob.Status.Failed && q.tryCount < 3 && q.finishedAtOpt < oneHourAgo)
        }
        .sortBy(_.createdAt.asc)
        .take(1)
        .result
    }.map(_.headOption)
  }

  def getById(id: Long): Future[Option[BackgroundJob]] = {
    db
      .run {
        query.filter(_.id === id).take(1).result
      }
      .map(_.headOption)
  }

  def updateTimeoutInitiatededJobs(): Future[Unit] = {
    import BackgroundJob._

    val upperBoundTime = new Date(System.currentTimeMillis() - BackgroundJobService.TEN_MINUTES_IN_MILLIS)

    db.run {
      query
        .filter { q =>
          q.status === BackgroundJob.Status.Initiated && q.initiatedAtOpt < upperBoundTime
        }
        .map { q => (q.status, q.error) }
        .update((BackgroundJob.Status.Failed, "Timeout"))
    }.map { _ => () }
  }

  def updateTimeoutStartedJobs(timeoutInMillis: Long): Future[Unit] = {
    import BackgroundJob._

    val upperBoundTime = new Date(System.currentTimeMillis() - timeoutInMillis)

    db.run {
      query
        .filter { q =>
          q.status === BackgroundJob.Status.Started && q.startedAtOpt < upperBoundTime
        }
        .map { q => (q.status, q.error) }
        .update((BackgroundJob.Status.Failed, "Timeout"))
    }.map { _ => () }
  }

  def initiate(id: Long, newTryCount: Int): Future[Unit] = {
    import BackgroundJob._

    db
      .run {
        query
          .filter(_.id === id)
          .map { q =>
            (q.status, q.initiatedAtOpt, q.tryCount)
          }
          .update((BackgroundJob.Status.Initiated, Some(new Date()), newTryCount))
      }
      .map { _ => () }
  }

  def uninitiate(id: Long, newTryCount: Int): Future[Unit] = {
    import BackgroundJob._

    db
      .run {
        query
          .filter(_.id === id)
          .map { q =>
            (q.status, q.initiatedAtOpt, q.tryCount, q.shouldRunAt)
          }
          .update((BackgroundJob.Status.Pending, None, newTryCount, new Date(System.currentTimeMillis() + BackgroundJobService.FIVE_MINUTES_IN_MILLIS)))
      }
      .map { _ => () }
  }

  def start(id: Long): Future[Unit] = {
    import BackgroundJob._

    db
      .run {
        query
          .filter(_.id === id)
          .map { q =>
            (q.status, q.startedAtOpt)
          }
          .update((BackgroundJob.Status.Started, Some(new Date())))
      }
      .map { _ => () }
  }

  def succeed(id: Long): Future[Unit] = {
    import BackgroundJob._

    db
      .run {
        query
          .filter(_.id === id)
          .map { q =>
            (q.status, q.finishedAtOpt)
          }
          .update((BackgroundJob.Status.Succeeded, Some(new Date())))
      }
      .map { _ => () }

  }

  def fail(id: Long, e: Throwable): Future[Unit] = {
    import BackgroundJob._

    db
      .run {
        query
          .filter(_.id === id)
          .map { q =>
            (q.status, q.finishedAtOpt, q.error)
          }
          .update((
            BackgroundJob.Status.Failed,
            Some(new Date()),
            s"${e.getMessage}\n${e.getStackTrace.mkString("\n")}"
          ))
      }
      .map { _ => () }
  }
}
