package givers.moonlight

import givers.moonlight.util.AsyncIdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json

class BackgroundJobDescriptionSpec extends AnyWordSpecLike with Matchers with AsyncIdiomaticMockito {
  case class JobIn(data: String)
  implicit case object Simple extends JobTypeJson[JobIn]("Simple")(using Json.format)

  "BackgroundJobDescription" should {
    "be implicitly formed" in {
      import givers.moonlight.BackgroundJobDescription.Implicits.given
      val desc: BackgroundJobDescription[JobIn] = JobIn("data")
    }

    "be explicitly formed" in {
      import givers.moonlight.BackgroundJobDescription.given
      val _: BackgroundJobDescription[JobIn] = JobIn("data").describe(Simple)
    }
  }
}
