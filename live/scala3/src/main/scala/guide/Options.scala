package guide

import io.github.maxtrezzi.jev4s.*

// snippet: described
enum Request(val description: String) extends Described derives JevChoice, CanEqual:
  case Refund      extends Request("The customer wants their money back")
  case Exchange    extends Request("The customer wants a different size or colour")
  case Information extends Request("The customer only asks a question")
  case Other       extends Request("None of the options above")
// end: described

// snippet: by-hand
final case class Agent(name: String, languages: List[String]) derives CanEqual

/** The agents on duty today: in a real program, from a database. */
val agents = List(Agent("Sofia", List("Spanish", "English")), Agent("Marco", List("Italian", "English")))

given JevChoice[Agent] =
  JevChoice(agents.map(a => ChoiceOption(a, a.name.toLowerCase, Some(s"Speaks ${a.languages.mkString(" and ")}")))*)
// end: by-hand

// snippet: options
@main def options(): Unit =
  val questions = (
    request = Choice[Request]("What does the customer want in `message`?"), // options from Request
    agent = Choice[Agent]("Who should answer `message`?"),                  // options from agents
  )
  client.ask(wrongSize, questions) match
    case Right(r) =>
      val agent: Agent = r.agent.choice // one of the values in agents
      println(s"${r.request.choice}, answered by ${agent.name}")
    case Left(error) => println(s"Jev did not answer: $error")
// end: options
