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
import com.ruimo.recoeng.json.RecommendByItemJsonRequest
import com.ruimo.recoeng.json.Asc
import com.ruimo.recoeng.json.Desc

object RecommendByItem extends Controller with HasLogger with JsonRequestHandler {
  def byItem = Action.async(BodyParsers.parse.json) { request =>
    request.body.validate[RecommendByItemJsonRequest].fold(
      errors => {
        logger.error(
          "Json RecommendByItem.byItem validation error: " + errors +
          ", request = " + request.body
        )
        Future {BadRequest(toJson(errors))}
      },
      req => {
        logger.info("Json RecommendByItem.byItem request: " + req)
        req.sortOrder match {
          case Asc(col) => handleByItem(req)
          case Desc(col) => handleByItem(req)
        }
      }
    )
  }

  def handleByItem(req: RecommendByItemJsonRequest): Future[Result] =
    queryItemSum(req, "itemItemSum1m")
}
