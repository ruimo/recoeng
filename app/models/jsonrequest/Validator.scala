package models.jsonrequest

import java.util.regex.Pattern

object Validator {
  def createValidator(fieldName: String, patternString: String): String => String = {
    val pattern: Pattern = Pattern.compile(patternString)
    return input: String =>
      if (pattern.matcher(input).matches) input
      else throw new AssertionError("Field '%s' should match pattern '%s'".format(fieldName, patternString))
  }
}
