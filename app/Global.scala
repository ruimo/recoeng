import play.api._
import play.api.Play.current
import scala.concurrent.duration._
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._

object Global extends GlobalSettings {
  override def onStart(app: Application) {
    Akka.system.scheduler.schedule(Duration(0, SECONDS), Duration(10, SECONDS)) {
      println("execute Every 10 seconds")
    }
  }
}
