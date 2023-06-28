package helpers

import com.google.inject.{Inject, Singleton}
import givers.moonlight.{JobExecutor, JobInSerDe, JobType}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SimpleJobExecutor @Inject()(implicit executionContext: ExecutionContext)
  extends JobExecutor(SimpleJobExecutor.jobType) {

  override def run(data: SimpleJobExecutor.JobData): Future[Unit] = {
    Future {
      if (data.data == "error") {
        throw new Exception("FakeError")
      }
    }
  }
}

object SimpleJobExecutor {
  case class JobData(data: String)

  val jobType: JobType[JobData] = JobType("Simple", JobInSerDe.json(Json.format[JobData]))
}
