package givers.moonlight

import play.api.libs.json.{JsResult, Json, OFormat}

import scala.util.Try

/**
 * serializer/deserializer
 *
 * @tparam IN data type
 */
trait JobInSerDe[IN] {
  def serialize(in: IN): String
  def deserialize(inSerialized: String): Try[IN]
}

object JobInSerDe {
  def json[IN](implicit format: OFormat[IN]): JobInSerDe[IN] = new JobInSerDe[IN] {
    override def serialize(in: IN): String = format.writes(in).toString()

    override def deserialize(inSerialized: String): Try[IN] = JsResult.toTry(format.reads(Json.parse(inSerialized)))
  }
}
