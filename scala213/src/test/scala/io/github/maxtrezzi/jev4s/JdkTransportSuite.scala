package io.github.maxtrezzi.jev4s

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import LocalServer.{Hang, Reply}

class JdkTransportSuite extends munit.FunSuite {

  /** A sleeper that waits for nothing and keeps each delay. */
  final class Recorder extends Sleeper {
    var delays: List[FiniteDuration]          = Nil
    def sleep(duration: FiniteDuration): Unit = delays = delays :+ duration
  }

  private def config(server: LocalServer, retry: RetryPolicy = RetryPolicy()) =
    JevConfig(new ApiKey("test-key"), "jev-1.13.0", server.baseUrl, timeout = 2.seconds, retry = retry)

  private def withServer(responses: LocalServer.Response*)(test: LocalServer => Unit): Unit = {
    val server = new LocalServer(responses: _*)
    try test(server)
    finally server.close()
  }

  test("a 200 returns the body; the request carries the key, the JSON type and the body") {
    withServer(Reply(200, """{"ok":1}""")) { server =>
      assertEquals(new JdkTransport(config(server)).send("""{"q":1}"""), Right("""{"ok":1}"""))
      val request = server.requests.asScala.toList.head
      assertEquals(request.method, "POST")
      assertEquals(request.path, "/v1/systemone")
      assertEquals(request.headers("authorization"), "Bearer test-key")
      assertEquals(request.headers("content-type"), "application/json")
      assertEquals(request.body, """{"q":1}""")
    }
  }

  test("a base URL with a path and a trailing slash keeps its path") {
    withServer(Reply(200, "{}")) { server =>
      val prefixed = config(server).copy(baseUrl = java.net.URI.create(s"${server.baseUrl}/proxy/"))
      new JdkTransport(prefixed).send("{}")
      assertEquals(server.requests.asScala.toList.map(_.path), List("/proxy/v1/systemone"))
    }
  }

  test("401, 400, 422 and 404 are errors, and are not retried") {
    val invalid = """{"detail":[{"loc":["body","model"],"msg":"Field required"}]}"""
    val unknown = """{"detail":{"error_type":"api_usage_error","message":"Unknown model: jev-0.0.0"}}"""
    val cases   = List[(Int, String, JevError)](
      (401, "{}", JevError.Unauthorized),
      (400, unknown, JevError.Rejected("Unknown model: jev-0.0.0")),
      (422, invalid, JevError.Rejected("body.model: Field required")),
      (404, "nope", JevError.Unexpected(404, "nope")),
      (403, """{"detail":{"message":"Forbidden"}}""", JevError.Unexpected(403, "Forbidden"))
    )
    for ((status, body, expected) <- cases)
      withServer(Reply(status, body), Reply(200, "{}")) { server =>
        val sleeper = new Recorder
        assertEquals(new JdkTransport(config(server), sleeper).send("{}"), Left(expected))
        assertEquals(server.requests.size, 1)
        assertEquals(sleeper.delays, Nil)
      }
  }

  test("a 429 is retried after the delay the server asks for") {
    withServer(Reply(429, "{}", "Retry-After" -> "3"), Reply(200, "done")) { server =>
      val sleeper = new Recorder
      assertEquals(new JdkTransport(config(server), sleeper, () => 0.0).send("{}"), Right("done"))
      assertEquals(sleeper.delays, List(3.seconds))
    }
  }

  test("each retry sends a Retrying event before its wait") {
    withServer(Reply(429, "{}", "Retry-After" -> "3"), Reply(503, "down"), Reply(200, "done")) { server =>
      var events    = List.empty[JevEvent]
      val transport = new JdkTransport(config(server), new Recorder, () => 0.0, e => events = events :+ e)
      assertEquals(transport.send("{}"), Right("done"))
      assertEquals(
        events,
        List[JevEvent](
          JevEvent.Retrying(JevError.RateLimited(Some(3.seconds)), 1, 3.seconds),
          JevEvent.Retrying(JevError.ServerError(503, "down"), 2, 1.second)
        )
      )
    }
  }

  test("529, 503 and 408 are retried with a doubling backoff, less the jitter") {
    withServer(Reply(529, ""), Reply(503, ""), Reply(200, "done")) { server =>
      val sleeper = new Recorder
      assertEquals(new JdkTransport(config(server), sleeper, () => 0.5).send("{}"), Right("done"))
      assertEquals(sleeper.delays, List(437500.micros, 875.millis))
    }
    withServer(Reply(408, "slow"), Reply(200, "done")) { server =>
      assertEquals(new JdkTransport(config(server), new Recorder, () => 0.0).send("{}"), Right("done"))
    }
  }

  test("when the retries are used up, the last error is returned") {
    withServer(Reply(503, "a"), Reply(503, "b"), Reply(503, "c"), Reply(200, "late")) { server =>
      val sleeper = new Recorder
      assertEquals(
        new JdkTransport(config(server), sleeper, () => 0.0).send("{}"),
        Left(JevError.ServerError(503, "c"))
      )
      assertEquals(sleeper.delays.size, 2)
      assertEquals(server.requests.size, 3)
    }
  }

  test("no retry whose wait would end after maxElapsed, counted from the first attempt") {
    withServer(Reply(503, "a"), Reply(503, "b"), Reply(200, "late")) { server =>
      var now              = 7.seconds.toNanos
      val sleeper: Sleeper = delay => now += delay.toNanos
      val budget           = config(server, RetryPolicy(maxElapsed = 1400.millis))
      // Waits of 0.5 s, then 1 s: the second would end 1.5 s after the first attempt.
      assertEquals(
        new JdkTransport(budget, sleeper, () => 0.0, nanoTime = () => now).send("{}"),
        Left(JevError.ServerError(503, "b"))
      )
      assertEquals(server.requests.size, 2)
    }
  }

  test("RetryPolicy.none sends once") {
    withServer(Reply(503, "a"), Reply(200, "late")) { server =>
      assertEquals(
        new JdkTransport(config(server, RetryPolicy.none), new Recorder).send("{}"),
        Left(JevError.ServerError(503, "a"))
      )
    }
  }

  test("a refused connection is a network error, and is retried") {
    val closed  = JevConfig(new ApiKey("k"), "m", LocalServer.closedUrl(), timeout = 2.seconds)
    val sleeper = new Recorder
    new JdkTransport(closed, sleeper, () => 0.0).send("{}") match {
      case Left(JevError.Network(message)) =>
        assert(!message.startsWith("no response within"), message)
        assertEquals(sleeper.delays.size, 2)
      case other => fail(s"expected a network error, got $other")
    }
  }

  test("no response within the timeout is a network error") {
    withServer(Hang) { server =>
      val quick = config(server, RetryPolicy.none).copy(timeout = 200.millis)
      assertEquals(new JdkTransport(quick).send("{}"), Left(JevError.Network("no response within 200 milliseconds")))
    }
  }

  test("a client of your own sends every request, retries included") {
    withServer(Reply(503, ""), Reply(200, "done")) { server =>
      val own = new CountingHttpClient()
      assertEquals(new JdkTransport(config(server), new Recorder, httpClient = Some(own)).send("{}"), Right("done"))
      assertEquals(own.sent.get, 2)
      assertEquals(server.requests.size, 2)
    }
  }

  test("config.timeout still limits each request sent with a client of your own") {
    withServer(Hang) { server =>
      val quick = config(server, RetryPolicy.none).copy(timeout = 200.millis)
      val own   = new CountingHttpClient()
      assertEquals(
        new JdkTransport(quick, httpClient = Some(own)).send("{}"),
        Left(JevError.Network("no response within 200 milliseconds"))
      )
      assertEquals(own.sent.get, 1)
    }
  }

  test("the default sleeper and random source are used when none is given") {
    withServer(Reply(503, ""), Reply(200, "done")) { server =>
      val fast = config(server, RetryPolicy(backoffInitial = 1.millis))
      assertEquals(new JdkTransport(fast).send("{}"), Right("done"))
    }
  }

  test("Retry-After: milliseconds first, then seconds; anything else is ignored") {
    def of(headers: (String, String)*) = JdkTransport.retryAfter(headers.toMap.get)
    assertEquals(of("retry-after-ms" -> "250", "retry-after" -> "9"), Some(250.millis))
    assertEquals(of("retry-after" -> " 1.5 "), Some(1500.millis))
    assertEquals(of("retry-after" -> "0"), Some(Duration.Zero))
    assertEquals(of("retry-after" -> "Wed, 21 Oct 2026 07:28:00 GMT"), None)
    assertEquals(of("retry-after" -> "-1"), None)
    assertEquals(of("retry-after-ms" -> "soon", "retry-after" -> "2"), Some(2.seconds))
    assertEquals(of(), None)
  }

  test("every 2xx is a success") {
    assertEquals(JdkTransport.result(204, "", _ => None), Right(""))
    assertEquals(JdkTransport.result(299, "x", _ => None), Right("x"))
    assertEquals(JdkTransport.result(300, "x", _ => None), Left(JevError.Unexpected(300, "x")))
    assertEquals(JdkTransport.result(199, "x", _ => None), Left(JevError.Unexpected(199, "x")))
    assertEquals(JdkTransport.result(599, "x", _ => None), Left(JevError.ServerError(599, "x")))
    assertEquals(
      JdkTransport.result(500, """{"detail":{"message":"Internal error"}}""", _ => None),
      Left(JevError.ServerError(500, "Internal error"))
    )
    assertEquals(JdkTransport.result(600, "x", _ => None), Left(JevError.Unexpected(600, "x")))
  }
}
