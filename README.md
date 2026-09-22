# jev4s

A Scala client for **Jev**, the "System One" model from TypeSafe AI. Jev does not write text:
you give it some content and a few typed questions, and it returns typed answers with
probabilities. jev4s keeps those types in Scala, so the compiler knows what each answer is.

> **Unofficial and independent.** jev4s is not affiliated with, endorsed by, or part of
> TypeSafe AI.

## Scala 3

```scala
// build.sbt
scalaVersion := "3.9.0"
libraryDependencies += "io.github.maxtrezzi" %% "jev4s" % "0.1.0-SNAPSHOT"
```

<!-- snippet: live/scala3/src/main/scala/Triage.scala#readme -->
```scala
import io.github.maxtrezzi.jev4s.*

// (1) Your own data. It is the state of the request in (4): what Jev reads and judges.
final case class Ticket(message: String, plan: String, chargesUsd: List[Double])

// (2) How a Ticket becomes JSON. The questions in (4) point at its fields by name: `message`.
given ToState[Ticket] = t => ujson.Obj("message" -> t.message, "plan" -> t.plan, "charges_usd" -> t.chargesUsd)

// (3) The possible answers of the Choice in (4). Jev picks one, and (5) gets a Team back.
enum Team derives JevChoice:
  case Billing, Technical, Sales

@main def triage(): Unit =
  val client = JevConfig.fromEnv("jev-1.13.0") match
    case Right(config) => JevClient(config)
    case Left(problem) => sys.error(problem.message)

  val ticket = // a value of (1)
    Ticket("You charged me twice for my order! I want my money back before Friday.", "pro", List(49.0, 49.0))

  // (4) One call: the state (1) and three questions, each with a name of your choice.
  val answers = client.ask(
    ticket,
    (
      team = Choice[Team]("Which team should handle `message`?"),               // options from (3)
      duplicate = Noul("Do `charges_usd` show the same amount charged twice?"), // a field named in (2)
      feeling = Score("How does the customer feel in `message`?", "Calm", "Annoyed", "Angry"),
    ),
  )

  // (5) The answers have the names of (4), and each one the type of its question.
  answers match
    case Right(r) if r.team.confidence >= Probability(0.8) =>
      val team: Team = r.team.choice // a value of (3)
      println(s"Send to $team. Duplicate charge: ${r.duplicate.isYes}. Feeling: ${r.feeling.score} of 2.")
    case Right(r)    => println(s"Maybe ${r.team.choice}, but Jev is not sure: a person decides.")
    case Left(error) => println(s"Jev did not answer: $error")
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Send to Billing. Duplicate charge: true. Feeling: 1.82 of 2.
```

The state is your own `Ticket`: jev4s turns it into JSON with the `ToState` of (2), and each
question names the field it is about. The questions are a named tuple, and the answers are a
named tuple with the same names. Each answer has the type of its question, so `r.team.choice` is
a `Team`, and a name you did not ask is a compile error. `Probability(0.8)` is checked by the
compiler too: `Probability(1.5)` does not compile.

## Scala 2.13

```scala
// build.sbt
scalaVersion := "2.13.18"
libraryDependencies += "io.github.maxtrezzi" %% "jev4s" % "0.1.0-SNAPSHOT"
```

<!-- snippet: live/scala213/src/main/scala/Triage.scala#readme -->
```scala
import io.github.maxtrezzi.jev4s._

// (1) Your own data. It is the state of the request in (5): what Jev reads and judges.
final case class Ticket(message: String, plan: String, chargesUsd: List[Double])
object Ticket {
  // (2) How a Ticket becomes JSON. The questions in (4) point at its fields by name: `message`.
  implicit val toState: ToState[Ticket] =
    t => ujson.Obj("message" -> t.message, "plan" -> t.plan, "charges_usd" -> t.chargesUsd)
}

// (3) The possible answers of the Choice in (4). Jev picks one, and (6) gets a Team back.
sealed abstract class Team extends Product with Serializable
object Team {
  case object Billing   extends Team
  case object Technical extends Team
  case object Sales     extends Team

  implicit val choices: JevChoice[Team] =
    JevChoice(ChoiceOption(Billing, "billing"), ChoiceOption(Technical, "technical"), ChoiceOption(Sales, "sales"))
}

object Triage {
  // (4) Each question with its name: a key, used to ask in (5) and to read the answer in (6).
  val team      = Choice.of[Team]("Which team should handle `message`?").as("team")            // options from (3)
  val duplicate = Noul("Do `charges_usd` show the same amount charged twice?").as("duplicate") // a field named in (2)
  val feeling   = Score("How does the customer feel in `message`?", List("Calm", "Annoyed", "Angry")).as("feeling")

  def main(args: Array[String]): Unit = {
    val client = JevConfig.fromEnv("jev-1.13.0") match {
      case Right(config) => JevClient.create(config)
      case Left(problem) => sys.error(problem.message)
    }

    val ticket = // a value of (1)
      Ticket("You charged me twice for my order! I want my money back before Friday.", "pro", List(49.0, 49.0))

    // (5) One call: the state (1) and the keys of (4).
    client.ask(ticket, team, duplicate, feeling) match {
      case Right(answers) =>
        // (6) Each key of (4) reads its own answer, with the type of its question.
        for {
          t <- answers.get(team)      // a ChoiceAnswer[Team]: t.choice is a value of (3)
          d <- answers.get(duplicate) // a NoulAnswer
          f <- answers.get(feeling)   // a ScoreAnswer
        } {
          if (t.confidence >= 0.8)
            println(s"Send to ${t.choice}. Duplicate charge: ${d.isYes}. Feeling: ${f.score} of 2.")
          else println(s"Maybe ${t.choice}, but Jev is not sure: a person decides.")
        }
      case Left(error) => println(s"Jev did not answer: $error")
    }
  }
}
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Send to Billing. Duplicate charge: true. Feeling: 1.8 of 2.
```

The state is your own `Ticket`, turned into JSON by the implicit `ToState` of (2). Each
question is a key with a name. You ask with the keys, and read each answer with the same key:
`answers.get(team)` is an `Option[ChoiceAnswer[Team]]`.

## Run it

1. **Get an API key** from TypeSafe AI, and put it in the environment variable
   `TYPESAFE_API_KEY`. This is the variable the official SDKs read.
2. **Build the library.** It is not on Maven Central yet. Clone this repository and run
   `sbt publishLocal`: this puts `0.1.0-SNAPSHOT` in your local repository, where your project
   finds it.
3. **Run the example.** Put the code above in a file of your project, such as
   `src/main/scala/Triage.scala`, and run `sbt run`. It makes one call to Jev, which costs a
   few hundred input tokens.

You need **JDK 17 or later**, and **Scala 3.9 or later** or **Scala 2.13**. The Scala 3 module
does not work with Scala 3.3 to 3.8 ([ADR-0017](docs/adr/0017-scala-3-9-lts.md)).

## Learn more

| Guide | What it teaches |
|---|---|
| [Jev concepts](docs/guide/concepts.md) | What Jev is, the three kinds of question, probabilities and confidence, how to write good questions, errors and retries. For both Scala versions. |
| [Tutorial for Scala 3](docs/guide/scala3.md) | jev4s step by step, from the first call to tests without the network. |
| [Tutorial for Scala 2.13](docs/guide/scala213.md) | The same steps in Scala 2.13. |

Every piece of code in this README and in the guides is compiled with the project. The programs
are in [`live/scala3`](live/scala3/src/main/scala) and [`live/scala213`](live/scala213/src/main/scala),
and you can run each one from this repository, for example `sbt "scala3Live/runMain triage"`.

## What jev4s gives you

- **Typed answers.** A Noul gives a probability, a Score a position on your scale, a Choice a
  value of your own type.
- **No exceptions.** Every call returns `Either[JevError, A]`. Mistakes that the compiler can
  see are compile errors, and a request with problems is not sent.
- **Retries like the official SDKs**, with a limit on the total time of a call.
- **No logs.** jev4s gives each event to a function of yours, which sends it to your logger or
  your metrics.
- **One dependency**, [ujson](https://com-lihaoyi.github.io/upickle/). HTTP uses the JDK's
  `java.net.http`.
- **Tests without the network**: build a client over a fake transport.

jev4s has one module for each Scala version, and each one is written in the style of its own
version ([ADR-0009](docs/adr/0009-two-native-modules-no-shared-code.md)). Both use the same
coordinates, so sbt picks the right one: `jev4s_3` or `jev4s_2.13`.

## Status

**In development.** Both modules work against the real API, but nothing is published yet. The
work plan is in [`docs/tasks/`](docs/tasks/README.md), and every design decision, with the
options that were rejected, is in [`docs/adr/`](docs/adr/README.md).

## Thanks

These community clients came first and shaped the design:
[scala-jev-sdk](https://github.com/ticofab/scala-jev-sdk) and
[zio-typesafe-ai](https://github.com/jamesward/zio-typesafe-ai).

## License

[Apache License 2.0](LICENSE).
