# jev4s

A Scala client for **Jev**, the "System One" model from TypeSafe AI. Jev does not write text:
you give it some content and a few typed questions, and it returns typed answers with
probabilities. jev4s keeps those types in Scala, so the compiler knows what each answer is.

> **Unofficial and independent.** jev4s is not affiliated with, endorsed by, or part of
> TypeSafe AI.

- **Direct style, in both Scala versions.** `client.ask` is a plain method call: it returns
  when Jev has answered, with the answers or the error as a value, `Either[JevError, A]`. There
  is no effect system, no `Future` and no `F[_]` to learn. On JDK 21, many calls can wait at
  once on virtual threads ([how](docs/guide/scala3.md#13-many-requests)); with cats-effect or
  ZIO, wrap a call in `IO.blocking` or `ZIO.attemptBlocking`.
- **Scala 3: the compiler checks your questions and your answers.** A name you did not ask, an
  answer read as the wrong type, a probability above 1: each one is a compile error
  ([below](#what-the-compiler-refuses)).
- **Scala 2.13: built for Spark 4.** Ask Jev about each row of a `Dataset`
  ([below](#on-spark-4)).

## Scala 3

```scala
// build.sbt
scalaVersion := "3.9.0"
libraryDependencies += "io.github.maxtrezzi" %% "jev4s" % "0.1.0"
```

<!-- snippet: live/scala3/src/main/scala/Triage.scala#readme -->
```scala
import io.github.maxtrezzi.jev4s.*

// (1) Your own data. It is the state of the request in (5): what Jev reads and judges.
final case class Ticket(message: String, plan: String, chargesUsd: List[Double])

// (2) How a Ticket becomes JSON. The questions in (4) point at its fields by name: `message`.
given ToState[Ticket] = t => ujson.Obj("message" -> t.message, "plan" -> t.plan, "charges_usd" -> t.chargesUsd)

// (3) The possible answers of the Choice in (4), and the levels of its Score, from low to high.
enum Team derives JevChoice:
  case Billing, Technical, Sales

enum Feeling derives JevScale:
  case Calm, Annoyed, Angry

@main def triage(): Unit =
  val client = JevConfig.fromEnv("jev-1.13.0") match
    case Right(config) => JevClient(config)
    case Left(problem) => sys.error(problem.message)

  val ticket = // a value of (1)
    Ticket("You charged me twice for my order! I want my money back before Friday.", "pro", List(49.0, 49.0))

  // (4) Three questions, each with a name of your choice.
  val questions = (
    team = Choice[Team]("Which team should handle `message`?"),               // options from (3)
    duplicate = Noul("Do `charges_usd` show the same amount charged twice?"), // a field named in (2)
    feeling = Score[Feeling]("How does the customer feel in `message`?"),     // levels from (3)
  )

  // (5) One call. The answers have the names of (4), and each one the type of its question.
  client.ask(ticket, questions) match
    case Right(r) if r.team.confidence >= Probability(0.8) =>
      val team: Team       = r.team.choice        // a value of (3)
      val feeling: Feeling = r.feeling.mostLikely // a value of (3)
      println(s"Send to $team. Duplicate charge: ${r.duplicate.isYes}. Feeling: $feeling (${r.feeling.score} of 2).")
    case Right(r)    => println(s"Maybe ${r.team.choice}, but Jev is not sure: a person decides.")
    case Left(error) => println(s"Jev did not answer: $error")
```

A run on 2026-09-25, with `jev-1.13.0`, printed:

```text
Send to Billing. Duplicate charge: true. Feeling: Angry (1.8 of 2).
```

The state is your own `Ticket`: jev4s turns it into JSON with the `ToState` of (2), and each
question names the field it is about. The questions are a named tuple, and the answers are a
named tuple with the same names. Each answer has the type of its question: `r.team.choice` is a
`Team`, and `r.feeling.mostLikely`, the level with the highest probability, is a `Feeling`. The
call in (5) returns an `Either`, and a `match` reads it: nothing to run, await or unwrap.

### What the compiler refuses

In the example above, `r` is the answers of `client.ask(ticket, questions)`. Each of these lines
is a compile error, and the comment shows the compiler's message, without the package names:

<!-- compile-errors: scala3/src/test/scala/ReadmeErrorsSuite.scala -->
```scala
r.priority                       // error: value priority is not a member of (team : ChoiceAnswer[Team], …
(r.team.choice: Feeling)         // error: Found: … Team … Required: Feeling
r.feeling.probabilities("Calm")  // error: Found: ("Calm" : String) Required: Feeling
Probability(1.5)                 // error: a Probability must be between 0 and 1
enum Mood derives JevScale:      // error: a Score needs 2 to 10 levels: JevScale can be derived only for an enum of 2 to 10 cases
  case Fine
client.ask(42, questions)        // error: no ToState[Int]: give one, for example `given ToState[Int] = s => ujson.Obj(...)`, …
client.ask(ticket, (team = questions.team, count = 3)) // error: every value in the named tuple must be a question: Noul, Score or Choice
```

A test compiles each line and compares the message, so this list stays true. What the compiler
cannot see, such as two levels with the same text, jev4s checks before it sends the request: it
returns every problem at once, as a value. No call throws an exception.

## On Spark 4

Spark 4 runs on Scala 2.13 only, and the 2.13 module of jev4s is built for it. This is the core
of the [Spark example](live/spark/src/main/scala/spark/SparkTriage.scala): one question for each
row of a `Dataset`, with the answer or the error in the row.

```scala
// build.sbt
scalaVersion := "2.13.16"
libraryDependencies ++= Seq(
  "io.github.maxtrezzi" %% "jev4s"     % "0.1.0",
  "org.apache.spark"    %% "spark-sql" % "4.0.4" % Provided
)
```

<!-- snippet: live/spark/src/main/scala/spark/SparkTriage.scala#readme -->
```scala
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
```

- **It is built for every Spark 4, and tested on Spark 4.0.4.** The module is built with Scala
  2.13.16, the Scala of Spark 4.0, and its pom asks for `scala-library` 2.13.16, so it needs no
  newer Scala library than a Spark 4 cluster has
  ([ADR-0036](docs/adr/0036-the-2-13-module-compiles-with-spark-4s-scala.md)).
- **Each action on the result calls Jev again**, for every row: `collect` and then `count` pay
  twice. Write the answers to storage, or `cache()` them, once
  ([measured](docs/guide/spark.md#3-each-action-calls-jev-again)).
- **The API key never travels.** Each executor reads it from its own environment, and builds
  one client, which all its tasks share.

The [Spark guide](docs/guide/spark.md) explains each line, and how to test the job without Jev.

## Scala 2.13

```scala
// build.sbt
scalaVersion := "2.13.16"
libraryDependencies += "io.github.maxtrezzi" %% "jev4s" % "0.1.0"
```

The same example in Scala 2.13, in two parts; the `Ticket` of (1) and its `ToState` (2) are in
[the whole program](live/scala213/src/main/scala/Triage.scala). The possible answers are your own
case objects, each one listed once:

<!-- snippet: live/scala213/src/main/scala/Triage.scala#readme-types -->
```scala
// (3) The possible answers of a Choice, and the levels of a Score, each case listed once.
sealed abstract class Team extends Product with Serializable
object Team {
  case object Billing   extends Team
  case object Technical extends Team
  case object Sales     extends Team

  implicit val choices: JevChoice[Team] = JevChoice.named(Billing, Technical, Sales)
}

sealed abstract class Feeling extends Product with Serializable
object Feeling {
  case object Calm    extends Feeling
  case object Annoyed extends Feeling
  case object Angry   extends Feeling

  implicit val levels: JevScale[Feeling] = JevScale.named(Calm, Annoyed, Angry) // from low to high
}
```

Each question is a key with a name. The answers come back as a tuple, in the order of the keys,
and each answer has the type of its question: `t.choice` is a `Team`, `f.mostLikely` a `Feeling`.

<!-- snippet: live/scala213/src/main/scala/Triage.scala#readme-ask -->
```scala
// (4) Each question with its name: a key.
val team      = Choice.of[Team]("Which team should handle `message`?").as("team")            // options from (3)
val duplicate = Noul("Do `charges_usd` show the same amount charged twice?").as("duplicate") // a field of (2)
val feeling   = Score.of[Feeling]("How does the customer feel in `message`?").as("feeling")  // levels from (3)

// (5) One call. The answers come back as a tuple, in the order of the keys.
client.ask(ticket, team, duplicate, feeling) match {
  case Right((t, d, f)) => // a ChoiceAnswer[Team], a NoulAnswer, a ScoreAnswer[Feeling]
    if (t.confidence >= 0.8)
      println(s"Send to ${t.choice}. Duplicate charge: ${d.isYes}. Feeling: ${f.mostLikely} (${f.score} of 2).")
    else println(s"Maybe ${t.choice}, but Jev is not sure: a person decides.")
  case Left(error) => println(s"Jev did not answer: $error")
}
```

A run on 2026-09-25, with `jev-1.13.0`, printed:

```text
Send to Billing. Duplicate charge: true. Feeling: Angry (1.81 of 2).
```

The [Scala 2.13 tutorial](docs/guide/scala213.md) explains each step.

## Run it

1. **Get an API key.** TypeSafe AI paused new sign-ups on 2026-09-22, in an announcement on its
   X account; accounts made before keep working. Without an account, reach Jev through a
   gateway, such as OpenRouter: [the concepts guide](docs/guide/concepts.md#10-through-a-gateway)
   shows how. Put the key in the environment variable `TYPESAFE_API_KEY`, the variable that the
   official SDKs read.
2. **Add jev4s to your project.** It is on Maven Central: put the lines for your Scala version,
   above, in your `build.sbt`.
3. **Run the example.** Put the Scala 3 example in a file of your project, such as
   `src/main/scala/Triage.scala`, and run `sbt run`. It makes one call to Jev, which costs a few
   hundred input tokens.

You need **Scala 3.9 or later**, or **Scala 2.13.16 or later**. The Scala 3 module does not work
with Scala 3.3 to 3.8 ([ADR-0017](docs/adr/0017-scala-3-9-lts.md)).

jev4s works on **JDK 17, 21 and 25**, and CI tests all three. Use JDK 21 or later if you can: a
call that waits, for an answer or before a retry, costs little on a virtual thread, and you can
close an `HttpClient` that you give to jev4s. The Spark example needs JDK 17 or 21, because
Spark 4.0 does not start on JDK 25.

## Learn more

| Guide | What it teaches |
|---|---|
| [Jev concepts](docs/guide/concepts.md) | What Jev is, the three kinds of question, probabilities and confidence, how to write good questions, errors and retries. For both Scala versions. |
| [Tutorial for Scala 3](docs/guide/scala3.md) | jev4s step by step, from the first call to tests without the network. |
| [Tutorial for Scala 2.13](docs/guide/scala213.md) | The same steps in Scala 2.13. |
| [jev4s with Apache Spark](docs/guide/spark.md) | One question for each row of a Spark `Dataset`: a client on each executor, a shared rate, and why each action calls Jev again. |

Every piece of code in this README and in the guides is compiled with the project. The programs are
in [`live/scala3`](live/scala3/src/main/scala) and [`live/scala213`](live/scala213/src/main/scala),
and you can run each one from this repository, for example `sbt "scala3Live/runMain triage"`.

## What jev4s gives you

- **Typed answers.** A Noul gives a probability, a Score a position on your scale and its most
  likely level, a Choice a value of your own type. The levels of a Score and the options of a
  Choice can be your own types too.
- **No exceptions.** Every call returns `Either[JevError, A]`. Mistakes that the compiler can
  see are compile errors, and a request with problems is not sent.
- **Retries like the official SDKs**, with a limit on the total time of a call.
- **No logs.** jev4s gives each event to a function of yours, which sends it to your logger or
  your metrics.
- **One dependency**, [ujson](https://com-lihaoyi.github.io/upickle/). HTTP uses the JDK's
  `java.net.http`.
- **Tests without the network.** `jev4s-testkit` gives your tests a client that answers with
  typed values, `(team = Team.Billing, urgent = true)`, checked by the compiler: chapter 12 of the
  tutorials, [Scala 3](docs/guide/scala3.md#12-testing-your-code) and
  [Scala 2.13](docs/guide/scala213.md#12-testing-your-code). Add it to your tests with
  `libraryDependencies += "io.github.maxtrezzi" %% "jev4s-testkit" % "0.1.0" % Test`.

jev4s has one module for each Scala version, and each one is written in the style of its own
version ([ADR-0009](docs/adr/0009-two-native-modules-no-shared-code.md)). Both use the same
coordinates, so sbt picks the right one: `jev4s_3` or `jev4s_2.13`.

## Status

**Early.** Version 0.1.0 is the first release. The versions stay at `0.x` while the Jev API is
in early access: a new minor version, such as `0.2.0`, can break your code, and a new patch
version cannot. The [CHANGELOG](CHANGELOG.md) lists the changes of each version. The work plan is
in [`docs/tasks/`](docs/tasks/README.md), and every design decision, with the
options that were rejected, is in [`docs/adr/`](docs/adr/README.md).

## Credits

The named tuples of the Scala 3 module and the typed keys of the 2.13 module are not our idea:
[scala-jev-sdk](https://github.com/ticofab/scala-jev-sdk) and
[zio-typesafe-ai](https://github.com/jamesward/zio-typesafe-ai) had them first.

## License

[Apache License 2.0](LICENSE).
