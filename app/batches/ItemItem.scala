package batches

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

object ItemItem {
  def houseKeepItemItem(itemItemHoldingDurationMillis: Long): Unit =
    houseKeepItemItem(toYyyyMmDd(System.currentTimeMillis - itemItemHoldingDurationMillis))

  def houseKeepItemItem(expirationYyyyMmDd: Int) {
    val dates: LinkedHashSet[(String, Double)] = sync {
      _.zRangeByScoreWithScores(
        "itemSoldDates", Score.Infinity, Score.exclusive(expirationYyyyMmDd), Some(0, 500)
      )
    }
    houseKeepItemItem(dates)
    if (! dates.isEmpty)
      houseKeepItemItem(expirationYyyyMmDd)
  }

  def houseKeepItemItem(dates: LinkedHashSet[(String, Double)]) {
    dates.foreach { e => houseKeepItemItem(e._1, e._2.toInt) }
  }

  def houseKeepItemItem(itemKey: String, yyyymmdd: Int, cursor: Long = 0) {
    val itemItemKey = "itemItem:" + itemKey

    val itemItem: (Long, Set[(String, String)]) = sync {
      _.hScan(itemItemKey)(cursor, Some(500))
    }
    
    // Do in transaction.
    Redis.pipelined(Redis.SalesDb) { pipe =>
      subtractItemItem(pipe, itemKey, itemItem._2)
      pipe.del(itemItemKey)
      pipe.zRem("itemSoldDates", itemKey)
    }

    val nextCursor = itemItem._1
    if (nextCursor != 0)
      houseKeepItemItem(itemKey, yyyymmdd, nextCursor)
  }

  def subtractItemItem(pipe: PipelineClient, itemKey: String, itemItemRecs: Set[(String, String)]) {
    val sumKey = dropDate(itemKey)
    itemItemRecs.foreach { e =>
      pipe.zIncrBy("itemItemSum1m:" + sumKey, e._1, - e._2.toInt)
    }
  }

  def dropDate(key: String): String = {
    val idx = key.lastIndexOf(':')
    if (idx == -1)
      throw new Error("itemKey(=" + key + ") does not have :.")
    key.substring(0, idx)
  }

  def sync[T](f: scredis.Redis => Future[T]): T = Redis.sync()(f)
}
