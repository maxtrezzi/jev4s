package io.github.maxtrezzi.jev4s

import java.io.IOException
import java.net.{ConnectException, URI}
import java.net.http.{HttpClient, HttpConnectTimeoutException, HttpRequest, HttpTimeoutException}
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.nio.channels.UnresolvedAddressException
import java.security.cert.CertificateException
import java.util.concurrent.ThreadLocalRandom
import javax.net.ssl.SSLException
import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.jdk.DurationConverters.*
import scala.jdk.OptionConverters.*

import io.github.maxtrezzi.jev4s.internal.Codec

/** The [[Transport]] over `java.net.http`, with the retries of `config.retry`. It sends a
  * [[JevEvent.Responded]] to `onEvent` for each response, and a [[JevEvent.Retrying]] before each
  * wait. `nanoTime` is the clock that measures `maxElapsed`; replace it only in tests.
  *
  * Without `httpClient`, each instance builds its own `java.net.http.HttpClient`, with its own
  * threads, and never closes it: build one transport and reuse it. Pass `httpClient` to use a
  * client of your own, for example with your executor or a proxy, or to close it on JDK 21. Then
  * its connect timeout is its own, and `config.timeout` still limits each request. jev4s never
  * closes a client you pass: you close it, after the last call.
  *
  * Before it sends anything, it checks what would make the JDK throw an exception: a base URL
  * that is not `http` or `https` with a host, a timeout of zero or less, an API key with a
  * character that an HTTP header cannot carry, and a [[RetryPolicy]] whose wait could be negative.
  * It returns [[JevError.InvalidConfig]] instead, and sends nothing.
  *
  * A thread interrupted while it sends or waits gets its `InterruptedException`: interruption is
  * a request to stop, not an error to return.
  */
final class JdkTransport(
    config: JevConfig,
    sleeper: Sleeper = Sleeper.thread,
    random: () => Double = () => ThreadLocalRandom.current().nextDouble(),
    onEvent: JevEvent => Unit = _ => (),
    nanoTime: () => Long = () => System.nanoTime(),
    httpClient: Option[HttpClient] = None,
) extends Transport:

  private val problems = JdkTransport.problems(config)
  // Lazy: with a timeout of zero or less, the JDK's builder throws, and no request is sent.
  private lazy val client = httpClient.getOrElse(HttpClient.newBuilder().connectTimeout(config.timeout.toJava).build())
  private val endpoint    = URI.create(s"${config.baseUrl.toString.stripSuffix("/")}/v1/systemone")
  private val connecting  = JdkTransport.connectLimit(config.timeout, httpClient)

  def send(body: String): Either[JevError, String] =
    if problems.isEmpty then attempt(body, retry = 1, start = nanoTime())
    else Left(JevError.InvalidConfig(problems.mkString("; ")))

  @tailrec private def attempt(body: String, retry: Int, start: Long): Either[JevError, String] =
    val result = once(body)
    result match
      case Left(error) if error.isRetryable =>
        config.retry.delayFor(retry, random(), JdkTransport.retryAfterOf(error), (nanoTime() - start).nanos) match
          case Some(delay) =>
            onEvent(JevEvent.Retrying(error, retry, delay))
            sleeper.sleep(delay)
            attempt(body, retry + 1, start)
          case None => result
      case _ => result

  private def once(body: String): Either[JevError, String] =
    val request = HttpRequest
      .newBuilder(endpoint)
      .timeout(config.timeout.toJava)
      .header("Authorization", s"Bearer ${config.apiKey.value}")
      .header("Content-Type", "application/json")
      .POST(BodyPublishers.ofString(body))
      .build()
    val sent =
      try Right(client.send(request, BodyHandlers.ofString()))
      catch case e: IOException => Left(JdkTransport.networkError(e, endpoint, config.timeout, connecting))
    sent.flatMap: response =>
      val header = (name: String) => response.headers.firstValue(name).toScala
      onEvent(JevEvent.Responded(response.statusCode, header("x-typesafe-request-id")))
      JdkTransport.result(response.statusCode, response.body, header)

object JdkTransport:

  /** A response turned into a body or an error. Statuses: https://docs.typesafe.ai/api.md. */
  private[jev4s] def result(status: Int, body: String, header: String => Option[String]): Either[JevError, String] =
    status match
      case s if s / 100 == 2             => Right(body)
      case 401                           => Left(JevError.Unauthorized)
      case 400 | 422                     => Left(JevError.Rejected(Codec.errorMessage(body)))
      case 429                           => Left(JevError.RateLimited(retryAfter(header)))
      case 529                           => Left(JevError.Overloaded)
      case s if s == 408 || s / 100 == 5 => Left(JevError.ServerError(s, Codec.errorMessage(body)))
      case s                             => Left(JevError.Unexpected(s, Codec.errorMessage(body)))

  /** How long a connection may take: `timeout`, which limits the whole request, or the connect
    * timeout of the caller's client when it is shorter. They are compared as `java.time.Duration`s:
    * a connect timeout above `Long.MaxValue` nanoseconds, such as `ChronoUnit.FOREVER`, is not a
    * Scala duration, and converting it throws.
    */
  private[jev4s] def connectLimit(timeout: FiniteDuration, httpClient: Option[HttpClient]): FiniteDuration =
    httpClient
      .flatMap(_.connectTimeout.toScala)
      .filter(_.compareTo(timeout.toJava) < 0)
      .fold(timeout)(_.toScala.toCoarsest)

  /** A request that got no response, as a [[JevError.Network]]. Measured on JDK 21 and 25: a
    * timeout while connecting is an `HttpConnectTimeoutException`, and one while waiting for the
    * response an `HttpTimeoutException`, its superclass; a refused connection and an unknown host
    * are a `ConnectException` with no message, the second caused by an
    * `UnresolvedAddressException`; a certificate that TLS refuses, not trusted or for another host,
    * is an `SSLHandshakeException` caused by a `CertificateException`. Other TLS failures, such as
    * a server that closes the connection during the handshake, carry no such cause, and may pass:
    * they are [[NetworkFailure.Other]], as a connection closed early is.
    *
    * The causes are read to a depth of 16, three at most in the chains measured: Java allows a
    * chain that loops, and reading it to its end would never return.
    */
  private[jev4s] def networkError(
      e: IOException,
      endpoint: URI,
      timeout: FiniteDuration,
      connectTimeout: FiniteDuration,
  ): JevError =
    def causedBy(cause: Class[?]) =
      Iterator.iterate[Throwable](e)(_.getCause).takeWhile(_ != null).take(16).exists(cause.isInstance)
    def message = Option(e.getMessage).getOrElse(e.getClass.getName)
    e match
      case _: HttpConnectTimeoutException =>
        JevError.Network(NetworkFailure.Timeout, s"no connection within $connectTimeout")
      case _: HttpTimeoutException => JevError.Network(NetworkFailure.Timeout, s"no response within $timeout")
      case _: ConnectException if causedBy(classOf[UnresolvedAddressException]) =>
        JevError.Network(NetworkFailure.Connect, s"the host ${endpoint.getHost} was not found")
      case _: ConnectException => JevError.Network(NetworkFailure.Connect, s"no connection to ${endpoint.getHost}")
      case _: SSLException if causedBy(classOf[CertificateException]) =>
        JevError.Network(NetworkFailure.Certificate, message)
      case _ => JevError.Network(NetworkFailure.Other, message)

  /** The delay a response asks for: `retry-after-ms` in milliseconds, else `retry-after` in
    * seconds. A date, a negative number or anything else that is not a number gives `None`.
    */
  private[jev4s] def retryAfter(header: String => Option[String]): Option[FiniteDuration] =
    def number(name: String) = header(name).flatMap(_.trim.toDoubleOption).filter(_ >= 0)
    number("retry-after-ms")
      .map(ms => (ms * 1e6).toLong.nanos)
      .orElse(number("retry-after").map(s => (s * 1e9).toLong.nanos))

  /** What in `config` `java.net.http` refuses with an `IllegalArgumentException`, whose message
    * can hold the API key. The rules are the JDK's, measured on JDK 21: a scheme `http` or `https`
    * in any case, a host, a positive timeout, and header characters from a tab and from space to
    * `\u00ff`, except `\u007f`. The key itself never appears in a problem.
    *
    * The retry policy's rules are those of the Python SDK's `RetryConfig`: a backoff of zero or
    * more, and a jitter between 0 and 1. Outside them a wait can be negative, and `Thread.sleep`
    * throws `IllegalArgumentException`; a jitter that is not a number is refused too.
    */
  private[jev4s] def problems(config: JevConfig): List[String] =
    val url   = config.baseUrl
    val retry = config.retry
    List(
      Option.unless(url.getHost != null && List("http", "https").exists(_.equalsIgnoreCase(url.getScheme)))(
        s"the base URL must be an http or https URL with a host: $url"
      ),
      Option.unless(config.timeout > Duration.Zero)(s"the timeout must be more than zero: ${config.timeout}"),
      Option.unless(config.apiKey.value.forall(headerCharacter))(
        "the API key has a character that an HTTP header cannot carry, such as a newline"
      ),
      Option.unless(retry.backoffInitial >= Duration.Zero)(
        s"the retry's backoffInitial must be zero or more: ${retry.backoffInitial}"
      ),
      Option.unless(retry.backoffMax >= Duration.Zero)(
        s"the retry's backoffMax must be zero or more: ${retry.backoffMax}"
      ),
      Option.unless(retry.jitter >= 0 && retry.jitter <= 1)(
        s"the retry's jitter must be between 0 and 1: ${retry.jitter}"
      ),
    ).flatten

  private def headerCharacter(c: Char): Boolean = c == '\t' || (c >= ' ' && c <= '\u00ff' && c != '\u007f')

  private def retryAfterOf(error: JevError): Option[FiniteDuration] = error match
    case JevError.RateLimited(delay) => delay
    case _                           => None
