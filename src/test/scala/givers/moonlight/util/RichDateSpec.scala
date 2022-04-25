package givers.moonlight.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Date
import scala.concurrent.duration.DurationInt

class RichDateSpec extends AnyWordSpecLike with Matchers {
  import RichDate._

  private val initialDate = new Date(10_000)

  "RichDate.add" should {
    "add interval" in {
      initialDate.add(1.minute).getTime shouldBe 70_000
    }
  }
  "RichDate.sub" should {
    "subtract interval" in {
      initialDate.sub(1.second).getTime shouldBe 9_000
      initialDate.sub(1.minute).getTime shouldBe 0
    }
  }
}
