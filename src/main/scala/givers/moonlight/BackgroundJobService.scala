package givers.moonlight

import com.google.inject.{Inject, Singleton}
import givers.moonlight.persistence.table.BackgroundJobTableComponent
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.OFormat
import slick.jdbc.JdbcProfile

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BackgroundJobService @Inject()(
  val dbConfigProvider: DatabaseConfigProvider
)(
  implicit ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] with BackgroundJobTableComponent {

  import dbConfig.profile.api._

  val query = backgroundJobs

  def queue[T <: Job](
    shouldRunAt: Date,
    priority: Int,
    param: T,
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
      paramsInJsonString = jsonFormat.writes(param).toString,
      priority = priority
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
      query.sortBy(_.id.desc).take(limit).result
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
        .sortBy { q => (q.priority.asc, q.createdAt.asc) }
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

  def updateTimeoutInitiatededJobs(timeoutInMillis: Long): Future[Unit] = {
    import BackgroundJob._

    val upperBoundTime = new Date(System.currentTimeMillis() - timeoutInMillis)

    db.run {
      query
        .filter { q =>
          q.status === BackgroundJob.Status.Initiated && q.initiatedAtOpt < upperBoundTime
        }
        .map { q => (q.status, q.error, q.finishedAtOpt) }
        .update((BackgroundJob.Status.Pending, "Timeout (from Initiated)", Some(new Date())))
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
        .map { q => (q.status, q.error, q.finishedAtOpt) }
        .update((BackgroundJob.Status.Failed, "Timeout (from Started)", Some(new Date())))
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
            (q.status, q.initiatedAtOpt, q.tryCount)
          }
          .update((BackgroundJob.Status.Pending, None, newTryCount))
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
