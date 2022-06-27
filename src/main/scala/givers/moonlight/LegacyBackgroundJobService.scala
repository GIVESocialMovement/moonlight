package givers.moonlight

import play.api.libs.json.OFormat

import java.util.Date
import scala.concurrent.Future

trait LegacyBackgroundJobService { self: BackgroundJobService =>
  def queue[T <: Job](shouldRunAt: Date, priority: Int, param: T)
                     (implicit jsonFormat: OFormat[T], id: JobId[T]): Future[BackgroundJob] = {
    enqueue(shouldRunAt, priority, param)
  }
}
