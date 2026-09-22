package io.github.maxtrezzi.jev4s

import scala.concurrent.duration._

class RetryPolicySuite extends munit.FunSuite {

  private val policy = RetryPolicy()

  test("the defaults are those of the official SDKs") {
    assertEquals(policy, RetryPolicy(2, 500.millis, 5.seconds, 0.25, 60.seconds, 30.seconds))
  }

  test("the backoff doubles from the initial delay, with no jitter at random 0") {
    assertEquals(policy.delayFor(1, 0.0, None, Duration.Zero), Some(500.millis))
    assertEquals(policy.delayFor(2, 0.0, None, Duration.Zero), Some(1.second))
  }

  test("the backoff stops growing at the maximum") {
    val many = policy.copy(maxRetries = 10)
    assertEquals(many.delayFor(4, 0.0, None, Duration.Zero), Some(4.seconds))
    assertEquals(many.delayFor(5, 0.0, None, Duration.Zero), Some(5.seconds))
    assertEquals(many.delayFor(10, 0.0, None, Duration.Zero), Some(5.seconds))
  }

  test("the jitter takes off up to its fraction of the delay") {
    assertEquals(policy.delayFor(1, 0.5, None, Duration.Zero), Some(437500.micros))
    assertEquals(policy.delayFor(1, 0.999, None, Duration.Zero).map(_ > 375.millis), Some(true))
    assertEquals(policy.copy(jitter = 0).delayFor(1, 0.999, None, Duration.Zero), Some(500.millis))
  }

  test("there is no delay once the retries are used up") {
    assertEquals(policy.delayFor(3, 0.0, None, Duration.Zero), None)
    assertEquals(policy.delayFor(3, 0.0, Some(1.second), Duration.Zero), None)
    assertEquals(RetryPolicy.none.delayFor(1, 0.0, None, Duration.Zero), None)
  }

  test("the server's delay wins, up to the maximum it may ask for") {
    assertEquals(policy.delayFor(1, 0.5, Some(7.seconds), Duration.Zero), Some(7.seconds))
    assertEquals(
      policy.copy(maxElapsed = 60.seconds).delayFor(1, 0.5, Some(60.seconds), Duration.Zero),
      Some(60.seconds)
    )
    assertEquals(policy.delayFor(1, 0.0, Some(61.seconds), Duration.Zero), Some(500.millis))
  }

  test("no retry whose wait would end after maxElapsed") {
    assertEquals(policy.delayFor(2, 0.0, None, 29.seconds), Some(1.second))
    assertEquals(policy.delayFor(2, 0.0, None, 29001.millis), None)
    assertEquals(policy.delayFor(1, 0.0, Some(31.seconds), Duration.Zero), None)
    assertEquals(policy.copy(maxElapsed = Duration.Zero).delayFor(1, 0.0, None, Duration.Zero), None)
  }

  test("the thread sleeper returns after a zero wait") {
    Sleeper.thread.sleep(Duration.Zero)
  }
}
