package guide

import io.github.maxtrezzi.jev4s._

// snippet: team
sealed abstract class Team extends Product with Serializable
object Team {
  case object Billing   extends Team
  case object Technical extends Team
  case object Sales     extends Team

  implicit val choices: JevChoice[Team] =
    JevChoice(ChoiceOption(Billing, "billing"), ChoiceOption(Technical, "technical"), ChoiceOption(Sales, "sales"))
}
// end: team

// snippet: questions
object Triage {
  val team    = Choice.of[Team]("Which team should handle `message`?").as("team") // the options come from Team
  val urgent  = Noul("Does the customer need an answer today?").as("urgent")
  val feeling = Score("How does the customer feel in `message`?", List("Calm", "Annoyed", "Angry")).as("feeling")
}
// end: questions

// snippet: ask
object ThreeQuestions {
  import Triage._

  def main(args: Array[String]): Unit =
    client.ask(Tickets.doubleCharge, team, urgent, feeling) match { // the Ticket of chapter 3, the keys above
      case Right(answers) =>
        val chosen: Option[Team]       = answers.get(team).map(_.choice)   // one of the cases of Team
        val isUrgent: Option[Boolean]  = answers.get(urgent).map(_.isYes)
        val score: Option[Double]      = answers.get(feeling).map(_.score) // from 0 (Calm) to 2 (Angry)
        val angry: Option[Probability] = answers.get(feeling).flatMap(_.probabilities.get("Angry"))
        println(s"$chosen, urgent: $isUrgent, feeling: $score, angry: $angry")
      case Left(error) => println(s"Jev did not answer: $error")
    }
}
// end: ask
