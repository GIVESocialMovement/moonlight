package givers.moonlight

import scala.concurrent.Future

abstract class JobExecutor[IN](val jobType: JobType[IN]) {
  def run(in: String): Future[Unit] = jobType.serDe.deserialize(in).fold(Future.failed, run)

  def run(in: IN): Future[Unit]
}
