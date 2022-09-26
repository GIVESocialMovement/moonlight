package givers.moonlight.util

import java.time.{Instant, LocalDateTime, LocalTime, ZoneId}
import java.util.Date
import scala.concurrent.duration._

object RichDate {

  private val systemZoneId: ZoneId = ZoneId.systemDefault()

  /**
   * Pimp my Library pattern for Date
   *
   * @param date
   *   date instance
   */
  implicit class RichDate(val date: Date) extends AnyVal {
    def add(interval: FiniteDuration): Date = new Date(date.getTime + interval.toMillis)

    def sub(interval: FiniteDuration): Date = new Date((date.getTime - interval.toMillis).max(0))

    def toInstant: Instant = Instant.ofEpochMilli(date.getTime)

    def midnight: Date = {
      val instant = toInstant

      val timestamp = LocalDateTime
        .ofInstant(instant, systemZoneId)
        .toLocalDate
        .toEpochSecond(LocalTime.MIDNIGHT, systemZoneId.getRules.getOffset(instant))

      new Date(timestamp * 1000)
    }
  }
}
