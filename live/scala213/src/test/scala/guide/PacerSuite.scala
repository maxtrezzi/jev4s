package guide

import java.net.{InetSocketAddress, URI}
import java.util.concurrent.Executors

import com.sun.net.httpserver.HttpServer

import io.github.maxtrezzi.jev4s._

/** The pacer of the guide against a local server that accepts 20 requests in any second, and
  * answers 429 to the others. No API key, no cost.
  */
class PacerSuite extends munit.FunSuite {

  private val reply = """{"model": "jev-1.13.0", "answers": {"urgent": {"type": "noul", "noul": 0.9}}}"""

  /** A client over a new local server that answers 429 above `perSecond` requests in any second. */
  private def limitedTo(perSecond: Int)(body: JevClient => Unit): Unit = {
    val server  = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    val started = collection.mutable.Queue.empty[Long]
    server.createContext(
      "/",
      exchange => {
        val allowed = started.synchronized {
          val now = System.nanoTime()
          while (started.nonEmpty && started.head <= now - 1000000000L) started.dequeue()
          val free = started.size < perSecond
          if (free) started.enqueue(now)
          free
        }
        val (status, answer) = if (allowed) (200, reply) else (429, """{"detail": {"message": "slow down"}}""")
        val bytes            = answer.getBytes("UTF-8")
        exchange.sendResponseHeaders(status, bytes.length.toLong)
        exchange.getResponseBody.write(bytes)
        exchange.close()
      }
    )
    val pool = Executors.newFixedThreadPool(16)
    server.setExecutor(pool)
    server.start()
    try {
      val url = URI.create(s"http://localhost:${server.getAddress.getPort}")
      body(JevClient.create(JevConfig(new ApiKey("test"), "jev-1.13.0", url, retry = RetryPolicy.none)))
    } finally {
      server.stop(0)
      pool.shutdown()
    }
  }

  private val messages = List.tabulate(30)(i => s"message $i")

  private def rateLimited(results: List[(String, Either[JevError, Boolean])]): Int =
    results.count { case (_, result) => result.left.exists(_.isInstanceOf[JevError.RateLimited]) }

  test("without a pacer, 30 messages on 8 threads go over a limit of 20 per second") {
    limitedTo(20) { client =>
      assert(rateLimited(Many.urgentAll(client, messages, perSecond = 1e6)) > 0)
    }
  }

  test("a pacer at 15 per second keeps 30 messages under the limit, and takes about 2 seconds") {
    limitedTo(20) { client =>
      val start   = System.nanoTime()
      val results = Many.urgentAll(client, messages, perSecond = 15)
      val seconds = (System.nanoTime() - start) / 1e9
      assertEquals(results.map(_._2), List.fill[Either[JevError, Boolean]](30)(Right(true)))
      assert(seconds >= 29 / 15.0, s"took $seconds s: the pacer let calls start too early")
    }
  }
}
