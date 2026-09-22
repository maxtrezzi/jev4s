package guide

import io.github.maxtrezzi.jev4s._

// snippet: service
/** Code of your own that uses Jev. It takes the client as a parameter, so a test can pass another. */
final class Router(client: JevClient) {
  import Triage._

  def route(ticket: Ticket): String =
    client.ask(ticket, team, urgent, feeling) match {
      case Right(answers) =>
        val chosen = answers.get(team).map(_.choice.toString).getOrElse("a person")
        if (answers.get(urgent).exists(_.isYes)) s"$chosen, today" else chosen
      case Left(error) => s"a person, because Jev did not answer: $error"
    }
}
// end: service
