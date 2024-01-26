package givers.moonlight.scheduled

import scala.concurrent.{ExecutionContext, Future}

/**
 * The same as background job but should run periodically on the some schedule A.k.a "Script"
 */
trait ScheduledJob {
  // job input
  type IN

  // job execution context
  def executionContext: ExecutionContext

  /**
   * Run job
   * @param input
   *   job input
   * @return
   */
  def run(input: IN): Future[Unit]
}
