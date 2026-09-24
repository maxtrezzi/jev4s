package guide

import java.util.concurrent.atomic.LongAdder

import io.github.maxtrezzi.jev4s._

object Events {

  // snippet: events
  val inputTokens = new LongAdder()
  val log         = System.getLogger("jev")

  def withEvents(config: JevConfig): JevClient =
    JevClient.create(
      config,
      onEvent = {
        case JevEvent.Replied(reply) =>
          reply.inputTokens.foreach(inputTokens.add) // None when the reply does not report them
          log.log(System.Logger.Level.DEBUG, s"answered by ${reply.model}")
        case JevEvent.Retrying(error, retry, delay) =>
          log.log(System.Logger.Level.WARNING, s"retry $retry in $delay after $error")
      }
    )
  // end: events
}
