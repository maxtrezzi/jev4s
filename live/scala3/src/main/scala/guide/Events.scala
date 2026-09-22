package guide

import java.util.concurrent.atomic.LongAdder

import io.github.maxtrezzi.jev4s.*

// snippet: events
val inputTokens = LongAdder()
val log         = System.getLogger("jev")

def withEvents(config: JevConfig): JevClient =
  JevClient(
    config,
    onEvent = {
      case JevEvent.Replied(reply) =>
        inputTokens.add(reply.inputTokens)
        log.log(System.Logger.Level.DEBUG, s"answered by ${reply.model}")
      case JevEvent.Retrying(error, retry, delay) =>
        log.log(System.Logger.Level.WARNING, s"retry $retry in $delay after $error")
    },
  )
// end: events
