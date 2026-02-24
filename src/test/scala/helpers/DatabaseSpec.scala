package helpers

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Suite
import play.api.db.slick.DatabaseConfigProvider
import slick.basic.{BasicProfile, DatabaseConfig}
import slick.jdbc.{H2Profile, JdbcBackend, JdbcProfile}

/**
 * In memory database test
 */
trait DatabaseSpec {
  self: Suite =>

  val dbConfig = new DatabaseConfig[H2Profile.type] {
    override def db: slick.jdbc.H2Profile.backend.JdbcDatabaseDef = H2Profile.api.Database.forURL(
      url = "jdbc:h2:mem:testdb;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )

    override val profile: H2Profile.type = H2Profile

    override def config: Config = ConfigFactory.empty()

    override def profileName: String = "H2Profile"

    override def profileIsObject: Boolean = true
  }

  lazy val dbConfigProvider: DatabaseConfigProvider = new DatabaseConfigProvider {
    override def get[P <: BasicProfile]: DatabaseConfig[P] = dbConfig.asInstanceOf[DatabaseConfig[P]]
  }

  lazy val profile: JdbcProfile = dbConfig.profile

  lazy val db = dbConfig.db
}
