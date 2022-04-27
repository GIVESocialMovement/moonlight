package givers.moonlight

import play.api.libs.json.OFormat

import scala.reflect.ClassTag


abstract class Job
case class JobId[T](value: String)

trait WorkerSpec {
  type Data <: Job
  type Runner <: Worker[Data]

  def identifier: String
  def previousIdentifiers: Set[String]

  implicit def classTag: ClassTag[Runner]
  implicit def jsonFormat: OFormat[Data]

  implicit def idToJobId: JobId[Data] = JobId(identifier)
}

trait AsyncWorkerSpec extends WorkerSpec {
  type Runner <: Worker[Data] with AsyncSupport[Data]
}