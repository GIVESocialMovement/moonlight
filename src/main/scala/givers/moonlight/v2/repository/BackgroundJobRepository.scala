package givers.moonlight.v2.repository

import com.google.inject.ImplementedBy
import givers.moonlight.BackgroundJob
import givers.moonlight.util.RichDate.RichDate

import java.util.Date
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * Parameters that are needed to check that the job can actually be started
 *
 * @param maxAcceptableAttemptsCount "try count" upper bound
 * @param now current date
 * @param betweenAttemptInterval how long to wait after a "failed attempt" before trying again
 *
 * @return
 */
case class JobReadyForStartCheckParams(
  maxAcceptableAttemptsCount: Int,
  now: Date,
  betweenAttemptInterval: FiniteDuration
) {
  def lastAttemptAfter: Date = now.sub(betweenAttemptInterval)
}

/**
 * Background job repository
 */
@ImplementedBy(classOf[BackgroundJobJdbcRepository])
trait BackgroundJobRepository {

  /**
   * Enqueue background job
   *
   * @param bgJob background job
   * @return background job with inserted id
   */
  def enqueue(bgJob: BackgroundJob): Future[BackgroundJob]

  /**
   * Get "paged" list of background jobs
   *
   * @param skip number of skipped records
   * @param take desired number of records
   * @return
   */
  def getJobs(skip: Long, take: Long): Future[Seq[BackgroundJob]]

  /**
   * Get pending jobs and jobs that were failed some time ago that still can be retried
   *
   * @param desiredNumberOfJobs desired number of jobs
   * @param checkParams parameters that are needed to check that the job can actually be started
   * @return
   */
  def getJobsReadyForStart(
    desiredNumberOfJobs: Long,
    checkParams: JobReadyForStartCheckParams
  ): Future[Seq[BackgroundJob]]

  /**
   * Get top pending job or job that wes failed some time ago that still can be retried
   *
   * @param checkParams parameters that are needed to check that the job can actually be started
   * @return
   */
  def getJobReadyForStart(checkParams: JobReadyForStartCheckParams): Future[Option[BackgroundJob]]

  /**
   * Service requests whose aim is to:
   * - "unstuck" initiated but not started jobs
   * - "unstuck" started jobs that reached timeout (may happen if moonlight instance was terminated)
   *
   * @param jobRunTimeout job run timeout
   * @param now current date
   * @return
   */
  def maintainExpiredJobs(jobRunTimeout: FiniteDuration, now: Date): Future[Int]

  /**
   * Change background job status to "Started" "respecting concurrency"
   * which means that if 2 instances/processes/threads will call
   * this method with the same id only one result should be true
   *
   * @param bgJobId background job id
   * @param newTryCount updated retry count
   * @param updateDate update date
   * @param checkParams parameters that are needed to check that the job can actually be started
   * @return future with the result:
   *         true - job is started by this instance
   *         false - job is started by someone else (concurrently)
   */
  def tryMarkJobAsStarted(
    bgJobId: Long,
    newTryCount: Int,
    updateDate: Date,
    checkParams: JobReadyForStartCheckParams
  ): Future[Boolean]

  /**
   * Change background job status to "Succeeded"
   *
   * @param bgJobId background job id
   * @param updateDate update date
   * @return
   */
  def markJobAsSucceed(bgJobId: Long, updateDate: Date): Future[Unit]

  /**
   * Change background job status to "Failed"
   *
   * @param bgJobId background job id
   * @param cause an exception
   * @param updateDate update date
   * @return
   */
  def markJobAsFailed(bgJobId: Long, cause: Throwable, updateDate: Date): Future[Unit]
}
