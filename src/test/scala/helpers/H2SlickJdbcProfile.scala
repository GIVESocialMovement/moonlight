package helpers

import givers.moonlight.persistence.SlickJdbcProfile

class H2SlickJdbcProfile extends SlickJdbcProfile(slick.jdbc.H2Profile)
