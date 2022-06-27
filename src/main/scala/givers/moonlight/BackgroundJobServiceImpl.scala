package givers.moonlight

import com.google.inject.{Inject, Singleton}
import givers.moonlight.util.DateTimeFactory
import givers.moonlight.v2.repository.BackgroundJobRepository
import play.api.libs.json.OFormat

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BackgroundJobServiceImpl @Inject()(repo: BackgroundJobRepository, dateTimeFactory: DateTimeFactory)
                                        (implicit ec: ExecutionContext)
  extends LegacyBackgroundJobService
    with BackgroundJobService {

  def enqueue[T <: Job](shouldRunAt: Date, priority: Int, param: T)
                       (implicit jsonFormat: OFormat[T], id: JobId[T]): Future[BackgroundJob] = {
    val backgroundJob = BackgroundJob(
      id = -1,
      createdAt = dateTimeFactory.now,
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

    repo.enqueue(backgroundJob)
  }
}
