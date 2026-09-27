# jev4s with Apache Spark

This guide shows how to ask Jev a question about each row of a Spark `Dataset`. It uses the
Scala 2.13 module, because Spark 4 is published for Scala 2.13 only. Read the
[Scala 2.13 tutorial](scala213.md) first: this guide uses its questions, its errors and its
pacer from [chapter 13](scala213.md#13-many-requests).

Every piece of code here is compiled with the project. The program is in
[`live/spark/src/main/scala/spark`](../../live/spark/src/main/scala/spark), and you can run it
with `sbt "scala213Spark/runMain spark.SparkTriage"`. It starts Spark on your machine and makes
one call to Jev for each of its three tickets.

Three things make jev4s fit Spark:

- **It is built with the Scala of Spark 4.** The 2.13 module needs no newer Scala library than a
  Spark 4.0 cluster has, so it can run on Spark 4.0, 4.1 and 4.2. The example is tested on Spark
  4.0.4 ([chapter 1](#1-set-up-a-project)).
- **The client is built on the executors, once for each JVM.** Nothing about it travels with
  your functions, the API key included ([chapter 2](#2-one-question-for-each-row)).
- **Errors are values.** A call that fails gives a row with its error, and the job goes on with
  the answers it already paid for.

## Contents

1. [Set up a project](#1-set-up-a-project)
2. [One question for each row](#2-one-question-for-each-row)
3. [Each action calls Jev again](#3-each-action-calls-jev-again)
4. [The API key on the executors](#4-the-api-key-on-the-executors)
5. [Testing without Jev](#5-testing-without-jev)

## 1. Set up a project

jev4s is built with **Scala 2.13.16**, the Scala of Spark 4.0, so it can run on Spark 4.0, 4.1
and 4.2. The example uses Spark 4.0.4, the only version it is tested on:

```scala
// build.sbt
scalaVersion := "2.13.16"
libraryDependencies ++= Seq(
  "io.github.maxtrezzi" %% "jev4s"     % "0.1.0-SNAPSHOT",
  "org.apache.spark"    %% "spark-sql" % "4.0.4" % Provided // the cluster brings its own Spark
)
```

jev4s is not on Maven Central yet: clone this repository and run `sbt publishLocal` in it.

Run Spark on **JDK 17 or 21**. Spark 4.0 does not start on JDK 25, although jev4s works on it.

## 2. One question for each row

The input is a `Dataset` of tickets, and the output has one row for each ticket, with the
answer or the error:

<!-- snippet: live/spark/src/main/scala/spark/SparkTriage.scala#rows -->
```scala
final case class Ticket(id: String, message: String)

/** The answer for one ticket, or why there is none: an error does not stop the job. */
final case class Triaged(id: String, urgent: Option[Boolean], probability: Option[Double], error: Option[String])
```

An error is a value in its row, as everywhere in jev4s. When one call fails, the job does not
fail, and you do not lose, or pay again for, the answers that you already have.

Spark runs your code on **executors**, which are other JVMs, often on other machines. It sends
them your functions, serialized. A `JevClient` cannot travel this way, so `triage` takes a
function, `client`, and calls it on the executor, inside `mapPartitions`:

<!-- snippet: live/spark/src/main/scala/spark/SparkTriage.scala#triage -->
```scala
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
}
```

**One client for each executor.** A `JevClient` keeps its own HTTP client, with its connections
and a few threads, so an executor should build one and keep it. The example's `client` is a
`lazy val` in an `object` ([chapter 3](#3-each-action-calls-jev-again) shows it). An `object`
exists once in each JVM, and a function that names it does not take it along: each executor
builds the client the first time a task asks for it, and its later tasks use the same one. On
this project's tests, 48 tickets in 48 partitions built 43 HTTP clients with a new client for
each partition, which held 78 threads after the job; with the `object`, they built 1, with 6
threads.

**The rate.** Jev limits the whole account, not each executor. With 4 partitions and 20 calls
per second for the account, each partition gets 5. If your cluster runs fewer partitions at the
same time than `partitions`, the real rate is lower, never higher. Choose the number of
partitions for the rate, not for the size of the data: at 20 calls per second and about 0.5 s
per call, 10 partitions are enough, and more only wait.

Some calls can still fail with `RateLimited`, for example when another job uses the same
account. The client already retried them: keep their rows, and send those tickets again later.

## 3. Each action calls Jev again

A `Dataset` is a plan, not a result. **Each action on it, such as `collect`, `count` or `write`,
runs the plan again, and so calls Jev again, for every ticket.** For 10 tickets, the tests of
this project measured:

| Action on `triage(...)` | Calls to Jev |
|---|---|
| `collect()` | 10 |
| `count()` | 10 |
| `orderBy("id").collect()` | 20: the sort reads its input once to sample it, then again |
| `cache()`, then `count()` | 10 |
| after that, `orderBy("id").collect()` and `count()` | 0 |

So make **one action**: write the answers to storage, for example with `write.parquet`, and read
them from there. Or `cache()` the result before you use it more than once. In the example, the
one action is `collect`, and the driver sorts the three rows:

<!-- snippet: live/spark/src/main/scala/spark/SparkTriage.scala#main -->
```scala
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
```

A run on 2026-09-25, with `jev-1.13.0`, printed:

```text
Triaged(t1,Some(true),Some(0.92),None)
Triaged(t2,Some(false),Some(0.07),None)
Triaged(t3,Some(true),Some(0.97),None)
```

Spark also runs a task again when it fails, for example when an executor is lost. The calls of
that task are made again, and paid again.

## 4. The API key on the executors

`client` runs on each executor, so it reads `TYPESAFE_API_KEY` from the environment of that
executor, and the key never travels with your functions. It reads it once, when the executor
builds its client: a new key is seen only by a new executor. On your machine, in local mode, the
executors are threads of your program, and they see your shell's environment.

On a cluster, give the variable to the executors with the cluster's own secrets, for example a
Kubernetes secret. If you use `spark.executorEnv.TYPESAFE_API_KEY` instead, know that the Spark
UI shows it: Spark hides the settings whose name matches `spark.redaction.regex`, and in Spark
4.0.4 its default, `(?i)secret|password|token|access[.]?key`, does not match `API_KEY`. Add it:

```text
spark.redaction.regex  (?i)secret|password|token|access[.]?key|api_key
```

## 5. Testing without Jev

`client` is a function, so a test can give `triage` a client that never uses the network, and
run Spark in local mode, with `master("local[2]")`. For the answers alone, a client from
jev4s-testkit is enough, as in [chapter 12](scala213.md#12-testing-your-code) of the tutorial:
`() => JevTestkit.answering(urgent.is(true))` builds it on each executor, and every row gets
`true`, with a probability of 1. The tests of this example also use a `Transport` of their own,
which answers each message in its own way and counts the calls.

Two things fail in Spark that work in a normal test:

- **Spark serializes the function**, and everything that it uses. A function that calls a method
  of your test class takes the whole class with it, and fails with "Task not serializable". Put
  the fake replies in an `object`.
- **A value that the function captures reaches the tasks as a copy.** A counter created in the
  test and captured by the fake transport stays at 0 in the test. Keep the counter in an
  `object`, which each JVM has once.

The tests of this example are in
[`live/spark/src/test/scala/spark`](../../live/spark/src/test/scala/spark). They also check the
rate against a local server that answers 429 above 10 calls per second, and that a client kept in
an `object` starts one HTTP client for 12 partitions.
