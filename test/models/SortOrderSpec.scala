package models

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification
import com.ruimo.recoeng.json.SortOrder
import com.ruimo.recoeng.json.Asc
import com.ruimo.recoeng.json.Desc

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

