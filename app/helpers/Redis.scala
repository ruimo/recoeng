package helpers

import scredis.PipelineClient
import scala.concurrent.Future
import scala.util.Try
import play.api.Play.current

object Redis {
  val SalesDb = 0
  val DbOffsetForTest = 8

  lazy val redisDbBase = play.api.Play.current.configuration.getInt("redis.db.base").getOrElse(0)

  val redis = scredis.Redis()

  def call[T](f: scredis.Redis => Future[T]): Future[T] = f(redis)
  
  def pipelined(dbNo: Int = SalesDb)(f: PipelineClient => Any): Future[IndexedSeq[Try[Any]]] = { 
    val dbno = dbNo + redisDbBase
    
    redis.pipelined { pipe =>
      pipe.select(dbno)
      f(pipe)
    }
  }
}
