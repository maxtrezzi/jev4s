package guide

import io.github.maxtrezzi.jev4s._

object Errors {

  // snippet: explain
  def explain(error: JevError): String = error match {
    case JevError.InvalidRequest(problems) => problems.map(_.message).mkString("; ")
    case JevError.Unauthorized             => "check TYPESAFE_API_KEY"
    case JevError.Rejected(message)        => s"Jev refused the request: $message"
    case JevError.RateLimited(after)       => s"too many requests; wait ${after.fold("a little")(_.toString)}"
    case other if other.isRetryable        => s"try again later: $other"
    case other                             => s"a defect to report: $other"
  }
  // end: explain

  // snippet: invalid
  def main(args: Array[String]): Unit = {
    // No request is sent: jev4s finds both problems first, and returns them together.
    val feeling = Score("How does the customer feel?", List("Calm")).as("feeling")
    val risk    = Score("How risky is the message?", List("Low", "High", "Low")).as("risk")
    println(client.ask(Tickets.doubleCharge, feeling, risk).left.map(explain))
  }
  // end: invalid
}
