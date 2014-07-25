package testhelpers

object Helper {
  def doWith[A, R](arg: A)(func: A => R): R = func(arg)
}
