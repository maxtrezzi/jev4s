package guide

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

import io.github.maxtrezzi.jev4s._

// snippet: pacer
/** Starts at most `perSecond` calls in each second, evenly spaced: each caller waits for its slot. */
final class Pacer(perSecond: Double) {
  private val gap  = (1e9 / perSecond).toLong // nanoseconds between two calls
  private val next = new AtomicLong(System.nanoTime())

  def pace[A](call: => A): A = {
    val now  = System.nanoTime()
    val slot = math.max(next.getAndAccumulate(now, (last, t) => math.max(last, t) + gap), now)
    TimeUnit.NANOSECONDS.sleep(slot - now)
    call
  }
}
// end: pacer

object Many {

  // snippet: many
  val urgent = Noul("Is the message urgent?").as("urgent")

  /** Asks if each message is urgent: `threads` calls at a time, and at most `perSecond` per second. */
  def urgentAll(
      client: JevClient,
      messages: List[String],
      perSecond: Double,
      threads: Int = 8
  ): List[(String, Either[JevError, Boolean])] = {
    val pool                          = Executors.newFixedThreadPool(threads)
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(pool)
    val pacer                         = new Pacer(perSecond)
    try {
      val answers = Future.traverse(messages) { message =>
        Future(message -> pacer.pace(client.ask(message, urgent)).map(_.isYes))
      }
      Await.result(answers, Duration.Inf)
    } finally pool.shutdown()
  }
  // end: many

  // snippet: rate-limited
  def main(args: Array[String]): Unit = {
    val messages = List(
      "Help! My payouts have been failing for 3 days.",
      "Can I change the colour of my invoices?",
      "Our whole team is locked out, and the demo starts in 10 minutes."
    )
    val (limited, answered) = urgentAll(client, messages, perSecond = 15).partition {
      case (_, Left(JevError.RateLimited(_))) => true
      case _                                  => false
    }
    answered.foreach { case (message, isUrgent) => println(s"$message -> $isUrgent") }
    if (limited.nonEmpty) println(s"Send ${limited.size} messages again later, at a lower rate")
  }
  // end: rate-limited
}
