// The Scala 2.13 example of the README, which quotes two parts of it (build/check-docs.py).
import io.github.maxtrezzi.jev4s._

// (1) Your own data. It is the state of the request in (5): what Jev reads and judges.
final case class Ticket(message: String, plan: String, chargesUsd: List[Double])
object Ticket {
  // (2) How a Ticket becomes JSON. The questions in (4) point at its fields by name: `message`.
  implicit val toState: ToState[Ticket] =
    t => ujson.Obj("message" -> t.message, "plan" -> t.plan, "charges_usd" -> t.chargesUsd)
}

// snippet: readme-types
// (3) The possible answers of a Choice, and the levels of a Score, each case listed once.
sealed abstract class Team extends Product with Serializable
object Team {
  case object Billing   extends Team
  case object Technical extends Team
  case object Sales     extends Team

  implicit val choices: JevChoice[Team] = JevChoice.named(Billing, Technical, Sales)
}

sealed abstract class Feeling extends Product with Serializable
object Feeling {
  case object Calm    extends Feeling
  case object Annoyed extends Feeling
  case object Angry   extends Feeling

  implicit val levels: JevScale[Feeling] = JevScale.named(Calm, Annoyed, Angry) // from low to high
}
// end: readme-types

object Triage {

  def main(args: Array[String]): Unit = {
    val client = JevConfig.fromEnv("jev-1.13.0") match {
      case Right(config) => JevClient.create(config)
      case Left(problem) => sys.error(problem.message)
    }

    val ticket = // a value of (1)
      Ticket("You charged me twice for my order! I want my money back before Friday.", "pro", List(49.0, 49.0))

    // snippet: readme-ask
    // (4) Each question with its name: a key.
    val team      = Choice.of[Team]("Which team should handle `message`?").as("team")            // options from (3)
    val duplicate = Noul("Do `charges_usd` show the same amount charged twice?").as("duplicate") // a field of (2)
    val feeling   = Score.of[Feeling]("How does the customer feel in `message`?").as("feeling")  // levels from (3)

    // (5) One call. The answers come back as a tuple, in the order of the keys.
    client.ask(ticket, team, duplicate, feeling) match {
      case Right((t, d, f)) => // a ChoiceAnswer[Team], a NoulAnswer, a ScoreAnswer[Feeling]
        if (t.confidence >= 0.8)
          println(s"Send to ${t.choice}. Duplicate charge: ${d.isYes}. Feeling: ${f.mostLikely} (${f.score} of 2).")
        else println(s"Maybe ${t.choice}, but Jev is not sure: a person decides.")
      case Left(error) => println(s"Jev did not answer: $error")
    }
    // end: readme-ask
  }
}
