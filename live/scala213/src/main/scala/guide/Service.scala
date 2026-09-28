package guide

import io.github.maxtrezzi.jev4s._

// snippet: service
/** Code of your own that uses Jev. It takes the client as a parameter, so a test can pass another. */
final class Router(client: JevClient) {
  import Triage._

  def route(ticket: Ticket): String =
    client.ask(ticket, team, urgent, feeling) match {
      case Right((t, u, _)) =>
        t.ifConfident(0.8) match {
          case Some(chosen) if u.isYes => s"$chosen, today"
          case Some(chosen)            => s"$chosen"
          case None                    => "a person, because Jev is not sure of the team"
        }
      case Left(error) => s"a person, because Jev did not answer: $error"
    }
}
// end: service
