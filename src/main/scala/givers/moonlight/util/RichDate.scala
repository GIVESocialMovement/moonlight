package givers.moonlight.util

import java.util.Date
import scala.concurrent.duration._

object RichDate {

  /**
   * Pimp my Library pattern for Date
   *
   * @param date date instance
   */
  implicit class RichDate(val date: Date) extends AnyVal {
    def add(interval: FiniteDuration): Date = new Date(date.getTime + interval.toMillis)

    def sub(interval: FiniteDuration): Date = new Date((date.getTime - interval.toMillis).max(0))
  }
}

