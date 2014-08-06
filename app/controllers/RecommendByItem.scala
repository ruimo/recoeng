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
import helpers.Redis
import scala.concurrent.Future
import scala.util.Try
import scredis.util.LinkedHashSet
import scredis.Score
import com.ruimo.recoeng.json.RecommendBySingleItemJsonRequest
import com.ruimo.recoeng.json.Asc
import com.ruimo.recoeng.json.Desc

object RecommendByItem extends Controller with HasLogger with JsonRequestHandler {
  def bySingleItem = Action.async(BodyParsers.parse.json) { request =>
    request.body.validate[RecommendBySingleItemJsonRequest].fold(
      errors => {
        logger.error(
          "Json RecommendByItem.bySingleItem validation error: " + errors +
          ", request = " + request.body
        )
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

  def handleBySingleItem(req: RecommendBySingleItemJsonRequest): Future[Result] =
    queryItemSum(req, "itemItemSum1m")
}
