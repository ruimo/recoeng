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
      
      doWith(sync(
        WS.url("http://localhost:3333" + controllers.routes.ItemItem.onSales())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
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
        """))
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        jsonResp \ "sequenceNumber" === JsString("12345")
        jsonResp \ "statusCode" === JsString("OK")
      }

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

      doWith(sync(
        WS.url("http://localhost:3333" + controllers.routes.ItemItem.onSales())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20141003000000",
    "sequenceNumber": "23456"
  },
  "mode": "0001",
  "dateTime": "20141002223344",
  "userCode": "1",
  "itemList": [
    {
      "storeCode": "0001",
      "itemCode": "1491",
      "quantity": 3
    },
    {
      "storeCode": "0001",
      "itemCode": "5819",
      "quantity": 1
    },
    {
      "storeCode": "0003",
      "itemCode": "8172",
      "quantity": 4
    }
  ]
}
        """))
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        jsonResp \ "sequenceNumber" === JsString("23456")
        jsonResp \ "statusCode" === JsString("OK")
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.zRangeWithScores[String]("itemSoldDates", end = -1)
        }
      )) { set =>
        set.size === 4
        set.contains("0001:1491:20141002", 20141002)
        set.contains("0002:5810:20141002", 20141002)
        set.contains("0001:5819:20141002", 20141002)
        set.contains("0003:8172:20141002", 20141002)
      }
      
      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.hGetAll[Int]("itemItem:0001:1491:20141002")
        }
      )) { optMap =>
        val map = optMap.get
        map.size === 3
        map("0002:5810") === 1
        map("0001:5819") === 2
        map("0003:8172") === 1
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
          map.size === 3
          map("0001:1491") === 2
          map("0002:5810") === 1
          map("0003:8172") === 1
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.hGetAll[Int]("itemItem:0003:8172:20141002")
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
          redis.zRangeWithScores[String]("itemItemSum1m:0001:1491", end = -1)
        }
      )) { set =>
        set.size === 3
        set.contains("0002:5810", 1)
        set.contains("0001:5819", 2)
        set.contains("0003:8172", 1)
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
        set.size === 3
        set.contains("0001:1491", 2)
        set.contains("0002:5810", 1)
        set.contains("0003:8172", 1)
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.zRangeWithScores[String]("itemItemSum1m:0003:8172", end = -1)
        }
      )) { set =>
        set.size === 2
        set.contains("0001:1491", 1)
        set.contains("0001:5819", 1)
      }

      doWith(sync(
        WS.url("http://localhost:3333" + controllers.routes.ItemItem.onSales())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20141010112233",
    "sequenceNumber": "34567"
  },
  "mode": "0001",
  "dateTime": "20141010111111",
  "userCode": "1",
  "itemList": [
    {
      "storeCode": "0001",
      "itemCode": "1491",
      "quantity": 3
    },
    {
      "storeCode": "0003",
      "itemCode": "8172",
      "quantity": 4
    }
  ]
}
        """))
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        jsonResp \ "sequenceNumber" === JsString("34567")
        jsonResp \ "statusCode" === JsString("OK")
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.zRangeWithScores[String]("itemSoldDates", end = -1)
        }
      )) { set =>
        set.size === 6
        set.contains("0001:1491:20141002", 20141002)
        set.contains("0002:5810:20141002", 20141002)
        set.contains("0001:5819:20141002", 20141002)
        set.contains("0003:8172:20141002", 20141002)
        set.contains("0001:1491:20141010", 20141010)
        set.contains("0003:8172:20141010", 20141010)
      }
      
      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.hGetAll[Int]("itemItem:0001:1491:20141002")
        }
      )) { optMap =>
        val map = optMap.get
        map.size === 3
        map("0002:5810") === 1
        map("0001:5819") === 2
        map("0003:8172") === 1
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.hGetAll[Int]("itemItem:0001:1491:20141010")
        }
      )) { optMap =>
        val map = optMap.get
        map.size === 1
        map("0003:8172") === 1
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
          map.size === 3
          map("0001:1491") === 2
          map("0002:5810") === 1
          map("0003:8172") === 1
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.hGetAll[Int]("itemItem:0003:8172:20141002")
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
          redis.hGetAll[Int]("itemItem:0003:8172:20141010")
        }
      )) { optMap =>
        val map = optMap.get
          map.size === 1
          map("0001:1491") === 1
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.zRangeWithScores[String]("itemItemSum1m:0001:1491", end = -1)
        }
      )) { set =>
        set.size === 3
        set.contains("0002:5810", 1)
        set.contains("0001:5819", 2)
        set.contains("0003:8172", 2)
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
        set.size === 3
        set.contains("0001:1491", 2)
        set.contains("0002:5810", 1)
        set.contains("0003:8172", 1)
      }

      doWith(sync(
        Redis.call { redis =>
          redis.select(Redis.DbOffsetForTest + Redis.SalesDb)
          redis.zRangeWithScores[String]("itemItemSum1m:0003:8172", end = -1)
        }
      )) { set =>
        set.size === 2
        set.contains("0001:1491", 2)
        set.contains("0001:5819", 1)
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
