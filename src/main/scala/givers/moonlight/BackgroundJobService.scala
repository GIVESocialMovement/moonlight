package givers.moonlight

import com.google.inject.ImplementedBy
import play.api.libs.json.OFormat

import java.util.Date
import scala.concurrent.Future

@ImplementedBy(classOf[BackgroundJobServiceImpl])
trait BackgroundJobService {
  def enqueue[T <: Job](shouldRunAt: Date, priority: Int, param: T)
                       (implicit jsonFormat: OFormat[T], id: JobId[T]): Future[BackgroundJob]
}
