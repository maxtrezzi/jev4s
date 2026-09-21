package io.github.maxtrezzi.jev4s

import scala.concurrent.duration.FiniteDuration

/** Why a request failed. The API returns errors as values and never throws. */
sealed abstract class JevError extends Product with Serializable {

  /** True when sending the same request again may succeed. */
  def isRetryable: Boolean = this match {
    case JevError.RateLimited(_) | JevError.Overloaded | JevError.ServerError(_, _) | JevError.Network(_) => true
    case JevError.InvalidRequest(_) | JevError.Unauthorized | JevError.Rejected(_) | JevError.Decoding(_) => false
  }
}

object JevError {

  /** The request was not sent: it has the listed problems. */
  final case class InvalidRequest(problems: List[Problem]) extends JevError

  /** HTTP 401: the API key is missing or wrong. */
  case object Unauthorized extends JevError

  /** HTTP 422: Jev refused the request. Sending it again gives the same answer. */
  final case class Rejected(message: String) extends JevError

  /** HTTP 429: too many requests. `retryAfter` comes from the `Retry-After` header. */
  final case class RateLimited(retryAfter: Option[FiniteDuration]) extends JevError

  /** HTTP 529: Jev is overloaded. */
  case object Overloaded extends JevError

  /** Any other HTTP 5xx status. */
  final case class ServerError(status: Int, body: String) extends JevError

  /** No HTTP response at all: a timeout, or a refused connection. */
  final case class Network(message: String) extends JevError

  /** The response could not be read. */
  final case class Decoding(message: String) extends JevError
}

/** A problem found in a request before it is sent. */
sealed abstract class Problem extends Product with Serializable {
  def message: String = this match {
    case Problem.NoQuestions                => "a request needs at least one question"
    case Problem.EmptyName                  => "a question name is empty"
    case Problem.DuplicateName(n)           => s"the question name '$n' is used more than once"
    case Problem.ScoreLevels(n, got)        => s"score '$n' needs 2 to 10 levels, got $got"
    case Problem.ChoiceOptions(n, got)      => s"choice '$n' needs 1 to 255 options, got $got"
    case Problem.DuplicateOptionKey(n, key) => s"choice '$n' uses the option key '$key' more than once"
  }
}

object Problem {
  case object NoQuestions                                        extends Problem
  case object EmptyName                                          extends Problem
  final case class DuplicateName(name: String)                   extends Problem
  final case class ScoreLevels(name: String, levels: Int)        extends Problem
  final case class ChoiceOptions(name: String, options: Int)     extends Problem
  final case class DuplicateOptionKey(name: String, key: String) extends Problem
}
