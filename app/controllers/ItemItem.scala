package controllers

import play.api.mvc._
import models.jsonrequest.{SalesItem, JsonRequestHeader, OnSalesJsonRequest}
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.i18n.Messages
import play.api.data.validation.ValidationError
import helpers.JsConstraints._
import helpers.ErrorEntry

object ItemItem extends Controller with HasLogger {
//  def config = play.api.Play.maybeApplication.map(_.configuration).get
//  def stubRedis = config.getBoolean("stub.redis").getOrElse(false)

  implicit val jsonRequestHeaderReads: Reads[JsonRequestHeader] = (
    (JsPath \ "dateTime").read(jodaDateReads("YYYYMMddHHmmss")) and
    (JsPath \ "sequenceNumber").read(regex("\\d{1,16}".r))
  )(JsonRequestHeader(_, _))

  implicit val salesItemReads: Reads[SalesItem] = (
    (JsPath \ "storeCode").read(regex("\\w{1,8}".r)) and
    (JsPath \ "itemCode").read(regex("\\w{1,24}".r)) and
    (JsPath \ "quantity").read[Int]
  )(SalesItem(_, _, _))

  implicit val onSalesReads: Reads[OnSalesJsonRequest] = (
    (JsPath \ "header").read[JsonRequestHeader] and
    (JsPath \ "mode").read(regex("\\w{4}".r)) and
    (JsPath \ "dateTime").read(jodaDateReads("YYYYMMddHHmmss")) and
    (JsPath \ "userCode").read(regex("\\w{1,8}".r)) and
    (JsPath \ "itemList").read[Seq[SalesItem]]
  )(OnSalesJsonRequest.apply _)

  def onSales = Action(BodyParsers.parse.json) { request =>
    request.body.validate[OnSalesJsonRequest].fold(
      errors => {
        logger.error("Json onSales request validation error: " + errors)
        BadRequest(toJson(errors))
      },
      req => {
        logger.info("Json onSales request: " + req)
        Ok(handleOnSales(req))
      }
    )
  }

  def handleOnSales(req: OnSalesJsonRequest): String =
    ""
  
  def toJson(errors: Seq[(JsPath, Seq[ValidationError])]): String =
    Json.toJson(errors.map {e => ErrorEntry(e._1, e._2)}).toString
 }
 
