package helpers

import com.redis.RedisClient
import com.redis.RedisCommand

object Redis {
  def config = play.api.Play.maybeApplication.map(_.configuration).get
  def redisHost = config.getString("redis.host").get
  def redisPort = config.getInt("redis.port").get
  def redisDbBase = config.getInt("redis.db.base").getOrElse(0)
  
  val SalesDb = 0

  def withRedis[T](dbNo: Int = 0, f: RedisCommand => T): T =
    f(new RedisClient(redisHost, redisPort, dbNo))
}
