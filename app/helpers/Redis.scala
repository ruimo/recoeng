package helpers

import scredis.PipelineClient
import scala.concurrent.Future
import scala.util.Try
import play.api.Play.current
import scala.concurrent.duration._
import scala.concurrent.Await

object Redis {
  val SalesDb = 0
  val DbOffsetForTest = 8

  lazy val redisDbBase = play.api.Play.current.configuration.getInt("redis.db.base").getOrElse(0)

  val redis = scredis.Redis()

  def call[T](f: scredis.Redis => Future[T]): Future[T] = f(redis)
  def sync[T](f: scredis.Redis => Future[T]): T = sync[T]()(f)

  def sync[T](
    dbNo: Int = SalesDb,
    atMost: Duration = Duration(10, SECONDS)
  )(
    f: scredis.Redis => Future[T]
  ): T = {
    redis.select(dbNo + redisDbBase)
    Await.result(
      f(redis), atMost
    )
  }
  
  def pipelined(dbNo: Int = SalesDb)(f: PipelineClient => Unit): Future[IndexedSeq[Try[Any]]] = { 
    val dbno = dbNo + redisDbBase
    
    redis.pipelined { pipe =>
      pipe.select(dbno)
      f(pipe)
    }
  }

  def pipelined1[T](dbNo: Int = SalesDb)(f: PipelineClient => Future[T]): Future[T] = {
    val dbno = dbNo + redisDbBase

    redis.pipelined1 { pipe =>
      pipe.select(dbno)
      f(pipe)
    }
  }
}
