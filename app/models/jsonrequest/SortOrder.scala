package models.jsonrequest

import java.util.regex.Pattern

object SortOrder {
  val SortPattern = Pattern.compile("""(asc|desc)\((\w+)\)""", Pattern.CASE_INSENSITIVE)
  def apply(s: String): SortOrder = {
    val m = SortPattern.matcher(s)
    if (m.matches()) {
      if (m.group(1).toLowerCase().equals("asc")) Asc(m.group(2))
      else Desc(m.group(2))
    }
    else throw new IllegalArgumentException("Invalid sort spec '" + s + "'")
  }
}

sealed trait SortOrder {
  val columnName: String
}
case class Asc(columnName: String) extends SortOrder
case class Desc(columnName: String) extends SortOrder
