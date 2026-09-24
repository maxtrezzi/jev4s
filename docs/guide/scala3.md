# jev4s tutorial for Scala 3

This tutorial starts with one question in a few lines, and adds one idea in each step. It
explains the Scala side. For what Jev does with your questions, and how to write good ones, read
[Jev concepts](concepts.md). For Scala 2.13, read the [Scala 2.13 tutorial](scala213.md).

Every piece of code here is compiled with the project. The programs are in
[`live/scala3/src/main/scala/guide`](../../live/scala3/src/main/scala/guide), and you can run
each one with `sbt "scala3Live/runMain guide.<name>"`, for example
`sbt "scala3Live/runMain guide.firstQuestion"`. Each run makes one call to Jev, which costs a
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

## 1. Set up a project

You need JDK 17 or later and **Scala 3.9 or later**. jev4s is not on Maven Central yet: clone
this repository and run `sbt publishLocal` in it. Then, in your project:

```scala
// build.sbt
scalaVersion := "3.9.0"
libraryDependencies += "io.github.maxtrezzi" %% "jev4s" % "0.1.0-SNAPSHOT"
```

Put your API key from TypeSafe AI in the environment variable `TYPESAFE_API_KEY`, in the shell
that starts sbt. jev4s has one import:

```scala
import io.github.maxtrezzi.jev4s.*
```

## 2. The first question

First, build a client. A program needs only one:

<!-- snippet: live/scala3/src/main/scala/guide/Client.scala#client -->
```scala
/** One client for the whole program. It is safe to share between threads. */
lazy val client: JevClient =
  JevConfig.fromEnv("jev-1.13.0") match
    case Right(config) => JevClient(config)
    case Left(problem) => sys.error(problem.message)
```

`JevConfig.fromEnv` reads the key from the environment. It returns an `Either`: `Left` with a
`ConfigError` when the key is not set, `Right` with a `JevConfig` when it is. The model has no
default, so you name it here. A new version of Jev can give different answers, so name a fixed
version, such as `jev-1.13.0`, and not the alias `jev-latest`.

The client is immutable, and safe to share between threads. It keeps its own HTTP connections,
so build it once, when the program starts, and use it everywhere. Then ask a question:

<!-- snippet: live/scala3/src/main/scala/guide/Client.scala#first -->
```scala
@main def firstQuestion(): Unit =
  val result = client.ask("Help! My payouts have been failing for 3 days.", (urgent = Noul("Is the message urgent?")))
  result match
    case Right(r)    => println(s"Urgent: ${r.urgent.isYes}, with a probability of ${r.urgent.probability.value}")
    case Left(error) => println(s"Jev did not answer: $error")
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Urgent: true, with a probability of 0.92
```

The numbers can change a little from one run to the next: the outputs in this tutorial are
examples, not promises.

A question has a name, here `urgent`, and you ask it inside a **named tuple**:
`(urgent = Noul(...))`. A `Noul` is a yes/no question. The answer is an
`Either[JevError, ...]`. When the call succeeds, `r` is a named tuple with the same name, so
`r.urgent` is the answer to your question: a `NoulAnswer`, with the probability of "yes" and
`isYes` when that probability is 0.5 or more.

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

<!-- snippet: live/scala3/src/main/scala/guide/Shop.scala#domain -->
```scala
final case class Customer(name: String, plan: String)
final case class Order(id: String, item: String, chargesUsd: List[Double])
final case class Ticket(customer: Customer, order: Order, message: String)

/** The shop's rules, the same for every ticket. */
val refundPolicy =
  "A duplicate charge is refunded at once. Any other refund needs the item back, unused, within 30 days."
```

A `ToState` turns a `Ticket` into the JSON that Jev reads. Write it once, next to your types:

<!-- snippet: live/scala3/src/main/scala/guide/Shop.scala#to-state -->
```scala
given ToState[Ticket] = t =>
  ujson.Obj(
    "message"       -> t.message,
    "customer"      -> ujson.Obj("name" -> t.customer.name, "plan" -> t.customer.plan),
    "order"         -> ujson.Obj("id" -> t.order.id, "item" -> t.order.item, "charges_usd" -> t.order.chargesUsd),
    "refund_policy" -> refundPolicy,
  )
```

Every field of this JSON has a name, such as `message` or `order.charges_usd`. A question points
at a field by writing its name between backticks, so Jev knows which part to judge. The
`refund_policy` is not part of a ticket, but a question about refunds needs it next to the
ticket, so it goes in the state too.

The examples use three tickets:

<!-- snippet: live/scala3/src/main/scala/guide/Shop.scala#tickets -->
```scala
/** Three tickets, used in every chapter. */
val doubleCharge = Ticket(
  Customer("Ana", "pro"),
  Order("A-104", "Hiking boots", List(89.0, 89.0)),
  "You charged me twice for my boots! I want the second payment back before Friday.",
)

val wrongSize = Ticket(
  Customer("Luca", "free"),
  Order("A-221", "Running shoes", List(120.0)),
  "The shoes are size 42 but I ordered 43. Can I change them? I prefer to write in Italian.",
)

val cannotLogIn = Ticket(
  Customer("Mia", "business"),
  Order("B-007", "Team subscription", List(300.0)),
  "Nobody on our team can log in since this morning. This is the third time this month!",
)
```

Now `ask` takes a `Ticket` directly, and the compiler finds its `ToState`. Without one, the call
does not compile, and the message says what to write:

<!-- snippet: live/scala3/src/main/scala/guide/Shop.scala#state -->
```scala
@main def state(): Unit =
  // `order.charges_usd` names a field of the JSON that the ToState above makes.
  client.ask(doubleCharge, (duplicate = Noul("Does `order.charges_usd` contain the same amount twice?"))) match
    case Right(r)    => println(s"Duplicate charge: ${r.duplicate.isYes}")
    case Left(error) => println(s"Jev did not answer: $error")
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Duplicate charge: true
```

You can also pass JSON that you build on the spot, as a `ujson.Value`, with no type of your own:

<!-- snippet: live/scala3/src/main/scala/guide/Shop.scala#json -->
```scala
val sameStateAsJson = ujson.Obj(
  "message"  -> "You charged me twice for my boots! I want the second payment back before Friday.",
  "customer" -> ujson.Obj("name" -> "Ana", "plan" -> "pro"),
)
```

A few rules help Jev ([more in the concepts guide](concepts.md#2-the-state)):

- Give each part a clear name: `refund_policy` says more than `text2`.
- Put in the state what a question needs to compare, such as the charges and the policy, and
  leave out what no question needs: every part costs input tokens.
- Keep the questions out of the state: the state holds facts, the questions hold judgments.

## 4. Three questions, three types

There are three kinds of question: `Noul` for yes or no, `Score` for a position on a scale, and
`Choice` for one option out of many. The options of a Choice can be the cases of an `enum`:

<!-- snippet: live/scala3/src/main/scala/guide/Questions.scala#team -->
```scala
enum Team derives JevChoice, CanEqual:
  case Billing, Technical, Sales
```

`derives JevChoice` makes the options: `Billing` goes to Jev as `billing`, `Technical` as
`technical`. `derives CanEqual` lets you compare a `Team` with `==`; you need it only if your
project compiles with `-language:strictEquality`, as this one does.

The levels of a Score can be the cases of an `enum` too, from low to high:

<!-- snippet: live/scala3/src/main/scala/guide/Questions.scala#feeling -->
```scala
enum Feeling derives JevScale, CanEqual:
  case Calm, Annoyed, Angry
```

`derives JevScale` makes the levels: Jev reads the name of each case, `Calm`, `Annoyed` and
`Angry`, and the order of the cases is the order of the scale. Declare the lowest level first:
no check can see an enum in the wrong order. An enum with fewer than 2 or more than 10 cases
does not compile.

Put the three questions in one named tuple. You can keep it in a `val` and use it many times:

<!-- snippet: live/scala3/src/main/scala/guide/Questions.scala#questions -->
```scala
val triage = (
  team = Choice[Team]("Which team should handle `message`?"), // the options come from Team
  urgent = Noul("Does the customer need an answer today?"),
  feeling = Score[Feeling]("How does the customer feel in `message`?"), // the levels come from Feeling
)
```

Here `Calm` is level 0 and `Angry` is level 2. Now ask all three about a ticket, in one call. Jev answers them in parallel, so three questions take
about the same time as one:

<!-- snippet: live/scala3/src/main/scala/guide/Questions.scala#ask -->
```scala
@main def threeQuestions(): Unit =
  client.ask(doubleCharge, triage) match // the Ticket of chapter 3, and the questions above
    case Right(r) =>
      val team: Team         = r.team.choice   // one of the cases of Team
      val urgent: Boolean    = r.urgent.isYes
      val feeling: Double    = r.feeling.score // from 0 (Calm) to 2 (Angry)
      val angry: Probability = r.feeling.probabilities(Feeling.Angry)
      println(s"$team, urgent: $urgent, feeling: $feeling, angry: ${angry.value}")
    case Left(error) => println(s"Jev did not answer: $error")
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Billing, urgent: false, feeling: 1.53, angry: 0.53
```

Each answer has the type of its question, and the types in this example are only there to show
it: you do not need to write them.

| Question | Answer | What you read |
|---|---|---|
| `Noul` | `NoulAnswer` | `probability`, `isYes` |
| `Score[Feeling]` | `ScoreAnswer[Feeling]` | `score`, `mostLikely` (a `Feeling`), `confidence`, `probabilities` |
| `Choice[Team]` | `ChoiceAnswer[Team]` | `choice` (a `Team`), `confidence`, `probabilities`, `ifConfident` |

`r.feeling.score` is a `Double` from 0 to 2, and it can fall between two levels, such as 1.3.
`r.feeling.mostLikely` is the level with the highest probability, a `Feeling`; when two levels
have the same probability, it is the lower one. `r.feeling.probabilities` gives the probability
of each level, and you read it with a case of the enum: `probabilities(Feeling.Angry)`.

You can also give the levels as text, with no enum:
`Score("How does the customer feel?", "Calm", "Annoyed", "Angry")`. Then each level is a
`ujson.Value`, and you read the probabilities with the level as you wrote it:
`probabilities("Angry")`. A misspelt level compiles, and fails only when the code runs; with an
enum, the compiler finds it.

The compiler checks the whole call. These mistakes do not compile:

| Mistake | What the compiler says |
|---|---|
| A name that you did not ask: `r.urgnet` | `value urgnet is not a member of ...` |
| The wrong answer type: `r.urgent` used as a `ScoreAnswer[Feeling]` | `Found: ...NoulAnswer`, `Required: ...ScoreAnswer[...Feeling]` |
| A text key on a Score over an enum: `probabilities("Angry")` | `Found: ("Angry" : String)`, `Required: ...Feeling` |
| A value that is not a question: `(urgent = Noul("U?"), count = 3)` | `every value in the named tuple must be a question: Noul, Score or Choice.` |
| A tuple without names: `(Noul("U?"), Noul("V?"))` | `the questions must be a named tuple, such as (urgent = Noul("Is it urgent?")).` |
| A Choice over a type with no options | `no options for Choice[...]: define a given JevChoice[...]` |
| A Score with no levels: `Score("How?")` | `a Score needs its levels: give them, as in Score("How?", "Calm", "Angry"), or name an enum that derives JevScale, as in Score[Mood]("How?")` |
| An enum with 1 case, or 11, that derives `JevScale` | `a Score needs 2 to 10 levels: JevScale can be derived only for an enum of 2 to 10 cases.` |
| A state with no `ToState` | `no ToState[...]: give one, for example ...` |

## 5. The options of a Choice

Jev reads the key of each option, and a description when there is one. A description helps Jev
tell the options apart. To give one, let the `enum` extend `Described`:

<!-- snippet: live/scala3/src/main/scala/guide/Options.scala#described -->
```scala
enum Request(val description: String) extends Described derives JevChoice, CanEqual:
  case Refund      extends Request("The customer wants their money back")
  case Exchange    extends Request("The customer wants a different size or colour")
  case Information extends Request("The customer only asks a question")
  case Other       extends Request("None of the options above")
```

The last option, `Other`, gives Jev a correct answer when no other option fits. Without it, Jev
must choose one of the others.

The keys are the names of the cases in snake_case: `Refund` becomes `refund`, and a case called
`TechnicalSupport` would become `technical_support`. `derives JevChoice` works only for an
`enum` whose cases have no parameters. For any other type, write the options by hand with a
`given JevChoice`:

<!-- snippet: live/scala3/src/main/scala/guide/Options.scala#by-hand -->
```scala
final case class Agent(name: String, languages: List[String]) derives CanEqual

/** The agents on duty today: in a real program, from a database. */
val agents = List(Agent("Sofia", List("Spanish", "English")), Agent("Marco", List("Italian", "English")))

given JevChoice[Agent] =
  JevChoice(agents.map(a => ChoiceOption(a, a.name.toLowerCase, Some(s"Speaks ${a.languages.mkString(" and ")}")))*)
```

Each `ChoiceOption` has your value, the key that Jev reads, and an optional description. Here
the options come from data, and the description tells Jev which languages each agent speaks.
The ticket `wrongSize` says that the customer prefers Italian, so Jev can match the two:

<!-- snippet: live/scala3/src/main/scala/guide/Options.scala#options -->
```scala
@main def options(): Unit =
  val questions = (
    request = Choice[Request]("What does the customer want in `message`?"), // options from Request
    agent = Choice[Agent]("Who should answer `message`?"),                  // options from agents
  )
  client.ask(wrongSize, questions) match
    case Right(r) =>
      val agent: Agent = r.agent.choice // one of the values in agents
      println(s"${r.request.choice}, answered by ${agent.name}")
    case Left(error) => println(s"Jev did not answer: $error")
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Exchange, answered by Marco
```

## 6. From answers to decisions

An answer is a probability, not a certainty. Your code decides how sure it must be before it
acts, and it can combine several answers. This is where a state with several parts pays off:
each question checks one fact, in the part of the state where it is, and your code joins them:

<!-- snippet: live/scala3/src/main/scala/guide/Decisions.scala#refund -->
```scala
/** Three facts, each from a different part of the state, combined by your code. */
val refundChecks = (
  asked = Noul("Does `message` ask for money back?"),
  duplicate = Noul("Does `order.charges_usd` contain the same amount twice?"),
  allowed = Noul("Does `refund_policy` allow a refund at once for this ticket?"),
)

def refundAtOnce(ticket: Ticket): Either[JevError, Boolean] =
  client.ask(ticket, refundChecks).map(r => r.asked.isYes && r.duplicate.isYes && r.allowed.isYes)
```

This is better than one question with three conditions: each answer can be checked on its own,
and the rule that joins them is in your code, where you can read and change it. See
[How to write good questions](concepts.md#5-how-to-write-good-questions).

`isYes` means a probability of 0.5 or more. When a mistake costs more, use your own limits. A
`Probability` is a number from 0 to 1, and you compare it with another `Probability`:

<!-- snippet: live/scala3/src/main/scala/guide/Decisions.scala#noul -->
```scala
def whenToAnswer(urgent: NoulAnswer): String =
  if urgent.probability >= Probability(0.9) then "now"
  else if urgent.probability >= Probability(0.4) then "a person decides"
  else "in the normal queue"
```

`Probability(0.9)` is checked by the compiler: `Probability(1.5)` does not compile, and the
error says `a Probability must be between 0 and 1`. Write the number with a decimal point:
`Probability(1.0)`, not `Probability(1)`. For a number that you know only at runtime, such as a
limit read from a file, use `Probability.from(x)`, which returns an `Option`.

A Choice and a Score have a `confidence`: how sure Jev is of the whole answer. `ifConfident`
returns the choice only when the confidence is high enough:

<!-- snippet: live/scala3/src/main/scala/guide/Decisions.scala#confidence -->
```scala
def route(team: ChoiceAnswer[Team]): String =
  team.ifConfident(Probability(0.8)) match
    case Some(team) => s"send to $team"
    case None       => "a person chooses the team"
```

To combine Scores, bring each one to a scale from 0 to 1 first. A Score with 3 levels goes from
0 to 2, and one with 5 levels from 0 to 4. A small extension method does it for any Score:

<!-- snippet: live/scala3/src/main/scala/guide/Decisions.scala#normalized -->
```scala
/** The score on a scale from 0 to 1, whatever the number of levels. */
extension (answer: ScoreAnswer[?]) def normalized: Double = answer.score / (answer.probabilities.size - 1)
```

Now the code can decide. The weights, 0.7 and 0.3, are yours: when the result does not match
what your team would decide, change them in the code.

<!-- snippet: live/scala3/src/main/scala/guide/Decisions.scala#decisions -->
```scala
@main def decisions(): Unit =
  println(s"Refund at once: ${refundAtOnce(doubleCharge)}, ${refundAtOnce(wrongSize)}")

  val questions = (
    team = Choice[Team]("Which team should handle `message`?"),
    urgent = Noul("Does the customer need an answer today?"),
    severity = Score("How bad is the problem in `message`?", "Cosmetic", "A workaround exists", "No workaround exists"),
    feeling = Score[Feeling]("How does the customer feel in `message`?"),
  )
  client.ask(cannotLogIn, questions) match
    case Right(r) =>
      val priority = 0.7 * r.severity.normalized + 0.3 * r.feeling.normalized
      println(f"${route(r.team)}, answer ${whenToAnswer(r.urgent)}, priority $priority%.2f")
    case Left(error) => println(s"Jev did not answer: $error")
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Refund at once: Right(true), Right(false)
send to Technical, answer a person decides, priority 0.91
```

## 7. Questions with structure

The instructions, the criteria of a Noul, the levels of a Score and the descriptions of a
Choice's options can be text or JSON. Text is enough for most questions. Use JSON when a question
needs data of its own next to it, or when each level needs examples:

<!-- snippet: live/scala3/src/main/scala/guide/Structure.scala#structure -->
```scala
/** An incident that your code already knows about, sent next to the question. */
val incident =
  ujson.Obj("id" -> "INC-12", "system" -> "login", "since" -> "this morning", "affects" -> "business plans")

val knownIncident = Noul(
  ujson.Obj(
    "incident" -> incident,
    "question" -> "Does `message` report the problem described in `incident`?",
  ),
  whenTrue = Some("The same system fails in the same way, even if the words are different"),
  whenFalse = Some("Another system, or no problem at all"),
)

val outOfService =
  ujson.Obj("level" -> "Out of service", "examples" -> ujson.Arr("Nobody can log in", "Every payment fails"))

val impact = Score(
  ujson.Obj("question" -> "How much does the problem in `message` stop the customer's work?"),
  ujson.Obj("level"    -> "No impact", "examples"   -> ujson.Arr("A question", "A typo on a page")),
  ujson.Obj("level"    -> "Slower work", "examples" -> ujson.Arr("A report is late", "A workaround exists")),
  outOfService,
)
```

The `incident` is not part of the ticket: it is something your code knows, and the question
compares the two. The state stays the same for every question, and each question can bring its
own data. Put the question in one field and the data in the others, and name the data with
backticks, as you do for the state.

A `String` and a `ujson.Value` can be mixed: a Score can have text levels and JSON levels. The
probabilities of a JSON level are keyed by the same JSON, so keep it in a `val`, like
`outOfService`, to read them:

<!-- snippet: live/scala3/src/main/scala/guide/Structure.scala#structure-ask -->
```scala
@main def structure(): Unit =
  client.ask(cannotLogIn, (knownIncident = knownIncident, impact = impact)) match
    case Right(r) =>
      println(s"Part of ${incident("id").str}: ${r.knownIncident.isYes}")
      println(s"Impact: ${r.impact.score} of 2, out of service: ${r.impact.probabilities(outOfService).value}")
    case Left(error) => println(s"Jev did not answer: $error")
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
Part of INC-12: true
Impact: 2.0 of 2, out of service: 1.0
```

`whenTrue` and `whenFalse` are the criteria of a Noul: what a "yes" and a "no" mean. Most Nouls
do not need them.

## 8. Questions built at runtime

A named tuple is fixed when you compile. When the questions come from data, such as checks kept
in a database, use `askMap` with a `Map` from names to questions:

<!-- snippet: live/scala3/src/main/scala/guide/Runtime.scala#runtime -->
```scala
/** Checks that come from a database or a file, not from the code. */
val checks = Map(
  "legal" -> "Does `message` mention a lawyer or a court?",
  "press" -> "Does `message` mention a journalist or a social network?",
  "vip"   -> "Is `customer.plan` a paid plan?",
)

/** The items of the shop, also from data. */
val items = List("hiking boots", "running shoes", "team subscription")

@main def runtime(): Unit =
  val questions = checks.map((name, text) => name -> Noul(text)) +
    ("item" -> Choice[String]("Which item is `message` about?")(using JevChoice.keys(items*)))
  client.askMap(doubleCharge, questions) match
    case Right(answers) =>
      answers.toList
        .sortBy(_._1)
        .foreach: (name, answer) =>
          answer match
            case a: NoulAnswer      => println(s"$name: ${a.isYes}")
            case a: ScoreAnswer[?]  => println(s"$name: ${a.score}")
            case a: ChoiceAnswer[?] => println(s"$name: ${a.choice}")
    case Left(error) => println(s"Jev did not answer: $error")
```

A run on 2026-09-22, with `jev-1.13.0`, printed:

```text
item: hiking boots
legal: false
press: false
vip: true
```

The answers are a `Map[String, Answer]`, with the same names. The compiler does not know which
question each name had, so each answer is an `Answer`: a `NoulAnswer`, a `ScoreAnswer` or a
`ChoiceAnswer[?]`. Match on it, with one case for each.

`JevChoice.keys(...)` makes options from strings: each key is also the value, so the choice is a
`String`. Use it for options that you know only at runtime.

## 9. When something goes wrong

jev4s does not throw exceptions. `ask` returns `Either[JevError, ...]`, and `JevError` is an
`enum` with one case for each kind of failure. The [table in the concepts
guide](concepts.md#errors) lists them all. Match on the ones your code handles in a special way:

<!-- snippet: live/scala3/src/main/scala/guide/Errors.scala#explain -->
```scala
def explain(error: JevError): String = error match
  case JevError.InvalidRequest(problems) => problems.map(_.message).mkString("; ")
  case JevError.Unauthorized             => "check TYPESAFE_API_KEY"
  case JevError.Rejected(message)        => s"Jev refused the request: $message"
  case JevError.RateLimited(after)       => s"too many requests; wait ${after.fold("a little")(_.toString)}"
  case other if other.isRetryable        => s"try again later: $other"
  case other                             => s"a defect to report: $other"
```

`isRetryable` is true when sending the same request again may work, such as after a network
error. The client has already retried these errors before it returns them (see
[Configuration](#10-configuration)).

jev4s checks a request before it sends it. A request with problems is not sent, so it costs
nothing, and you get all the problems at once:

<!-- snippet: live/scala3/src/main/scala/guide/Errors.scala#invalid -->
```scala
@main def invalid(): Unit =
  // No request is sent: jev4s finds both problems first, and returns them together.
  val result = client.ask(
    doubleCharge,
    (
      feeling = Score("How does the customer feel?", "Calm"),
      risk = Score("How risky is the message?", "Low", "High", "Low"),
    ),
  )
  println(result.left.map(explain))
```

This prints `Left(score 'feeling' needs 2 to 10 levels, got 1; score 'risk' uses the level 'Low'
more than once)`.

A Score over an enum that derives `JevScale` has its number of levels checked by the compiler
instead: an enum of 1 case does not compile.

## 10. Configuration

`JevConfig` is a case class. Change it with `copy`:

<!-- snippet: live/scala3/src/main/scala/guide/Settings.scala#config -->
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

<!-- snippet: live/scala3/src/main/scala/guide/Settings.scala#by-hand -->
```scala
def fromVault(secret: String): JevConfig = JevConfig(ApiKey(secret), model = "jev-1.13.0")
```

`ask` blocks the calling thread until the answer arrives. To make several calls at the same
time, call `ask` from several threads; on JDK 21 or later, virtual threads are a cheap way to do
it. One client serves all the threads.

## 11. Logs and metrics

jev4s never writes logs. It gives each event to a function that you pass as `onEvent`:

<!-- snippet: live/scala3/src/main/scala/guide/Events.scala#events -->
```scala
val inputTokens = LongAdder()
val log         = System.getLogger("jev")

def withEvents(config: JevConfig): JevClient =
  JevClient(
    config,
    onEvent = {
      case JevEvent.Replied(reply) =>
        inputTokens.add(reply.inputTokens)
        log.log(System.Logger.Level.DEBUG, s"answered by ${reply.model}")
      case JevEvent.Retrying(error, retry, delay) =>
        log.log(System.Logger.Level.WARNING, s"retry $retry in $delay after $error")
    },
  )
```

There are two events. `Replied` comes after each successful call, with the model that answered
and the input tokens it cost: this example adds them up. `Retrying` comes before each retry,
with the error, the number of the retry and the wait.

Match every case, and do not write `case _`. If a later version of jev4s adds an event, the
compiler then shows you each place where you need to handle it. `onEvent` runs on the thread
that called `ask`, so keep it short.

## 12. Testing your code

Your own code should not build the client. Let it take a `JevClient` as a parameter:

<!-- snippet: live/scala3/src/main/scala/guide/Service.scala#service -->
```scala
/** Code of your own that uses Jev. It takes the client as a parameter, so a test can pass another. */
final class Router(client: JevClient):
  def route(ticket: Ticket): String =
    client.ask(ticket, triage) match
      case Right(r) if r.urgent.isYes => s"${r.team.choice}, today"
      case Right(r)                   => s"${r.team.choice}"
      case Left(error)                => s"a person, because Jev did not answer: $error"
```

In a test, build the client with `JevClient.withTransport`. A `Transport` is one function: it
takes the body of the request and returns the body of the reply, or a `JevError`. A fake one
returns what the test needs, and no network is used:

<!-- snippet: live/scala3/src/test/scala/guide/RouterSuite.scala#test -->
```scala
class RouterSuite extends munit.FunSuite:

  /** A reply in the format of the API, for the questions of `triage`. */
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

  test("an urgent billing ticket goes to Billing today"):
    val client = JevClient.withTransport("jev-1.13.0", _ => Right(reply))
    assertEquals(Router(client).route(doubleCharge), "Billing, today")

  test("when Jev does not answer, a person decides"):
    val client = JevClient.withTransport("jev-1.13.0", _ => Left(JevError.Overloaded))
    assertEquals(Router(client).route(doubleCharge), "a person, because Jev did not answer: Overloaded")
```

The reply is in the format of the API, with one answer for each name in the questions. The
concepts guide shows [real replies](concepts.md#3-three-kinds-of-question) that you can copy.
A client over your own transport does not retry: the retries belong to the transport of
`JevClient(config)`.

This test uses [munit](https://scalameta.org/munit/), but any test library works.
