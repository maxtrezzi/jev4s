package guide

import io.github.maxtrezzi.jev4s.*

// snippet: refund
/** Three facts, each from a different part of the state, combined by your code. */
val refundChecks = (
  asked = Noul("Does `message` ask for money back?"),
  duplicate = Noul("Does `order.charges_usd` contain the same amount twice?"),
  allowed = Noul("Does `refund_policy` allow a refund at once for this ticket?"),
)

def refundAtOnce(ticket: Ticket): Either[JevError, Boolean] =
  client.ask(ticket, refundChecks).map(r => r.asked.isYes && r.duplicate.isYes && r.allowed.isYes)
// end: refund

// snippet: noul
def whenToAnswer(urgent: NoulAnswer): String =
  if urgent.probability >= Probability(0.9) then "now"
  else if urgent.probability >= Probability(0.4) then "a person decides"
  else "in the normal queue"
// end: noul

// snippet: confidence
def route(team: ChoiceAnswer[Team]): String =
  team.ifConfident(Probability(0.8)) match
    case Some(team) => s"send to $team"
    case None       => "a person chooses the team"
// end: confidence

// snippet: noul-confidence
def checkCharge(duplicate: NoulAnswer): String =
  duplicate.ifConfident(Probability(0.8)) match
    case Some(true)  => "refund the second charge"
    case Some(false) => "explain the charges"
    case None        => "a person checks the order"
// end: noul-confidence

// snippet: most-likely
def reply(feeling: ScoreAnswer[Feeling]): String =
  feeling.mostLikely match // one case for each level, and no `case _`
    case Feeling.Calm    => "a short answer"
    case Feeling.Annoyed => "an apology, then the answer"
    case Feeling.Angry   => "a call from a person"
// end: most-likely

// snippet: decisions
@main def decisions(): Unit =
  println(s"Refund at once: ${refundAtOnce(doubleCharge)}, ${refundAtOnce(wrongSize)}")

  val questions = (
    team = Choice[Team]("Which team should handle `message`?"),
    urgent = Noul("Does the customer need an answer today?"),
    severity = Score("How bad is the problem in `message`?", "Cosmetic", "A workaround exists", "No workaround exists"),
    feeling = Score[Feeling]("How does the customer feel in `message`?"),
  )
  client.ask(cannotLogIn, questions) match
    case Right(r) =>
      val priority = 0.7 * r.severity.normalized + 0.3 * r.feeling.normalized
      println(f"${route(r.team)}, answer ${whenToAnswer(r.urgent)}, priority $priority%.2f, ${reply(r.feeling)}")
    case Left(error) => println(s"Jev did not answer: $error")
// end: decisions
