package guide

import io.github.maxtrezzi.jev4s.*

// snippet: service
/** Code of your own that uses Jev. It takes the client as a parameter, so a test can pass another. */
final class Router(client: JevClient):
  def route(ticket: Ticket): String =
    client.ask(ticket, triage) match
      case Right(r) if r.urgent.isYes => s"${r.team.choice}, today"
      case Right(r)                   => s"${r.team.choice}"
      case Left(error)                => s"a person, because Jev did not answer: $error"
// end: service
