package givers.moonlight.v2

import givers.moonlight.{AsyncSupport, AsyncWorkerSpec, Worker}
import play.api.inject.Injector

import scala.concurrent.duration.FiniteDuration

class WorkerSearchException(msg: String) extends Exception(msg)

/**
 * Moonlight app settings
 *
 * @param parallelism number of threads that will be used to run jobs
 * @param pauseDurationWhenNoJobs pause between unsuccessful (when no jobs to run) job retrieval attempts
 * @param maintenanceInterval pause between job maintenance @see[[JobDispatcher.scheduleMaintenance]]
 * @param betweenRunAttemptInterval how long to wait before trying to run a job again if it was failed
 * @param maxJobRetries how many times a single job can be restarted
 * @param jobRunTimeout how long to wait for job finish
 * @param workerSpecs a list of worker specifications
 */
case class MoonlightSettings(
  parallelism: Int,
  pauseDurationWhenNoJobs: FiniteDuration,
  maintenanceInterval: FiniteDuration,
  betweenRunAttemptInterval: FiniteDuration,
  maxJobRetries: Int,
  jobRunTimeout: FiniteDuration,
  workerSpecs: Seq[AsyncWorkerSpec]
) {
  /**
   * Get worker for a job type
   *
   * @param jobType job type
   * @param injector DI system injector
   * @return
   */
  def getWorkerByJobType(jobType: String)(implicit injector: Injector): Worker[_] with AsyncSupport[_] = {
    workerSpecs
      .filter(worker => worker.identifier == jobType || worker.previousIdentifiers.contains(jobType)) match {
        case Nil =>
          throw new WorkerSearchException(s"Unrecognized job type '$jobType'.")
        case one :: Nil =>
          injector.instanceOf(one.classTag)
        case multiple =>
          val names = multiple.map(_.classTag.getClass.getCanonicalName).mkString(", ")
          throw new WorkerSearchException(s"Ambiguous job type '$jobType'. " +
            s"Multiple workers ($names) are defined to process this job type.")
      }
  }
}