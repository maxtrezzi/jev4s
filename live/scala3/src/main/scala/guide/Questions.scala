package guide

import io.github.maxtrezzi.jev4s.*

// snippet: team
enum Team derives JevChoice, CanEqual:
  case Billing, Technical, Sales
// end: team

// snippet: questions
val triage = (
  team = Choice[Team]("Which team should handle `message`?"), // the options come from Team
  urgent = Noul("Does the customer need an answer today?"),
  feeling = Score("How does the customer feel in `message`?", "Calm", "Annoyed", "Angry"),
)
// end: questions

// snippet: ask
@main def threeQuestions(): Unit =
  client.ask(doubleCharge, triage) match // the Ticket of chapter 3, and the questions above
    case Right(r) =>
      val team: Team         = r.team.choice   // one of the cases of Team
      val urgent: Boolean    = r.urgent.isYes
      val feeling: Double    = r.feeling.score // from 0 (Calm) to 2 (Angry)
      val angry: Probability = r.feeling.probabilities("Angry")
      println(s"$team, urgent: $urgent, feeling: $feeling, angry: ${angry.value}")
    case Left(error) => println(s"Jev did not answer: $error")
// end: ask
