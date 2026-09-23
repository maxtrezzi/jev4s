package io.github.maxtrezzi.jev4s

import scala.concurrent.duration._

/** When a failed request is sent again. Only a retryable [[JevError]] is retried: a rate limit, an
  * overload, a server error or a network failure.
  *
  * The defaults are those of TypeSafe's official SDKs: 2 retries, a delay of 0.5 s doubled up to
  * 5 s with up to 25% taken off at random, and the server's `Retry-After` honoured up to 60 s.
  *
  * `maxElapsed` limits the time a call spends retrying, 30 s by default, which the SDKs do not
  * limit: a retry whose wait would end later than `maxElapsed` after the first attempt is not
  * made, and the last error is returned. The last attempt can still take up to the config's
  * `timeout` on top of it.
  */
final case class RetryPolicy(
    maxRetries: Int = 2,
    backoffInitial: FiniteDuration = 500.millis,
    backoffMax: FiniteDuration = 5.seconds,
    jitter: Double = 0.25,
    maxRetryAfter: FiniteDuration = 60.seconds,
    maxElapsed: FiniteDuration = 30.seconds
) {

  /** The wait before retry number `retry` (1 for the first), or `None` when the retries are used
    * up or the wait would end after `maxElapsed`. `random` is a number in [0, 1); `retryAfter` is
    * the delay the server asked for, if any; `elapsed` is the time since the first attempt began.
    * The same arguments always give the same result.
    */
  def delayFor(
      retry: Int,
      random: Double,
      retryAfter: Option[FiniteDuration],
      elapsed: FiniteDuration
  ): Option[FiniteDuration] =
    Option
      .when(retry <= maxRetries)(retryAfter.filter(_ <= maxRetryAfter).getOrElse(backoff(retry, random)))
      .filter(delay => elapsed + delay <= maxElapsed)

  private def backoff(retry: Int, random: Double): FiniteDuration = {
    val doubled = backoffInitial.toNanos.toDouble * math.pow(2, (retry - 1).toDouble)
    val capped  = math.min(doubled, backoffMax.toNanos.toDouble)
    (capped * (1 - jitter * random)).toLong.nanos
  }
}

object RetryPolicy {

  /** Never retry. */
  val none: RetryPolicy = RetryPolicy(maxRetries = 0)
}

/** How the transport waits between retries. Replace it in tests to wait for nothing. */
trait Sleeper {
  def sleep(duration: FiniteDuration): Unit
}

object Sleeper {

  /** Blocks the calling thread for the whole wait. On JDK 21 or later, a virtual thread makes this
    * cheap. On JDK 17 there are no virtual threads: the thread waits and does no other work.
    */
  val thread: Sleeper = duration => Thread.sleep(duration.toMillis)
}
