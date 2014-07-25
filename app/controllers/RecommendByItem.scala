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
import scredis.util.LinkedHashSet
import scredis.Score

object RecommendByItem extends Controller with HasLogger with JsonRequestHandler {
  implicit val recommendBySingleItem: Reads[RecommendBySingleItemJsonRequest] = (
    (JsPath \ "header").read[JsonRequestHeader] and
    (JsPath \ "storeCode").read(regex("\\w{1,8}".r)) and
    (JsPath \ "itemCode").read(regex("\\w{1,24}".r)) and
    (JsPath \ "sort").read(regex("""(?i)(?:asc|desc)\(cost\)""".r)) and
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
        req.sortOrder match {
          case Asc(col) => handleBySingleItem(req)
          case Desc(col) => handleBySingleItem(req)
        }
      }
    )
  }

  def handleBySingleItem(req: RecommendBySingleItemJsonRequest): Future[Result] = {
    val key = "itemItemSum1m:" + req.storeCode + ":" + req.itemCode
    Redis.pipelined1(Redis.SalesDb) { pipe =>
      req.sortOrder match {
        case Asc(col) =>
          pipe.zRangeByScoreWithScores(
            key, Score.Infinity, Score.Infinity, Some(req.paging.offset, req.paging.limit)
          )
        case Desc(col) =>
          pipe.zRevRangeByScoreWithScores(
            key, Score.Infinity, Score.Infinity, Some(req.paging.offset, req.paging.limit)
          )
      }
    }.map { recs =>
      val result = Ok(Json.obj(
        "sequenceNumber" -> req.header.sequenceNumber,
        "statusCode" -> "OK",
        "message" -> "",
        "itemList" -> JsArray(
          recs.toSeq.map { r =>
            val key = r._1.split(":")
            Json.obj(
              "storeCode" -> key(0),
              "itemCode" -> key(1),
              "score" -> r._2
            )
          }
        ),
        "sort" -> req.sort,
        "paging" -> Map(
          "offset" -> req.paging.offset,
          "limit" -> req.paging.limit
        )
      ))
      logger.info("Json RecommendByItem.bySingleItem response: " + result)
      result
    }
  }
}
