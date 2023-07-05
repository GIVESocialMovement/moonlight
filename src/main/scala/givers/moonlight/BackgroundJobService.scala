package givers.moonlight

import com.google.inject.ImplementedBy

import java.util.Date
import scala.concurrent.Future
import scala.language.implicitConversions

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

object BackgroundJobDescription {
  implicit class JobInDescriptor[IN](in: IN) {
    def describe(jobType: JobType[IN]): BackgroundJobDescription[IN] = {
      BackgroundJobDescription(jobType, in)
    }
  }

  object Implicits {

    /**
     * Implicit descriptor !! Use it only when IN class has only one executor. Otherwise use JobInDescriptor
     * @param in
     *   job in data
     * @tparam IN
     *   job in type
     * @return
     */
    implicit def describeImplicitly[IN: JobType](in: IN): BackgroundJobDescription[IN] = {
      BackgroundJobDescription(implicitly[JobType[IN]], in)
    }
  }
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
