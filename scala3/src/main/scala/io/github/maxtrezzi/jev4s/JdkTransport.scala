package io.github.maxtrezzi.jev4s

import java.io.IOException
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpTimeoutException}
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.ThreadLocalRandom
import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.jdk.DurationConverters.*
import scala.jdk.OptionConverters.*

import io.github.maxtrezzi.jev4s.internal.Codec

/** The [[Transport]] over `java.net.http`, with the retries of `config.retry`. Before each wait
  * it sends a [[JevEvent.Retrying]] to `onEvent`. `nanoTime` is the clock that measures
  * `maxElapsed`; replace it only in tests.
  *
  * Without `httpClient`, each instance builds its own `java.net.http.HttpClient`, with its own
  * threads, and never closes it: build one transport and reuse it. Pass `httpClient` to use a
  * client of your own, for example with your executor or a proxy, or to close it on JDK 21. Then
  * its connect timeout is its own, and `config.timeout` still limits each request. jev4s never
  * closes a client you pass: you close it, after the last call.
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

  private val client   = httpClient.getOrElse(HttpClient.newBuilder().connectTimeout(config.timeout.toJava).build())
  private val endpoint = URI.create(s"${config.baseUrl.toString.stripSuffix("/")}/v1/systemone")

  def send(body: String): Either[JevError, String] = attempt(body, retry = 1, start = nanoTime())

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
    try
      val response = client.send(request, BodyHandlers.ofString())
      JdkTransport.result(response.statusCode, response.body, name => response.headers.firstValue(name).toScala)
    catch
      case _: HttpTimeoutException => Left(JevError.Network(s"no response within ${config.timeout}"))
      case e: IOException          => Left(JevError.Network(Option(e.getMessage).getOrElse(e.getClass.getName)))

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

  /** The delay a response asks for: `retry-after-ms` in milliseconds, else `retry-after` in
    * seconds. A date, a negative number or anything else that is not a number gives `None`.
    */
  private[jev4s] def retryAfter(header: String => Option[String]): Option[FiniteDuration] =
    def number(name: String) = header(name).flatMap(_.trim.toDoubleOption).filter(_ >= 0)
    number("retry-after-ms")
      .map(ms => (ms * 1e6).toLong.nanos)
      .orElse(number("retry-after").map(s => (s * 1e9).toLong.nanos))

  private def retryAfterOf(error: JevError): Option[FiniteDuration] = error match
    case JevError.RateLimited(delay) => delay
    case _                           => None
