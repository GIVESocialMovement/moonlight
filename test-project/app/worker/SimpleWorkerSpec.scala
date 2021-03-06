package worker

import com.google.inject.{Inject, Singleton}
import givers.moonlight.{BackgroundJob, Worker, WorkerSpec}
import play.api.libs.json.{Json, OFormat}

import scala.reflect.ClassTag

object SimpleWorkerSpec extends WorkerSpec {
  case class Job(data: String) extends givers.moonlight.Job

  type Data = Job
  type Runner = SimpleWorker

  implicit val classTag = ClassTag(classOf[SimpleWorker])
  implicit val jsonFormat: OFormat[Job] = Json.format[Job]

  val identifier = "Simple"
  val previousIdentifiers = Set.empty
}

@Singleton
class SimpleWorker @Inject()() extends Worker[SimpleWorkerSpec.Job] {

  def run(param: SimpleWorkerSpec.Job, job: BackgroundJob): Unit = {
    println(s"Process data: ${param.data}")
    Thread.sleep(10000)
  }
}
