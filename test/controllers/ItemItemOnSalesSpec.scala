package controllers

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import org.joda.time.format.DateTimeFormat
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
import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber
import testhelpers.Helper._

@RunWith(classOf[JUnitRunner])
class ItemItemOnSalesSpec extends Specification {
  implicit val intParser = IntParser

  "ItemItem controller" should {
    val appWithMemoryDatabase = FakeApplication(
      additionalConfiguration = inMemoryDatabase("default") + ("redis.db.base" -> Redis.DbOffsetForTest)
    )
    
    "create valid redis request" in new WithServer(appWithMemoryDatabase, port = 3333) {
      Redis.sync { _.flushDb() }
      doWith(Redis.sync { _.zRangeWithScores[String]("itemSoldDates", end = -1) }) { _.size === 0 }

      doWith(Await.result(
        WS.url("http://localhost:3333" + controllers.routes.RecommendByItem.byItem())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20140421234411",
    "sequenceNumber": "3194710"
  },
  "salesItems": [
    {
      "storeCode": "4",
      "itemCode": "20481",
      "quantity": 2
    }
  ],
  "sort": "desc(score)",
  "paging": {
    "offset": 0,
    "limit": 10
  }
}    
            """)), Duration(10, SECONDS)
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        doWith(jsonResp \ "header") { header =>
          header \ "sequenceNumber" === JsString("3194710")
          header \ "statusCode" === JsString("OK")
        }
        (jsonResp \ "salesItems").asInstanceOf[JsArray].value.size === 0
      }        

      doWith(Await.result(
        WS.url("http://localhost:3333" + controllers.routes.ItemItem.onSales())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20141002112233",
    "sequenceNumber": "12345"
  },
  "transactionMode": "0001",
  "dateTime": "20141002112233",
  "userCode": "1",
  "salesItems": [
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
        """)), Duration(10, SECONDS)
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        doWith(jsonResp \ "header") { header =>
          header \ "sequenceNumber" === JsString("12345")
          header \ "statusCode" === JsString("OK")
        }
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemSoldDates", end = -1)
      }) { set =>
        set.size === 3
        set.contains("0001:1491:20141002", 20141002) must beTrue
        set.contains("0002:5810:20141002", 20141002) must beTrue
        set.contains("0001:5819:20141002", 20141002) must beTrue
      }
      
      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0001:1491:20141002")
      }) { optMap =>
        val map = optMap.get
        map.size === 2
        map("0002:5810") === 1
        map("0001:5819") === 1
      }

      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0002:5810:20141002")
      }) { optMap =>
        val map = optMap.get
          map.size === 2
          map("0001:1491") === 1
          map("0001:5819") === 1
      }

      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0001:5819:20141002")
      }) { optMap =>
        val map = optMap.get
          map.size === 2
          map("0001:1491") === 1
          map("0002:5810") === 1
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0001:1491", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0002:5810", 1) must beTrue
        set.contains("0001:5819", 1) must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0002:5810", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0001:1491", 1) must beTrue
        set.contains("0001:5819", 1) must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0001:5819", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0001:1491", 1) must beTrue
        set.contains("0002:5810", 1) must beTrue
      }

      doWith(Await.result(
        WS.url("http://localhost:3333" + controllers.routes.RecommendByItem.byItem())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20140421234411",
    "sequenceNumber": "3194711"
  },
  "salesItems": [
    {
      "storeCode": "0001",
      "itemCode": "1491",
      "quantity": 2
    }
  ],
  "sort": "desc(score)",
  "paging": {
    "offset": 0,
    "limit": 10
  }
}    
            """)), Duration(10, SECONDS)
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        doWith(jsonResp \ "header") { header =>
          header \ "sequenceNumber" === JsString("3194711")
          header \ "statusCode" === JsString("OK")
        }
        doWith((jsonResp \ "salesItems").asInstanceOf[JsArray]) { salesItems =>
          salesItems.value.size === 2
          doWith(
            salesItems.value.map { o =>
              (
                (o \ "storeCode").asInstanceOf[JsString].value +
                ":" +
                (o \ "itemCode").asInstanceOf[JsString].value,
                o \ "score"
              )
            }.toMap
          ) { map =>
            map("0002:5810") === JsNumber(1.0)
            map("0001:5819") === JsNumber(1.0)
          }
        }
      }        

      doWith(Await.result(
        WS.url("http://localhost:3333" + controllers.routes.ItemItem.onSales())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20141003000000",
    "sequenceNumber": "23456"
  },
  "transactionMode": "0001",
  "dateTime": "20141002223344",
  "userCode": "1",
  "salesItems": [
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
        """)), Duration(10, SECONDS)
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        doWith(jsonResp \ "header") { header =>
          header \ "sequenceNumber" === JsString("23456")
          header \ "statusCode" === JsString("OK")
        }
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemSoldDates", end = -1)
      }) { set =>
        set.size === 4
        set.contains("0001:1491:20141002", 20141002) must beTrue
        set.contains("0002:5810:20141002", 20141002) must beTrue
        set.contains("0001:5819:20141002", 20141002) must beTrue
        set.contains("0003:8172:20141002", 20141002) must beTrue
      }
      
      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0001:1491:20141002")
      }) { optMap =>
        val map = optMap.get
        map.size === 3
        map("0002:5810") === 1
        map("0001:5819") === 2
        map("0003:8172") === 1
      }

      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0002:5810:20141002")
      }) { optMap =>
        val map = optMap.get
          map.size === 2
          map("0001:1491") === 1
          map("0001:5819") === 1
      }

      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0001:5819:20141002")
      }) { optMap =>
        val map = optMap.get
          map.size === 3
          map("0001:1491") === 2
          map("0002:5810") === 1
          map("0003:8172") === 1
      }

      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0003:8172:20141002")
      }) { optMap =>
        val map = optMap.get
          map.size === 2
          map("0001:1491") === 1
          map("0001:5819") === 1
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0001:1491", end = -1)
      }) { set =>
        set.size === 3
        set.contains("0002:5810", 1) must beTrue
        set.contains("0001:5819", 2) must beTrue
        set.contains("0003:8172", 1) must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0002:5810", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0001:1491", 1) must beTrue
        set.contains("0001:5819", 1) must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0001:5819", end = -1)
      }) { set =>
        set.size === 3
        set.contains("0001:1491", 2) must beTrue
        set.contains("0002:5810", 1) must beTrue
        set.contains("0003:8172", 1) must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0003:8172", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0001:1491", 1) must beTrue
        set.contains("0001:5819", 1) must beTrue
      }

      doWith(Await.result(
        WS.url("http://localhost:3333" + controllers.routes.ItemItem.onSales())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20141010112233",
    "sequenceNumber": "34567"
  },
  "transactionMode": "0001",
  "dateTime": "20141010111111",
  "userCode": "1",
  "salesItems": [
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
        """)), Duration(10, SECONDS)
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        doWith(jsonResp \ "header") { header =>
          header \ "sequenceNumber" === JsString("34567")
          header \ "statusCode" === JsString("OK")
        }
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemSoldDates", end = -1)
      }) { set =>
        set.size === 6
        set.contains("0001:1491:20141002", 20141002) must beTrue
        set.contains("0002:5810:20141002", 20141002) must beTrue
        set.contains("0001:5819:20141002", 20141002) must beTrue
        set.contains("0003:8172:20141002", 20141002) must beTrue
        set.contains("0001:1491:20141010", 20141010) must beTrue
        set.contains("0003:8172:20141010", 20141010) must beTrue
      }
      
      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0001:1491:20141002")
      }) { optMap =>
        val map = optMap.get
        map.size === 3
        map("0002:5810") === 1
        map("0001:5819") === 2
        map("0003:8172") === 1
      }

      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0001:1491:20141010")
      }) { optMap =>
        val map = optMap.get
        map.size === 1
        map("0003:8172") === 1
      }

      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0002:5810:20141002")
      }) { optMap =>
        val map = optMap.get
          map.size === 2
          map("0001:1491") === 1
          map("0001:5819") === 1
      }

      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0001:5819:20141002")
      }) { optMap =>
        val map = optMap.get
          map.size === 3
          map("0001:1491") === 2
          map("0002:5810") === 1
          map("0003:8172") === 1
      }

      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0003:8172:20141002")
      }) { optMap =>
        val map = optMap.get
          map.size === 2
          map("0001:1491") === 1
          map("0001:5819") === 1
      }

      doWith(Redis.sync { redis =>
        redis.hGetAll[Int]("itemItem:0003:8172:20141010")
      }) { optMap =>
        val map = optMap.get
          map.size === 1
          map("0001:1491") === 1
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0001:1491", end = -1)
      }) { set =>
        set.size === 3
        set.contains("0002:5810", 1) must beTrue
        set.contains("0001:5819", 2) must beTrue
        set.contains("0003:8172", 2) must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0002:5810", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0001:1491", 1) must beTrue
        set.contains("0001:5819", 1) must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0001:5819", end = -1)
      }) { set =>
        set.size === 3
        set.contains("0001:1491", 2) must beTrue
        set.contains("0002:5810", 1) must beTrue
        set.contains("0003:8172", 1) must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0003:8172", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0001:1491", 2) must beTrue
        set.contains("0001:5819", 1) must beTrue
      }

      doWith(Await.result(
        WS.url("http://localhost:3333" + controllers.routes.RecommendByItem.byItem())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20140421234411",
    "sequenceNumber": "3194712"
  },
  "salesItems": [
    {
      "storeCode": "0001",
      "itemCode": "1491",
      "quantity": 1
    }
  ],
  "sort": "desc(score)",
  "paging": {
    "offset": 0,
    "limit": 2
  }
}    
            """)), Duration(10, SECONDS)
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        doWith(jsonResp \ "header") { header =>
          header \ "sequenceNumber" === JsString("3194712")
          header \ "statusCode" === JsString("OK")
        }
        doWith((jsonResp \ "salesItems").asInstanceOf[JsArray]) { salesItems =>
          salesItems.value.size === 2
          doWith(
            salesItems.value.map { o =>
              (
                (o \ "storeCode").asInstanceOf[JsString].value +
                ":" +
                (o \ "itemCode").asInstanceOf[JsString].value,
                o \ "score"
              )
            }.toMap
          ) { map =>
            map("0001:5819") === JsNumber(2.0)
            map("0003:8172") === JsNumber(2.0)
          }
        }
      }        

      doWith(Await.result(
        WS.url("http://localhost:3333" + controllers.routes.RecommendByItem.byItem())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20140421234411",
    "sequenceNumber": "3194712"
  },
  "salesItems": [
    {
      "storeCode": "0001",
      "itemCode": "1491",
      "quantity": 1
    }
  ],
  "sort": "desc(score)",
  "paging": {
    "offset": 2,
    "limit": 10
  }
}    
            """)), Duration(10, SECONDS)
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        doWith(jsonResp \ "header") { header =>
          header \ "sequenceNumber" === JsString("3194712")
          header \ "statusCode" === JsString("OK")
        }
        doWith((jsonResp \ "salesItems").asInstanceOf[JsArray]) { salesItems =>
          salesItems.value.size === 1
          doWith(
            salesItems.value.map { o =>
              (
                (o \ "storeCode").asInstanceOf[JsString].value +
                ":" +
                (o \ "itemCode").asInstanceOf[JsString].value,
                o \ "score"
              )
            }.toMap
          ) { map =>
            map("0002:5810") === JsNumber(1.0)
          }
        }
      }        

      // Expire record.
      batches.ItemItem.houseKeepItemItem(20141007)

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemSoldDates", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0001:1491:20141010", 20141010) must beTrue
        set.contains("0003:8172:20141010", 20141010) must beTrue
      }
      
      doWith(Redis.sync { _.keys("itemItem:0001:1491:20141002") }) { _.size === 0 }
      doWith(Redis.sync { _.keys("itemItem:0002:5810:20141002") }) { _.size === 0 }
      doWith(Redis.sync { _.keys("itemItem:0001:5819:20141002") }) { _.size === 0 }
      doWith(Redis.sync { _.keys("itemItem:0003:8172:20141002") }) { _.size === 0 }
      doWith(Redis.sync { _.hGetAll[Int]("itemItem:0001:1491:20141010") }) { optMap =>
        val map = optMap.get
        map.size === 1
        map("0003:8172") === 1
      }

      doWith(Redis.sync { _.hGetAll[Int]("itemItem:0003:8172:20141010") }) { optMap =>
        val map = optMap.get
          map.size === 1
          map("0001:1491") === 1
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0001:1491", end = -1)
      }) { set =>
        set.size === 3
        set.contains("0002:5810", 0) must beTrue
        set.contains("0001:5819", 0) must beTrue
        set.contains("0003:8172", 1) must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0002:5810", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0001:1491", 0) must beTrue
        set.contains("0001:5819", 0) must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0001:5819", end = -1)
      }) { set =>
        set.size === 3
        set.contains("0001:1491", 0) must beTrue
        set.contains("0002:5810", 0) must beTrue
        set.contains("0003:8172", 0) must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSum1m:0003:8172", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0001:1491", 1) must beTrue
        set.contains("0001:5819", 0) must beTrue
      }

      // Record with score=0 should not be recommended.
      doWith(Await.result(
        WS.url("http://localhost:3333" + controllers.routes.RecommendByItem.byItem())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20140421234411",
    "sequenceNumber": "3194713"
  },
  "salesItems": [
    {
      "storeCode": "0003",
      "itemCode": "8172",
      "quantity": 1
    }
  ],
  "sort": "desc(score)",
  "paging": {
    "offset": 0,
    "limit": 10
  }
}    
            """)), Duration(10, SECONDS)
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        doWith(jsonResp \ "header") { header =>
          header \ "sequenceNumber" === JsString("3194713")
          header \ "statusCode" === JsString("OK")
        }
        doWith((jsonResp \ "salesItems").asInstanceOf[JsArray]) { salesItems =>
          salesItems.value.size === 1
          doWith(
            salesItems.value.map { o =>
              (
                (o \ "storeCode").asInstanceOf[JsString].value +
                ":" +
                (o \ "itemCode").asInstanceOf[JsString].value,
                o \ "score"
              )
            }.toMap
          ) { map =>
            map("0001:1491") === JsNumber(1.0)
          }
        }
      }        
    }
  }
}
