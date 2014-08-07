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
import com.ruimo.recoeng.json.OnSalesJsonRequest
import com.ruimo.recoeng.json.SalesItem
import com.ruimo.recoeng.json.JsonRequestHeader

object ItemItem extends Controller with HasLogger with JsonRequestHandler {
  implicit val onSalesReads: Reads[OnSalesJsonRequest] = (
    (JsPath \ "header").read[JsonRequestHeader] and
    (JsPath \ "transactionMode").read(regex("\\w{4}".r)) and
    (JsPath \ "dateTime").read(jodaDateReads("YYYYMMddHHmmss") keepAnd regex("\\d{14}".r)) and
    (JsPath \ "userCode").read(regex("\\w{1,8}".r)) and
    (JsPath \ "salesItems").read[Seq[SalesItem]]
  )(OnSalesJsonRequest.apply _)

  def onSales = Action.async(BodyParsers.parse.json) { request =>
    request.body.validate[OnSalesJsonRequest].fold(
      errors => {
        logger.error(
          "Json ItemItem.onSales request validation error: " + errors +
          ", request = " + request.body
        )
        Future {BadRequest(toJson(errors))}
      },
      req => {
        logger.info("Json ItemItem.onSales request: " + req)
        handleOnSales(req)
      }
    )
  }

  def handleOnSales(req: OnSalesJsonRequest): Future[Result] = {
    val keySet = req.salesItems.map(_.redisCode).toSet
    val tranDate = req.tranDateInYyyyMmDd
    val res: Future[IndexedSeq[Try[Any]]] = Redis.pipelined(Redis.SalesDb) { pipe =>
      keySet.foreach { key => pipe.zAdd("itemSoldDates", (key + ":" + tranDate, tranDate.toDouble)) }
      for (key1 <- keySet; key2 <- keySet if key1 != key2) {
        pipe.hIncrBy("itemItem:" + key1 + ":" + tranDate)(key2, 1)
        pipe.zIncrBy("itemItemSum1m:" + key1, key2, 1)
      }
    }

    toJsonResult(req, res)
  }
    
  def toJsonResult(req: OnSalesJsonRequest, res: Future[IndexedSeq[Try[Any]]]): Future[Result] = {
    res.map { r =>
      if (r.exists(_.isFailure)) {
        val result = InternalServerError(
          Json.obj(
            "header" -> Json.obj(
              "sequenceNumber" -> req.header.sequenceNumber,
              "statusCode" -> "SYS",
              "message" -> r.toString
            )
          )
        )
        logger.error("Json onSales response: " + result)
        result
      }
      else {
        val result = Ok(
          Json.obj(
            "header" -> Json.obj(
              "sequenceNumber" -> req.header.sequenceNumber,
              "statusCode" -> "OK",
              "message" -> ""
            )
          )
        )
        logger.info("Json onSales response: " + result)
        result
      }
    }
  }
}
