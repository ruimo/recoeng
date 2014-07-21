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
import helpers.ErrorEntry
import helpers.Redis
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import scala.concurrent.Future
import scala.util.Try

object ItemItem extends Controller with HasLogger {
//  def config = play.api.Play.maybeApplication.map(_.configuration).get
//  def stubRedis = config.getBoolean("stub.redis").getOrElse(false)

  val YmdKeyFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd")

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

  def onSales = Action.async(BodyParsers.parse.json) { request =>
    request.body.validate[OnSalesJsonRequest].fold(
      errors => {
        logger.error("Json onSales request validation error: " + errors)
        Future {BadRequest(toJson(errors))}
      },
      req => {
        logger.info("Json onSales request: " + req)
        handleOnSales(req)
      }
    )
  }

  def handleOnSales(req: OnSalesJsonRequest): Future[Result] = {
    val keySet = req.itemList.map(it => it.storeCode + ":" + it.itemCode).toSet
    val tranDate = YmdKeyFormat.print(req.dateTime).toInt
    val res: Future[IndexedSeq[Try[Any]]] = Redis.pipelined(Redis.SalesDb) { pipe =>
      keySet.foreach { key => pipe.zAdd("itemSoldDates", (key + ":" + tranDate, tranDate)) }
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
            Json.obj("sequenceNumber" -> req.header.sequenceNumber, "statusCode" -> "SYS", "message" -> r.toString)
        )
        logger.error("Json onSales response: " + result)
        result
      }
      else {
        val result = Ok(Json.obj("sequenceNumber" -> req.header.sequenceNumber, "statusCode" -> "OK", "message" -> ""))
        logger.info("Json onSales response: " + result)
        result
      }
    }
  }
  
  def toJson(errors: Seq[(JsPath, Seq[ValidationError])]): JsValue =
    Json.toJson(errors.map {e => ErrorEntry(e._1, e._2)})
}