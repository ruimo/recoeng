package helpers

import com.redis.RedisClient
import com.redis.RedisCommand

object Redis {
  val SalesDb = 0

  def config = play.api.Play.maybeApplication.map(_.configuration).get
  def redisHost = config.getString("redis.host").get
  def redisPort = config.getInt("redis.port").get
  def redisDbBase = config.getInt("redis.db.base").getOrElse(0)
  
  def withRedis[T](dbNo: Int = 0)(f: RedisClient => T): T =
    f(new RedisClient(redisHost, redisPort, dbNo))

  def withRedisPipeline(dbNo: Int = 0)(f: RedisClient#PipelineClient => Any): Option[List[Any]] =
    (new RedisClient(redisHost, redisPort, dbNo)).pipeline(f)
}
