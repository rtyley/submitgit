package lib

import org.joda.time.Period
import org.joda.time.format.PeriodFormat

object Dates {

  val humanPeriodFormat = PeriodFormat.getDefault

  implicit class RichPeriod(period: Period) {
    lazy val pretty = period.withMillis(0).toString(humanPeriodFormat)
  }
}
