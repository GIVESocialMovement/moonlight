package givers.moonlight

import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json

class BackgroundJobDescriptionSpec extends AnyWordSpecLike with Matchers with IdiomaticMockito {
  case class JobIn(data: String)
  implicit case object Simple extends JobTypeJson[JobIn]("Simple")(Json.format)

  "BackgroundJobDescription" should {
    "be implicitly formed" in {
      import givers.moonlight.BackgroundJobDescription.Implicits._
      val desc: BackgroundJobDescription[JobIn] = JobIn("data")
    }

    "be explicitly formed" in {
      import givers.moonlight.BackgroundJobDescription._
      val _: BackgroundJobDescription[JobIn] = JobIn("data").describe(Simple)
    }
  }
}
