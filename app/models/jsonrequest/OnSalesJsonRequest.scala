package models.jsonrequest

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.IllegalFieldValueException
import helpers.Formatters._

case class JsonRequestHeader(
  dateTime: DateTime,
  sequenceNumber: String
)
  
case class SalesItem(
  storeCode: String,
  itemCode: String,
  quantity: Integer
) {
  lazy val redisCode: String = storeCode + ":" + itemCode
}

case class OnSalesJsonRequest(
  header: JsonRequestHeader,
  mode: String,
  dateTime: DateTime,
  userCode: String,
  itemList: Seq[SalesItem]
) {
  lazy val tranDateInYyyyMmDd: Int = toYyyyMmDd(dateTime)
}
