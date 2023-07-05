package givers.moonlight

import com.google.inject.ImplementedBy
import play.api.libs.json.OFormat

import java.util.Date
import scala.concurrent.Future

case class BackgroundJobPriority(intRepresentation: Int)

object BackgroundJobPriority {
  val URGENT: BackgroundJobPriority = BackgroundJobPriority(1)
  val HIGH: BackgroundJobPriority = BackgroundJobPriority(5)
  val NORMAL: BackgroundJobPriority = BackgroundJobPriority(10)
  val LOW: BackgroundJobPriority = BackgroundJobPriority(15)
  val MINIMUM: BackgroundJobPriority = BackgroundJobPriority(20)
}

case class BackgroundJobDescription[IN](jobType: JobType[IN], jobIn: IN) {
  def serializeIn: String = jobType.serDe.serialize(jobIn)
}

@ImplementedBy(classOf[BackgroundJobServiceImpl])
trait BackgroundJobService {

  /**
   * Enqueue background job
   *
   * @param job
   *   job with input
   * @param delayTo
   *   if job should be executed not right now
   * @param priority
   *   job priority
   * @return
   */
  def enqueue[IN](
    job: BackgroundJobDescription[IN],
    delayTo: Option[Date] = None,
    priority: BackgroundJobPriority = BackgroundJobPriority.NORMAL
  ): Future[BackgroundJob]
}
