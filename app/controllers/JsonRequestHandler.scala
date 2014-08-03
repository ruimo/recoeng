package controllers

import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import helpers.JsConstraints._
import helpers.ErrorEntry
import scala.concurrent.Future
import helpers.Redis
import scredis.Score
import play.api.mvc.Controller
import play.api.mvc.Result
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import com.ruimo.recoeng.json.JsonRequestHeader
import com.ruimo.recoeng.json.JsonRequestPaging
import com.ruimo.recoeng.json.JsonRequestCursorPaging
import com.ruimo.recoeng.json.SalesItem
import com.ruimo.recoeng.json.RecommendBySingleItemJsonRequest
import com.ruimo.recoeng.json.ScoredItem
import com.ruimo.recoeng.json.Asc
import com.ruimo.recoeng.json.Desc

trait JsonRequestHandler extends Controller with HasLogger {
  implicit val jsonRequestHeaderReads: Reads[JsonRequestHeader] = (
    (JsPath \ "dateTime").read(jodaDateReads("YYYYMMddHHmmss")) and
    (JsPath \ "sequenceNumber").read(regex("\\d{1,16}".r))
  )(JsonRequestHeader.apply _)

  implicit val jsonRequestPagingReads: Reads[JsonRequestPaging] = (
    (JsPath \ "offset").read[Int] and
    (JsPath \ "limit").read[Int]
  )(JsonRequestPaging.apply _)

  implicit val jsonRequestCursorPagingReads: Reads[JsonRequestCursorPaging] = (
    (JsPath \ "cursor").read(regex("\\d+".r)) and
    (JsPath \ "limit").read[Int]
  )(JsonRequestCursorPaging.apply _)

  implicit val salesItemReads: Reads[SalesItem] = (
    (JsPath \ "storeCode").read(regex("\\w{1,8}".r)) and
    (JsPath \ "itemCode").read(regex("\\w{1,24}".r)) and
    (JsPath \ "quantity").read[Int]
  )(SalesItem.apply _)

  implicit val scoredItemReads: Reads[ScoredItem] = (
    (JsPath \ "storeCode").read(regex("\\w{1,8}".r)) and
    (JsPath \ "itemCode").read(regex("\\w{1,24}".r)) and
    (JsPath \ "score").read[Double]
  )(ScoredItem.apply _)

  implicit val recommendBySingleItem: Reads[RecommendBySingleItemJsonRequest] = (
    (JsPath \ "header").read[JsonRequestHeader] and
    (JsPath \ "storeCode").read(regex("\\w{1,8}".r)) and
    (JsPath \ "itemCode").read(regex("\\w{1,24}".r)) and
    (JsPath \ "sort").read(regex("""(?i)(?:asc|desc)\(cost\)""".r)) and
    (JsPath \ "paging").read[JsonRequestPaging]
  )(RecommendBySingleItemJsonRequest.apply _)

  def toJson(errors: Seq[(JsPath, Seq[ValidationError])]): JsValue =
    Json.toJson(errors.map {e => ErrorEntry(e._1, e._2)})

  def queryItemSum(req: RecommendBySingleItemJsonRequest, keyBase: String): Future[Result] = {
    val key = keyBase + ":" + req.storeCode + ":" + req.itemCode
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
        "header" -> Json.obj(
          "sequenceNumber" -> req.header.sequenceNumber,
          "statusCode" -> "OK",
          "message" -> ""
        ),
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
