package controllers

import scredis.util.LinkedHashSet
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
import com.ruimo.recoeng.json.RecommendByItemJsonRequest
import com.ruimo.recoeng.json.ScoredItem
import com.ruimo.recoeng.json.Asc
import com.ruimo.recoeng.json.Desc

trait JsonRequestHandler extends Controller with HasLogger {
  implicit val jsonRequestHeaderReads: Reads[JsonRequestHeader] = (
    (JsPath \ "dateTime").read(jodaDateReads("YYYYMMddHHmmss") keepAnd regex("\\d{14}".r)) and
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

  implicit val recommendByItem: Reads[RecommendByItemJsonRequest] = (
    (JsPath \ "header").read[JsonRequestHeader] and
    (JsPath \ "salesItems").read[Seq[SalesItem]] and
    (JsPath \ "sort").read(regex("""(?i)(?:asc|desc)\(score\)""".r)) and
    (JsPath \ "paging").read[JsonRequestPaging]
  )(RecommendByItemJsonRequest.apply _)

  def toJson(errors: Seq[(JsPath, Seq[ValidationError])]): JsValue =
    Json.toJson(errors.map {e => ErrorEntry(e._1, e._2)})

  implicit val scoredItemOrder: Ordering[ScoredItem] = Ordering.by[ScoredItem, Double](_.score)

  def queryItemSum(req: RecommendByItemJsonRequest, keyBase: String): Future[Result] = {
    def queryRedis(storeCode: String, itemCode: String): Future[LinkedHashSet[(String, Double)]] = {
      val key = keyBase + ":" + storeCode + ":" + itemCode
        
      Redis.pipelined1(Redis.SalesDb) { pipe =>
        req.sortOrder match {
          case Asc(col) =>
            pipe.zRangeByScoreWithScores(
              key, Score.exclusive(0), Score.Infinity, Some(req.paging.offset, req.paging.limit)
            )
          case Desc(col) =>
            pipe.zRevRangeByScoreWithScores(
              key, Score.Infinity, Score.exclusive(0), Some(req.paging.offset, req.paging.limit)
            )
        }
      }
    }
    
    val shouldExcluded = req.salesItems.map {
      it => it.storeCode + ":" + it.itemCode
    }.toSet

    Future.fold {
      req.salesItems.map {
        it => queryRedis(it.storeCode, it.itemCode)
      }
    }(LinkedHashSet[(String, Double)]()) {
      (sum, h) => {
        val removedThemselves = h.filter { e =>
          ! shouldExcluded.contains(e._1)
        }
        (sum ++ removedThemselves).take(req.paging.limit)
      }
    }.map { recs =>
      val result = Ok(Json.obj(
        "header" -> Json.obj(
          "sequenceNumber" -> req.header.sequenceNumber,
          "statusCode" -> "OK",
          "message" -> ""
        ),
        "salesItems" -> JsArray(
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
      logger.info("Json RecommendByItem.byItem response: " + result)
      result
    }
  }
}
