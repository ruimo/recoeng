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

@RunWith(classOf[JUnitRunner])
class RecommendByItemSpec extends Specification {
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
println("header = " + response.header("Content-Type"))
println("body = " + response.body)
        response.header("Content-Type").toString.indexOf("application/json") !== -1
        val jsonResp = Json.parse(response.body)
        jsonResp \ "sequenceNumber" === JsString("3194710")
        jsonResp \ "statusCode" === JsString("OK")
      }
    } 
  }
}

