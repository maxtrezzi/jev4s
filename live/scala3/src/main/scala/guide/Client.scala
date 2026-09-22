// The examples of docs/guide/scala3.md, which quotes them from here (build/check-docs.py).
// Each @main makes one paid call: sbt "scala3Live/runMain guide.firstQuestion".
package guide

import io.github.maxtrezzi.jev4s.*

// snippet: client
/** One client for the whole program. It is safe to share between threads. */
lazy val client: JevClient =
  JevConfig.fromEnv("jev-1.13.0") match
    case Right(config) => JevClient(config)
    case Left(problem) => sys.error(problem.message)
// end: client

// snippet: first
@main def firstQuestion(): Unit =
  val result = client.ask("Help! My payouts have been failing for 3 days.", (urgent = Noul("Is the message urgent?")))
  result match
    case Right(r)    => println(s"Urgent: ${r.urgent.isYes}, with a probability of ${r.urgent.probability.value}")
    case Left(error) => println(s"Jev did not answer: $error")
// end: first
