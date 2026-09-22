import io.github.maxtrezzi.jev4s.*

/** Calls the real API once and shows how to handle each error a caller meets in practice. The
  * answer is the program's output, so it is printed; what the client did goes to a logger, here
  * the JDK's `System.Logger`, as it would in an application.
  * Run with TYPESAFE_API_KEY set: `sbt scala3Live/run`. It costs a few hundred input tokens.
  */
enum Dept derives JevChoice, CanEqual:
  case Billing, Technical, Sales

@main def example(): Unit =
  JevConfig.fromEnv("jev-1.13.0") match
    case Left(error)   => println(s"cannot start: ${error.message}")
    case Right(config) =>
      val log    = System.getLogger("jev4s.example")
      val client = JevClient(
        config,
        onEvent = {
          case JevEvent.Replied(reply) =>
            log.log(System.Logger.Level.INFO, s"answered by ${reply.model}, ${reply.inputTokens} input tokens")
          case JevEvent.Retrying(error, n, delay) =>
            log.log(System.Logger.Level.WARNING, s"retry $n in $delay after $error")
        },
      )
      val ticket = "Help! My payouts have been failing for 3 days and I have a launch tomorrow."
      client.ask(
        ticket,
        (dept = Choice[Dept]("Which team should handle this?"), urgent = Noul("Does this convey urgency?")),
      ) match
        case Right(r)                          => println(s"route to ${r.dept.choice}, urgent: ${r.urgent.isYes}")
        case Left(JevError.Unauthorized)       => println("401: the API key is wrong: check TYPESAFE_API_KEY")
        case Left(JevError.Rejected(message))  => println(s"422: Jev refused the request: $message")
        case Left(JevError.RateLimited(after)) =>
          println(s"429: too many requests, even after retries; wait ${after.fold("a little")(_.toString)}")
        case Left(other) => println(s"failed: $other")
