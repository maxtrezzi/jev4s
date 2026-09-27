import io.github.maxtrezzi.jev4s.*

/** The compile errors that the Scala 3 tutorial, `docs/guide/scala3.md`, shows (ADR-0044). Its
  * `Team` and `Feeling` are those of [[ReadmeErrorsSuite]], and its tickets are text: the entries
  * need a state, not the tutorial's `Ticket`.
  */
class Scala3GuideErrorsSuite extends DocumentedErrorsSuite("documents/scala3.md"):

  // Named by the code of the entries, which is compiled in this scope and never run.
  val client       = JevClient.withTransport("jev-1.13.0", _ => Left(JevError.Unauthorized))
  val doubleCharge = "You charged me twice for my boots! I want the second payment back before Friday."
  val triage       = (
    team = Choice[Team]("Which team should handle `message`?"),
    urgent = Noul("Does the customer need an answer today?"),
    feeling = Score[Feeling]("How does the customer feel in `message`?"),
  )
  def r = client.ask(doubleCharge, triage).toOption.get

  // Chapter 3
  documented(
    """client.ask(42, (urgent = Noul("Is the message urgent?")))""",
    compileErrors("""client.ask(42, (urgent = Noul("Is the message urgent?")))"""),
  )

  // Chapter 4
  documented("r.urgnet", compileErrors("r.urgnet"))
  documented(
    "val feeling: ScoreAnswer[Feeling] = r.urgent",
    compileErrors("val feeling: ScoreAnswer[Feeling] = r.urgent"),
  )
  documented("""r.feeling.probabilities("Angry")""", compileErrors("""r.feeling.probabilities("Angry")"""))
  documented(
    """client.ask(doubleCharge, (urgent = Noul("Urgent?"), count = 3))""",
    compileErrors("""client.ask(doubleCharge, (urgent = Noul("Urgent?"), count = 3))"""),
  )
  documented(
    """client.ask(doubleCharge, (Noul("Urgent?"), Noul("Angry?")))""",
    compileErrors("""client.ask(doubleCharge, (Noul("Urgent?"), Noul("Angry?")))"""),
  )
  documented(
    """Choice[java.time.DayOfWeek]("Which day?")""",
    compileErrors("""Choice[java.time.DayOfWeek]("Which day?")"""),
  )
  documented("""Score("How does the customer feel?")""", compileErrors("""Score("How does the customer feel?")"""))
  documented("enum Mood derives JevScale:\n  case Fine", compileErrors("enum Mood derives JevScale:\n  case Fine"))
  documented(
    "enum Mood derives JevScale:\n  case A, B, C, D, E, F, G, H, I, J, K",
    compileErrors("enum Mood derives JevScale:\n  case A, B, C, D, E, F, G, H, I, J, K"),
  )

  // Chapter 6
  documented("Probability(1.5)", compileErrors("Probability(1.5)"))
  documented("Probability(1)", compileErrors("Probability(1)"))
