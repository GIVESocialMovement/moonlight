package givers.moonlight.scheduled

import givers.moonlight.scheduled.quartz.ValidCronExpression

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
  given fromValidCronExpression: Conversion[ValidCronExpression, CronSchedule] with
    def apply(vce: ValidCronExpression): CronSchedule = CronSchedule(vce.stringExpression)
}
