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
import java.util.concurrent.TimeUnit

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

@RunWith(classOf[JUnitRunner])
class ItemItemSequenceErrorSpec extends Specification {
  "item item controller" should {
    val appWithMemoryDatabase = FakeApplication(
      additionalConfiguration = inMemoryDatabase("default") + ("stub.redis" -> true)
    )

    "Should return error if sequence number is invalid" in new WithServer(appWithMemoryDatabase, port = 3333) {
      val request = Json.parse(
        """
{
  "header": {
    "dateTime": "20140421234411",
    "sequenceNumber": ""
  },
  "mode": "0",
  "dateTime": "20140421234411",
  "userCode": "1",
  "salesItems": [
    {
      "storeCode": "4",
      "itemCode": "20481",
      "quantity": 3
    },
    {
      "storeCode": "2",
      "itemCode": "20412",
      "quantity": 1
    }
  ]
}
        """
      )

      val response = Await.result(
        WS.url("http://localhost:3333" + controllers.routes.ItemItem.onSales()).post(request),
        Duration(5, SECONDS)
      )
      println(response.body)
      response.status === 400
    }
  }
}
