package guide

import io.github.maxtrezzi.jev4s.*

// snippet: explain
def explain(error: JevError): String = error match
  case JevError.InvalidRequest(problems)    => problems.map(_.message).mkString("; ")
  case JevError.InvalidConfig(message)      => s"fix the config: $message"
  case JevError.Unauthorized                => "check TYPESAFE_API_KEY"
  case JevError.Rejected(message)           => s"Jev refused the request: $message"
  case JevError.RateLimited(after)          => s"too many requests; wait ${after.fold("a little")(_.toString)}"
  case JevError.Unexpected(status, message) => s"HTTP $status, check the base URL and the key: $message"
  case JevError.Network(NetworkFailure.Certificate, message) => s"check the base URL, or your proxy: $message"
  case other if other.isRetryable                            => s"try again later: $other"
  case other                                                 => s"a defect to report: $other"
// end: explain

// snippet: invalid
@main def invalid(): Unit =
  // No request is sent: jev4s finds both problems first, and returns them together.
  val result = client.ask(
    doubleCharge,
    (
      feeling = Score("How does the customer feel?", "Calm"),
      risk = Score("How risky is the message?", "Low", "High", "Low"),
    ),
  )
  println(result.left.map(explain))
// end: invalid
