package helpers

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Suite
import play.api.db.slick.DatabaseConfigProvider
import slick.basic.{BasicProfile, DatabaseConfig}
import slick.jdbc
import slick.jdbc.{H2Profile, JdbcBackend, JdbcProfile}

/**
 * In memory database test
 */
trait DatabaseSpec {
  self: Suite =>

  val dbConfig = new DatabaseConfig[H2Profile] {

    override def db: profile.backend.Database = profile.api.Database.forURL(
      url = "jdbc:h2:mem:testdb;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )

    override val profile: H2Profile = H2Profile

    override def config: Config = ConfigFactory.empty()

    override def profileName: String = "H2Profile"

    override def profileIsObject: Boolean = true
  }

  lazy val dbConfigProvider: DatabaseConfigProvider = new DatabaseConfigProvider {
    override def get[P <: BasicProfile]: DatabaseConfig[P] = dbConfig.asInstanceOf[DatabaseConfig[P]]
  }

  final lazy val profile: JdbcProfile = dbConfig.profile

  lazy val db: dbConfig.profile.backend.JdbcDatabaseDef = dbConfig.db
}
