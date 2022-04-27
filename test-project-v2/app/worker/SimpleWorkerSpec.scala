package worker

import com.google.inject.{Inject, Singleton}
import givers.moonlight.{AsyncSupport, BackgroundJob, Worker, AsyncWorkerSpec}
import play.api.libs.json.{Json, OFormat}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object SimpleWorkerSpec extends AsyncWorkerSpec {
  case class Job(data: String) extends givers.moonlight.Job

  type Data = Job
  type Runner = SimpleWorker

  implicit val classTag = ClassTag(classOf[SimpleWorker])
  implicit val jsonFormat: OFormat[Job] = Json.format[Job]

  val identifier = "Simple"
  val previousIdentifiers = Set.empty
}

@Singleton
class SimpleWorker @Inject()(implicit ec: ExecutionContext) extends Worker[SimpleWorkerSpec.Job]  with AsyncSupport[SimpleWorkerSpec.Job] {
  def run(param: SimpleWorkerSpec.Job, job: BackgroundJob): Unit = {
    println(s"Process data: ${param.data}")
    Thread.sleep(10000)
  }

  override def runAsync(job: BackgroundJob, data: SimpleWorkerSpec.Job): Future[Unit] = {
    Future(run(data, job))
  }
}
