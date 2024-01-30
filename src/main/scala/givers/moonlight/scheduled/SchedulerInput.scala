package givers.moonlight.scheduled

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

/**
 * Scheduler input
 *
 * @param in
 *   job input
 * @param schedule
 *   schedule
 * @param timeout
 *   job single execution timeout
 * @tparam SJ
 *   Scheduled job class
 */
case class SchedulerInput[SJ <: ScheduledJob: ClassTag](in: SJ#IN, schedule: Schedule, timeout: FiniteDuration) {
  def jobInnerClassTag: Class[SJ] = implicitly[ClassTag[SJ]].runtimeClass.asInstanceOf[Class[SJ]]
}
