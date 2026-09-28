package guide

import io.github.maxtrezzi.jev4s._

object Structure {

  // snippet: structure
  /** An incident that your code already knows about, sent next to the question. */
  val incident =
    ujson.Obj("id" -> "INC-12", "system" -> "login", "since" -> "this morning", "affects" -> "business plans")

  val knownIncident = Noul(
    ujson.Obj(
      "incident" -> incident,
      "question" -> "Does `message` report the problem described in `incident`?"
    ),
    whenTrue = Some("The same system fails in the same way, even if the words are different"),
    whenFalse = Some("Another system, or no problem at all")
  ).as("knownIncident")

  val outOfService =
    ujson.Obj("level" -> "Out of service", "examples" -> ujson.Arr("Nobody can log in", "Every payment fails"))

  val impact = Score(
    ujson.Obj("question" -> "How much does the problem in `message` stop the customer's work?"),
    List(
      ujson.Obj("level" -> "No impact", "examples"   -> ujson.Arr("A question", "A typo on a page")),
      ujson.Obj("level" -> "Slower work", "examples" -> ujson.Arr("A report is late", "A workaround exists")),
      outOfService
    )
  ).as("impact")
  // end: structure

  // snippet: structure-ask
  def main(args: Array[String]): Unit =
    client.ask(Tickets.cannotLogIn, knownIncident, impact) match {
      case Right((k, i)) =>
        println(s"Part of ${incident("id").str}: ${k.isYes}")
        println(s"Impact: ${i.score} of 2, out of service: ${i.probabilities.get(outOfService)}")
      case Left(error) => println(s"Jev did not answer: $error")
    }
  // end: structure-ask
}
