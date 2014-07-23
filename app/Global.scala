import play.api._
import play.api.Play.current
import scala.concurrent.duration._
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import helpers.Redis
import org.joda.time.DateTime
import scredis.PipelineClient
import helpers.Formatters._

object Global extends GlobalSettings {
  val logger = Logger(getClass)
  lazy val itemItemHoldingDurationMillis: Long =
    play.api.Play.current.configuration.getMilliseconds("item.item.holding.duration").getOrElse(
      Duration(30, DAYS).toMillis
    )

  override def onStart(app: Application) {
    Akka.system.scheduler.schedule(Duration(0, SECONDS), Duration(1, DAYS)) {
      logger.info("Start house keeping recommend data. Duration(ms): " + itemItemHoldingDurationMillis)
      Redis.pipelined(Redis.SalesDb)(houseKeepRecommendData)
    }
  }

  def houseKeepRecommendData(pipe: PipelineClient): Unit = {
    val expirationYyyyMmDd: Int = toYyyyMmDd(System.currentTimeMillis - itemItemHoldingDurationMillis)
    pipe.zRangeByScoreWithScores(
      "itemSoldDates", Score.Infinity.asMin, Score.exclusive(expirationYyyyMmDd), Some(0, 500)
    )
  }
}
