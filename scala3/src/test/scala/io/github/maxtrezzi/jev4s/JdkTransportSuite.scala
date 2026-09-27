package io.github.maxtrezzi.jev4s

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import LocalServer.{Hang, Reply}

class JdkTransportSuite extends munit.FunSuite:

  /** A sleeper that waits for nothing and keeps each delay. */
  final class Recorder extends Sleeper:
    var delays: List[FiniteDuration]          = Nil
    def sleep(duration: FiniteDuration): Unit = delays = delays :+ duration

  private def config(server: LocalServer, retry: RetryPolicy = RetryPolicy()) =
    JevConfig(ApiKey("test-key"), "jev-1.13.0", server.baseUrl, timeout = 2.seconds, retry = retry)

  private def withServer(responses: LocalServer.Response*)(test: LocalServer => Unit): Unit =
    val server = LocalServer(responses*)
    try test(server)
    finally server.close()

  test("a 200 returns the body; the request carries the key, the JSON type and the body"):
    withServer(Reply(200, """{"ok":1}""")): server =>
      assertEquals(JdkTransport(config(server)).send("""{"q":1}"""), Right("""{"ok":1}"""))
      val request = server.requests.asScala.toList.head
      assertEquals(request.method, "POST")
      assertEquals(request.path, "/v1/systemone")
      assertEquals(request.headers("authorization"), "Bearer test-key")
      assertEquals(request.headers("content-type"), "application/json")
      assertEquals(request.body, """{"q":1}""")

  test("a base URL with a path and a trailing slash keeps its path"):
    withServer(Reply(200, "{}")): server =>
      val prefixed = config(server).copy(baseUrl = java.net.URI.create(s"${server.baseUrl}/proxy/"))
      JdkTransport(prefixed).send("{}")
      assertEquals(server.requests.asScala.toList.map(_.path), List("/proxy/v1/systemone"))

  test("401, 400, 422 and 404 are errors, and are not retried"):
    val invalid = """{"detail":[{"loc":["body","model"],"msg":"Field required"}]}"""
    val unknown = """{"detail":{"error_type":"api_usage_error","message":"Unknown model: jev-0.0.0"}}"""
    for (status, body, expected) <- List(
        (401, "{}", JevError.Unauthorized),
        (400, unknown, JevError.Rejected("Unknown model: jev-0.0.0")),
        (422, invalid, JevError.Rejected("body.model: Field required")),
        (404, "nope", JevError.Unexpected(404, "nope")),
        (403, """{"detail":{"message":"Forbidden"}}""", JevError.Unexpected(403, "Forbidden")),
      )
    do
      withServer(Reply(status, body), Reply(200, "{}")): server =>
        val sleeper = Recorder()
        assertEquals(JdkTransport(config(server), sleeper).send("{}"), Left(expected))
        assertEquals(server.requests.size, 1)
        assertEquals(sleeper.delays, Nil)

  test("a 429 is retried after the delay the server asks for"):
    withServer(Reply(429, "{}", "Retry-After" -> "3"), Reply(200, "done")): server =>
      val sleeper = Recorder()
      assertEquals(JdkTransport(config(server), sleeper, () => 0.0).send("{}"), Right("done"))
      assertEquals(sleeper.delays, List(3.seconds))

  test("each response sends a Responded event with its request id, and each retry a Retrying event"):
    val id = "x-typesafe-request-id"
    withServer(
      Reply(429, "{}", "Retry-After" -> "3", id -> "req_1"),
      Reply(503, "down"),
      Reply(200, "done", id -> "req_3"),
    ): server =>
      var events    = List.empty[JevEvent]
      val transport = JdkTransport(config(server), Recorder(), () => 0.0, e => events = events :+ e)
      assertEquals(transport.send("{}"), Right("done"))
      assertEquals(
        events,
        List(
          JevEvent.Responded(429, Some("req_1")),
          JevEvent.Retrying(JevError.RateLimited(Some(3.seconds)), 1, 3.seconds),
          JevEvent.Responded(503, None),
          JevEvent.Retrying(JevError.ServerError(503, "down"), 2, 1.second),
          JevEvent.Responded(200, Some("req_3")),
        ),
      )

  test("an error that is not retried sends its Responded event too"):
    withServer(Reply(401, "{}", "x-typesafe-request-id" -> "req_401")): server =>
      var events = List.empty[JevEvent]
      assertEquals(
        JdkTransport(config(server), onEvent = e => events = events :+ e).send("{}"),
        Left(JevError.Unauthorized),
      )
      assertEquals(events, List(JevEvent.Responded(401, Some("req_401"))))

  test("a request that gets no response sends no Responded event"):
    var events = List.empty[JevEvent]
    val closed = JevConfig(ApiKey("k"), "jev-1.13.0", LocalServer.closedUrl(), retry = RetryPolicy.none)
    assert(JdkTransport(closed, onEvent = e => events = events :+ e).send("{}").isLeft)
    assertEquals(events, Nil)

  test("529, 503 and 408 are retried with a doubling backoff, less the jitter"):
    withServer(Reply(529, ""), Reply(503, ""), Reply(200, "done")): server =>
      val sleeper = Recorder()
      assertEquals(JdkTransport(config(server), sleeper, () => 0.5).send("{}"), Right("done"))
      assertEquals(sleeper.delays, List(437500.micros, 875.millis))
    withServer(Reply(408, "slow"), Reply(200, "done")): server =>
      assertEquals(JdkTransport(config(server), Recorder(), () => 0.0).send("{}"), Right("done"))

  test("when the retries are used up, the last error is returned"):
    withServer(Reply(503, "a"), Reply(503, "b"), Reply(503, "c"), Reply(200, "late")): server =>
      val sleeper = Recorder()
      assertEquals(JdkTransport(config(server), sleeper, () => 0.0).send("{}"), Left(JevError.ServerError(503, "c")))
      assertEquals(sleeper.delays.size, 2)
      assertEquals(server.requests.size, 3)

  test("no retry whose wait would end after maxElapsed, counted from the first attempt"):
    withServer(Reply(503, "a"), Reply(503, "b"), Reply(200, "late")): server =>
      var now              = 7.seconds.toNanos
      val sleeper: Sleeper = delay => now += delay.toNanos
      val budget           = config(server, RetryPolicy(maxElapsed = 1400.millis))
      // Waits of 0.5 s, then 1 s: the second would end 1.5 s after the first attempt.
      assertEquals(
        JdkTransport(budget, sleeper, () => 0.0, nanoTime = () => now).send("{}"),
        Left(JevError.ServerError(503, "b")),
      )
      assertEquals(server.requests.size, 2)

  test("RetryPolicy.none sends once"):
    withServer(Reply(503, "a"), Reply(200, "late")): server =>
      assertEquals(
        JdkTransport(config(server, RetryPolicy.none), Recorder()).send("{}"),
        Left(JevError.ServerError(503, "a")),
      )

  test("a refused connection is a network error, and is retried"):
    val closed  = JevConfig(ApiKey("k"), "m", LocalServer.closedUrl(), timeout = 2.seconds)
    val sleeper = Recorder()
    assertEquals(
      JdkTransport(closed, sleeper, () => 0.0).send("{}"),
      Left(JevError.Network(NetworkFailure.Connect, "no connection to 127.0.0.1")),
    )
    assertEquals(sleeper.delays.size, 2)

  /** `top`, caused by each of `causes` in turn: the chains that `java.net.http` throws. */
  private def chain(top: java.io.IOException, causes: Throwable*): java.io.IOException =
    causes.foldLeft[Throwable](top)((outer, cause) => outer.initCause(cause).getCause)
    top

  test("each failure with no response is the kind of network error that the JDK's exception says"):
    import java.io.{EOFException, IOException}
    import java.net.ConnectException
    import java.net.http.{HttpConnectTimeoutException, HttpTimeoutException}
    import java.nio.channels.{ClosedChannelException, UnresolvedAddressException}
    import java.security.cert.CertificateException
    import javax.net.ssl.SSLHandshakeException
    val endpoint                              = java.net.URI.create("https://api.typesafe.ai/v1/systemone")
    def error(e: IOException)                 = JdkTransport.networkError(e, endpoint, 30.seconds, 5.seconds)
    def network(f: NetworkFailure, m: String) = JevError.Network(f, m)
    val pkix                                  = "(certificate_unknown) PKIX path building failed"
    assertEquals(
      error(HttpTimeoutException("request timed out")),
      network(NetworkFailure.Timeout, "no response within 30 seconds"),
    )
    assertEquals(
      error(HttpConnectTimeoutException("HTTP connect timed out")),
      network(NetworkFailure.Timeout, "no connection within 5 seconds"),
    )
    assertEquals(
      error(chain(ConnectException(), ConnectException(), UnresolvedAddressException())),
      network(NetworkFailure.Connect, "the host api.typesafe.ai was not found"),
    )
    assertEquals(
      error(chain(ConnectException(), ConnectException(), ClosedChannelException())),
      network(NetworkFailure.Connect, "no connection to api.typesafe.ai"),
    )
    assertEquals(
      error(chain(SSLHandshakeException(pkix), SSLHandshakeException(pkix), CertificateException(pkix))),
      network(NetworkFailure.Certificate, pkix),
    )
    assertEquals(
      error(chain(SSLHandshakeException("Remote host terminated the handshake"))),
      network(NetworkFailure.Other, "Remote host terminated the handshake"),
    )
    assertEquals(
      error(chain(IOException("HTTP/1.1 header parser received no bytes"), EOFException("EOF reached while reading"))),
      network(NetworkFailure.Other, "HTTP/1.1 header parser received no bytes"),
    )
    assertEquals(error(IOException()), network(NetworkFailure.Other, "java.io.IOException"))

  test("a chain of causes that loops is read to an end"):
    val (first, second) = (java.net.ConnectException(), java.net.ConnectException())
    first.initCause(second)
    second.initCause(first)
    val endpoint = java.net.URI.create("https://api.typesafe.ai/v1/systemone")
    assertEquals(
      JdkTransport.networkError(first, endpoint, 30.seconds, 30.seconds),
      JevError.Network(NetworkFailure.Connect, "no connection to api.typesafe.ai"),
    )

  test("a connection may take the request's timeout, or the shorter connect timeout of your own client"):
    def own(connect: Option[Int]) =
      val builder = java.net.http.HttpClient.newBuilder()
      Some(connect.fold(builder)(s => builder.connectTimeout(java.time.Duration.ofSeconds(s.toLong))).build())
    assertEquals(JdkTransport.connectLimit(30.seconds, None), 30.seconds)
    assertEquals(JdkTransport.connectLimit(30.seconds, own(None)), 30.seconds)
    assertEquals(JdkTransport.connectLimit(30.seconds, own(Some(2))), 2.seconds)
    assertEquals(JdkTransport.connectLimit(30.seconds, own(Some(60))), 30.seconds)
    assertEquals(JdkTransport.connectLimit(30000.millis, own(Some(30))).toString, "30000 milliseconds")

  test("a connect timeout too long for a Scala duration leaves the request's timeout"):
    val forever = java.time.temporal.ChronoUnit.FOREVER.getDuration
    val own     = java.net.http.HttpClient.newBuilder().connectTimeout(forever).build()
    assertEquals(JdkTransport.connectLimit(30.seconds, Some(own)), 30.seconds)

  test("no connection within the connect timeout of your own client is a timeout that says so"):
    // A socket that accepts and never answers: the TLS handshake of an https request waits.
    val silent = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
    try
      val url    = java.net.URI.create(s"https://127.0.0.1:${silent.getLocalPort}")
      val own    = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofMillis(200)).build()
      val config = JevConfig(ApiKey("k"), "m", url, timeout = 5.seconds, retry = RetryPolicy.none)
      assertEquals(
        JdkTransport(config, httpClient = Some(own)).send("{}"),
        Left(JevError.Network(NetworkFailure.Timeout, "no connection within 200 milliseconds")),
      )
    finally silent.close()

  test("no response within the timeout is a network error"):
    withServer(Hang): server =>
      val quick = config(server, RetryPolicy.none).copy(timeout = 200.millis)
      assertEquals(
        JdkTransport(quick).send("{}"),
        Left(JevError.Network(NetworkFailure.Timeout, "no response within 200 milliseconds")),
      )

  test("a client of your own sends every request, retries included"):
    withServer(Reply(503, ""), Reply(200, "done")): server =>
      val own = CountingHttpClient()
      assertEquals(JdkTransport(config(server), Recorder(), httpClient = Some(own)).send("{}"), Right("done"))
      assertEquals(own.sent.get, 2)
      assertEquals(server.requests.size, 2)

  test("config.timeout still limits each request sent with a client of your own"):
    withServer(Hang): server =>
      val quick = config(server, RetryPolicy.none).copy(timeout = 200.millis)
      val own   = CountingHttpClient()
      assertEquals(
        JdkTransport(quick, httpClient = Some(own)).send("{}"),
        Left(JevError.Network(NetworkFailure.Timeout, "no response within 200 milliseconds")),
      )
      assertEquals(own.sent.get, 1)

  test("a config that java.net.http would refuse is an error, and nothing is sent"):
    withServer(Reply(200, "{}")): server =>
      val events  = collection.mutable.ListBuffer.empty[JevEvent]
      val invalid = config(server).copy(apiKey = ApiKey("secret-key\n"), timeout = Duration.Zero)
      assertEquals(
        JdkTransport(invalid, onEvent = events += _).send("{}"),
        Left(
          JevError.InvalidConfig(
            "the timeout must be more than zero: 0 days; " +
              "the API key has a character that an HTTP header cannot carry, such as a newline"
          )
        ),
      )
      assertEquals(server.requests.size, 0)
      assertEquals(events.toList, Nil)

  test("a base URL is http or https, in any case, with a host"):
    def problems(url: String) = JdkTransport.problems(JevConfig(ApiKey("k"), "m", java.net.URI.create(url)))
    assertEquals(problems("https://api.typesafe.ai"), Nil)
    assertEquals(problems("HTTP://localhost:8080"), Nil)
    for url <- List("api.typesafe.ai", "ftp://api.typesafe.ai", "https:///v1") do
      assertEquals(problems(url), List(s"the base URL must be an http or https URL with a host: $url"))

  test("a timeout is more than zero"):
    def problems(timeout: FiniteDuration) =
      JdkTransport.problems(JevConfig(ApiKey("k"), "m", timeout = timeout))
    assertEquals(problems(1.nanosecond), Nil)
    assertEquals(problems(Duration.Zero), List("the timeout must be more than zero: 0 days"))
    assertEquals(problems((-1).second), List("the timeout must be more than zero: -1 seconds"))

  test("an API key has only the characters an HTTP header can carry, and never appears in a problem"):
    def problems(key: String) = JdkTransport.problems(JevConfig(ApiKey(key), "m"))
    for c <- List('\t', ' ', '~', '\u0080', '\u00ff') do assertEquals(problems(s"key${c}key"), Nil, c.toInt)
    for c <- List('\u0000', '\n', '\u001f', '\u007f', '\u0100') do
      assertEquals(
        problems(s"secret${c}key"),
        List("the API key has a character that an HTTP header cannot carry, such as a newline"),
        c.toInt,
      )

  test("a retry policy whose wait could be negative is an error, with the default sleeper, and nothing is sent"):
    withServer(Reply(503, ""), Reply(503, ""), Reply(503, "")): server =>
      for (retry, problem) <- List(
          RetryPolicy(jitter = 2.0)                 -> "the retry's jitter must be between 0 and 1: 2.0",
          RetryPolicy(backoffInitial = (-1).second) -> "the retry's backoffInitial must be zero or more: -1 seconds",
          RetryPolicy(backoffMax = (-1).second)     -> "the retry's backoffMax must be zero or more: -1 seconds",
        )
      do assertEquals(JdkTransport(config(server, retry)).send("{}"), Left(JevError.InvalidConfig(problem)))
      assertEquals(server.requests.size, 0)

  test("a retry policy has backoffs of zero or more and a jitter between 0 and 1"):
    def problems(retry: RetryPolicy) = JdkTransport.problems(JevConfig(ApiKey("k"), "m", retry = retry))
    for retry <- List(RetryPolicy(), RetryPolicy(backoffInitial = Duration.Zero, backoffMax = Duration.Zero))
    do assertEquals(problems(retry), Nil)
    for jitter <- List(0.0, 1.0) do assertEquals(problems(RetryPolicy(jitter = jitter)), Nil, jitter)
    for jitter <- List(-0.1, 1.1, Double.NaN) do
      assertEquals(
        problems(RetryPolicy(jitter = jitter)),
        List(s"the retry's jitter must be between 0 and 1: $jitter"),
        jitter,
      )
    assertEquals(
      problems(RetryPolicy(backoffInitial = (-1).nanosecond, backoffMax = (-1).nanosecond)),
      List(
        "the retry's backoffInitial must be zero or more: -1 nanoseconds",
        "the retry's backoffMax must be zero or more: -1 nanoseconds",
      ),
    )

  test("the default sleeper and random source are used when none is given"):
    withServer(Reply(503, ""), Reply(200, "done")): server =>
      val fast = config(server, RetryPolicy(backoffInitial = 1.millis))
      assertEquals(JdkTransport(fast).send("{}"), Right("done"))

  test("Retry-After: milliseconds first, then seconds; anything else is ignored"):
    def of(headers: (String, String)*) = JdkTransport.retryAfter(headers.toMap.get)
    assertEquals(of("retry-after-ms" -> "250", "retry-after" -> "9"), Some(250.millis))
    assertEquals(of("retry-after" -> " 1.5 "), Some(1500.millis))
    assertEquals(of("retry-after" -> "0"), Some(Duration.Zero))
    assertEquals(of("retry-after" -> "Wed, 21 Oct 2026 07:28:00 GMT"), None)
    assertEquals(of("retry-after" -> "-1"), None)
    assertEquals(of("retry-after-ms" -> "soon", "retry-after" -> "2"), Some(2.seconds))
    assertEquals(of(), None)

  test("every 2xx is a success"):
    assertEquals(JdkTransport.result(204, "", _ => None), Right(""))
    assertEquals(JdkTransport.result(299, "x", _ => None), Right("x"))
    assertEquals(JdkTransport.result(300, "x", _ => None), Left(JevError.Unexpected(300, "x")))
    assertEquals(JdkTransport.result(199, "x", _ => None), Left(JevError.Unexpected(199, "x")))
    assertEquals(JdkTransport.result(599, "x", _ => None), Left(JevError.ServerError(599, "x")))
    assertEquals(
      JdkTransport.result(500, """{"detail":{"message":"Internal error"}}""", _ => None),
      Left(JevError.ServerError(500, "Internal error")),
    )
    assertEquals(JdkTransport.result(600, "x", _ => None), Left(JevError.Unexpected(600, "x")))
