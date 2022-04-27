package helpers

import org.scalatest.{BeforeAndAfter, Suite}
import play.api.Logger

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Failure

/**
 * Allow automatically create/drop schemas
 */
trait DatabaseSchemaSupport extends BeforeAndAfter {
  self: DatabaseSpec with Suite =>

  private val logger = Logger(this.getClass)

  protected val DB_OPERATION_TIMEOUT: Duration = 5.seconds

  import profile.api._

  protected def schemas: profile.SchemaDescription

  before {
    val f = dbConfig.db.run(schemas.create)

    f.onComplete {
      case Failure(exception) => logger.error("schema creation failure", exception)
      case _ =>
    }

    Await.result(f, DB_OPERATION_TIMEOUT)
  }

  after {
    val f = dbConfig.db.run(schemas.drop)

    f.onComplete {
      case Failure(exception) => logger.error("schema dropping failure", exception)
      case _ =>
    }

    Await.result(f, DB_OPERATION_TIMEOUT)
  }
}

