package givers.moonlight

import play.api.libs.json.OFormat

/**
 * Job type
 * @param id
 *   type identifier
 * @param serDe
 *   serializer/deserializer
 * @tparam IN
 *   data input type
 */
trait JobType[IN] {
  val id: String
  val serDe: JobInSerDe[IN]

  def desc(in: IN): BackgroundJobDescription[IN] = BackgroundJobDescription(this, in)
}

class JobTypeJson[IN](val id: String)(implicit val jsonFormat: OFormat[IN]) extends JobType[IN] {
  val serDe = JobInSerDe.json(jsonFormat)
}
