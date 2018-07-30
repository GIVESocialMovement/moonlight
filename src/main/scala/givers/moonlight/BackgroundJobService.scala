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
  val ONE_HOUR_IN_MILLIS = 3600000L
}

@Singleton
class BackgroundJobService @Inject()(
  val dbConfigProvider: DatabaseConfigProvider
)(
  implicit ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import BackgroundJobService._
  import dbConfig.profile.api._

  val query = TableQuery[BackgroundJobTable]

  private[this] val DEFAULT_TIMEOUT = Duration.apply(10, TimeUnit.SECONDS)
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
    val oneHourAgo = new Date(now.getTime - ONE_HOUR_IN_MILLIS)

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

  def updateTimeoutJobs(): Future[Unit] = {
    import BackgroundJob._

    val threeHoursAgo = new Date(System.currentTimeMillis() - ONE_HOUR_IN_MILLIS)

    db.run {
      query
        .filter { q =>
          q.status === BackgroundJob.Status.Started &&
            q.startedAtOpt < threeHoursAgo
        }
        .map { q => (q.status, q.error) }
        .update((BackgroundJob.Status.Failed, "Timeout"))
    }.map { _ => () }
  }

  def start(id: Long, newTryCount: Int): Unit = {
    import BackgroundJob._

    Await.result(
      awaitable = db.run {
        query
          .filter(_.id === id)
          .map { q =>
            (q.status, q.startedAtOpt, q.tryCount)
          }
          .update((BackgroundJob.Status.Started, Some(new Date()), newTryCount))
      },
      atMost = DEFAULT_TIMEOUT
    )

    ()
  }

  def succeed(id: Long): Unit = {
    import BackgroundJob._

    Await.result(
      awaitable = db.run {
        query
          .filter(_.id === id)
          .map { q =>
            (q.status, q.finishedAtOpt)
          }
          .update((BackgroundJob.Status.Succeeded, Some(new Date())))
      },
      atMost = DEFAULT_TIMEOUT
    )

    ()
  }

  def fail(id: Long, e: Throwable): Unit = {
    import BackgroundJob._

    Await.result(
      awaitable = db.run {
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
      },
      atMost = DEFAULT_TIMEOUT
    )

    ()
  }
}
