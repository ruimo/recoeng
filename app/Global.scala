import play.api._
import play.api.Play.current
import scala.concurrent.duration._
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import helpers.Redis
import org.joda.time.DateTime
import scredis.PipelineClient
import scredis.Score
import helpers.Formatters._
import scala.concurrent.Await
import scredis.util.LinkedHashSet
import scala.concurrent.Future

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

  def houseKeepRecommendData(pipe: PipelineClient): Unit =
    houseKeepRecommendData(pipe, toYyyyMmDd(System.currentTimeMillis - itemItemHoldingDurationMillis))

  def houseKeepRecommendData(pipe: PipelineClient, expirationYyyyMmDd: Int) {
    val dates: LinkedHashSet[(String, Double)] = sync(
      pipe.zRangeByScoreWithScores(
        "itemSoldDates", Score.Infinity, Score.exclusive(expirationYyyyMmDd), Some(0, 500)
      )
    )
    houseKeepRecommendData(pipe, dates)
    if (! dates.isEmpty)
      houseKeepRecommendData(pipe, expirationYyyyMmDd)
  }

  def houseKeepRecommendData(pipe: PipelineClient, dates: LinkedHashSet[(String, Double)]) {
    dates.foreach { e =>
      houseKeepRecommendData(pipe, e._1, e._1.toInt)
    }
  }

  def houseKeepRecommendData(pipe: PipelineClient, itemKey: String, yyyymmdd: Int, cursor: Long = 0) {
    val itemItemKey = "itemItem:" + itemKey

    val itemItem: (Long, Set[(String, String)]) = sync(
      pipe.hScan(itemItemKey)(cursor, Some(500))
    )
    
    subtractItemItem(pipe: PipelineClient, itemKey, itemItem._2)
    sync(pipe.del(itemItemKey))

    val nextCursor = itemItem._1
    if (nextCursor != 0)
      houseKeepRecommendData(pipe, itemKey, yyyymmdd, nextCursor)
  }

  def subtractItemItem(pipe: PipelineClient, itemKey: String, itemItemRecs: Set[(String, String)]) {
    val sumKey = dropDate(itemKey)
    itemItemRecs.foreach { e =>
      sync(pipe.zIncrBy("itemItemSum1m:" + sumKey, e._1, - e._2.toInt))
    }
  }

  def dropDate(key: String): String = {
    val idx = key.lastIndexOf(':')
    if (idx == -1)
      throw new Error("itemKey(=" + key + ") does not have :.")
    key.substring(0, idx)
  }

  def sync[T](future: Future[T]): T = Await.result(
    future, Duration(10, SECONDS)
  )
}
