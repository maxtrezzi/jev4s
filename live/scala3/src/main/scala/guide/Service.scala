package guide

import io.github.maxtrezzi.jev4s.*

// snippet: service
/** Code of your own that uses Jev. It takes the client as a parameter, so a test can pass another. */
final class Router(client: JevClient):
  def route(ticket: Ticket): String =
    client.ask(ticket, triage) match
      case Right(r) =>
        r.team.ifConfident(Probability(0.8)) match
          case Some(team) if r.urgent.isYes => s"$team, today"
          case Some(team)                   => s"$team"
          case None                         => "a person, because Jev is not sure of the team"
      case Left(error) => s"a person, because Jev did not answer: $error"
// end: service
