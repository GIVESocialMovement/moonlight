package givers.moonlight

import com.google.inject.{Inject, Singleton}
import givers.moonlight.util.DateTimeFactory
import givers.moonlight.v2.repository.BackgroundJobRepository

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BackgroundJobServiceImpl @Inject() (repo: BackgroundJobRepository, dateTimeFactory: DateTimeFactory)(implicit
  ec: ExecutionContext
) extends BackgroundJobService {

  /**
   * @inheritdoc
   */
  def enqueue[IN](
    job: BackgroundJobDescription[IN],
    delayTo: Option[Date] = None,
    priority: BackgroundJobPriority = BackgroundJobPriority.NORMAL
  ): Future[BackgroundJob] = {
    val backgroundJob = BackgroundJob(
      id = -1,
      createdAt = dateTimeFactory.now,
      shouldRunAt = delayTo.getOrElse(dateTimeFactory.now),
      initiatedAtOpt = None,
      startedAtOpt = None,
      finishedAtOpt = None,
      status = BackgroundJob.Status.Pending,
      error = "",
      tryCount = 0,
      jobType = job.jobType.id,
      paramsInJsonString = job.serializeIn,
      priority = priority.intRepresentation
    )

    repo.enqueue(backgroundJob)
  }
}
