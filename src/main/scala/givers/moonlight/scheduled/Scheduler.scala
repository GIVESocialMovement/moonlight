package givers.moonlight.scheduled

import com.google.inject.ImplementedBy
import givers.moonlight.scheduled.Scheduler.StopScheduler
import givers.moonlight.scheduled.quartz.QuartzScheduler

import scala.concurrent.Future

/**
 * Job Scheduler. Periodically runs jobs
 */
@ImplementedBy(classOf[QuartzScheduler])
trait Scheduler {

  /**
   * Start scheduler
   * @return
   *   stop function
   */
  def run(): StopScheduler
}

object Scheduler {
  type StopScheduler = () => Future[Unit]
}
