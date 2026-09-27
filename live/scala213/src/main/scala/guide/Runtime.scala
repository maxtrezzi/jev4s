package guide

import io.github.maxtrezzi.jev4s._

// snippet: runtime
object Runtime {

  /** Checks that come from a database or a file, not from the code. */
  val checks = Map(
    "legal" -> "Does `message` mention a lawyer or a court?",
    "press" -> "Does `message` mention a journalist or a social network?",
    "vip"   -> "Is `customer.plan` a paid plan?"
  )

  /** The items of the shop, also from data. */
  val items = List("hiking boots", "running shoes", "team subscription")

  def main(args: Array[String]): Unit = {
    val questions: Map[String, Question[_]] = checks.map { case (name, text) => name -> Noul(text) } +
      ("item" -> Choice.keys("Which item is `message` about?", items: _*))
    client.askMap(Tickets.doubleCharge, questions) match {
      case Right(answers) =>
        answers.toList.sortBy(_._1).foreach { case (name, answer) =>
          answer match {
            case a: NoulAnswer      => println(s"$name: ${a.isYes}")
            case a: ScoreAnswer[_]  => println(s"$name: ${a.score}")
            case a: ChoiceAnswer[_] => println(s"$name: ${a.choice}")
          }
        }
      case Left(error) => println(s"Jev did not answer: $error")
    }
  }
}
// end: runtime
