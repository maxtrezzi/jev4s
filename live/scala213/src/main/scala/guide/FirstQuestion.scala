package guide

import io.github.maxtrezzi.jev4s._

// snippet: first
object FirstQuestion {
  val urgent = Noul("Is the message urgent?").as("urgent")

  def main(args: Array[String]): Unit =
    client.ask("Help! My payouts have been failing for 3 days.", urgent) match {
      case Right(u)    => println(s"Urgent: ${u.isYes}, with a probability of ${u.probability.value}")
      case Left(error) => println(s"Jev did not answer: $error")
    }
}
// end: first
