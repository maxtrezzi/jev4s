# jev4s tutorial for Scala 2.13

This tutorial starts with one question in a few lines, and adds one idea in each step. It
explains the Scala side. For what Jev does with your questions, and how to write good ones, read
[Jev concepts](concepts.md). For Scala 3, read the [Scala 3 tutorial](scala3.md).

Every piece of code here is compiled with the project. The programs are in
[`live/scala213/src/main/scala/guide`](../../live/scala213/src/main/scala/guide), and you can
run each one with `sbt "scala213Live/runMain guide.<Name>"`, for example
`sbt "scala213Live/runMain guide.FirstQuestion"`. Each run makes one call to Jev, which costs a
few hundred input tokens.

## Contents

1. [Set up a project](#1-set-up-a-project)
2. [The first question](#2-the-first-question)
3. [The state: your own data](#3-the-state-your-own-data)
4. [Three questions, three types](#4-three-questions-three-types)
5. [The options of a Choice](#5-the-options-of-a-choice)
6. [From answers to decisions](#6-from-answers-to-decisions)
7. [Questions with structure](#7-questions-with-structure)
8. [Questions built at runtime](#8-questions-built-at-runtime)
9. [When something goes wrong](#9-when-something-goes-wrong)
10. [Configuration](#10-configuration)
11. [Logs and metrics](#11-logs-and-metrics)
12. [Testing your code](#12-testing-your-code)
13. [Many requests](#13-many-requests)

## 1. Set up a project

You need JDK 17 or later and **Scala 2.13.16 or later**. jev4s is not on Maven Central yet:
clone this repository and run `sbt publishLocal` in it. Then, in your project:

```scala
// build.sbt
scalaVersion := "2.13.16"
libraryDependencies += "io.github.maxtrezzi" %% "jev4s" % "0.1.0-SNAPSHOT"
```

Put your API key from TypeSafe AI in the environment variable `TYPESAFE_API_KEY`, in the shell
that starts sbt. jev4s has one import:

```scala
import io.github.maxtrezzi.jev4s._
```

## 2. The first question

First, build a client. A program needs only one. In these examples it is in the package object
of the package `guide`:

<!-- snippet: live/scala213/src/main/scala/guide/package.scala#client -->
```scala
/** One client for the whole program. It is safe to share between threads. */
lazy val client: JevClient =
  JevConfig.fromEnv("jev-1.13.0") match {
    case Right(config) => JevClient.create(config)
    case Left(problem) => sys.error(problem.message)
  }
```

`JevConfig.fromEnv` reads the key from the environment. It returns an `Either`: `Left` with a
`ConfigError` when the key is not set, `Right` with a `JevConfig` when it is. The model has no
default, so you name it here. A new version of Jev can give different answers, so name a fixed
version, such as `jev-1.13.0`, and not the alias `jev-latest`.

The client is immutable, and safe to share between threads. It keeps its own HTTP connections,
so build it once, when the program starts, and use it everywhere. Then ask a question:

<!-- snippet: live/scala213/src/main/scala/guide/FirstQuestion.scala#first -->
```scala
object FirstQuestion {
  val urgent = Noul("Is the message urgent?").as("urgent")

  def main(args: Array[String]): Unit =
    client.ask("Help! My payouts have been failing for 3 days.", urgent) match {
      case Right(u)    => println(s"Urgent: ${u.isYes}, with a probability of ${u.probability.value}")
      case Left(error) => println(s"Jev did not answer: $error")
    }
}
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Urgent: true, with a probability of 0.92
```

The numbers can change a little from one run to the next: the outputs in this tutorial are
examples, not promises.

A question with a name is a **key**: `Noul(...).as("urgent")`. A `Noul` is a yes/no question.
`ask` takes the state and the key, and returns an `Either`: a `JevError` on the left, or the
answer on the right. The key knows the type of its answer, so the answer here is a `NoulAnswer`,
with the probability of "yes" and `isYes` when that probability is 0.5 or more.

Jev never sees the name `urgent`. It reads only the text of the question, so write the whole
question there.

Here the state is one sentence. In a real program, it is your own data: the next chapter shows
how.

## 3. The state: your own data

The state is what Jev reads and judges, so it decides how good the answers are. A plain string,
as in chapter 2, is fine for one message. Real decisions need more: the message, who wrote it,
what they bought, and the rules of your business. Jev compares these parts, so give it all of
them in one state.

The rest of this tutorial follows one example: the support desk of a shop. These are its data,
as ordinary Scala types:

<!-- snippet: live/scala213/src/main/scala/guide/Shop.scala#domain -->
```scala
final case class Customer(name: String, plan: String)
final case class Order(id: String, item: String, chargesUsd: List[Double])
final case class Ticket(customer: Customer, order: Order, message: String)
```

An implicit `ToState` turns a `Ticket` into the JSON that Jev reads. In the companion object of
`Ticket`, the compiler finds it without an import:

<!-- snippet: live/scala213/src/main/scala/guide/Shop.scala#to-state -->
```scala
object Ticket {

  /** The shop's rules, the same for every ticket. */
  val refundPolicy =
    "A duplicate charge is refunded at once. Any other refund needs the item back, unused, within 30 days."

  implicit val toState: ToState[Ticket] = t =>
    ujson.Obj(
      "message"       -> t.message,
      "customer"      -> ujson.Obj("name" -> t.customer.name, "plan" -> t.customer.plan),
      "order"         -> ujson.Obj("id" -> t.order.id, "item" -> t.order.item, "charges_usd" -> t.order.chargesUsd),
      "refund_policy" -> refundPolicy
    )
}
```

Every field of this JSON has a name, such as `message` or `order.charges_usd`. A question points
at a field by writing its name between backticks, so Jev knows which part to judge. The
`refund_policy` is not part of a ticket, but a question about refunds needs it next to the
ticket, so it goes in the state too.

The examples use three tickets:

<!-- snippet: live/scala213/src/main/scala/guide/Shop.scala#tickets -->
```scala
/** Three tickets, used in every chapter. */
object Tickets {
  val doubleCharge = Ticket(
    Customer("Ana", "pro"),
    Order("A-104", "Hiking boots", List(89.0, 89.0)),
    "You charged me twice for my boots! I want the second payment back before Friday."
  )

  val wrongSize = Ticket(
    Customer("Luca", "free"),
    Order("A-221", "Running shoes", List(120.0)),
    "The shoes are size 42 but I ordered 43. Can I change them? I prefer to write in Italian."
  )

  val cannotLogIn = Ticket(
    Customer("Mia", "business"),
    Order("B-007", "Team subscription", List(300.0)),
    "Nobody on our team can log in since this morning. This is the third time this month!"
  )
}
```

Now `ask` takes a `Ticket` directly. Without a `ToState`, the call does not compile, and the
message says what to write:

<!-- snippet: live/scala213/src/main/scala/guide/Shop.scala#state -->
```scala
object State {
  // `order.charges_usd` names a field of the JSON that the ToState above makes.
  val duplicate = Noul("Does `order.charges_usd` contain the same amount twice?").as("duplicate")

  def main(args: Array[String]): Unit =
    client.ask(Tickets.doubleCharge, duplicate) match {
      case Right(d)    => println(s"Duplicate charge: ${d.isYes}")
      case Left(error) => println(s"Jev did not answer: $error")
    }
}
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Duplicate charge: true
```

You can also pass JSON that you build on the spot, as a `ujson.Value`, with no type of your own:

<!-- snippet: live/scala213/src/main/scala/guide/Shop.scala#json -->
```scala
val sameStateAsJson = ujson.Obj(
  "message"  -> "You charged me twice for my boots! I want the second payment back before Friday.",
  "customer" -> ujson.Obj("name" -> "Ana", "plan" -> "pro")
)
```

A few rules help Jev ([more in the concepts guide](concepts.md#2-the-state)):

- Give each part a clear name: `refund_policy` says more than `text2`.
- Put in the state what a question needs to compare, such as the charges and the policy, and
  leave out what no question needs: every part costs input tokens.
- Keep the questions out of the state: the state holds facts, the questions hold judgments.

## 4. Three questions, three types

There are three kinds of question: `Noul` for yes or no, `Score` for a position on a scale, and
`Choice` for one option out of many. The options of a Choice are values of your own type, with
an implicit `JevChoice` that lists them:

<!-- snippet: live/scala213/src/main/scala/guide/Questions.scala#team -->
```scala
sealed abstract class Team extends Product with Serializable
object Team {
  case object Billing   extends Team
  case object Technical extends Team
  case object Sales     extends Team

  implicit val choices: JevChoice[Team] =
    JevChoice(ChoiceOption(Billing, "billing"), ChoiceOption(Technical, "technical"), ChoiceOption(Sales, "sales"))
}
```

Each `ChoiceOption` has your value and the key that Jev reads. The implicit is in the companion
object of `Team`, so the compiler finds it without an import. `extends Product with
Serializable` keeps the inferred types simple: a `List(Team.Billing, Team.Sales)` is a
`List[Team]`.

The levels of a Score can be values of your own type too, with an implicit `JevScale` that lists
them from low to high:

<!-- snippet: live/scala213/src/main/scala/guide/Questions.scala#feeling -->
```scala
sealed abstract class Feeling extends Product with Serializable
object Feeling {
  case object Calm    extends Feeling
  case object Annoyed extends Feeling
  case object Angry   extends Feeling

  implicit val levels: JevScale[Feeling] =
    JevScale(ScaleLevel(Calm, "Calm"), ScaleLevel(Annoyed, "Annoyed"), ScaleLevel(Angry, "Angry"))
}
```

Each `ScaleLevel` has your value and the text that Jev reads. The order of the list is the order
of the scale, so put the lowest level first: no check can see a list in the wrong order.

Make one key for each question. Keeping the keys in an object lets you use them in many places:

<!-- snippet: live/scala213/src/main/scala/guide/Questions.scala#questions -->
```scala
object Triage {
  val team    = Choice.of[Team]("Which team should handle `message`?").as("team") // the options come from Team
  val urgent  = Noul("Does the customer need an answer today?").as("urgent")
  val feeling = Score.of[Feeling]("How does the customer feel in `message`?").as("feeling")
}
```

`Choice.of[Team]` takes its options from the implicit `JevChoice[Team]`, and `Score.of[Feeling]`
its levels from the implicit `JevScale[Feeling]`: here `Calm` is level 0 and `Angry` is level 2.
Now ask all three about a ticket, in one call. Jev answers them in parallel, so three questions take about the
same time as one:

<!-- snippet: live/scala213/src/main/scala/guide/Questions.scala#ask -->
```scala
object ThreeQuestions {
  import Triage._

  def main(args: Array[String]): Unit =
    client.ask(Tickets.doubleCharge, team, urgent, feeling) match { // the Ticket of chapter 3, the keys above
      case Right((t, u, f)) => // the answers, in the order of the keys
        val chosen: Team       = t.choice // one of the cases of Team
        val isUrgent: Boolean  = u.isYes
        val score: Double      = f.score  // from 0 (Calm) to 2 (Angry)
        val angry: Probability = f.probabilities(Feeling.Angry)
        println(s"$chosen, urgent: $isUrgent, feeling: $score, angry: ${angry.value}")
      case Left(error) => println(s"Jev did not answer: $error")
    }
}
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Billing, urgent: false, feeling: 1.52, angry: 0.52
```

With several keys, `ask` returns a tuple of the answers, in the order of the keys, and
`case Right((t, u, f))` gives each one a name. Each answer has the type of its question, and the
types in this example are only there to show it: you do not need to write them. `ask` takes 1 to
10 keys; for more, use `askMap` ([chapter 8](#8-questions-built-at-runtime)).

| Question | Answer | What you read |
|---|---|---|
| `Noul` | `NoulAnswer` | `probability`, `isYes` |
| `Score[Feeling]` | `ScoreAnswer[Feeling]` | `score`, `mostLikely` (a `Feeling`), `confidence`, `probabilities` |
| `Choice[Team]` | `ChoiceAnswer[Team]` | `choice` (a `Team`), `confidence`, `probabilities`, `ifConfident` |

The score is a `Double` from 0 to 2, and it can fall between two levels, such as 1.3.
`mostLikely` is the level with the highest probability, a `Feeling`; when two levels have the
same probability, it is the lower one. `probabilities` gives the probability of each level, and
you read it with a value of your type: `probabilities.get(Feeling.Angry)`.

You can also give the levels as text, with no type of your own:
`Score("How does the customer feel?", List("Calm", "Annoyed", "Angry"))`. Then each level is a
`ujson.Value`, and you read the probabilities with the level as you wrote it:
`probabilities.get("Angry")`. A misspelt level compiles, and gives `None` when the code runs.

## 5. The options of a Choice

Jev reads the key of each option, and a description when there is one. A description helps Jev
tell the options apart. It is the third argument of `ChoiceOption`:

<!-- snippet: live/scala213/src/main/scala/guide/Options.scala#described -->
```scala
sealed abstract class Request extends Product with Serializable
object Request {
  case object Refund      extends Request
  case object Exchange    extends Request
  case object Information extends Request
  case object Other       extends Request

  implicit val choices: JevChoice[Request] = JevChoice(
    ChoiceOption(Refund, "refund", Some("The customer wants their money back")),
    ChoiceOption(Exchange, "exchange", Some("The customer wants a different size or colour")),
    ChoiceOption(Information, "information", Some("The customer only asks a question")),
    ChoiceOption(Other, "other", Some("None of the options above"))
  )
}
```

The last option, `Other`, gives Jev a correct answer when no other option fits. Without it, Jev
must choose one of the others.

The options can come from data. Here the list of agents could come from a database,
`JevChoice.fromOptions` takes a `List`, and the description tells Jev which languages each agent
speaks:

<!-- snippet: live/scala213/src/main/scala/guide/Options.scala#by-hand -->
```scala
final case class Agent(name: String, languages: List[String])
object Agent {

  /** The agents on duty today: in a real program, from a database. */
  val all = List(Agent("Sofia", List("Spanish", "English")), Agent("Marco", List("Italian", "English")))

  implicit val choices: JevChoice[Agent] = JevChoice.fromOptions(
    all.map(a => ChoiceOption(a, a.name.toLowerCase, Some(s"Speaks ${a.languages.mkString(" and ")}")))
  )
}
```

The ticket `wrongSize` says that the customer prefers Italian, so Jev can match the two. The
answer is an `Agent`, with its name and languages:

<!-- snippet: live/scala213/src/main/scala/guide/Options.scala#options -->
```scala
object Options {
  val request = Choice.of[Request]("What does the customer want in `message`?").as("request") // options from Request
  val agent   = Choice.of[Agent]("Who should answer `message`?").as("agent")                  // options from Agent.all

  def main(args: Array[String]): Unit =
    client.ask(Tickets.wrongSize, request, agent) match {
      case Right((r, a)) =>
        val chosen: Agent = a.choice // one of the values in Agent.all
        println(s"${r.choice}, answered by ${chosen.name}")
      case Left(error) => println(s"Jev did not answer: $error")
    }
}
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Exchange, answered by Marco
```

## 6. From answers to decisions

An answer is a probability, not a certainty. Your code decides how sure it must be before it
acts, and it can combine several answers. This is where a state with several parts pays off:
each question checks one fact, in the part of the state where it is, and your code joins them:

<!-- snippet: live/scala213/src/main/scala/guide/Decisions.scala#refund -->
```scala
/** Three facts, each from a different part of the state, combined by your code. */
val asked     = Noul("Does `message` ask for money back?").as("asked")
val duplicate = Noul("Does `order.charges_usd` contain the same amount twice?").as("duplicate")
val allowed   = Noul("Does `refund_policy` allow a refund at once for this ticket?").as("allowed")

def refundAtOnce(ticket: Ticket): Either[JevError, Boolean] =
  client.ask(ticket, asked, duplicate, allowed).map { case (a, d, r) => a.isYes && d.isYes && r.isYes }
```

This is better than one question with three conditions: each answer can be checked on its own,
and the rule that joins them is in your code, where you can read and change it. See
[How to write good questions](concepts.md#5-how-to-write-good-questions).

`isYes` means a probability of 0.5 or more. When a mistake costs more, use your own limits. A
`Probability` is always from 0 to 1, and you compare it with a `Double`:

<!-- snippet: live/scala213/src/main/scala/guide/Decisions.scala#noul -->
```scala
def whenToAnswer(urgent: NoulAnswer): String =
  if (urgent.probability >= 0.9) "now"
  else if (urgent.probability >= 0.4) "a person decides"
  else "in the normal queue"
```

To make a `Probability` from a number, use `Probability.from(x)`, which returns `None` when `x`
is not from 0 to 1.

A Choice and a Score have a `confidence`: how sure Jev is of the whole answer. `ifConfident`
returns the choice only when the confidence is high enough:

<!-- snippet: live/scala213/src/main/scala/guide/Decisions.scala#confidence -->
```scala
def route(team: ChoiceAnswer[Team]): String =
  team.ifConfident(0.8) match {
    case Some(team) => s"send to $team"
    case None       => "a person chooses the team"
  }
```

To combine Scores, bring each one to a scale from 0 to 1 first. A Score with 3 levels goes from
0 to 2, and one with 5 levels from 0 to 4. A small function does it for any Score:

<!-- snippet: live/scala213/src/main/scala/guide/Decisions.scala#normalized -->
```scala
/** The score on a scale from 0 to 1, whatever the number of levels. */
def normalized(answer: ScoreAnswer[_]): Double = answer.score / (answer.probabilities.size - 1)
```

Now the code can decide. The weights, 0.7 and 0.3, are yours: when the result does not match
what your team would decide, change them in the code.

<!-- snippet: live/scala213/src/main/scala/guide/Decisions.scala#decisions -->
```scala
val team     = Choice.of[Team]("Which team should handle `message`?").as("team")
val urgent   = Noul("Does the customer need an answer today?").as("urgent")
val severity =
  Score("How bad is the problem in `message`?", List("Cosmetic", "A workaround exists", "No workaround exists"))
    .as("severity")
val feeling = Score.of[Feeling]("How does the customer feel in `message`?").as("feeling")

def main(args: Array[String]): Unit = {
  println(s"Refund at once: ${refundAtOnce(Tickets.doubleCharge)}, ${refundAtOnce(Tickets.wrongSize)}")

  client.ask(Tickets.cannotLogIn, team, urgent, severity, feeling) match {
    case Right((t, u, s, f)) =>
      val priority = 0.7 * normalized(s) + 0.3 * normalized(f)
      println(f"${route(t)}, answer ${whenToAnswer(u)}, priority $priority%.2f")
    case Left(error) => println(s"Jev did not answer: $error")
  }
}
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Refund at once: Right(true), Right(false)
send to Technical, answer a person decides, priority 0.90
```

## 7. Questions with structure

The instructions, the criteria of a Noul, the levels of a Score and the descriptions of a
Choice's options can be text or JSON. Text is enough for most questions. Use JSON when a question
needs data of its own next to it, or when each level needs examples:

<!-- snippet: live/scala213/src/main/scala/guide/Structure.scala#structure -->
```scala
/** An incident that your code already knows about, sent next to the question. */
val incident =
  ujson.Obj("id" -> "INC-12", "system" -> "login", "since" -> "this morning", "affects" -> "business plans")

val knownIncident = Noul(
  ujson.Obj(
    "incident" -> incident,
    "question" -> "Does `message` report the problem described in `incident`?"
  ),
  whenTrue = Some("The same system fails in the same way, even if the words are different"),
  whenFalse = Some("Another system, or no problem at all")
).as("knownIncident")

val outOfService =
  ujson.Obj("level" -> "Out of service", "examples" -> ujson.Arr("Nobody can log in", "Every payment fails"))

val impact = Score(
  ujson.Obj("question" -> "How much does the problem in `message` stop the customer's work?"),
  List(
    ujson.Obj("level" -> "No impact", "examples"   -> ujson.Arr("A question", "A typo on a page")),
    ujson.Obj("level" -> "Slower work", "examples" -> ujson.Arr("A report is late", "A workaround exists")),
    outOfService
  )
).as("impact")
```

The `incident` is not part of the ticket: it is something your code knows, and the question
compares the two. The state stays the same for every question, and each question can bring its
own data. Put the question in one field and the data in the others, and name the data with
backticks, as you do for the state.

A `String` and a `ujson.Value` can be mixed: a Score can have text levels and JSON levels. The
probabilities of a JSON level are keyed by the same JSON, so keep it in a `val`, like
`outOfService`, to read them:

<!-- snippet: live/scala213/src/main/scala/guide/Structure.scala#structure-ask -->
```scala
def main(args: Array[String]): Unit =
  client.ask(Tickets.cannotLogIn, knownIncident, impact) match {
    case Right((k, i)) =>
      println(s"Part of ${incident("id").str}: ${k.isYes}")
      println(s"Impact: ${i.score} of 2, out of service: ${i.probabilities.get(outOfService)}")
    case Left(error) => println(s"Jev did not answer: $error")
  }
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Part of INC-12: true
Impact: 2.0 of 2, out of service: Some(1.0)
```

`whenTrue` and `whenFalse` are the criteria of a Noul: what a "yes" and a "no" mean. Most Nouls
do not need them.

## 8. Questions built at runtime

The keys of `ask` are fixed when you compile. When the questions come from data, such as checks
kept in a database, use `askMap` with a `Map` from names to questions:

<!-- snippet: live/scala213/src/main/scala/guide/Runtime.scala#runtime -->
```scala
object Runtime {

  /** Checks that come from a database or a file, not from the code. */
  val checks = Map(
    "legal" -> "Does `message` mention a lawyer or a court?",
    "press" -> "Does `message` mention a journalist or a social network?",
    "vip"   -> "Is `customer.plan` a paid plan?"
  )

  /** The items of the shop, also from data. */
  val items = List("hiking boots", "running shoes", "team subscription")

  def main(args: Array[String]): Unit = {
    val questions: Map[String, Question[_]] = checks.map { case (name, text) => name -> Noul(text) } +
      ("item" -> Choice.of[String]("Which item is `message` about?")(JevChoice.keys(items: _*)))
    client.askMap(Tickets.doubleCharge, questions) match {
      case Right(answers) =>
        answers.toList.sortBy(_._1).foreach { case (name, answer) =>
          answer match {
            case a: NoulAnswer      => println(s"$name: ${a.isYes}")
            case a: ScoreAnswer[_]  => println(s"$name: ${a.score}")
            case a: ChoiceAnswer[_] => println(s"$name: ${a.choice}")
          }
        }
      case Left(error) => println(s"Jev did not answer: $error")
    }
  }
}
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
item: hiking boots
legal: false
press: false
vip: true
```

The answers are a `Map[String, Answer]`, with the same names. The compiler does not know which
question each name had, so each answer is an `Answer`: a `NoulAnswer`, a `ScoreAnswer[_]` or a
`ChoiceAnswer[_]`. Match on it, with one case for each.

`JevChoice.keys(...)` makes options from strings: each key is also the value, so the choice is a
`String`. Use it for options that you know only at runtime.

## 9. When something goes wrong

jev4s does not throw exceptions. `ask` returns an `Either` with a `JevError` on the left, and `JevError` is a
sealed type with one case for each kind of failure. The [table in the concepts
guide](concepts.md#errors) lists them all. Match on the ones your code handles in a special way:

<!-- snippet: live/scala213/src/main/scala/guide/Errors.scala#explain -->
```scala
def explain(error: JevError): String = error match {
  case JevError.InvalidRequest(problems) => problems.map(_.message).mkString("; ")
  case JevError.Unauthorized             => "check TYPESAFE_API_KEY"
  case JevError.Rejected(message)        => s"Jev refused the request: $message"
  case JevError.RateLimited(after)       => s"too many requests; wait ${after.fold("a little")(_.toString)}"
  case other if other.isRetryable        => s"try again later: $other"
  case other                             => s"a defect to report: $other"
}
```

`isRetryable` is true when sending the same request again may work, such as after a network
error. The client has already retried these errors before it returns them (see
[Configuration](#10-configuration)).

jev4s checks a request before it sends it. A request with problems is not sent, so it costs
nothing, and you get all the problems at once:

<!-- snippet: live/scala213/src/main/scala/guide/Errors.scala#invalid -->
```scala
def main(args: Array[String]): Unit = {
  // No request is sent: jev4s finds both problems first, and returns them together.
  val feeling = Score("How does the customer feel?", List("Calm")).as("feeling")
  val risk    = Score("How risky is the message?", List("Low", "High", "Low")).as("risk")
  println(client.ask(Tickets.doubleCharge, feeling, risk).left.map(explain))
}
```

This prints `Left(score 'feeling' needs 2 to 10 levels, got 1; score 'risk' uses the level 'Low'
more than once)`.

## 10. Configuration

`JevConfig` is a case class. Change it with `copy`:

<!-- snippet: live/scala213/src/main/scala/guide/Settings.scala#config -->
```scala
val patient: Either[ConfigError, JevConfig] =
  JevConfig
    .fromEnv("jev-1.13.0")
    .map(_.copy(timeout = 30.seconds, retry = RetryPolicy(maxRetries = 4, maxElapsed = 2.minutes)))

val noRetries: Either[ConfigError, JevConfig] = JevConfig.fromEnv("jev-1.13.0").map(_.copy(retry = RetryPolicy.none))
```

| Field | Default | What it does |
|---|---|---|
| `apiKey` | from `TYPESAFE_API_KEY` | The key, as an `ApiKey`, which always prints as `<hidden>` |
| `model` | none | The model, such as `jev-1.13.0` |
| `baseUrl` | `https://api.typesafe.ai`, or `TYPESAFE_BASE_URL` | The address of the API; `https`, or `http` only on `localhost` |
| `timeout` | 10 seconds | The longest wait for one answer |
| `retry` | `RetryPolicy()` | When to send a failed request again |

`RetryPolicy()` retries 2 times, with a wait of 0.5 s that doubles up to 5 s, and never retries
for more than 30 s in total (`maxElapsed`). The [concepts
guide](concepts.md#retries) explains each rule.

When the key does not come from the environment, such as from a secret store, build the config
yourself:

<!-- snippet: live/scala213/src/main/scala/guide/Settings.scala#by-hand -->
```scala
def fromVault(secret: String): JevConfig = JevConfig(new ApiKey(secret), model = "jev-1.13.0")
```

`ask` blocks the calling thread until the answer arrives. To make several calls at the same
time, call `ask` from several threads, for example with `Future`s. One client serves all the
threads. [Chapter 13](#13-many-requests) shows how to stay under the limit
of your account.

`JevClient.create(config)` builds its own `java.net.http.HttpClient`. To use one of yours, for
example with a proxy or your own executor, pass `httpClient = Some(yours)`. It keeps its own
connect timeout, `timeout` still limits each request, and jev4s never closes it: you do.

## 11. Logs and metrics

jev4s never writes logs. It gives each event to a function that you pass as `onEvent`:

<!-- snippet: live/scala213/src/main/scala/guide/Events.scala#events -->
```scala
val inputTokens   = new LongAdder()
val log           = System.getLogger("jev")
val lastRequestId = ThreadLocal.withInitial[Option[String]](() => None)

def withEvents(config: JevConfig): JevClient =
  JevClient.create(
    config,
    onEvent = {
      case JevEvent.Replied(reply) =>
        reply.inputTokens.foreach(inputTokens.add) // None when the reply does not report them
        log.log(System.Logger.Level.DEBUG, s"answered by ${reply.model}")
      case JevEvent.Retrying(error, retry, delay) =>
        log.log(System.Logger.Level.WARNING, s"retry $retry in $delay after $error")
      case JevEvent.Responded(status, requestId) =>
        lastRequestId.set(requestId) // the latest response on this thread
        log.log(System.Logger.Level.DEBUG, s"HTTP $status, request ${requestId.getOrElse("with no id")}")
    }
  )
```

There are three events. `Replied` comes after each successful call, with the model that answered
and the input tokens it cost: this example adds them up. `inputTokens` is an `Option[Long]`: it
is `None` when the reply does not report the tokens, and the answers still come back. `Retrying`
comes before each retry, with the error, the number of the retry and the wait. `Responded` comes
after each HTTP response, also an error or one that is retried, with its status and its
**request id**: the `x-typesafe-request-id` header, or `None` when the response has none. A
request that gets no response, such as a timeout, sends no `Responded`.

The request id is what TypeSafe's support asks for when a call goes wrong. The error does not
carry it, but the events come in order, on the thread that called `ask`, before `ask` returns. So
the last `Responded` on that thread belongs to the error. The example keeps it in a
`ThreadLocal`, and this function adds it to the error:

<!-- snippet: live/scala213/src/main/scala/guide/Events.scala#request-id -->
```scala
val urgent = Noul("Is the message urgent?").as("urgent")

/** On an error, says which request failed: TypeSafe's support asks for its id. */
def askOrReport(client: JevClient, message: String): Either[String, Boolean] = {
  lastRequestId.remove() // forget the id of an earlier call on this thread
  client.ask(message, urgent) match {
    case Right(u)    => Right(u.isYes)
    case Left(error) => Left(s"$error, request ${lastRequestId.get.getOrElse("with no response")}")
  }
}
```

It forgets the id of an earlier call first: if this call gets no response at all, there is no id
to show.

Match every case, and do not write `case _`. If a later version of jev4s adds an event, the
compiler then shows you each place where you need to handle it. `onEvent` runs on the thread
that called `ask`, so keep it short.

## 12. Testing your code

Your own code should not build the client. Let it take a `JevClient` as a parameter:

<!-- snippet: live/scala213/src/main/scala/guide/Service.scala#service -->
```scala
/** Code of your own that uses Jev. It takes the client as a parameter, so a test can pass another. */
final class Router(client: JevClient) {
  import Triage._

  def route(ticket: Ticket): String =
    client.ask(ticket, team, urgent, feeling) match {
      case Right((t, u, _)) if u.isYes => s"${t.choice}, today"
      case Right((t, _, _))            => s"${t.choice}"
      case Left(error)                 => s"a person, because Jev did not answer: $error"
    }
}
```

In a test, build the client with `JevClient.withTransport`. A `Transport` is one function: it
takes the body of the request and returns the body of the reply, or a `JevError`. A fake one
returns what the test needs, and no network is used:

<!-- snippet: live/scala213/src/test/scala/guide/RouterSuite.scala#test -->
```scala
class RouterSuite extends munit.FunSuite {

  /** A reply in the format of the API, for the questions of `Triage`. */
  val reply = """{
    "model": "jev-1.13.0",
    "answers": {
      "team": {"type": "choice", "choice": "billing", "confidence": 0.9,
               "probabilities": {"billing": 0.95, "technical": 0.03, "sales": 0.02}},
      "urgent": {"type": "noul", "noul": 0.97},
      "feeling": {"type": "score", "score": 1.2, "confidence": 0.7,
                  "probabilities": {"0": 0.0, "1": 0.8, "2": 0.2}}
    },
    "usage": {"input_tokens": 300, "output_tokens": 40}
  }"""

  test("an urgent billing ticket goes to Billing today") {
    val client = JevClient.withTransport("jev-1.13.0", _ => Right(reply))
    assertEquals(new Router(client).route(Tickets.doubleCharge), "Billing, today")
  }

  test("when Jev does not answer, a person decides") {
    val client = JevClient.withTransport("jev-1.13.0", _ => Left(JevError.Overloaded))
    assertEquals(new Router(client).route(Tickets.doubleCharge), "a person, because Jev did not answer: Overloaded")
  }
}
```

The reply is in the format of the API, with one answer for each key in the request. The
concepts guide shows [real replies](concepts.md#3-three-kinds-of-question) that you can copy.
A client over your own transport does not retry: the retries belong to the transport of
`JevClient.create(config)`.

This test uses [munit](https://scalameta.org/munit/), but any test library works.

## 13. Many requests

To send many requests, call `ask` from a small pool of threads, and keep the number of calls in
each second under the limit of your account.

**The limit.** Jev limits each account. On 2026-09-24, TypeSafe's page
[Models](https://docs.typesafe.ai/models.md) gave 1,200 requests per minute (20 per second) and
250,000 input tokens per second for `jev-1.13.0`. The same page says that the limits can change
without notice, and that they are higher on custom and enterprise plans. Through a
[gateway](concepts.md#10-through-a-gateway), the gateway's limits apply. A reply has no header
that says how close you are to the limit, so you choose the rate yourself.

**More threads do not give more answers.** Above the limit, Jev answers "429 Too Many Requests".
jev4s retries a few times, then returns `RateLimited`. On a test server that accepted 20
requests per second, 32 threads without any control lost 67 to 92 answers out of 200. With the
same 32 threads and a pacer at 19 requests per second, no answer was lost.

A **pacer** gives each call a start time, evenly spaced: with 15 calls per second, one call
every 67 ms. A thread that comes too early sleeps until its time. It is a few lines, and jev4s
does not include one, because only you know the right rate for your account:

<!-- snippet: live/scala213/src/main/scala/guide/Many.scala#pacer -->
```scala
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
```

Use it around each call, from a fixed pool of threads:

<!-- snippet: live/scala213/src/main/scala/guide/Many.scala#many -->
```scala
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
```

How many threads? About the rate multiplied by the time of one call: at 15 calls per second and
about 0.5 s per call, 8 threads. With fewer, you do not reach the rate; with more, the extra
threads only wait for the pacer. One client serves all the threads.

Some calls can still fail with `RateLimited`, for example when another program uses the same
account. The client already retried them, so do not send them again at once, in a loop. Keep
them, and send them again later at a lower rate, or give them to a person:

<!-- snippet: live/scala213/src/main/scala/guide/Many.scala#rate-limited -->
```scala
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
```

Run it with `sbt "scala213Live/runMain guide.Many"`. It makes three calls to Jev.
