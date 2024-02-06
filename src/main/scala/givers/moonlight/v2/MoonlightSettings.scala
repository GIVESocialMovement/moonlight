package givers.moonlight.v2

import givers.moonlight.JobExecutor
import givers.moonlight.scheduled.SchedulerInput

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.util.Random

/**
 * Moonlight app settings
 *
 * @param parallelism
 *   number of threads that will be used to run jobs
 * @param pauseDurationWhenNoJobs
 *   pause between unsuccessful (when no jobs to run) job retrieval attempts
 * @param maintenanceInterval
 *   pause between job maintenance @see[[JobDispatcher.scheduleMaintenance]]
 * @param countMetricsCollectionInterval
 *   pause between metrics collection @see[[JobDispatcher.scheduleMetrics]]
 * @param betweenRunAttemptInterval
 *   how long to wait before trying to run a job again if it was failed
 * @param maxJobRetries
 *   how many times a single job can be restarted
 * @param jobRunTimeout
 *   how long to wait for job finish
 * @param completedJobsTtl
 *   how long to keep Succeeded/Failed jobs in the database
 * @param executors
 *   a list of executors
 * @param schedulerInputs
 *   scheduler jobs
 */
case class MoonlightSettings(
  parallelism: Int,
  pauseDurationWhenNoJobs: FiniteDuration,
  maintenanceInterval: FiniteDuration,
  countMetricsCollectionInterval: FiniteDuration,
  betweenRunAttemptInterval: FiniteDuration,
  maxJobRetries: Int,
  jobRunTimeout: FiniteDuration,
  completedJobsTtl: FiniteDuration,
  executors: Seq[JobExecutor[_]],
  schedulerInputs: Seq[SchedulerInput[_]]
) {

  /**
   * Randomize duration a bit to decrease job concurrency effect when all threads tries to pick the same job and the log
   * will be spammed with messages like '... can't be started because it's started by someone else'
   *
   * @param random
   *   randomizer
   * @return
   */
  def pauseDurationWhenNoJobsRandomized(implicit random: Random): FiniteDuration = {
    val millis = pauseDurationWhenNoJobs.toMillis
    val delta = (millis * MoonlightSettings.durationRandomizationBound).toLong

    random.between(millis, millis + delta).millis
  }
}

object MoonlightSettings {

  /**
   * 0 - 1 (0% - 100%) randomization bounds
   */
  val durationRandomizationBound = 0.1
}
