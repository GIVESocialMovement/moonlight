package givers.moonlight

/**
 * Joby type
 * @param id type identifier
 * @param serDe serializer/deserializer
 * @tparam IN data input type
 */
case class JobType[IN](id: String, serDe: JobInSerDe[IN])
