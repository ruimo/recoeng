package controllers

import com.typesafe.config.{ ConfigFactory, Config }
import play.api.mvc._
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
import com.ruimo.recoeng.json.RecommendByItemJsonRequest
import com.ruimo.recoeng.json.Asc
import com.ruimo.recoeng.json.CreateItemRecommendBySite
import com.ruimo.recoeng.json.ListItemRecommendBySite
import com.ruimo.recoeng.json.JsonRequestHeader
import com.ruimo.recoeng.json.JsonRequestCursorPaging
import com.ruimo.recoeng.json.ScoredItem
import com.ruimo.recoeng.json.Desc

object ItemRecommendBySite extends Controller with HasLogger with JsonRequestHandler {
  implicit val createItemRecommendBySiteReads: Reads[CreateItemRecommendBySite] = (
    (JsPath \ "header").read[JsonRequestHeader] and
    (JsPath \ "storeCode").read(regex("\\w{1,8}".r)) and
    (JsPath \ "itemCode").read(regex("\\w{1,24}".r)) and
    (JsPath \ "salesItems").read[Seq[ScoredItem]]
  )(CreateItemRecommendBySite.apply _)

  implicit val listItemRecommendBySiteReads: Reads[ListItemRecommendBySite] = (
    (JsPath \ "header").read[JsonRequestHeader] and
    (JsPath \ "cursorPaging").read[JsonRequestCursorPaging]
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

  def list = Action.async(BodyParsers.parse.json) { request =>
    request.body.validate[ListItemRecommendBySite].fold(
      errors => {
        logger.error("Json ItemRecommendBySite.list request validation error: " + errors)
        Future {BadRequest(toJson(errors))}
      },
      req => {
        logger.info("Json ItemRecommendBySite.list request: " + req)
        handleList(req)
      }
    )
  }

  def handleCreate(req: CreateItemRecommendBySite): Future[Result] = {
    val key = req.storeCode + ":" + req.itemCode
    Redis.pipelined(Redis.SalesDb) { pipe =>
      pipe.zAddFromMap(
        "itemItemSite:" + key, req.salesItemsAsMap.asInstanceOf[Map[Any, Double]]
      )
      pipe.sAdd("itemSite", key)
    }

    Future {
      Ok(
        Json.obj(
          "header" -> Json.obj(
            "sequenceNumber" -> req.header.sequenceNumber,
            "statusCode" -> "OK",
            "message" -> ""
          )
        )
      )
    }
  }

  def handleList(req: ListItemRecommendBySite): Future[Result] = {
    val cursor = req.paging.cursor.toLong

    Redis.pipelined1(Redis.SalesDb) {
      _.sScan[String]("itemSite")(cursor, Some(req.paging.limit)) 
    }.map { t =>
      val nextCursor: Long = t._1
      val rec: Set[String] = t._2

      Ok(
        Json.obj(
          "header" -> Json.obj(
            "sequenceNumber" -> req.header.sequenceNumber,
            "statusCode" -> "OK",
            "message" -> ""
          ),
          "salesItems" -> JsArray(
            rec.toSeq.map { e =>
              val key = e.split(":")
              Json.obj(
                "storeCode" -> key(0),
                "itemCode" -> key(1)
              )
            }
          ),
          "cursorPaging" -> Json.obj(
            "cursor" -> nextCursor.toString,
            "limit" -> req.paging.limit
          )
        )
      )
    }
  }

  def byItem = Action.async(BodyParsers.parse.json) { request =>
    request.body.validate[RecommendByItemJsonRequest].fold(
      errors => {
        logger.error("Json ItemRecommendBySite.byItem validation error: " + errors)
        Future {BadRequest(toJson(errors))}
      },
      req => {
        logger.info("Json ItemRecommendBySite.byItem request: " + req)
        req.sortOrder match {
          case Asc(col) => handleByItem(req)
          case Desc(col) => handleByItem(req)
        }
      }
    )
  }

  def handleByItem(req: RecommendByItemJsonRequest): Future[Result] =
    queryItemSum(req, "itemItemSite")
}
