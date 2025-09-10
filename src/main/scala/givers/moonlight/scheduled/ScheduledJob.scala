package givers.moonlight.scheduled

import scala.concurrent.{ExecutionContext, Future}

/**
 * The same as background job but should run periodically on the some schedule A.k.a "Script"
 */
trait ScheduledJob[IN]:
  // job input
  type INPUT = IN

  // job execution context
  def executionContext: ExecutionContext

  /**
   * Run job
   * @param input
   *   job input
   * @return
   */
  def run(input: INPUT): Future[Unit]
