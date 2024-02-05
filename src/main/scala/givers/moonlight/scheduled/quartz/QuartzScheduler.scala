package givers.moonlight.scheduled.quartz

import com.codahale.metrics.MetricRegistry
import givers.moonlight.scheduled.Scheduler.StopScheduler
import givers.moonlight.scheduled.quartz.QuartzScheduledJob.{
  JOB_DATA_IN_ARG,
  JOB_RUNNER_ARG,
  JOB_TIMEOUT,
  METRIC_REGISTRY
}
import givers.moonlight.scheduled.{CronSchedule, Schedule, Scheduler}
import givers.moonlight.v2.MoonlightSettings
import org.quartz.CronScheduleBuilder.cronSchedule
import org.quartz.JobBuilder.newJob
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.{CronExpression, JobDataMap, Trigger}
import play.api.inject.Injector

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
 * Quartz scheduler
 *
 * @param settings
 *   moonlight settings
 * @param metricRegistry
 *   metrics registry
 * @param ec
 *   execution context
 */
class QuartzScheduler @Inject() (
  settings: MoonlightSettings,
  metricRegistry: MetricRegistry,
  injector: Injector
)(implicit ec: ExecutionContext)
    extends Scheduler {

  /**
   * @inheritdoc
   */
  override def run(): StopScheduler = {
    val schedulerFactory = new org.quartz.impl.StdSchedulerFactory()

    val quartz = schedulerFactory.getScheduler()
    val startTime = System.currentTimeMillis()

    settings.schedulerInputs.zipWithIndex.foreach { case (in, index) =>
      val jobInnerClass = in.jobInnerClassTag
      val trigger = scheduleToTrigger(in.schedule, s"schedule_${jobInnerClass.getName}_${index}_${startTime}")

      val dataMap = new JobDataMap
      dataMap.put(JOB_RUNNER_ARG, injector.instanceOf(in.jobInnerClassTag))
      dataMap.put(JOB_DATA_IN_ARG, in.in)
      dataMap.put(JOB_TIMEOUT, in.timeout)
      dataMap.put(METRIC_REGISTRY, metricRegistry)

      // we use the same class for all jobs
      val job = newJob(classOf[QuartzScheduledJob])
        .withIdentity(s"${jobInnerClass.getName}_${index}_${startTime}")
        .setJobData(dataMap)
        .build()

      quartz.scheduleJob(job, trigger)
    }

    quartz.start()

    () => Future(quartz.shutdown(true))
  }

  /**
   * Transform schedule to quartz trigger
   *
   * @param schedule
   *   schedule
   * @param triggerId
   *   trigger identifier
   * @return
   */
  private def scheduleToTrigger(schedule: Schedule, triggerId: String): Trigger = {
    schedule match {
      case CronSchedule(expression) =>
        val quartzSchedule = cronSchedule(new CronExpression(expression))

        newTrigger()
          .withIdentity(triggerId)
          .withSchedule(quartzSchedule)
          .build()
    }
  }
}
