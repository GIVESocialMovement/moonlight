package helpers

import java.util.concurrent.TimeUnit

import play.api.db.evolutions.DefaultEvolutionsApi
import play.api.db.slick.evolutions.SlickDBApi
import play.api.db.slick.{DatabaseConfigProvider, DbName, SlickApi}
import slick.basic.{BasicProfile, DatabaseConfig}
import slick.jdbc.PostgresProfile.api._
import utest.TestSuite

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

object BaseSpec {

  def await[T](future: Future[T]): T = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Await.result(future, Duration(10, TimeUnit.SECONDS))
  }

  val db = {

    Class.forName("org.postgresql.Driver") // load the driver
    val dummyDb = Database.forDataSource(
      ds = new slick.jdbc.DatabaseUrlDataSource {
        url = "postgres://moonlight_test_user:test@localhost:5432/postgres"
      },
      maxConnections = Some(1),
      executor = slick.util.AsyncExecutor("dummyDb", 1, 1, 1, 1)
    )
    await(dummyDb.run { sqlu"DROP DATABASE IF EXISTS moonlight_test" })
    await(dummyDb.run { sqlu"CREATE DATABASE moonlight_test" })

    val main = Database.forDataSource(
      ds = new slick.jdbc.DatabaseUrlDataSource {
        url = "postgres://moonlight_test_user:test@localhost:5432/moonlight_test"
      },
      maxConnections = None
    )

    val databaseConfig: DatabaseConfig[slick.jdbc.PostgresProfile] = new DatabaseConfig[slick.jdbc.PostgresProfile] {
      def db = main
      val profile = slick.jdbc.PostgresProfile
      val driver = profile
      def config = null
      def profileName = "slick.jdbc.PostgresProfile"
      def profileIsObject = true
    }

    val slickApi = new SlickApi {
      def dbConfigs[P <: BasicProfile]() = List((DbName("default"), databaseConfig.asInstanceOf[DatabaseConfig[P]]))
      def dbConfig[P <: BasicProfile](name: DbName) = databaseConfig.asInstanceOf[DatabaseConfig[P]]
    }

    new DefaultEvolutionsApi(SlickDBApi(slickApi)).applyFor("default")

    main
  }
}

abstract class BaseSpec extends TestSuite {
  val dbConfigProvider = {
    val databaseConfig = new DatabaseConfig[slick.jdbc.PostgresProfile] {
      def db = BaseSpec.db.asInstanceOf[slick.jdbc.PostgresProfile#Backend#Database]
      val profile = slick.jdbc.PostgresProfile
      val driver = profile
      def config = null
      def profileName = "slick.jdbc.PostgresProfile"
      def profileIsObject = true
    }

    new DatabaseConfigProvider {
      def get[P <: BasicProfile] = databaseConfig.asInstanceOf[DatabaseConfig[P]]
    }
  }

  def await[T](future: Future[T]): T = BaseSpec.await(future)

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global


  override def utestBeforeEach(path: Seq[String]): Unit = {
    await(
      BaseSpec.db.run {
        sql"SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename ASC;".as[String]
      }.flatMap { tables =>
        Future.sequence(
          tables.toList
            .filterNot(_ == "play_evolutions")
            .map { table =>
              BaseSpec.db.run { sqlu"TRUNCATE #$table RESTART IDENTITY;" }
            }
        )
      }
    )
  }
}
