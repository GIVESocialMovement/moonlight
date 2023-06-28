package givers.moonlight.v2

import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.inject.Injector

import scala.concurrent.duration.DurationInt
import scala.util.Random

class MoonlightSettingsSpec extends AnyWordSpecLike with Matchers with IdiomaticMockito {
  private val settings = MoonlightSettings(1, 3.seconds, 0.seconds, 5.seconds, 0.seconds, 1, 0.seconds, 90.days, Seq.empty)

  private implicit val injector: Injector = mock[Injector]

  "MoonlightSettings.pauseDurationWhenNoJobsRandomized" should {
    "return random pause" in {
      implicit val random: Random = mock[Random]
      random.between(3.seconds.toMillis, 3300L) returns 3100L
      settings.pauseDurationWhenNoJobsRandomized shouldBe 3100.millis
    }
  }
}
