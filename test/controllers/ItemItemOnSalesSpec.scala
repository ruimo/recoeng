package controllers

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import models.jsonrequest.OnSalesJsonRequest
import models.jsonrequest.JsonRequestHeader
import org.joda.time.format.DateTimeFormat
import models.jsonrequest.SalesItem
import play.api.test._
import play.api.test.Helpers._
import play.api.libs.json.Json
import play.api.libs.ws.WS
import scala.concurrent.Await
import helpers.Redis
import scala.concurrent.duration._
import play.api.libs.json.JsString
import scala.concurrent.Future
import scredis.util.LinkedHashSet
import scredis.parsing.IntParser

@RunWith(classOf[JUnitRunner])
class ItemItemOnSalesSpec extends Specification {
  implicit val intParser = IntParser

  "ItemItem controller" should {
    val appWithMemoryDatabase = FakeApplication(
      additionalConfiguration = inMemoryDatabase("default") + ("redis.db.base" -> Redis.DbOffsetForTest)
    )
    
    "create valid redis request" in new WithServer(appWithMemoryDatabase, port = 3333) {
      sync(
        Redis.pipelined(Redis.SalesDb) { pipe =>
          pipe.flushDb()
        }
      )
      
      val request = Json.parse(
        """
{
  "header": {
    "dateTime": "20141002112233",
    "sequenceNumber": "12345"
  },
  "mode": "0001",
  "dateTime": "20141002112233",
  "userCode": "1",
  "itemList": [
    {
      "storeCode": "0001",
      "itemCode": "1491",
      "quantity": 3
    },
    {
      "storeCode": "0002",
      "itemCode": "5810",
      "quantity": 1
    },
    {
      "storeCode": "0001",
      "itemCode": "5819",
      "quantity": 4
    }
  ]
}
        """
      )

      val response = sync(
        WS.url("http://localhost:3333" + controllers.routes.ItemItem.onSales())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(request)
      )

      response.status === 200
      response.header("Content-Type").toString.indexOf("application/json") !== -1
      val jsonResp = Json.parse(response.body)
      jsonResp \ "sequenceNumber" === JsString("12345")
      jsonResp \ "statusCode" === JsString("OK")

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.zRangeWithScores[String]("itemSoldDates", end = -1)
        }
      )) { set =>
        set.size === 3
        set.contains("0001:1491:20141002", 20141002)
        set.contains("0002:5810:20141002", 20141002)
        set.contains("0001:5819:20141002", 20141002)
      }
      
      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.hGetAll[Int]("itemItem:0001:1491:20141002")
        }
      )) { optMap =>
        val map = optMap.get
        map.size === 2
        map("0002:5810") === 1
        map("0001:5819") === 1
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.hGetAll[Int]("itemItem:0002:5810:20141002")
        }
      )) { optMap =>
        val map = optMap.get
          map.size === 2
          map("0001:1491") === 1
          map("0001:5819") === 1
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.hGetAll[Int]("itemItem:0001:5819:20141002")
        }
      )) { optMap =>
        val map = optMap.get
          map.size === 2
          map("0001:1491") === 1
          map("0002:5810") === 1
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.zRangeWithScores[String]("itemItemSum1m:0001:1491", end = -1)
        }
      )) { set =>
        set.size === 2
        set.contains("0002:5810", 1)
        set.contains("0001:5819", 1)
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.zRangeWithScores[String]("itemItemSum1m:0002:5810", end = -1)
        }
      )) { set =>
        set.size === 2
        set.contains("0001:1491", 1)
        set.contains("0001:5819", 1)
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.zRangeWithScores[String]("itemItemSum1m:0001:5819", end = -1)
        }
      )) { set =>
        set.size === 2
        set.contains("0001:1491", 1)
        set.contains("0002:5810", 1)
      }
    }
  }

  def sync[T](future: Future[T]): T = Await.result(
    future, Duration(5, SECONDS)
  )

  def doWith[T](arg: T)(func: T => Unit) {
    func(arg)
  }
}
