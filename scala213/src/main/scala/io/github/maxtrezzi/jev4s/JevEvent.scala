package io.github.maxtrezzi.jev4s

import scala.concurrent.duration.FiniteDuration

/** Something the client did that a caller may want to log, count or trace. The client never logs
  * on its own: it hands each event to the `onEvent` it was built with.
  *
  * Match on it exhaustively, with no `case _`: if a later version adds a kind of event, the
  * compiler then warns at each match that does not handle it.
  */
sealed abstract class JevEvent extends Product with Serializable

object JevEvent {

  /** A successful reply, after its answers were read: the model that answered and the input
    * tokens billed. Sent once per call, not once per attempt.
    */
  final case class Replied(reply: Reply) extends JevEvent

  /** A retryable error, just before the wait for retry number `retry` (1 for the first). */
  final case class Retrying(error: JevError, retry: Int, delay: FiniteDuration) extends JevEvent
}
