package controllers

import com.typesafe.config.{ ConfigFactory, Config }
import play.api.mvc._
import models.jsonrequest.{CreateItemRecommendBySite, ScoredItem, JsonRequestHeader}
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.validation.ValidationError
import helpers.JsConstraints._
import helpers.ErrorEntry
import helpers.Redis
import scala.concurrent.Future
import scala.util.Try

object ItemRecommendBySite extends Controller with HasLogger with JsonRequestHandler {
  implicit val createItemRecommendBySiteReads: Reads[CreateItemRecommendBySite] = (
    (JsPath \ "header").read[JsonRequestHeader] and
    (JsPath \ "storeCode").read(regex("\\w{1,8}".r)) and
    (JsPath \ "itemCode").read(regex("\\w{1,24}".r)) and
    (JsPath \ "itemList").read[Seq[ScoredItem]]
  )(CreateItemRecommendBySite.apply _)

  def create = Action.async(BodyParsers.parse.json) { request =>
    request.body.validate[CreateItemRecommendBySite].fold(
      errors => {
        logger.error("Json ItemRecommendBySite.create request validation error: " + errors)
        Future {BadRequest(toJson(errors))}
      },
      req => {
        logger.info("Json ItemRecommendBySite.create request: " + req)
        handleCreate(req)
      }
    )
  }

  def handleCreate(req: CreateItemRecommendBySite): Future[Result] = {
    Redis.pipelined(Redis.SalesDb) { pipe =>
      pipe.zAddFromMap(
        "itemBySite:" + req.storeCode + ":" + req.itemCode, 
        req.itemListAsMap.asInstanceOf[Map[Any, Double]]
      )
    }

    Future {Ok("")}
  }
}
