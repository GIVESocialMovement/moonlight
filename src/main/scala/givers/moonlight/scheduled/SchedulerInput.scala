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
case class SchedulerInput[IN, SJ <: ScheduledJob[IN]](
  in: IN,
  schedule: Schedule,
  timeout: FiniteDuration
)(using c: ClassTag[SJ]) {
  def jobInnerClassTag: Class[SJ] = summon[ClassTag[SJ]].runtimeClass.asInstanceOf[Class[SJ]]
}

object SchedulerInput {
  case class SchedulerInputUnattached[IN](in: IN, schedule: Schedule, timeout: FiniteDuration):
    def forJob[SJ <: ScheduledJob[IN]](using c: ClassTag[SJ]): SchedulerInput[IN, SJ] =
      SchedulerInput(in, schedule, timeout)

  def apply[IN](in: IN, schedule: Schedule, timeout: FiniteDuration): SchedulerInputUnattached[IN] =
    SchedulerInputUnattached[IN](in, schedule, timeout)
}
