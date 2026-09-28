package guide

import io.github.maxtrezzi.jev4s._

// snippet: described
sealed abstract class Request(val description: String) extends Product with Serializable with Described
object Request {
  case object Refund      extends Request("The customer wants their money back")
  case object Exchange    extends Request("The customer wants a different size or colour")
  case object Information extends Request("The customer only asks a question")
  case object Other       extends Request("None of the options above")

  implicit val choices: JevChoice[Request] = JevChoice.named(Refund, Exchange, Information, Other)
}
// end: described

// snippet: by-hand
final case class Agent(name: String, languages: List[String])
object Agent {

  /** The agents on duty today: in a real program, from a database. */
  val all = List(Agent("Sofia", List("Spanish", "English")), Agent("Marco", List("Italian", "English")))

  implicit val choices: JevChoice[Agent] = JevChoice.fromOptions(
    all.map(a => ChoiceOption(a, a.name.toLowerCase, Some(s"Speaks ${a.languages.mkString(" and ")}")))
  )
}
// end: by-hand

// snippet: options
object Options {
  val request = Choice.of[Request]("What does the customer want in `message`?").as("request") // options from Request
  val agent   = Choice.of[Agent]("Who should answer `message`?").as("agent")                  // options from Agent.all

  def main(args: Array[String]): Unit =
    client.ask(Tickets.wrongSize, request, agent) match {
      case Right((r, a)) =>
        val chosen: Agent = a.choice // one of the values in Agent.all
        println(s"${r.choice}, answered by ${chosen.name}")
      case Left(error) => println(s"Jev did not answer: $error")
    }
}
// end: options
