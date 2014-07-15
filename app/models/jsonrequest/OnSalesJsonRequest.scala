package models.jsonrequest

case class JsonRequestHeader(
  sequenceNumber: String
) {
}
  
case class SalesItem(
  storeCode: String,
  itemCode: String,
  quantity: Integer
)

case class OnSalesJsonRequest(
  header: JsonRequestHeader,
  mode: String,
  dateTime: String,
  userCode: String,
  itemList: Seq[SalesItem]
)

object JsonRequest {
  val SequenceNumberValidator = Validator.createValidator("sequenceNumber", "\\d{1-16}")
}
