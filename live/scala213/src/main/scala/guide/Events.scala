package guide

import java.util.concurrent.atomic.LongAdder

import io.github.maxtrezzi.jev4s._

object Events {

  // snippet: events
  val inputTokens   = new LongAdder()
  val log           = System.getLogger("jev")
  val lastRequestId = ThreadLocal.withInitial[Option[String]](() => None)

  def withEvents(config: JevConfig): JevClient =
    JevClient.create(
      config,
      onEvent = {
        case JevEvent.Replied(reply) =>
          reply.inputTokens.foreach(inputTokens.add) // None when the reply does not report them
          log.log(System.Logger.Level.DEBUG, s"answered by ${reply.model}")
        case JevEvent.Retrying(error, retry, delay) =>
          log.log(System.Logger.Level.WARNING, s"retry $retry in $delay after $error")
        case JevEvent.Responded(status, requestId) =>
          lastRequestId.set(requestId) // the latest response on this thread
          log.log(System.Logger.Level.DEBUG, s"HTTP $status, request ${requestId.getOrElse("with no id")}")
      }
    )
  // end: events

  // snippet: request-id
  val urgent = Noul("Is the message urgent?").as("urgent")

  /** On an error, says which request failed: TypeSafe's support asks for its id. */
  def askOrReport(client: JevClient, message: String): Either[String, Boolean] = {
    lastRequestId.remove() // forget the id of an earlier call on this thread
    client.ask(message, urgent) match {
      case Right(u)    => Right(u.isYes)
      case Left(error) => Left(s"$error, request ${lastRequestId.get.getOrElse("with no response")}")
    }
  }
  // end: request-id
}
