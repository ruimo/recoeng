package controllers

import com.typesafe.config.{ ConfigFactory, Config }
import play.api.mvc._
import models.jsonrequest.{SalesItem, JsonRequestHeader, OnSalesJsonRequest}
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.validation.ValidationError
import helpers.JsConstraints._
import helpers.Redis
import scala.concurrent.Future
import scala.util.Try
import models.jsonrequest._

object RecommendByItem extends Controller with HasLogger with JsonRequestHandler {
  implicit val recommendBySingleItem: Reads[RecommendBySingleItemJsonRequest] = (
    (JsPath \ "header").read[JsonRequestHeader] and
    (JsPath \ "storeCode").read(regex("\\w{1,8}".r)) and
    (JsPath \ "itemCode").read(regex("\\w{1,24}".r)) and
    (JsPath \ "sort").read(regex("[A-Za-z0-9()]{1,64}".r)) and
    (JsPath \ "paging").read[JsonRequestPaging]
  )(RecommendBySingleItemJsonRequest.apply _)

  def bySingleItem = Action.async(BodyParsers.parse.json) { request =>
    request.body.validate[RecommendBySingleItemJsonRequest].fold(
      errors => {
        logger.error("Json RecommendByItem.bySingleItem validation error: " + errors)
        Future {BadRequest(toJson(errors))}
      },
      req => {
        logger.info("Json RecommendByItem.bySingleItem request: " + req)
        handleBySingleItem(req)
      }
    )
  }

  def handleBySingleItem(req: RecommendBySingleItemJsonRequest): Future[Result] = {
    Future {Ok("")}
  }
}
