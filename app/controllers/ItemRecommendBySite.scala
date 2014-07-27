package controllers

import com.typesafe.config.{ ConfigFactory, Config }
import play.api.mvc._
import models.jsonrequest._
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

  implicit val listItemRecommendBySiteReads: Reads[ListItemRecommendBySite] = (
    (JsPath \ "header").read[JsonRequestHeader] and
    (JsPath \ "sort").read(regex("\\w{0,64}".r)) and
    (JsPath \ "paging").read[JsonRequestPaging]
  )(ListItemRecommendBySite.apply _)

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
        "itemItemSite:" + req.storeCode + ":" + req.itemCode, 
        req.itemListAsMap.asInstanceOf[Map[Any, Double]]
      )
    }

    Future {
      Ok(
        Json.obj(
          "sequenceNumber" -> req.header.sequenceNumber,
          "statusCode" -> "OK",
          "message" -> ""
        )
      )
    }
  }

  def bySingleItem = Action.async(BodyParsers.parse.json) { request =>
    request.body.validate[RecommendBySingleItemJsonRequest].fold(
      errors => {
        logger.error("Json ItemRecommendBySite.bySingleItem validation error: " + errors)
        Future {BadRequest(toJson(errors))}
      },
      req => {
        logger.info("Json ItemRecommendBySite.bySingleItem request: " + req)
        req.sortOrder match {
          case Asc(col) => handleBySingleItem(req)
          case Desc(col) => handleBySingleItem(req)
        }
      }
    )
  }

  def handleBySingleItem(req: RecommendBySingleItemJsonRequest): Future[Result] =
    queryItemSum(req, "itemItemSite")
}
