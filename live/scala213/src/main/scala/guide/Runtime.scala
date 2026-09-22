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
    val keys = checks.toList.sorted.map { case (name, text) => Noul(text).as(name) }
    val item = Choice("Which item is `message` about?", items.map(i => ChoiceOption(i, i))).as("item")
    client.ask(Tickets.doubleCharge, (item :: keys): _*) match {
      case Right(answers) =>
        keys.foreach(key => println(s"${key.name}: ${answers.get(key).map(_.isYes)}"))
        println(s"item: ${answers.get(item).map(_.choice)}")
      case Left(error) => println(s"Jev did not answer: $error")
    }
  }
}
// end: runtime
