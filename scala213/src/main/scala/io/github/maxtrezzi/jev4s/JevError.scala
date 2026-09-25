package io.github.maxtrezzi.jev4s

import scala.concurrent.duration.FiniteDuration

/** Why a request failed. The API returns errors as values and never throws. */
sealed abstract class JevError extends Product with Serializable {

  /** True when sending the same request again may succeed. */
  def isRetryable: Boolean = this match {
    case JevError.RateLimited(_) | JevError.Overloaded | JevError.ServerError(_, _) | JevError.Network(_) => true
    case JevError.InvalidRequest(_) | JevError.InvalidConfig(_) | JevError.Unauthorized | JevError.Rejected(_) |
        JevError.Unexpected(_, _) | JevError.Decoding(_) =>
      false
  }
}

object JevError {

  /** The request was not sent: it has the listed problems. */
  final case class InvalidRequest(problems: List[Problem]) extends JevError

  /** The request was not sent: the config has a value that HTTP cannot carry, such as an API key
    * with a newline or a timeout of zero. The message says which, and never shows the key.
    */
  final case class InvalidConfig(message: String) extends JevError

  /** HTTP 401: the API key is missing or wrong. */
  case object Unauthorized extends JevError

  /** HTTP 400 or 422: Jev refused the request, for example for an unknown model or a malformed
    * question. Sending it again gives the same answer.
    */
  final case class Rejected(message: String) extends JevError

  /** HTTP 429: too many requests. `retryAfter` comes from the `Retry-After` header. */
  final case class RateLimited(retryAfter: Option[FiniteDuration]) extends JevError

  /** HTTP 529: Jev is overloaded. */
  case object Overloaded extends JevError

  /** HTTP 408, or any HTTP 5xx status other than 529, with the message of its body: the server
    * did not complete the request.
    */
  final case class ServerError(status: Int, message: String) extends JevError

  /** Any other HTTP status, such as 403 or 404, with the message of its body. Sending the same
    * request again does not help.
    */
  final case class Unexpected(status: Int, message: String) extends JevError

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
    case Problem.DuplicateLevel(n, level)   => s"score '$n' uses the level '$level' more than once"
    case Problem.ChoiceOptions(n, got)      => s"choice '$n' needs 1 to 255 options, got $got"
    case Problem.DuplicateOptionKey(n, key) => s"choice '$n' uses the option key '$key' more than once"
    case Problem.DuplicateLevelValue(n, v)  => s"score '$n' gives the value '$v' to more than one level"
    case Problem.DuplicateOptionValue(n, v) => s"choice '$n' gives the value '$v' to more than one option"
  }
}

object Problem {
  case object NoQuestions                                            extends Problem
  case object EmptyName                                              extends Problem
  final case class DuplicateName(name: String)                       extends Problem
  final case class ScoreLevels(name: String, levels: Int)            extends Problem
  final case class DuplicateLevel(name: String, level: String)       extends Problem
  final case class ChoiceOptions(name: String, options: Int)         extends Problem
  final case class DuplicateOptionKey(name: String, key: String)     extends Problem
  final case class DuplicateLevelValue(name: String, value: String)  extends Problem
  final case class DuplicateOptionValue(name: String, value: String) extends Problem
}
