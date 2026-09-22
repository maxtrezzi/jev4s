// The first example of the README, which quotes it from here (build/check-docs.py).
// snippet: readme
import io.github.maxtrezzi.jev4s.*

// (1) Your own data. It is the state of the request in (4): what Jev reads and judges.
final case class Ticket(message: String, plan: String, chargesUsd: List[Double])

// (2) How a Ticket becomes JSON. The questions in (4) point at its fields by name: `message`.
given ToState[Ticket] = t => ujson.Obj("message" -> t.message, "plan" -> t.plan, "charges_usd" -> t.chargesUsd)

// (3) The possible answers of the Choice in (4). Jev picks one, and (5) gets a Team back.
enum Team derives JevChoice:
  case Billing, Technical, Sales

@main def triage(): Unit =
  val client = JevConfig.fromEnv("jev-1.13.0") match
    case Right(config) => JevClient(config)
    case Left(problem) => sys.error(problem.message)

  val ticket = // a value of (1)
    Ticket("You charged me twice for my order! I want my money back before Friday.", "pro", List(49.0, 49.0))

  // (4) One call: the state (1) and three questions, each with a name of your choice.
  val answers = client.ask(
    ticket,
    (
      team = Choice[Team]("Which team should handle `message`?"),               // options from (3)
      duplicate = Noul("Do `charges_usd` show the same amount charged twice?"), // a field named in (2)
      feeling = Score("How does the customer feel in `message`?", "Calm", "Annoyed", "Angry"),
    ),
  )

  // (5) The answers have the names of (4), and each one the type of its question.
  answers match
    case Right(r) if r.team.confidence >= Probability(0.8) =>
      val team: Team = r.team.choice // a value of (3)
      println(s"Send to $team. Duplicate charge: ${r.duplicate.isYes}. Feeling: ${r.feeling.score} of 2.")
    case Right(r)    => println(s"Maybe ${r.team.choice}, but Jev is not sure: a person decides.")
    case Left(error) => println(s"Jev did not answer: $error")
// end: readme
