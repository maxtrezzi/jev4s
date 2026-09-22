package guide

import io.github.maxtrezzi.jev4s._

// snippet: described
sealed abstract class Request extends Product with Serializable
object Request {
  case object Refund      extends Request
  case object Exchange    extends Request
  case object Information extends Request
  case object Other       extends Request

  implicit val choices: JevChoice[Request] = JevChoice(
    ChoiceOption(Refund, "refund", Some("The customer wants their money back")),
    ChoiceOption(Exchange, "exchange", Some("The customer wants a different size or colour")),
    ChoiceOption(Information, "information", Some("The customer only asks a question")),
    ChoiceOption(Other, "other", Some("None of the options above"))
  )
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
      case Right(answers) =>
        for (r <- answers.get(request); a <- answers.get(agent)) println(s"${r.choice}, answered by ${a.choice.name}")
      case Left(error) => println(s"Jev did not answer: $error")
    }
}
// end: options
