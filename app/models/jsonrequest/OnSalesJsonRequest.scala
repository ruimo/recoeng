package models.jsonrequest

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.IllegalFieldValueException

case class JsonRequestHeader(
  dateTime: DateTime,
  sequenceNumber: String
)
  
case class SalesItem(
  storeCode: String,
  itemCode: String,
  quantity: Integer
)

case class OnSalesJsonRequest(
  header: JsonRequestHeader,
  mode: String,
  dateTime: DateTime,
  userCode: String,
  itemList: Seq[SalesItem]
)
