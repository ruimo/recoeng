package controllers

import play.api.mvc._
import models.jsonrequest.{SalesItem, JsonRequestHeader, OnSalesJsonRequest}
import play.api.libs.json._ // JSON library
import play.api.libs.json.Reads._ // Custom validation helpers
import play.api.libs.functional.syntax._ // Combinator syntax

object ItemItem extends Controller {
  implicit val jsonRequestHeaderReads: Reads[JsonRequestHeader] =
    (JsPath \ "sequenceNumber").read[String].map(JsonRequestHeader(_))

  implicit val salesItemReads: Reads[SalesItem] = (
    (JsPath \ "storeCode").read[String] and
    (JsPath \ "itemCode").read[String] and
    (JsPath \ "quantity").read[Int]
  )(SalesItem(_, _, _))

  implicit val onSalesReads: Reads[OnSalesJsonRequest] = (
    (JsPath \ "header").read[JsonRequestHeader] and
    (JsPath \ "mode").read[String] and
    (JsPath \ "dateTime").read[String] and
    (JsPath \ "userCode").read[String] and
    (JsPath \ "itemList").read[Seq[SalesItem]]
  )(OnSalesJsonRequest(_, _, _, _, _))

  def onSales = Action(BodyParsers.parse.json) { request =>
    val req = request.body.validate[OnSalesJsonRequest]
    req.fold(
      errors => {
        Ok("")
      },
      req => {
        Ok("")
      }
    )
  }
}
