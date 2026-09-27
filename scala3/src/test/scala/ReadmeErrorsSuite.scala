import io.github.maxtrezzi.jev4s.*

// What the README's Scala 3 example defines (live/scala3/src/main/scala/Triage.scala), at the top
// level as in the README, so that the compiler names the types as a reader sees them.
final case class Ticket(message: String, plan: String, chargesUsd: List[Double])

given ToState[Ticket] = t => ujson.Obj("message" -> t.message, "plan" -> t.plan, "charges_usd" -> t.chargesUsd)

enum Team derives JevChoice:
  case Billing, Technical, Sales

enum Feeling derives JevScale:
  case Calm, Annoyed, Angry

/** The compile errors that the README shows against its Scala 3 example (ADR-0044). */
class ReadmeErrorsSuite extends DocumentedErrorsSuite("documents/README.md"):

  // Named by the code of the entries, which is compiled in this scope and never run.
  val client    = JevClient.withTransport("jev-1.13.0", _ => Left(JevError.Unauthorized))
  val ticket    = Ticket("You charged me twice for my order!", "pro", List(49.0, 49.0))
  val questions = (
    team = Choice[Team]("Which team should handle `message`?"),
    duplicate = Noul("Do `charges_usd` show the same amount charged twice?"),
    feeling = Score[Feeling]("How does the customer feel in `message`?"),
  )
  def r = client.ask(ticket, questions).toOption.get

  documented("r.priority", compileErrors("r.priority"))
  documented("(r.team.choice: Feeling)", compileErrors("(r.team.choice: Feeling)"))
  documented("""r.feeling.probabilities("Calm")""", compileErrors("""r.feeling.probabilities("Calm")"""))
  documented("Probability(1.5)", compileErrors("Probability(1.5)"))
  documented("enum Mood derives JevScale:\n  case Fine", compileErrors("enum Mood derives JevScale:\n  case Fine"))
  documented("client.ask(42, questions)", compileErrors("client.ask(42, questions)"))
  documented(
    "client.ask(ticket, (team = questions.team, count = 3))",
    compileErrors("client.ask(ticket, (team = questions.team, count = 3))"),
  )
