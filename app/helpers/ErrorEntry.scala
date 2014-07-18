package helpers

import play.api.libs.json.JsPath
import play.api.data.validation.ValidationError

case class ErrorEntry(path: JsPath, erros: Seq[ValidationError])
