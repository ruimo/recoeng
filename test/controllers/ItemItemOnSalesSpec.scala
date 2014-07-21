package controllers

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import models.jsonrequest.OnSalesJsonRequest
import models.jsonrequest.JsonRequestHeader
import org.joda.time.format.DateTimeFormat
import models.jsonrequest.SalesItem



@RunWith(classOf[JUnitRunner])
class ItemItemOnSalesSpec extends Specification {
  "ItemItem controller" should {
    "create valid redis request" in {
      var result: List[(String, (String, String))] = List()
      val req = OnSalesJsonRequest(
        JsonRequestHeader(
          DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseDateTime("2014-12-30 20:12:23"),
          "1"
        ),
        "0001",
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseDateTime("2014-12-30 20:12:50"),
        "2",
        List(
          SalesItem("0001", "165201", 3),
          SalesItem("0002", "512361", 1),
          SalesItem("0001", "810430", 2)
        )
      )

      ItemItem.handleOnSales(
        req,
        storeDb = (zsetKey, key) => {result = (zsetKey, key)::result}
      )
      result = result.reverse

      result.size === 6
      result.head === ("201412", ("0001:165201", "0002:512361"))
      result = result.tail
      result.head === ("201412", ("0001:165201", "0001:810430"))
      result = result.tail
      result.head === ("201412", ("0002:512361", "0001:165201"))
      result = result.tail
      result.head === ("201412", ("0002:512361", "0001:810430"))
      result = result.tail
      result.head === ("201412", ("0001:810430", "0001:165201"))
      result = result.tail
      result.head === ("201412", ("0001:810430", "0002:512361"))
      result.tail === Nil
    }
  }
}


