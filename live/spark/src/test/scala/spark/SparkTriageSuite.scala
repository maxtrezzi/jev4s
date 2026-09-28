package spark

import java.net.{InetSocketAddress, URI}
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import scala.jdk.CollectionConverters._

import com.sun.net.httpserver.HttpServer
import org.apache.spark.sql.SparkSession

import io.github.maxtrezzi.jev4s._
import io.github.maxtrezzi.jev4s.testkit._

/** The example of the guide on Spark in local mode, two executor threads, with a fake transport
  * or a local server. No API key, no cost.
  */
class SparkTriageSuite extends munit.FunSuite {
  import SparkTriageSuite.reply

  private val spark = FunFixture[SparkSession](
    _ => SparkSession.builder().master("local[2]").appName("test").config("spark.ui.enabled", "false").getOrCreate(),
    _.stop()
  )

  spark.test("each ticket gets its answer, and an error stays in its own row") { spark =>
    import spark.implicits._
    val tickets = Seq(Ticket("t1", "locked out"), Ticket("t2", "invoice colour"), Ticket("t3", "bad key")).toDS()
    val client  = () =>
      JevClient.withTransport(
        "jev-1.13.0",
        body =>
          if (body.contains("locked out")) Right(reply(0.9))
          else if (body.contains("bad key")) Left(JevError.Unauthorized)
          else Right(reply(0.2))
      )
    assertEquals(
      SparkTriage.triage(tickets, partitions = 2, perSecond = 100, client).collect().sortBy(_.id).toList,
      List(
        Triaged("t1", Some(true), Some(0.9), None),
        Triaged("t2", Some(false), Some(0.2), None),
        Triaged("t3", None, None, Some("Unauthorized"))
      )
    )
  }

  spark.test("a client of the test kit, built on the executors, answers each row") { spark =>
    import spark.implicits._
    val tickets = Seq(Ticket("t1", "locked out"), Ticket("t2", "invoice colour")).toDS()
    val client  = () => JevTestkit.answering(SparkTriage.urgent.is(true))
    assertEquals(
      SparkTriage.triage(tickets, partitions = 2, perSecond = 100, client).collect().sortBy(_.id).toList,
      List(Triaged("t1", Some(true), Some(1.0), None), Triaged("t2", Some(true), Some(1.0), None))
    )
  }

  spark.test("each action calls Jev again, unless the answers are cached") { spark =>
    import spark.implicits._
    val tickets = Seq.tabulate(10)(i => Ticket(s"t$i", s"message $i")).toDS()
    val calls   = SparkTriageSuite.calls
    // The transport names the object: a captured `calls` would reach the tasks as a copy.
    val client =
      () => JevClient.withTransport("jev-1.13.0", _ => { SparkTriageSuite.calls.incrementAndGet(); Right(reply(0.9)) })
    def callsFor(action: => Any): Int = { calls.set(0); action; calls.get }

    val answers = SparkTriage.triage(tickets, partitions = 2, perSecond = 1000, client)
    assertEquals(callsFor(answers.collect()), 10)
    assertEquals(callsFor(answers.count()), 10)
    assertEquals(callsFor(answers.orderBy("id").collect()), 20) // a sort samples its input first

    val cached = answers.cache()
    assertEquals(callsFor(cached.count()), 10)
    assertEquals(callsFor { cached.orderBy("id").collect(); cached.count() }, 0)
  }

  /** A local server that answers 429 above `perSecond` requests in any second, and its URL. */
  private def limitedTo(perSecond: Int)(body: URI => Unit): Unit = {
    val server  = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    val started = collection.mutable.Queue.empty[Long]
    server.createContext(
      "/",
      exchange => {
        val allowed = started.synchronized {
          val now = System.nanoTime()
          while (started.nonEmpty && started.head <= now - 1000000000L) started.dequeue()
          val free = started.size < perSecond
          if (free) started.enqueue(now)
          free
        }
        val (status, answer) = if (allowed) (200, reply(0.9)) else (429, """{"detail": {"message": "slow down"}}""")
        val bytes            = answer.getBytes("UTF-8")
        exchange.sendResponseHeaders(status, bytes.length.toLong)
        exchange.getResponseBody.write(bytes)
        exchange.close()
      }
    )
    val pool = Executors.newFixedThreadPool(8)
    server.setExecutor(pool)
    server.start()
    try body(URI.create(s"http://localhost:${server.getAddress.getPort}"))
    finally {
      server.stop(0)
      pool.shutdown()
    }
  }

  private def local(url: URI): () => JevClient =
    () => JevClient.create(JevConfig(new ApiKey("test"), "jev-1.13.0", url, retry = RetryPolicy.none))

  private def tickets(spark: SparkSession) = {
    import spark.implicits._
    Seq.tabulate(24)(i => Ticket(s"t$i", s"message $i")).toDS()
  }

  spark.test("without a pacer, two partitions go over a limit of 10 per second") { spark =>
    limitedTo(10) { url =>
      val errors =
        SparkTriage.triage(tickets(spark), partitions = 2, perSecond = 1e6, local(url)).collect().flatMap(_.error)
      assert(errors.exists(_.startsWith("RateLimited")), errors.toList)
    }
  }

  /** The selector threads of the JDK's HTTP clients: one for each `HttpClient` that is alive. */
  private def httpClients(): Set[String] =
    Thread.getAllStackTraces.keySet.asScala.map(_.getName).filter(_.matches("HttpClient-\\d+-SelectorManager")).toSet

  spark.test("one client for each executor JVM: one HTTP client, however many tasks run") { spark =>
    limitedTo(1000) { url =>
      SparkTriageSuite.url = url
      val before  = httpClients()
      val results = SparkTriage.triage(tickets(spark), partitions = 12, perSecond = 1e6, () => SparkTriageSuite.client)
      assertEquals(results.collect().flatMap(_.error).toList, Nil)
      assertEquals((httpClients() -- before).size, 1)
    }
  }

  spark.test("two partitions share a rate of 8 per second, and stay under a limit of 10") { spark =>
    limitedTo(10) { url =>
      val start   = System.nanoTime()
      val results = SparkTriage.triage(tickets(spark), partitions = 2, perSecond = 8, local(url)).collect()
      val seconds = (System.nanoTime() - start) / 1e9
      assertEquals(results.flatMap(_.error).toList, Nil)
      assertEquals(results.length, 24)
      assert(seconds >= 11 / 4.0, s"took $seconds s: each partition should start at most 4 calls per second")
    }
  }
}

/** Outside the suite: a function that Spark sends to the executors must not capture the suite. */
object SparkTriageSuite {
  val calls = new AtomicInteger()

  /** The local server of the test that uses [[client]]; set before the client is built. */
  @volatile var url: URI = _

  /** One client for each JVM, as in the example: an `object` exists once in each JVM. */
  lazy val client: JevClient =
    JevClient.create(JevConfig(new ApiKey("test"), "jev-1.13.0", url, retry = RetryPolicy.none))

  def reply(noul: Double): String =
    s"""{"model": "jev-1.13.0", "answers": {"urgent": {"type": "noul", "noul": $noul}}}"""
}
