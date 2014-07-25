package models.jsonrequest

import java.util.regex.Pattern

object SortOrder {
  val SortPattern = """(?i)(asc|desc)\((\w+)\)""".r
  def apply(s: String): SortOrder = s match {
    case SortPattern(order, code) => if (order.toLowerCase == "asc") Asc(code) else Desc(code)
    case _ => throw new IllegalArgumentException("Invalid sort spec '" + s + "'")
  }
}

sealed trait SortOrder {
  val columnName: String
}
case class Asc(columnName: String) extends SortOrder
case class Desc(columnName: String) extends SortOrder
