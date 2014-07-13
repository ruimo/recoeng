package controllers

import play.api._
import play.api.mvc._

object Application extends Controller {
  def onSales = Action(BodyParsers.parse.json) { request =>

    Ok(views.html.index("Your new application is ready."))
  }
}
