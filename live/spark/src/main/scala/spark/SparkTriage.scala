// The example of docs/guide/spark.md, which quotes it from here (build/check-docs.py).
// It makes one paid call per ticket: sbt "scala213Spark/runMain spark.SparkTriage".
package spark

import org.apache.spark.sql.{Dataset, SparkSession}

import guide.Pacer
import io.github.maxtrezzi.jev4s._

// snippet: rows
final case class Ticket(id: String, message: String)

/** The answer for one ticket, or why there is none: an error does not stop the job. */
final case class Triaged(id: String, urgent: Option[Boolean], probability: Option[Double], error: Option[String])
// end: rows

object SparkTriage {

  // snippet: triage
  val urgent = Noul("Is the message urgent?").as("urgent")

  /** Asks Jev if each ticket is urgent, in `partitions` partitions at once, with at most
    * `perSecond` calls per second in total. `client` runs on the executors: a client cannot
    * travel from the driver, and the API key should not.
    */
  def triage(
      tickets: Dataset[Ticket],
      partitions: Int,
      perSecond: Double,
      client: () => JevClient
  ): Dataset[Triaged] = {
    import tickets.sparkSession.implicits._
    // snippet: readme
    tickets.repartition(partitions).mapPartitions { rows =>
      val jev   = client()                          // the executor's client
      val pacer = new Pacer(perSecond / partitions) // the partitions share the account's rate
      rows.map { ticket =>
        pacer.pace(jev.ask(ticket.message, urgent)) match {
          case Right(u)    => Triaged(ticket.id, Some(u.isYes), Some(u.probability.value), None)
          case Left(error) => Triaged(ticket.id, None, None, Some(error.toString))
        }
      }
    }
    // end: readme
  }
  // end: triage

  // snippet: main
  /** One client for each executor JVM, built by the first task that needs it, and shared by all
    * the tasks after it. It reads the API key from TYPESAFE_API_KEY on the executor.
    */
  lazy val client: JevClient =
    JevConfig.fromEnv("jev-1.13.0") match {
      case Right(config) => JevClient.create(config)
      case Left(problem) => sys.error(problem.message)
    }

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().master("local[2]").appName("triage").getOrCreate()
    import spark.implicits._
    try {
      val tickets = Seq(
        Ticket("t1", "Help! My payouts have been failing for 3 days."),
        Ticket("t2", "Can I change the colour of my invoices?"),
        Ticket("t3", "Our whole team is locked out, and the demo starts in 10 minutes.")
      ).toDS()
      // One action: each action on `triage` would call Jev again, for every ticket.
      triage(tickets, partitions = 2, perSecond = 10, () => client).collect().sortBy(_.id).foreach(println)
    } finally spark.stop()
  }
  // end: main
}
