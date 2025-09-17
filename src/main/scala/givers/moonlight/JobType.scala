package givers.moonlight

import play.api.libs.json.OFormat

/**
 * Job type
 *
 * @tparam IN
 *   data input type
 */
trait JobType[IN] {

  // type identifier
  val id: String

  // serializer/deserializer
  val serDe: JobInSerDe[IN]

  def desc(in: IN): BackgroundJobDescription[IN] = BackgroundJobDescription(this, in)
}

class JobTypeJson[IN](val id: String)(using val jsonFormat: OFormat[IN]) extends JobType[IN] {
  val serDe: JobInSerDe[IN] = JobInSerDe.json
}
