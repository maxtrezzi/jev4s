package guide

import io.github.maxtrezzi.jev4s._

object Decisions {

  // snippet: refund
  /** Three facts, each from a different part of the state, combined by your code. */
  val asked     = Noul("Does `message` ask for money back?").as("asked")
  val duplicate = Noul("Does `order.charges_usd` contain the same amount twice?").as("duplicate")
  val allowed   = Noul("Does `refund_policy` allow a refund at once for this ticket?").as("allowed")

  def refundAtOnce(ticket: Ticket): Either[JevError, Boolean] =
    client.ask(ticket, asked, duplicate, allowed).map { case (a, d, r) => a.isYes && d.isYes && r.isYes }
  // end: refund

  // snippet: noul
  def whenToAnswer(urgent: NoulAnswer): String =
    if (urgent.probability >= 0.9) "now"
    else if (urgent.probability >= 0.4) "a person decides"
    else "in the normal queue"
  // end: noul

  // snippet: confidence
  def route(team: ChoiceAnswer[Team]): String =
    team.ifConfident(0.8) match {
      case Some(team) => s"send to $team"
      case None       => "a person chooses the team"
    }
  // end: confidence

  // snippet: normalized
  /** The score on a scale from 0 to 1, whatever the number of levels. */
  def normalized(answer: ScoreAnswer[_]): Double = answer.score / (answer.probabilities.size - 1)
  // end: normalized

  // snippet: decisions
  val team     = Choice.of[Team]("Which team should handle `message`?").as("team")
  val urgent   = Noul("Does the customer need an answer today?").as("urgent")
  val severity =
    Score("How bad is the problem in `message`?", List("Cosmetic", "A workaround exists", "No workaround exists"))
      .as("severity")
  val feeling = Score.of[Feeling]("How does the customer feel in `message`?").as("feeling")

  def main(args: Array[String]): Unit = {
    println(s"Refund at once: ${refundAtOnce(Tickets.doubleCharge)}, ${refundAtOnce(Tickets.wrongSize)}")

    client.ask(Tickets.cannotLogIn, team, urgent, severity, feeling) match {
      case Right((t, u, s, f)) =>
        val priority = 0.7 * normalized(s) + 0.3 * normalized(f)
        println(f"${route(t)}, answer ${whenToAnswer(u)}, priority $priority%.2f")
      case Left(error) => println(s"Jev did not answer: $error")
    }
  }
  // end: decisions
}
