package io.github.maxtrezzi.jev4s

import scala.concurrent.duration.*

class JevErrorSuite extends munit.FunSuite:

  test("rate limits, overload, server errors and network failures are retryable"):
    assert(JevError.RateLimited(Some(2.seconds)).isRetryable)
    assert(JevError.Overloaded.isRetryable)
    assert(JevError.ServerError(503, "unavailable").isRetryable)
    assert(JevError.Network("timeout").isRetryable)

  test("invalid, unauthorized, rejected and undecodable requests are not retryable"):
    assert(!JevError.InvalidRequest(List(Problem.NoQuestions)).isRetryable)
    assert(!JevError.InvalidConfig("the timeout must be more than zero: 0 days").isRetryable)
    assert(!JevError.Unauthorized.isRetryable)
    assert(!JevError.Rejected("bad field").isRetryable)
    assert(!JevError.Decoding("not JSON").isRetryable)
    assert(!JevError.Unexpected(404, "not found").isRetryable)

  test("every problem has its own message"):
    assertEquals(Problem.NoQuestions.message, "a request needs at least one question")
    assertEquals(Problem.EmptyName.message, "a question name is empty")
    assertEquals(Problem.DuplicateName("mood").message, "the question name 'mood' is used more than once")
    assertEquals(Problem.ScoreLevels("mood", 1).message, "score 'mood' needs 2 to 10 levels, got 1")
    assertEquals(Problem.DuplicateLevel("mood", "Calm").message, "score 'mood' uses the level 'Calm' more than once")
    assertEquals(Problem.ChoiceOptions("dept", 0).message, "choice 'dept' needs 1 to 255 options, got 0")
    assertEquals(
      Problem.DuplicateOptionKey("dept", "a").message,
      "choice 'dept' uses the option key 'a' more than once",
    )
    assertEquals(
      Problem.DuplicateLevelValue("mood", "Calm").message,
      "score 'mood' gives the value 'Calm' to more than one level",
    )
    assertEquals(
      Problem.DuplicateOptionValue("dept", "Billing").message,
      "choice 'dept' gives the value 'Billing' to more than one option",
    )
