package givers.moonlight.util

import com.google.inject.Singleton

import java.util.Date

/**
 * Date/time factory class
 *
 * The purpose of it is to get better abstraction and simplify tests of time related entities by using mock of this class
 */
@Singleton
class DateTimeFactory {
  /**
   * Get current date
   *
   * @return
   */
  def now: Date = new Date
}
