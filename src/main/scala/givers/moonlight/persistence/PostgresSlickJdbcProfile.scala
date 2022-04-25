package givers.moonlight.persistence

import slick.jdbc.PostgresProfile

/**
 * Postgres Jdbc profile
 */
trait PostgresSlickJdbcProfile extends SlickJdbcProfile {
  override lazy val profile = PostgresProfile
}
