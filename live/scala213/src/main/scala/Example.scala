import io.github.maxtrezzi.jev4s._

/** Calls the real API once and shows how to handle each error a caller meets in practice. The
  * answer is the program's output, so it is printed; what the client did goes to a logger, here
  * the JDK's `System.Logger`, as it would in an application.
  * Run with TYPESAFE_API_KEY set: `sbt scala213Live/run`. It costs a few hundred input tokens.
  */
object Example {

  sealed abstract class Dept extends Product with Serializable
  object Dept {
    case object Billing   extends Dept
    case object Technical extends Dept
    case object Sales     extends Dept
    implicit val choices: JevChoice[Dept] =
      JevChoice.fromOptions(
        List(ChoiceOption(Billing, "billing"), ChoiceOption(Technical, "technical"), ChoiceOption(Sales, "sales"))
      )
  }

  def main(args: Array[String]): Unit =
    JevConfig.fromEnv("jev-1.13.0") match {
      case Left(error)   => println(s"cannot start: ${error.message}")
      case Right(config) =>
        val log    = System.getLogger("jev4s.example")
        val client = JevClient.create(
          config,
          onEvent = {
            case JevEvent.Replied(reply) =>
              log.log(
                System.Logger.Level.INFO,
                s"answered by ${reply.model}, ${reply.inputTokens.getOrElse("unknown")} input tokens"
              )
            case JevEvent.Retrying(error, n, delay) =>
              log.log(System.Logger.Level.WARNING, s"retry $n in $delay after $error")
          }
        )
        val ticket = "Help! My payouts have been failing for 3 days and I have a launch tomorrow."
        val dept   = Choice.of[Dept]("Which team should handle this?").as("dept")
        val urgent = Noul("Does this convey urgency?").as("urgent")
        client.ask(ticket, dept, urgent) match {
          case Right((d, u))                     => println(s"route to ${d.choice}, urgent: ${u.isYes}")
          case Left(JevError.Unauthorized)       => println("401: the API key is wrong: check TYPESAFE_API_KEY")
          case Left(JevError.Rejected(message))  => println(s"422: Jev refused the request: $message")
          case Left(JevError.RateLimited(after)) =>
            println(s"429: too many requests, even after retries; wait ${after.fold("a little")(_.toString)}")
          case Left(other) => println(s"failed: $other")
        }
    }
}
