package guide

import io.github.maxtrezzi.jev4s.*

// snippet: runtime
/** Checks that come from a database or a file, not from the code. */
val checks = Map(
  "legal" -> "Does `message` mention a lawyer or a court?",
  "press" -> "Does `message` mention a journalist or a social network?",
  "vip"   -> "Is `customer.plan` a paid plan?",
)

/** The items of the shop, also from data. */
val items = List("hiking boots", "running shoes", "team subscription")

@main def runtime(): Unit =
  val questions = checks.map((name, text) => name -> Noul(text)) +
    ("item" -> Choice[String]("Which item is `message` about?")(using JevChoice.keys(items*)))
  client.askMap(doubleCharge, questions) match
    case Right(answers) =>
      answers.toList
        .sortBy(_._1)
        .foreach: (name, answer) =>
          answer match
            case a: NoulAnswer      => println(s"$name: ${a.isYes}")
            case a: ScoreAnswer     => println(s"$name: ${a.score}")
            case a: ChoiceAnswer[?] => println(s"$name: ${a.choice}")
    case Left(error) => println(s"Jev did not answer: $error")
// end: runtime
