package io.github.maxtrezzi.jev4s

import scala.concurrent.duration.FiniteDuration

/** Why a request failed. The API returns errors as values and never throws. */
enum JevError derives CanEqual:

  /** The request was not sent: it has the listed problems. */
  case InvalidRequest(problems: List[Problem])

  /** HTTP 401: the API key is missing or wrong. */
  case Unauthorized

  /** HTTP 400 or 422: Jev refused the request, for example for an unknown model or a malformed
    * question. Sending it again gives the same answer.
    */
  case Rejected(message: String)

  /** HTTP 429: too many requests. `retryAfter` comes from the `Retry-After` header. */
  case RateLimited(retryAfter: Option[FiniteDuration])

  /** HTTP 529: Jev is overloaded. */
  case Overloaded

  /** HTTP 408, or any HTTP 5xx status other than 529, with the message of its body: the server
    * did not complete the request.
    */
  case ServerError(status: Int, message: String)

  /** Any other HTTP status, such as 403 or 404, with the message of its body. Sending the same
    * request again does not help.
    */
  case Unexpected(status: Int, message: String)

  /** No HTTP response at all: a timeout, or a refused connection. */
  case Network(message: String)

  /** The response could not be read. */
  case Decoding(message: String)

  /** True when sending the same request again may succeed. */
  def isRetryable: Boolean = this match
    case RateLimited(_) | Overloaded | ServerError(_, _) | Network(_)                    => true
    case InvalidRequest(_) | Unauthorized | Rejected(_) | Unexpected(_, _) | Decoding(_) => false

/** A problem found in a request before it is sent. */
enum Problem derives CanEqual:
  case NoQuestions
  case EmptyName
  case DuplicateName(name: String)
  case ScoreLevels(name: String, levels: Int)
  case DuplicateLevel(name: String, level: String)
  case ChoiceOptions(name: String, options: Int)
  case DuplicateOptionKey(name: String, key: String)

  def message: String = this match
    case NoQuestions                => "a request needs at least one question"
    case EmptyName                  => "a question name is empty"
    case DuplicateName(n)           => s"the question name '$n' is used more than once"
    case ScoreLevels(n, got)        => s"score '$n' needs 2 to 10 levels, got $got"
    case DuplicateLevel(n, level)   => s"score '$n' uses the level '$level' more than once"
    case ChoiceOptions(n, got)      => s"choice '$n' needs 1 to 255 options, got $got"
    case DuplicateOptionKey(n, key) => s"choice '$n' uses the option key '$key' more than once"
