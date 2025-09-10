package givers.moonlight.persistence

import slick.jdbc.PostgresProfile

/**
 * Postgres Jdbc profile
 */
class PostgresSlickJdbcProfile extends SlickJdbcProfile(PostgresProfile)
