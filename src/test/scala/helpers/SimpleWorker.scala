package helpers

import com.google.inject.{Inject, Singleton}
import givers.moonlight.{BackgroundJob, Worker, WorkerSpec}
import play.api.libs.json.{Json, OFormat}

import scala.reflect.ClassTag

object SimpleWorker extends WorkerSpec {
  case class Job(data: String) extends givers.moonlight.Job

  type Data = Job
  type Runner = SimpleWorker

  implicit val classTag = ClassTag(classOf[SimpleWorker])
  implicit val jsonFormat: OFormat[Job] = Json.format[Job]

  val identifier = "Simple"
  val previousIdentifiers = Set("PreviousSimple", "Ambiguous")
}

@Singleton
class SimpleWorker @Inject()() extends Worker[SimpleWorker.Job] {
  def run(param: SimpleWorker.Job, job: BackgroundJob): Unit = {
    if (param.data == "error") {
      throw new Exception("FakeError")
    }
  }
}

object AnotherWorker extends WorkerSpec {
  case class Job(data: String) extends givers.moonlight.Job

  type Data = Job
  type Runner = AnotherWorker

  implicit val classTag = ClassTag(classOf[AnotherWorker])
  implicit val jsonFormat: OFormat[Job] = Json.format[Job]

  val identifier = "Another"
  val previousIdentifiers = Set.empty
}

@Singleton
class AnotherWorker @Inject()() extends Worker[AnotherWorker.Job] {
  def run(param: AnotherWorker.Job, job: BackgroundJob): Unit = ()
}

object AmbiguousWorker extends WorkerSpec {
  case class Job(data: String) extends givers.moonlight.Job

  type Data = Job
  type Runner = AmbiguousWorker

  implicit val classTag = ClassTag(classOf[AmbiguousWorker])
  implicit val jsonFormat: OFormat[Job] = Json.format[Job]

  val identifier = "Ambiguous"
  val previousIdentifiers = Set.empty
}

@Singleton
class AmbiguousWorker @Inject()() extends Worker[AmbiguousWorker.Job] {
  def run(param: AmbiguousWorker.Job, job: BackgroundJob): Unit = {}
}
