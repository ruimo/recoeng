package helpers

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

object Formatters {
  val YyyyMmDdFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd")
  def toYyyyMmDd(dateTime: DateTime): Int = YyyyMmDdFormat.print(dateTime).toInt
  def toYyyyMmDd(millis: Long): Int = YyyyMmDdFormat.print(millis).toInt
}
