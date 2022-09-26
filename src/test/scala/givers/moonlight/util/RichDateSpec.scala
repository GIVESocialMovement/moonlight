package givers.moonlight.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.{LocalDate, LocalDateTime, ZoneId}
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

  "RichDate.midnight" should {
    "zero time" in {
      val zone = ZoneId.systemDefault()
      val ldTime = LocalDateTime.of(2022, 9, 24, 11, 12, 13)
      val date = Date.from(ldTime.toInstant(zone.getRules.getOffset(ldTime)))

      LocalDate.ofInstant(date.midnight.toInstant, zone) shouldBe LocalDate.of(2022, 9, 24)
    }
  }
}
