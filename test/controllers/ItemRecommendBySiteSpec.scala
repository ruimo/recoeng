package controllers

import scala.concurrent.duration._
import org.fest.assertions.Assertions._
import org.fest.assertions.StringAssert
import play.api.test.FakeRequest
import play.api.libs.json.Json
import org.specs2.mutable.Specification
import play.api.test.WithServer
import play.api.test._
import play.api.test.Helpers._
import play.api.libs.ws.WS
import scala.concurrent.Await
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import java.util.concurrent.TimeUnit
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import helpers.Redis
import testhelpers.Helper._
import play.api.Play.current
import play.api.libs.json.JsString
import play.api.test.Helpers._
import play.api.test._
import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber

@RunWith(classOf[JUnitRunner])
class ItemRecommendBySiteSpec extends Specification {
  "recommend by item controller" should {
    val appWithMemoryDatabase = FakeApplication(
      additionalConfiguration = inMemoryDatabase("default") + ("redis.db.base" -> Redis.DbOffsetForTest)
    )

    "Can create recommend by site record" in new WithServer(appWithMemoryDatabase, port = 3333) {
      Redis.sync { _.flushDb() }
      doWith(Redis.sync { _.zRangeWithScores[String]("itemSoldDates", end = -1) }) { _.size === 0 }

      doWith(Await.result(
        WS.url("http://localhost:3333" + controllers.routes.ItemRecommendBySite.create())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20140421234411",
    "sequenceNumber": "3194710"
  },
  "storeCode": "0001",
  "itemCode": "5817",
  "itemList": [
    {
      "storeCode": "0001",
      "itemCode": "4810",
      "score": 40
    },
    {
      "storeCode": "0002",
      "itemCode": "1048",
      "score": 10
    }
  ]
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
      }

      doWith(Redis.sync { redis =>
        redis.sMembers[String]("itemSite")
      }) { set =>
        set.size === 1
        set.contains("0001:5817") must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSite:0001:5817", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0001:4810", 40.0) === true
        set.contains("0002:1048", 10.0) === true
      }

      doWith(Await.result(
        WS.url("http://localhost:3333" + controllers.routes.ItemRecommendBySite.bySingleItem())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20140421234411",
    "sequenceNumber": "3194720"
  },
  "storeCode": "0001",
  "itemCode": "5817",
  "sort": "desc(cost)",
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
          header \ "sequenceNumber" === JsString("3194720")
          header \ "statusCode" === JsString("OK")
        }

        doWith((jsonResp \ "itemList").asInstanceOf[JsArray]) { itemList =>
          itemList.value.size === 2
          doWith(
            itemList.value.map { o =>
              (
                (o \ "storeCode").asInstanceOf[JsString].value +
                ":" +
                (o \ "itemCode").asInstanceOf[JsString].value,
                o \ "score"
              )
            }.toMap
          ) { map =>
            map("0001:4810") === JsNumber(40.0)
            map("0002:1048") === JsNumber(10.0)
          }
        }
      }

      // Create another recommendation
      doWith(Await.result(
        WS.url("http://localhost:3333" + controllers.routes.ItemRecommendBySite.create())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20140421234411",
    "sequenceNumber": "3194730"
  },
  "storeCode": "0003",
  "itemCode": "1834",
  "itemList": [
    {
      "storeCode": "0001",
      "itemCode": "4810",
      "score": 40
    },
    {
      "storeCode": "0005",
      "itemCode": "1233",
      "score": 10
    }
  ]
}    
            """)), Duration(10, SECONDS)
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        doWith(jsonResp \ "header") { header =>
          header \ "sequenceNumber" === JsString("3194730")
          header \ "statusCode" === JsString("OK")
        }
      }

      doWith(Redis.sync { redis =>
        redis.sMembers[String]("itemSite")
      }) { set =>
        set.size === 2
        set.contains("0001:5817") must beTrue
        set.contains("0003:1834") must beTrue
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSite:0001:5817", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0001:4810", 40.0) === true
        set.contains("0002:1048", 10.0) === true
      }

      doWith(Redis.sync { redis =>
        redis.zRangeWithScores[String]("itemItemSite:0003:1834", end = -1)
      }) { set =>
        set.size === 2
        set.contains("0001:4810", 40.0) === true
        set.contains("0005:1233", 10.0) === true
      }

      doWith(Await.result(
        WS.url("http://localhost:3333" + controllers.routes.ItemRecommendBySite.list())
          .withHeaders("Content-Type" -> "application/json; charset=utf-8")
          .post(Json.parse("""
{
  "header": {
    "dateTime": "20140421234411",
    "sequenceNumber": "3194780"
  },
  "sort": "",
  "cursorPaging": {
    "cursor": "0",
    "limit": 10
  }
}    
            """)), Duration(10, SECONDS)
      )) { response =>
        response.status === 200
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        doWith(jsonResp \ "header") { header =>
          header \ "sequenceNumber" === JsString("3194780")
          header \ "statusCode" === JsString("OK")
        }

        doWith((jsonResp \ "itemList").asInstanceOf[JsArray]) { itemList =>
          itemList.value.size === 2
          doWith(
            itemList.value.map { o =>
              (o \ "storeCode").asInstanceOf[JsString].value +
              ":" +
              (o \ "itemCode").asInstanceOf[JsString].value
            }.toSet
          ) { set =>
            set.contains("0001:5817") must beTrue
            set.contains("0003:1834") must beTrue
          }
        }
      }
    } 
  }
}

