package helpers

import play.api.libs.json.Reads
import play.api.data.validation.ValidationError
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsPath
import play.api.libs.json.Writes
import play.api.libs.json.JsString
import play.api.i18n.Messages
import play.api.libs.json.JsPath
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._

object JsConstraints {
  def regex(r: => scala.util.matching.Regex, error: String = "error.pattern")(implicit reads: Reads[String]) =
    Reads[String](js => reads.reads(js).flatMap { o =>
      r.unapplySeq(o).map(_ => JsSuccess(o)).getOrElse(JsError(ValidationError(error, r)))
    })

  implicit val jsPathWrites: Writes[JsPath] = Writes {
    path => JsString(path.toString)
  } 

  implicit val validationErrorWrites: Writes[ValidationError] = Writes {
    err => JsString(Messages(err.message, err.args: _*))
  }

  implicit val errorEntryWrites: Writes[ErrorEntry] = (
    (JsPath \ "path").write[JsPath] and
    (JsPath \ "errors").write[Seq[ValidationError]]
  )(unlift(ErrorEntry.unapply))
}
