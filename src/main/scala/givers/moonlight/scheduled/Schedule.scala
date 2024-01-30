package givers.moonlight.scheduled

import givers.moonlight.scheduled.quartz.macros.ValidCronExpression

/**
 * Abstract schedule
 */
sealed trait Schedule

/**
 * @see
 *   [[https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html]]
 * @param cronString
 *   quartz similar cron string
 */
case class CronSchedule(cronString: String) extends Schedule

object CronSchedule {
  implicit def fromValidCronExpression(vce: ValidCronExpression): CronSchedule = CronSchedule(vce.stringExpression)

}
