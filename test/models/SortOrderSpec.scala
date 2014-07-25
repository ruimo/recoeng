package models

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import models.jsonrequest.SortOrder
import models.jsonrequest.Asc
import org.specs2.mutable.Specification
import models.jsonrequest.Desc

@RunWith(classOf[JUnitRunner])
class SortOrderSpec extends Specification {
  "sort order" should {
    "Asc(code) can be parsed" in {
      SortOrder("Asc(code)") === Asc("code")
    }

    "Desc(code) can be parsed" in {
      SortOrder("desc(code)") === Desc("code")
    }
  }
}

