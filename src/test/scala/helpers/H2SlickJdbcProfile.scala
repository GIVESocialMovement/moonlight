package helpers

import givers.moonlight.persistence.SlickJdbcProfile

trait H2SlickJdbcProfile extends SlickJdbcProfile{
  override val profile = slick.jdbc.H2Profile
}
