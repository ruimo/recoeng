package controllers

import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import models.jsonrequest._
import helpers.JsConstraints._
import helpers.ErrorEntry

trait JsonRequestHandler {
  implicit val jsonRequestHeaderReads: Reads[JsonRequestHeader] = (
    (JsPath \ "dateTime").read(jodaDateReads("YYYYMMddHHmmss")) and
    (JsPath \ "sequenceNumber").read(regex("\\d{1,16}".r))
  )(JsonRequestHeader(_, _))

  implicit val jsonRequestPagingReads: Reads[JsonRequestPaging] = (
    (JsPath \ "offset").read[Int] and
    (JsPath \ "limit").read[Int]
  )(JsonRequestPaging.apply _)

  def toJson(errors: Seq[(JsPath, Seq[ValidationError])]): JsValue =
    Json.toJson(errors.map {e => ErrorEntry(e._1, e._2)})
}
