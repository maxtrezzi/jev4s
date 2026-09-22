// The second example of the README, which quotes it from here (build/check-docs.py).
// snippet: readme
import io.github.maxtrezzi.jev4s._

// (1) Your own data. It is the state of the request in (5): what Jev reads and judges.
final case class Ticket(message: String, plan: String, chargesUsd: List[Double])
object Ticket {
  // (2) How a Ticket becomes JSON. The questions in (4) point at its fields by name: `message`.
  implicit val toState: ToState[Ticket] =
    t => ujson.Obj("message" -> t.message, "plan" -> t.plan, "charges_usd" -> t.chargesUsd)
}

// (3) The possible answers of the Choice in (4). Jev picks one, and (6) gets a Team back.
sealed abstract class Team extends Product with Serializable
object Team {
  case object Billing   extends Team
  case object Technical extends Team
  case object Sales     extends Team

  implicit val choices: JevChoice[Team] =
    JevChoice(ChoiceOption(Billing, "billing"), ChoiceOption(Technical, "technical"), ChoiceOption(Sales, "sales"))
}

object Triage {
  // (4) Each question with its name: a key, used to ask in (5) and to read the answer in (6).
  val team      = Choice.of[Team]("Which team should handle `message`?").as("team")            // options from (3)
  val duplicate = Noul("Do `charges_usd` show the same amount charged twice?").as("duplicate") // a field named in (2)
  val feeling   = Score("How does the customer feel in `message`?", List("Calm", "Annoyed", "Angry")).as("feeling")

  def main(args: Array[String]): Unit = {
    val client = JevConfig.fromEnv("jev-1.13.0") match {
      case Right(config) => JevClient.create(config)
      case Left(problem) => sys.error(problem.message)
    }

    val ticket = // a value of (1)
      Ticket("You charged me twice for my order! I want my money back before Friday.", "pro", List(49.0, 49.0))

    // (5) One call: the state (1) and the keys of (4).
    client.ask(ticket, team, duplicate, feeling) match {
      case Right(answers) =>
        // (6) Each key of (4) reads its own answer, with the type of its question.
        for {
          t <- answers.get(team)      // a ChoiceAnswer[Team]: t.choice is a value of (3)
          d <- answers.get(duplicate) // a NoulAnswer
          f <- answers.get(feeling)   // a ScoreAnswer
        } {
          if (t.confidence >= 0.8)
            println(s"Send to ${t.choice}. Duplicate charge: ${d.isYes}. Feeling: ${f.score} of 2.")
          else println(s"Maybe ${t.choice}, but Jev is not sure: a person decides.")
        }
      case Left(error) => println(s"Jev did not answer: $error")
    }
  }
}
// end: readme
