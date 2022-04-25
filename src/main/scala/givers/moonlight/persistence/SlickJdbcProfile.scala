package givers.moonlight.persistence

import slick.jdbc.JdbcProfile

/**
 * Keeps Jdbc profile implementation
 */
trait SlickJdbcProfile {
  protected val profile: JdbcProfile
}
