# Tasks

Work items that are not a milestone: a fix or a change of design found between milestones.
Each one has its own branch, `task/<slug>`, and follows the definition of done in
[`milestones.md`](milestones.md#definition-of-done).

---

### T1 — Scala 3 model polish before M2

**Status:** Done 2026-09-22 —
[ADR-0019](../adr/0019-probability-literals-and-questions-compared-by-value.md),
[ADR-0020](../adr/0020-code-stryker4s-cannot-mutate-is-mutated-by-hand.md)

**Branch:** `task/scala3-model-polish`

A review of the M1 model against the showcase bar of
[ADR-0009](../adr/0009-two-native-modules-no-shared-code.md) found one defect and two places
where the Scala 3 module does not use what Scala 3 offers. M2 builds `Probability` and
`ChoiceAnswer` in the codec, so their contract is settled before M2 starts.

1. **Two `Choice` questions with different options are equal.** The options come from a
   `using` clause, the second parameter list, and a case's `equals` and `hashCode` read only
   the first. `Question` also has no `CanEqual`, so under `strictEquality` two questions cannot
   be compared at all.
2. **`Probability` stops half way.** Its comparisons take a `Double`, so two probabilities
   cannot be compared with each other, and `ifConfident(min: Double)` accepts `-3` or `1.5`
   without complaint.
3. **`Probability.from` returns `Either[String, _]`**, the only stringly typed error in the
   library.

Out of scope, noted for later: `Noul`'s `Option[String]` parameters, `Network` dropping the
cause, and compile-time checks of `Score` levels and derived option counts.

**Done when:** the three points are fixed in `scala3`, the 2.13 module follows where its
language allows, and the definition of done holds in both modules.

#### Found

| Module | Tests | Coverage | Mutants |
|---|---|---|---|
| `scala3` | 28 (22 in M1) | 82 statements, 100% statements and branches | 64: 53 killed, 11 `Ignored` and applied by hand, all detected |
| `scala213` | 23 (22 in M1) | 100% statements and branches | 53: 53 killed |

- **The Choice defect was real.** On M1, `Choice[Tier]("Plan?")` with two different given
  `JevChoice` values gave `==` true and the same `hashCode`. `QuestionSuite` now fails if a
  Choice's options leave its equality.
- **A given `CanEqual` written as a generic method is never run**, so scoverage reported it
  as an uncovered statement: CanEqual evidence is used only by the compiler. `CanEqual` is
  contravariant, so one `given CanEqual[Question[?], Question[?]]`, a value, covers every
  pair of questions and is covered.
- **Giving `Probability` a `CanEqual` changed what mutation testing sees.** In M1 the mutants
  `>=` → `==` on a `Probability` did not compile under `strictEquality`. Now they compile, and
  two survived in `isYes` and `ifConfident`, because no test used a value strictly above the
  threshold. Two assertions kill them.
- **Stryker4s 1.1.1 cannot mutate `inline` code.** It fails the whole run with *"Scala212
  doesn't support inline modifiers"*, and setting `strykerScalaDialect` to Scala 3 does not
  change that. [ADR-0020](../adr/0020-code-stryker4s-cannot-mutate-is-mutated-by-hand.md)
  excludes the mutations inside the `inline` method and applies them by hand. Leaving out
  `ConditionalExpression` from the list is enough to bring the failure back.
- **scoverage has no problem with `inline`**: the constructor's body is not a runtime
  statement, and the module stays at 100%. This closes the `inline` point that ADR-0008 left
  to verify.
- **`compileErrors` in munit reports the position of the test, not of the snippet**, so the
  tests compare only the first line of its output, the message.
- The Scala 3 changes also compile and pass on Scala 3.9.0, the version of
  [D1](open-decisions.md#d1--scala-3-target-version).

### T2 — Structured instructions and criteria

**Status:** Done

The API accepts a JSON object or array wherever it accepts a string in `instructions` and in
`criteria` (https://docs.typesafe.ai/primitives/advanced.md). The model holds strings only
([ADR-0021](../adr/0021-what-the-codec-keeps-from-a-jev-reply.md)). Widening the types must
keep a caller who passes strings compiling unchanged, in both modules.

**Done when:** a question can carry structured instructions and criteria, the codec sends them,
and a recorded golden case covers them.

#### Found

| Module | Tests | Coverage | Mutants |
|---|---|---|---|
| `scala3` | 111 (109 before) | 100% statements and branches | 186: 172 killed, 2 compile errors, 12 `Ignored` and applied by hand, all detected |
| `scala213` | 100 (98 before) | 100% statements and branches | 178: 177 killed, 1 `Ignored` and applied by hand, all detected |

- [ADR-0031](../adr/0031-instructions-and-criteria-are-ujson-values.md): the fields are
  `ujson.Value`, and ujson's own conversion from `String` keeps string callers compiling in both
  Scala versions, `Some("yes")` included.
- **Scala 3.9's `into` does not cover the case.** `scala.Conversion.into` needs no language
  import and converts a direct argument or a vararg, but not an argument inside `Some(...)`, and
  not a spread `List[String]`.
- **What does break:** a `List[String]` spread into a Score, `instructions` read as a `String`,
  and a Score's keys compared with a `Set[String]`: two tests and the two live suites.
- `golden/structured`, one paid call: JSON in every place that takes it and one Score level left
  as text. The reply's `legend` repeats the JSON levels as sent, so keying the probabilities by
  the question's levels keeps agreeing with it.
- The live suites compile against the new types, but have no structured case: the golden case
  covers the format, and a live one would add a paid call to every live run.

### T3 — Newer major versions of the GitHub actions

**Status:** Done 2026-09-22

On 2026-09-22 the latest releases were `actions/checkout` v7.0.1, `actions/setup-java` v6.0.1,
`actions/upload-artifact` v7.0.1 and `sbt/setup-sbt` v1.5.9. The workflow uses the v4 versions
that ran green in M1. Read each changelog for breaking changes before moving.

**Done when:** the workflow uses the current major versions and a pull request runs green.

#### Found

- The workflow now uses `actions/checkout@v7`, `actions/setup-java@v6` and
  `actions/upload-artifact@v7`. `sbt/setup-sbt@v1` already follows its latest release, v1.5.9.
- **No breaking change applies to this workflow.** The majors in between move the actions to
  Node 24, which needs runner v2.327.1 or later: GitHub's hosted runners have it. checkout v6
  stores credentials in a separate file, which nothing here reads; checkout v7 refuses fork code
  under `pull_request_target` and `workflow_run`, which this workflow does not use.
  upload-artifact v7 adds `archive`, whose default, `true`, keeps the zipped upload. setup-java
  v6 still takes `distribution`, `java-version` and `cache: sbt`.
- `actionlint` reports nothing on the workflow. It checks the syntax and the inputs, not a run,
  so it could not close the item on its own.
- **The new majors run green on GitHub's hosted runners.** Pull request #2 ran the whole
  workflow: all 10 jobs passed, with no change to the steps and no warning from the actions.

### T4 — Fixes from the review of the integrated branch

**Status:** Done

A review of M2–M6 together, with probes run against a `publishLocal` build, found:

1. **The API key in a test diff.** munit prints a case class field by field, so a failed
   `assertEquals` over two `JevConfig`s showed `apiKey = "secret-…"`.
2. **A `ClassCastException` from `Answers.get` in 2.13.** Scala's `==` on numbers is
   cooperative (`1 == 1L`), so a `Choice[Int]` and a `Choice[Long]` with the same options are
   equal, and a key of one read the other's answer.
3. **Two threads per client, never closed.** Each `JdkTransport` builds its own `HttpClient`:
   50 clients took a JVM from 7 threads to 108.
4. **Plain `http` to any host.** `TYPESAFE_BASE_URL=http://…` sent the key unencrypted.
5. **`ServerError` keeps the raw body**, which for a proxy's 5xx page is HTML.
6. **ADR-0022 counts one cast in `ask`; the code has two.**

Checked and left as they are: a huge or infinite `Retry-After` falls back to the backoff and
never throws; the two codecs follow the same logic.

**Done when:** the six points are fixed in both modules, and the definition of done holds.

#### Found

| Module | Tests | Coverage | Mutants |
|---|---|---|---|
| `scala3` | 107 (105 before) | 100% statements and branches | 182: 169 killed, 1 compile error, 12 `Ignored` and applied by hand, all detected |
| `scala213` | 96 (92 before) | 100% statements and branches | 174: 173 killed, 1 `Ignored` and applied by hand, all detected |

- Points 1 and 4 are [ADR-0027](../adr/0027-the-api-key-is-a-type-and-travels-over-tls.md),
  point 2 is [ADR-0028](../adr/0028-a-key-matches-a-question-of-the-same-classes.md), point 5
  is [ADR-0029](../adr/0029-every-http-error-carries-a-short-message.md). Point 3 is
  documentation: `HttpClient.close` exists only from JDK 21, so a client is not closeable
  while the library supports JDK 17.
- **Point 6 is fixed in the code, not in the ADR.** `ask` takes `NamedTuple[N, V]` instead of
  `Q <: AnyNamedTuple`, so `questions.toTuple` needs no cast, and the one cast of ADR-0022 is
  the only one. The result type is the same type written out.
- **An unnamed tuple fails differently.** It now conforms to `NamedTuple[N, V]` and fails on
  the search for `QuestionNames`, which has its own message: *"the questions must be a named
  tuple, such as (urgent = Noul("Is it urgent?"))."* Of the compile errors `CompileErrorSuite`
  checks, two are now the compiler's own messages, not three.
- **`compileErrors` is evaluated when the test compiles.** An incremental build that does not
  recompile `CompileErrorSuite` keeps the old errors, and the suite passed against a signature
  it no longer described. Run it after `clean` when a signature changes.
- **A collection of one element does not test `forall`.** Stryker4s found the collection case
  of the 2.13 match alive under `exists`; the test now uses `Vector("a", 1)` against
  `Vector("a", 1L)`.
- The `defaultBaseUrl` mutants were applied by hand again, because `JevConfigSuite` changed:
  detected in both modules, now by the default base URL test alone.
- The Scala 3 Scaladoc read `${reply.model}` in the `JevClient` example as an undefined variable,
  the defect the 2.13 module already had; it is escaped.
- Checked on JDK 21 and 25 on this machine; JDK 17 is left to CI.

### T5 — A time limit on the retries of a call

**Status:** Done

With the defaults, a call could keep its thread for up to 150 s: three attempts of 10 s and two
waits of up to 60 s that the server may ask for. The review of T4 left it out as a choice of
ADR-0024; the owner asked for a limit.

**Done when:** a retry that would end after the limit is not made, in both modules, with the
limit and the clock tested without real waits.

#### Found

| Module | Tests | Coverage | Mutants |
|---|---|---|---|
| `scala3` | 109 | 100% statements and branches | 186: 172 killed, 2 compile errors, 12 `Ignored` and applied by hand, all detected |
| `scala213` | 98 | 100% statements and branches | 178: 177 killed, 1 `Ignored` and applied by hand, all detected |

- [ADR-0030](../adr/0030-a-call-retries-for-at-most-maxelapsed.md): `maxElapsed = 30.seconds`,
  the Python SDK's budget, which ADR-0024 had recorded and left out.
- With the default limit, a `Retry-After` between 30 s and the 60 s of `maxRetryAfter` ends the
  retries at once: the caller gets `RateLimited(Some(delay))`.

---

### T6 — User guides

**Status:** Done

**Branch:** `task/t6-user-guides`

The owner asked for user documentation: the README opens with one meaningful example for each
Scala version, with the sbt settings in view; then three guides, one with the concepts of Jev
shared by both versions and one tutorial for each version, each starting from the simplest call
and adding one idea at a time.

**Done when:** every example compiles in CI and is quoted exactly, and each example that calls
the API has run once against it.

#### Built

- **README**: an example for Scala 3 and one for 2.13 at the top, each with its `build.sbt`
  lines — the caller's own `Ticket` as the state through a `ToState`, three questions of the
  three kinds that name the fields they judge, a Choice over the caller's own type, a confidence
  gate — with numbered comments that link each definition to the place that uses it; then how to
  run it, the guides, and what jev4s gives. The errors, retries, events and
  testing sections moved to the guides.
- **`docs/guide/concepts.md`**: state, the three kinds of question, probabilities and
  confidence, how to write questions, many questions in one call, structure, models and cost,
  and how jev4s maps each concept in both versions, with the errors and retries. Every JSON
  reply is copied from `golden/`; the facts come from docs.typesafe.ai, read 2026-09-22.
- **`docs/guide/scala3.md`, `docs/guide/scala213.md`**: twelve chapters each, the same order in
  both, ending with a test over a fake transport. After a first call on a string, chapter 3 makes
  the state the caller's own data — a shop's `Ticket` with its customer, order and refund policy —
  and every later chapter works on the same three tickets.
- **Examples**: 12 source files in `live/scala3` and 13 in `live/scala213`, and a
  `guide.RouterSuite` in each, which runs without the key. The docs check compares the 58 quoted
  blocks with them ([ADR-0032](../adr/0032-documentation-quotes-compiled-examples.md)).

#### Found

- **`Described` in the 2.13 module was read by nothing.** Its Scaladoc said it gives each option
  a description that Jev reads, but 2.13 has no derivation, and no code in the module looked at
  it: a description reached Jev only through `ChoiceOption`. The owner chose to remove it rather
  than add a helper that reads it, so 2.13 does not grow an API that Scala 3 does not have. In
  2.13 a description is the third argument of `ChoiceOption`, as the tutorial shows.
- **A `Score(...)` in Scala 3 has the type `Question[ScoreAnswer]`**, like every `enum` case
  built without `new`, so its `levels` cannot be read back. The tutorial keeps a JSON level in a
  `val` to look up its probability.
- **The errors of `Probability(...)` come after typing**: a file with a type error and an
  out-of-range `Probability` reports only the type error. Alone, `Probability(-1.0)` and
  `Probability(1.5)` fail with *"a Probability must be between 0 and 1"*.
- The invalid-request example makes no call, and prints the same line in both modules:
  `Left(score 'feeling' needs 2 to 10 levels, got 1; score 'risk' uses the level 'Low' more than
  once)`.
- **Every example that calls the API ran against `jev-1.13.0`**, with the owner's OK: 10 calls for
  each module, all answered, with the same decisions in both modules — for example a refund at
  once for the duplicate charge and not for the wrong size. The README and the tutorials show the
  output of that run, marked as such. The numbers differ a little between runs of the same
  question (a Score of 1.53 and 1.52), so the tutorials say that the outputs are examples.
- **The first version of the examples gave the state too little weight**: a one-line string in
  the README and in most chapters, and a different toy case in each chapter. The owner's review
  found it; the examples were rewritten around one domain, and the concepts guide's section on
  the state now shows paths into the state, one state for many questions, and what to leave out.

---

The items below were planned on 2026-09-23, after a review of the code at the end of T6. Each
ADR they cite is `Proposed`: the branch that implements the item makes it `Accepted`, sets its
`Amends` header, and records the amendment in the ADR it amends. The same review re-ran the
whole gate on `deb86ae`, on JDK 21: 111 and 100 tests, 100% statement and branch coverage, 186
and 178 mutants all detected, the docs check clean — the numbers T5 and T6 recorded.

### T7 — A Score typed by the caller's enum

**Status:** Done 2026-09-24 — [ADR-0034](../adr/0034-a-score-typed-by-the-callers-enum.md)

**Branch:** `task/typed-score`

A `Score[Mood]("…")` whose levels come from an enum that `derives JevScale`, answering a
`ScoreAnswer[Mood]` with `mostLikely` and `probabilities(Mood.Angry)`. The text form,
`Score("…", "Calm", "Angry")`, keeps compiling as a `Score[ujson.Value]`. Scala 3 first, then
2.13 with a `JevScale` written by hand.

To settle while implementing: `mostLikely` when two levels share the highest probability
(proposed: the first in the order of the scale).

**Done when:** both forms work in both modules; one case and eleven cases are compile errors in
Scala 3, and `Score("How?")` gives the message of ADR-0034; the golden tests pass with `golden/`
unchanged; the README, the guides and `live/` use the new types; the definition of done holds.

#### Found

- **The tie rule is confirmed**: the owner accepted ADR-0034 on 2026-09-24 with `mostLikely` as
  the lower of two equal levels. `maxByOption` over the levels in the order of the scale keeps
  the first of equal maxima, so the Codec needs no rule of its own; `CodecSuite` tests it with the
  keys of `probabilities` in both orders.
- **A Score answer with an empty `probabilities` is now a `Decoding` error**, `'q': no
  probabilities`: there is no level to give as `mostLikely`. Before, it decoded to an empty map.
  No real reply has been seen without probabilities.
- **In 2.13 the typed form is `Score.of[L]`**, as `Choice.of[C]` is. A second public `apply` is
  not possible: the case class's own `apply(instructions, List[ScaleLevel[L]])` and the text
  `apply(instructions, List[ujson.Value])` have the same erasure, and even with a
  `DummyImplicit` to tell them apart, `Score("How?", List("Calm", "Angry"))` stops compiling,
  because Scala 2 types an argument of an overloaded method without its expected type and
  `List[String]` is not a `List[ujson.Value]`. The companion therefore defines the case class's
  `apply` itself, `private`, which hides it from callers, and the text `apply` keeps a
  `DummyImplicit` for the erasure. The constructor is private too. Checked on Scala 2.13.18 with
  `-Xlint -Werror`.
- **Scala 3 also has `JevScale(levels*)`, levels written by hand**, as `JevChoice(options*)` is,
  for a type that is not an enum. Its level count is checked by the `Validator`, as for the text
  form.
- **The level count needs no `inline`**, as ADR-0034 expected: `JevScale.LevelCount[N]` has one
  given, which needs `(N >= 2 && N <= 10) =:= true`. Its `@implicitNotFound` gives the first line
  of the error; the compiler adds the failed search after it.
- **`JevChoice.Cases` is shared, so its message names both type classes**: "JevChoice and
  JevScale can be derived only for an enum whose cases have no parameters; …". ADR-0022 quotes
  the old text, which named only `JevChoice`.
- **The recorded outputs in the README and the guides stay true.** The enums `Feeling` use the
  same names as the text levels they replace, so the requests are the same as the recorded runs,
  and the examples still print `score` and the probability of `Angry`. They do not print
  `mostLikely`: that would need a new paid run.
- **`docs/guide/concepts.md` said to round the score to the nearest level** when code needs one
  level: the trap ADR-0034 names. It now points to `mostLikely`.
- Measured: `scala3` 123 tests and `scala213` 106, 100% statement and branch coverage in both;
  Stryker4s detects every mutant, 174 of 188 in `scala3` (12 ignored, 2 compile errors) and 179 of
  180 in `scala213` (1 ignored).

### T8 — A reply without input tokens still answers

**Status:** Done 2026-09-24 — [ADR-0035](../adr/0035-a-reply-without-input-tokens-still-answers.md)

**Branch:** `task/tolerant-codec`

`Reply(model, inputTokens: Option[Long])`; a reply without `usage` or `input_tokens` decodes its
answers. `model` stays required. Follows T7, which also changes the codec.

**Done when:** a reply without `usage` gives `Right` and a `Replied` with no token count, in both
modules, tested with JSON written by hand; the definition of done holds.

#### Found

- **The shape is `Option[Long]`.** ADR-0035 left `Option[Long]` or a type of its own open; absence
  is the only case, so `Option` needs no new name. The owner accepted ADR-0035 on 2026-09-24.
- **Every way to lose the count gives `None`, never an error**: no `usage`, a `usage` that is not
  an object, a `usage` without `input_tokens`, and an `input_tokens` that is `3.5` or `"3"`.
  `CodecSuite` tests each, and `0` stays `Some(0)`. The Codec's error `'input_tokens': expected a
  whole number` is gone with them.
- **The examples in `live/` read the count as an `Option`**: the tutorials' `onEvent` adds it with
  `foreach`, and the example of each module logs `unknown` when it is missing. Both tutorials and
  `concepts.md` say that `inputTokens` can be `None`.
- Measured: `scala3` 124 tests and `scala213` 107, 100% statement and branch coverage in both;
  Stryker4s detects every mutant, 172 of 186 in `scala3` (12 ignored, 2 compile errors in
  `RetryPolicy`, as before) and 177 of 178 in `scala213` (1 ignored).

### T9 — The 2.13 module on Scala 2.13.16

**Status:** Done 2026-09-24 — [ADR-0036](../adr/0036-the-2-13-module-compiles-with-spark-4s-scala.md)

**Branch:** `task/scala-2-13-16`

Compile `scala213` and `scala213Live` with Scala 2.13.16, the Scala of Spark 4.0. Check first
that the scoverage plugin, Stryker4s and munit exist for 2.13.16. Update the README,
`docs/guide/scala213.md` and `AGENTS.md`.

**Done when:** the definition of done holds on 2.13.16, and a new project on Scala 2.13.16 runs
an example against a `publishLocal` build.

#### Found

- **The owner accepted ADR-0036 on 2026-09-24.** `scala213` sets `scalaVersion := "2.13.16"`, and
  `scala213Live` follows it.
- **The tools exist for 2.13.16, but two of them are built with 2.13.18.** sbt-scoverage 2.4.4
  uses the compiler plugin 2.5.2, which is published for 2.13.16. munit and the Stryker4s runner
  are published per binary version, `_2.13`, but munit 1.3.6 and `stryker4s-testrunner` 1.1.1
  depend on `scala-library` 2.13.18 (their poms, read 2026-09-24). munit 1.2.0 is the last on
  2.13.16; 1.2.1 needs 2.13.17.
- **sbt 1.13 checks the compile classpath only (SIP-51).** When a dependency of the main code
  needs a newer `scala-library` than `scalaVersion`, the build stops with "Expected
  `scalaVersion` to be 2.13.18 or later". The test classpath is not checked: with munit 1.3.6
  the 2.13 tests compile with 2.13.16 and run on `scala-library` 2.13.18. The main code compiles
  against 2.13.16, which is what ADR-0036 needs, so munit stays at 1.3.6 in both modules. munit
  1.2.0 also passes the 107 tests, on 2.13.16, if the tests must run on that library too.
- **Stryker4s puts its runner on the compile classpath, and the check stops it.** CI and
  `AGENTS.md` run the mutation testing with `set scala213 / allowUnsafeScalaLibUpgrade := true`,
  which turns the error into a warning for that run. It is not in `build.sbt`, where it would
  also turn off the check for the published module.
- **The published pom asks for `scala-library` 2.13.16**, where it asked for 2.13.18. A new sbt
  project on 2.13.16, outside the repository, compiled the README's 2.13 example unchanged
  against a `publishLocal` build, with `-Werror -Xlint`, and ran its questions over a fake
  transport: `scala-library` 2.13.16 at run time, and the expected answers. No paid call. The
  same project on 2.13.18 runs, and on 2.13.15 sbt refuses it with "Expected `scalaVersion` to be
  2.13.16 or later": the refusal an sbt project on 2.13.16 got before this task, when the pom
  asked for 2.13.18.
- Measured on 2.13.16: `scala213` 107 tests, 100% statement and branch coverage; Stryker4s
  detects 177 of 178 mutants (1 ignored), as on 2.13.18.

### T10 — Typed tuples from the 2.13 client

**Status:** Done 2026-09-24 — [ADR-0037](../adr/0037-the-2-13-client-answers-with-a-typed-tuple.md)

**Branch:** `task/fixed-arity-ask`

`ask(state, k1, …, kN): Either[JevError, (A1, …, AN)]`, and a dynamic `askAll(state, keys*)`.
Blocked by T9, so that a change of Scala patch and a change of API are not tested together.

To settle while implementing: N (proposed: 10), overloads by hand or generated, and what
`askAll` returns.

**Done when:** the 2.13 README reads its answers with no `Option`; every overload has a test; the
definition of done holds.

#### Found

- **The owner accepted ADR-0037 on 2026-09-24**, with N = 10 and the overloads written by hand,
  and changed the dynamic form: `askMap(state, Map[String, Question[_]])` returning
  `Map[String, Answer]`, the name and type of Scala 3, not `askAll`.
- **One key returns the answer, not a `Tuple1`.** `ask(state, urgent)` is an
  `Either[JevError, NoulAnswer]`; `(A1)` in the ADR's notation is `A1` in Scala.
- **ADR-0028 is superseded, not amended.** Its whole decision was the comparison in
  `Answers.get`, which is gone with `Answers`. ADR-0023 is amended: its `Answers.get` and its
  "no `askMap`".
- **The overloads resolve by arity alone** on 2.13.16 with `-Werror -Xlint`, rechecked in a
  project outside the repository. 11 keys do not compile, as an overloaded `ask` with no
  alternative that fits; a test checks it.
- **A test reads every position of every overload**: ten Nouls answered with the probabilities
  0.01 to 0.10, so a swapped position shows as a wrong number. Stryker4s makes no mutant of an
  index.
- **The guide's printed outputs lost their `Some(...)`**: chapters 3, 4 and 8 printed `Option`s,
  and now print the values. The values are those of the runs of 2026-09-22; the examples were not
  run again against the real API.
- **The three examples ran again against the real API on 2026-09-25**, with `jev-1.13.0`, and
  printed the values that the guide now shows. `State` and `Runtime` printed the same lines as on
  2026-09-22; `ThreeQuestions` printed a feeling of 1.51 and an angry probability of 0.51, where
  the run of 2026-09-22 printed 1.52 and 0.52.
- Measured: `scala213` 108 tests, 100% statement and branch coverage; Stryker4s detects 170 of
  171 mutants (1 ignored), where it counted 178 with `Answers`.

### T11 — The caller's own HTTP client

**Status:** Done 2026-09-24 — [ADR-0038](../adr/0038-the-caller-may-pass-its-own-http-client.md)

**Branch:** `task/caller-http-client`

An optional `HttpClient` parameter on `JdkTransport` and on the client's constructor, never in
`JevConfig`.

**Done when:** a client built with an outside `HttpClient` uses it, tested against the local
server; the Scaladoc states the connect timeout and who closes the client; the definition of
done holds.

#### Found

- **The owner accepted ADR-0038 on 2026-09-24**, with `httpClient: Option[HttpClient] = None`,
  both modules in one task, and a test through a client that counts its requests. Its Context now
  says what the parameter adds: control of the client. Sharing one was already possible, with one
  `JdkTransport` passed to many clients through `JevClient.withTransport`.
- **`HttpClient` is an abstract class, so a test can wrap a real one.** `CountingHttpClient`, in
  the tests of each module, delegates every method and counts `send`. Through it, a 503 and a
  200 count 2 requests: the retries go through the caller's client too.
- **The request timeout still applies to a caller's client**: `JevConfig.timeout` is set on each
  `HttpRequest`, so a server that never answers gives "no response within 200 milliseconds" with
  a client that has no connect timeout at all.
- Measured: `scala3` 127 tests, `scala213` 111; 100% statement and branch coverage in both;
  Stryker4s detects every mutant, 172 of 186 in `scala3` (12 ignored, 2 compile errors) and 170
  of 171 in `scala213` (1 ignored), as before: the new code adds no mutant.

### T12 — Scaladoc fixes

**Status:** Done

**Branch:** `docs/scaladoc-fixes`

1. `Sleeper.thread` says a sleep is cheap on a virtual thread; JDK 17, which the library
   supports, has none.
2. `Question`: its `ujson.Value`s are mutable. Changing one after the question is built changes
   the question, and the keys of a text Score's `probabilities`. An enum case cannot copy its
   arguments, so the Scaladoc is the fix.
3. `JevConfig`: the `https` check of [ADR-0027](../adr/0027-the-api-key-is-a-type-and-travels-over-tls.md)
   runs in `fromEnv` only; a config built by hand is not checked.

**Done when:** the three points are in both modules and `sbt doc` passes.

#### Found

- **Point 2 breaks the lookup, not the keys, and only from five levels.** The Codec uses the
  Score's own level values as the keys of `probabilities`, so a key is the same object as its
  level and still equals it after a change. What breaks is the hash: with a `ujson.Obj` level
  changed after the reply, ujson 4.4.3 on Scala 3.9.0 gives

  | Levels | Map | `probabilities(level)` | `keySet` holds the level |
  |---|---|---|---|
  | 3 | `Map3` | found | yes |
  | 5 | `HashMap` | `None` from `get`, `NoSuchElementException` from `apply` | yes |

  `Map1` to `Map4` compare keys with `equals` and never hash them. The Scaladoc of `Question`
  therefore says that looking up a level *can* fail, not that the keys stop matching. It names
  `ujson.Obj` and `ujson.Arr` as the values that can change; `String` and `ujson.Str` cannot.
- **`docs/guide/concepts.md` said the same as point 3 without the exception**: "The base URL must
  use `https`" read as true of every config. It now says that `fromEnv` checks it and a config
  built by hand is not checked.

### T13 — A guide section on gateways

**Status:** Done 2026-09-24

**Branch:** `docs/gateways`

A section in `docs/guide/concepts.md` on reaching Jev through a gateway. On 2026-09-23, with a
local server in place of the gateway, the client called `POST /typesafe/v1/systemone` for a base
URL ending in `/typesafe`, as the official SDKs do, and accepted a reply whose `model` was
`typesafe-ai/jev`. What the section says, each with its source read again when writing:

- OpenRouter works with `fromEnv`: `TYPESAFE_BASE_URL=https://openrouter.ai/api` and the
  OpenRouter key in `TYPESAFE_API_KEY`.
- Vercel AI Gateway uses `https://ai-gateway.vercel.sh/typesafe` and a key in
  `AI_GATEWAY_API_KEY`, which neither `fromEnv` nor the official SDKs read: the config is built
  by hand, with `https`.

**Done when:** the section's examples are quoted from `live/` and the docs check passes.

#### Found

- **The sources, read 2026-09-24:** OpenRouter's page on the TypeSafe SDK and Vercel's page on the
  TypeSafe API with AI Gateway (last updated 2026-09-21). Both serve TypeSafe's request and reply
  at `<base URL>/v1/systemone`, and both confirm the base URLs and keys above.
- **OpenRouter names the model without the patch number.** Its page lists `jev-1.13` (routed as
  `typesafe/jev-1.13`) and `jev-latest`, and says nothing of `jev-1.13.0`, the name the guides use
  with TypeSafe; its reply names `typesafe/jev-1.13-20260917`. The guide's OpenRouter example uses
  `jev-1.13`. Vercel takes and returns `typesafe-ai/jev`.
- **Both gateways add fields to the reply**: `id`, `provider` and `usage.cost` on OpenRouter,
  `provider_metadata` on Vercel. The codec ignored them already; a test in each module's
  `CodecSuite` now decodes both documented replies.
- **Vercel's errors are `{"message", "error_type"}`**, not TypeSafe's `detail`, so their message
  comes back as the body, cut to 200 characters. The guide says so.
- The section is chapter 10 of `docs/guide/concepts.md`; its examples are in `guide/Gateways.scala`
  in both live projects. The OpenRouter line is the same in both versions and quoted once.
- Measured: `scala3` 128 tests, `scala213` 112; no main code changed, so coverage and mutants are
  as T11 recorded. No paid call.

### T14 — A guide chapter on many requests

**Status:** Done 2026-09-25 — [ADR-0039](../adr/0039-the-library-does-not-limit-the-request-rate.md)

**Branch:** `docs/parallel-requests`

A chapter in each tutorial: a small fixed thread pool, a pacer whose rate is a parameter, and
what to do with the `RateLimited` errors that remain. More threads do not give more answers
above the account's limit; ADR-0039 has the measurement. No fixed rate is given as if it were
stable.

**Done when:** the chapters are in both tutorials; their examples compile on JDK 17 and 21; a
test runs the pacer against a local server without the API key.

#### Found

- **The owner accepted ADR-0039** with no limiter in the library. Its Context was rewritten first:
  TypeSafe's Models page, read directly on 2026-09-24, publishes 1,200 requests per minute and
  250,000 input tokens per second, and says they move and are higher on custom and enterprise
  plans. The Forces now add that a limit is per account while a limiter is per process, and name
  the alternative, a library that ships one at 90% of the published limits.
- **Jev sends no rate-limit header (T15)**, so the chapter says the caller chooses the rate, and
  quotes the published limits with their date as a place to start.
- **The pacer is about ten lines**: one `AtomicLong` holds the next free start time, and
  `getAndAccumulate` gives each caller its own slot without a lock.
- **The test is a local server that answers 429 above 20 requests in any second.** Without
  pacing, 30 messages on 8 threads get at least one `RateLimited`; at 15 per second, all 30 are
  answered and the run takes at least 29/15 s. With the pacer's gap set to 0 by hand, the second
  test fails. Ten runs per module passed.
- **CI now runs the guides' tests** (`testOnly guide.*` in each live project), which it only
  compiled before: `RouterSuite` runs too. `LiveSuite`, which calls the paid API, still never
  runs in CI.
- Chapter 13 in both tutorials; chapter 10 points to it; the concepts guide's limits give the
  published numbers with their date. No paid call.

### T15 — Every header of a real reply

**Status:** Done 2026-09-24

**Branch:** `task/capture-all-headers`

`build/capture-golden.py` keeps only `content-type` and `retry-after`. Keep every response
header and the response time, then make one real call: about 300 input tokens, about
$0.00001. Record any rate-limit header and the measured latency here, for T14.

**Done when:** the headers and the latency are recorded with the date and the model.

#### Found

- **The owner approved one paid call on 2026-09-24**: `golden/noul` recorded again with
  `jev-1.13.0`, 289 input tokens, the same answer as on 2026-09-22 (0.98).
- **Jev sends no rate-limit header.** The reply's headers were `connection`, `content-length`,
  `content-type`, `date`, `server` (`istio-envoy`), `x-envoy-upstream-service-time` and
  `x-typesafe-request-id`. Nothing tells a caller how close it is to the limit, so T14's pacer
  cannot read its rate from the reply: the caller sets it.
- **Latency:** 473 ms from sending to the whole body, measured from this build environment through
  its proxy, a new TLS connection included; `x-envoy-upstream-service-time` said 66 ms. One call
  is one sample, not a distribution.
- **Each reply has an `x-typesafe-request-id`**, such as `req_01a0…`. jev4s drops it today; it
  could be what TypeSafe's support asks for. Not in scope here: a candidate for a later task.
- `build/capture-golden.py` now keeps every header and `elapsed_ms` in `meta.json`, and
  `--only <case>` records one case alone; without it, `--force` would make all nine calls.

### T16 — The request id of each reply

**Status:** Done 2026-09-25 — [ADR-0040](../adr/0040-the-request-id-of-each-response-is-an-event.md)

**Branch:** `task/request-id`

Every reply of Jev carries an `x-typesafe-request-id` header, such as `req_01a0…` (T15). jev4s
drops it: `Transport` returns only the body of a successful response, and a `JevError` carries
no header. TypeSafe's official SDKs keep it on their errors, as `request_id` in Python and
`requestId` in JavaScript, "the `x-typesafe-request-id` response header, or `None` if absent"
(SDK references, read 2026-09-25). It is what a caller would give TypeSafe's support about one
call.

The ADR decides, in the Scala 3 module first:

- where the id of a successful reply goes: `Reply`, and so `JevEvent.Replied`, fits ADR-0025;
- where the id of a failed call goes: on the `JevError` values that come from a response, or in
  a `JevEvent`;
- how it crosses `Transport`, which today returns a body and nothing else, without breaking a
  caller's own `Transport`;
- whether a gateway's reply, which may not carry it (T13), gives `None`.

**Done when:** the ADR is accepted; both modules keep the id where it decides, tested against the
local server with and without the header; the guides' chapter on events and errors mentions it;
the definition of done holds.

#### Found

- **An error carries a request id too.** `golden/error-401` was recorded again on 2026-09-25 with
  a made-up key, approved by the owner: the 401 has the same headers as a success, with its own
  `x-typesafe-request-id`. Its body did not change.
- **The owner accepted ADR-0040 on 2026-09-25, the event over the two other forms**: the id on
  each `JevError` (as the SDKs do) and the id on `Reply` through a second `Transport` method. The
  event changes no existing type and gives the id of every attempt; the cost is that a caller
  links it to the error through the order of the events.
- **`JdkTransport` sends `Responded` for every HTTP response**, before it decides to retry or to
  return, and none for a request that got no response. The `try` now covers only
  `client.send`, so an `IOException` thrown by `onEvent` is not taken for a network error.
- **The guides show how to keep the id with the error**: `onEvent` stores the latest id in a
  `ThreadLocal`, and `askOrReport` clears it before the call and adds it to the error after.
- Measured: `scala3` 130 tests, `scala213` 114; 100% statement and branch coverage in both;
  Stryker4s detects every mutant, 173 of 187 in `scala3` (12 ignored, 2 compile errors, as since
  T7) and 171 of 172 in `scala213` (1 ignored). The one new mutant in each module is the header
  name, killed by the tests with and without the header.

### T17 — A review before the first release

**Status:** Done 2026-09-25 — [ADR-0042](../adr/0042-a-config-java-net-http-refuses-is-an-error.md)

**Branch:** `task/pre-release-review`

Before M7 publishes `0.1.0`, a review of the code of both modules, of the README and the guides,
and of the examples in `live/` that they quote.

**Done when:** each point found is fixed, or the owner decided to leave it; the definition of
done holds.

#### Found

Checked on 2026-09-25, on `dev` at fe14ec4, JDK 21, no call to the real API:

- `sbt test`: `scala3` 130 tests, `scala213` 114, no warning. Scaladoc builds in both modules;
  the Scala 3 Scaladoc prints its own warning, "Option -classpath was updated", which
  `-Werror` does not cover. The live projects compile, the guides' tests and the four Spark tests
  pass, the formatting check passes in the five projects, and `build/check-docs.py` finds no
  problem in 41 ADRs, 57 Markdown files and 74 quoted examples.

Found in the code, and fixed on this branch, as the owner asked (2026-09-25):

1. **The API key in an exception message.** `ApiKey("abc\n")`, for example a key read from a
   file with its final newline, made `ask` throw `IllegalArgumentException: invalid header
   value: "Bearer abc…"` from `java.net.http`, with the whole key in the message. It broke
   [ADR-0027](../adr/0027-the-api-key-is-a-type-and-travels-over-tls.md) (the key never in a log)
   and [ADR-0002](../adr/0002-direct-style-no-effect-system.md) (no exceptions).
   `JevConfig.fromEnv` trims the key, so only a key given by hand did it.
2. **A config built by hand could make the client throw.** Measured, each with
   `IllegalArgumentException` from `java.net.http`: a base URL with no scheme
   (`URI.create("api.typesafe.ai")`) or with a scheme other than `http` and `https`, on `ask`;
   a `timeout` of zero or less, when the client was built. The README, the concepts guide and both
   tutorials say that jev4s does not throw.

   Points 1 and 2 are [ADR-0042](../adr/0042-a-config-java-net-http-refuses-is-an-error.md):
   `JdkTransport` checks the JDK's own rules, measured on JDK 21 (a scheme `http` or `https` in
   any case, a host, a positive timeout, header characters from a tab and from space to `\u00ff`
   except `\u007f`), builds its `HttpClient` only when it first sends, and returns the new
   `JevError.InvalidConfig` without sending. Tests check each boundary character, that nothing
   is sent and no event goes out, that the key is not in the message, and that
   `JevClient(config)` with a zero timeout does not throw.
3. **Two options of a Choice, or two levels of a Score, with the same value.** The `Validator`
   checked keys and level texts, not values. With `ChoiceOption(1, "a")` and
   `ChoiceOption(1, "b")` the request was sent, and `probabilities` kept one of the two. Two new
   problems, `DuplicateOptionValue` and `DuplicateLevelValue`, report it. A value repeated
   together with its key or text is reported once, by the problem that already existed, so
   `Score("How?", "Low", "High", "Low")` still gives one `DuplicateLevel`.

- Measured after the fixes: `scala3` 139 tests, `scala213` 123; 100% statement and branch
  coverage in both; Stryker4s detects every mutant, 205 of 220 in `scala3` (12 ignored as
  before, 3 compile errors: the new `timeout > Duration.Zero` mutated to `==` does not compile
  under `strictEquality`, as the two of `RetryPolicy` since T7) and 204 of 205 in `scala213`
  (1 ignored).

Found in the documentation, and fixed on this branch:

- `CONTRIBUTING.md` gave the formatting command without `scala213Spark/scalafmtAll`, which CI
  checks.
- The Scaladoc of the Scala 3 `Example.scala` was on `enum Dept`, not on `example`.
- The Scala 3 tutorial, chapter 8, called the answer `ScoreAnswer` next to `ChoiceAnswer[?]`;
  it is `ScoreAnswer[?]`.
- A Scaladoc line of `JdkTransport` broke in the middle of a sentence, in both modules.

- **The owner accepted ADR-0042 on 2026-09-25.**
- **The README's two examples and the seven examples of the Scala 3 tutorial ran again against
  the real API on 2026-09-25** (approved by the owner, eleven calls, `jev-1.13.0`), and the
  documents now show those outputs. Four values moved: the README's Scala 3 feeling 1.82 to
  1.78; the first question's probability 0.92 to 0.91; `threeQuestions` feeling 1.53 to 1.46
  and angry 0.53 to 0.46; the priority of `decisions` 0.91 to 0.90. The other five printed the
  same lines as on 2026-09-22. The 2.13 tutorial's chapters 2, 5, 6 and 7 still show the runs
  of 2026-09-22.

Left for M7, not defects: no CHANGELOG yet; `build.sbt` has no `scmInfo`, `developers` or
`versionScheme`; the README and the guides say that jev4s is not on Maven Central and use
`0.1.0-SNAPSHOT`.

---

The items below were planned on 2026-09-25, after a review of `dev` at `0b462c5` and of what
changed outside the repository since T13. The same review re-ran the tests with coverage on a
copy of `0b462c5`, on JDK 21, with no key: `scala3` 139 tests and `scala213` 123, 100% statement
and branch coverage in both, as T17 recorded. Mutation testing was not re-run.

### T18 — Library fixes, and 2.13 options and levels without repeated names

**Status:** Done 2026-09-25 — [ADR-0043](../adr/0043-2-13-options-and-levels-named-after-their-case-objects.md), [ADR-0045](../adr/0045-a-retry-policy-whose-wait-can-be-negative-is-an-error.md)

**Branch:** `docs/plan-t18-t19`, with T18 to T28, as the owner asked.

Every change to the two published modules that the review planned, on one branch, so that the
definition of done — coverage, mutants, both modules — runs once. T19, the documentation, uses
what this task adds.

**Fixes**

1. **A `RetryPolicy` can make a call throw.** `delayFor` returns a negative delay when `jitter`
   is above 1 or `backoffInitial` is negative, and `Sleeper.thread` passes it to `Thread.sleep`.
   Measured on `0b462c5`, Scala 3, a local server answering 503: `RetryPolicy(jitter = 2.0)` and
   `RetryPolicy(backoffInitial = FiniteDuration(-1, SECONDS))` make `JdkTransport.send` throw
   `IllegalArgumentException: timeout value is negative`. `maxRetries = -1` returns the first
   error, and `jitter = Double.NaN` retries with no wait: neither throws. This is the class of
   defect ADR-0042 closed for `JevConfig`, whose check does not look at `retry`. The Python SDK
   0.7.1 refuses the same values when its `RetryConfig` is built: a negative or non-finite
   backoff, and a jitter outside [0, 1] (`_core/retry.py`, read 2026-09-25).
   To settle: whether the check joins `JdkTransport.problems`, as `JevError.InvalidConfig`
   (which amends ADR-0042, since the JDK method is `Thread.sleep`, not `java.net.http`), or
   whether `delayFor` clamps the delay at zero.
2. **The Scaladoc of `RetryPolicy` says the SDKs have no retry budget.** "`maxElapsed` limits the
   time a call spends retrying, 30 s by default, which the SDKs do not limit", in both modules.
   The Python SDK 0.7.1 has one, `timeout: float | None = 30.0`, "Total retry budget in seconds
   per SDK call" (`_core/retry.py`, read 2026-09-25); the JavaScript SDK has none. ADR-0030
   already says so. The Scaladoc should name the JavaScript SDK.

**Ergonomics**

3. **2.13 options and levels named after their case objects (ADR-0043).** Spark 4 runs Scala 2.13
   only, so the users of the Spark example write the 2.13 module, and its ergonomics count as
   much as those of Scala 3. In Scala 3 an enum `derives JevChoice` or `derives JevScale`; in
   2.13 each case is written three times, in the README and in `live/scala213`:

   ```scala
   JevScale(ScaleLevel(Calm, "Calm"), ScaleLevel(Annoyed, "Annoyed"), ScaleLevel(Angry, "Angry"))
   JevChoice(ChoiceOption(Billing, "billing"), ChoiceOption(Technical, "technical"), ChoiceOption(Sales, "sales"))
   ```

   A `case object` knows its own name, `productPrefix`, so the caller lists the cases once, in
   the order of the scale:

   ```scala
   implicit val levels: JevScale[Feeling]  = JevScale.named(Calm, Annoyed, Angry)
   implicit val choices: JevChoice[Team]   = JevChoice.named(Billing, Technical, Sales)
   ```

   Checked on 2026-09-25 in the 2.13 module's tests, compiled with 2.13.16 and `-Xlint -Werror`:
   `productPrefix` gives `Calm` and `VeryAngry` for case objects nested in the companion object
   of a sealed class. For a case class it gives the class name alone (`Other` for `Other(1)`), so
   two instances share a text; the `Validator` already reports that as `DuplicateLevel` or
   `DuplicateOptionKey`. The rules are Scala 3's: the key in snake_case (`TechnicalSupport` →
   `technical_support`), the level text as the case's name, a description from `Described`.
   Settled by the owner on 2026-09-25: the name is `named`; the bound is `L <: Product` (changed
   by T28 to `(L with Product)*`, which accepts a sealed trait), not `toString` nor a type class;
   a `Described` trait in the 2.13 module gives descriptions; the change has an ADR, because it
   adds 2.13 API. This branch makes ADR-0043 `Accepted` and records
   the amendment in ADR-0023 and ADR-0034.
4. **A Choice over keys known only at runtime, in one call.** The tutorials' chapter 8 writes
   `Choice[String]("…")(using JevChoice.keys(items*))` in Scala 3 and
   `Choice.of[String]("…")(JevChoice.keys(items: _*))` in 2.13, the one awkward call of the
   guides. `Choice.keys("Which item?", items*)`, returning a `Choice[String]`, says the same in
   one call, in both modules, Scala 3 first. No ADR expected: it is `JevChoice.keys` in the shape
   of the other constructors; if the implementation meets a real choice, write one.

Out of scope, noted for later: a threshold on `NoulAnswer`, next to `ChoiceAnswer.ifConfident`
(`typesafe-sdk-scala` has `isYes(0.8)`).

**Done when:** each point is fixed, or the owner decided to leave it; `live/scala213` lists each
case once; `named` and `Choice.keys` are tested, a case class included for `named`; the
definition of done holds in both modules.

**Outcome.** Done on the working tree of `docs/plan-t18-t19`, together with T19, at the owner's
request.

1. **Settled by the owner on 2026-09-25: the check joins `JdkTransport.problems`**, as
   `JevError.InvalidConfig` ([ADR-0045](../adr/0045-a-retry-policy-whose-wait-can-be-negative-is-an-error.md),
   which widens ADR-0042). `backoffInitial` and `backoffMax` must be zero or more, and `jitter`
   between 0 and 1, both included, as in the Python SDK; `NaN` is refused. A negative
   `backoffMax` throws too, and was not in the list above: `min(doubled, backoffMax)` is then
   negative. Tests in both modules: each value against a local server answering 503, with the
   default sleeper, returns `InvalidConfig` and sends nothing; and each boundary of each rule.
   The Scaladoc of `RetryPolicy`, `JevConfig`, `JevError.InvalidConfig` and `JdkTransport` says
   so.
2. The Scaladoc of `RetryPolicy` names the Python SDK's 30 s budget and the JavaScript SDK's lack
   of one, in both modules.
3. `JevChoice.named`, `JevScale.named` and `Described` in the 2.13 module, as ADR-0043 decides;
   the snake_case function is written again, with Scala 3's tests. The owner's decisions are
   recorded in ADR-0043, now `Accepted`, which amends ADR-0023 and ADR-0034. Tested with case
   objects, `Described` case objects, and a case class whose two instances share a name, which
   the `Validator` reports as `DuplicateOptionKey` and `DuplicateLevel`. `live/scala213`, the
   README and the 2.13 tutorial list each case once; chapter 5 of the tutorial gives its
   descriptions with `Described`, as the Scala 3 tutorial does.
4. `Choice.keys(instructions, keys*)` in both modules, a `Choice[String]`; chapter 8 of both
   tutorials uses it. No ADR: it is `JevChoice.keys` in the shape of `Choice`.

- Measured after the changes, JDK 25: `scala3` 142 tests, `scala213` 132; 100% statement and
  branch coverage in both; Stryker4s detects every mutant, 224 of 236 in `scala3` (12 ignored,
  5 compile errors) and 224 of 225 in `scala213` (1 ignored).

### T19 — Documentation and examples: Spark 4, the Scala 3 compiler, and real outputs

**Status:** Done 2026-09-25 — [ADR-0044](../adr/0044-documented-compile-errors-are-checked-by-a-test.md), [ADR-0046](../adr/0046-the-spark-example-holds-one-client-per-executor-jvm.md)

**Branch:** `docs/plan-t18-t19`, with T18 to T28, as the owner asked.

The owner asked on 2026-09-25 for a README that stresses two things, each with an example a
reader takes in at a glance: the Spark compatibility of the 2.13 module, and the robustness of
the Scala 3 module. The same review found gaps in the guides and the examples. All of it is on
one branch, and its paid calls are one run at the end, approved once.

**The README.** Today it opens with the Scala 3 example and then a 2.13 example of about 60
lines. Spark appears only in a sentence of "Run it" and a row of "Learn more"; what the compiler
refuses appears only in prose ("`Probability(1.5)` does not compile"). The new order:

1. **Two claims, each linked to its section.** "Scala 3: the compiler checks your questions and
   your answers." "Scala 2.13: the only Scala client for Jev that runs on Spark 4" (point 7).
2. **The Scala 3 example**, as today, printing `mostLikely` next to `score`: the text under it
   names `r.feeling.mostLikely`, and T7 added the field so that code does not round the score.
3. **What the compiler refuses**: a block of lines against the example's questions, each with the
   compiler's message, checked as ADR-0044 decides. Measured on 2026-09-25 on `0b462c5`, Scala
   3.9.0, with the README's `Team`, `Feeling` and three questions:

   | Line | The error, with `…` for what is left out |
   |---|---|
   | `r.priority` | `value priority is not a member of (team : ChoiceAnswer[Team], duplicate : NoulAnswer, feeling : …)` (fully qualified names) |
   | `(r.team.choice: Feeling)` | `Found: … Team` / `Required: Feeling` |
   | `r.feeling.probabilities("Calm")` | `Found: ("Calm" : String)` / `Required: Feeling` |
   | `Probability(1.5)` | `a Probability must be between 0 and 1` |
   | an enum of one case that `derives JevScale` | `a Score needs 2 to 10 levels: JevScale can be derived only for an enum of 2 to 10 cases` |
   | `client.ask(42, questions)` | ``no ToState[Int]: give one, for example `given ToState[Int] = s => ujson.Obj(...)`, or pass a String or a ujson.Value`` |
   | `(team = …, count = 3)` as the questions | `every value in the named tuple must be a question: Noul, Score or Choice` |

   Then one sentence for what no compiler can see: a request is checked before it is sent, with
   every problem at once, and no call throws.
4. **On Spark 4**: the core of the Spark example, about ten lines quoted from `live/spark` after
   point 15, and three facts with their source: the published pom asks for `scala-library`
   2.13.16, so the module runs on Spark 4.0, 4.1 and 4.2 (ADR-0036); each action on the result
   calls Jev again, so write it or cache it once (measured in `docs/guide/spark.md`); the key is
   read on the executors and never travels.
5. **A 2.13 fragment** of a few lines — a key, `ask`, the tuple of answers — with `JevScale.named`
   and `JevChoice.named` (T18), pointing to the 2.13 tutorial for the whole example. For a 2.13
   reader the Spark section is the first example; the README loses about 50 lines.
6. **Run it**: a new user reaches Jev through a gateway, because TypeSafe paused new sign-ups
   on 2026-09-22 (its announcement on X; no reopening found on 2026-09-25), and accounts made
   before keep working. Point to the concepts guide's chapter 10, and say where the pause is
   announced rather than when it ends. Recommend JDK 21 and keep JDK 17 supported: the library's
   own documents say it works better on 21 (a blocked call is cheap on a virtual thread, the
   Scaladoc of `Sleeper.thread`, ADR-0002; only from 21 can a caller close the `HttpClient` it
   passes, ADR-0038), nothing in the library needs 21, and the Spark example passes on both
   (ADR-0041).
7. **Other Scala clients**, brought up to date, checked on 2026-09-25:
   - `typesafe-sdk-scala` by aoprisan (`io.github.aoprisan`), 0.4.0 on Maven Central since
     2026-09-23: Scala 3.3 LTS, no runtime dependency, blocking, `CompletableFuture` and `Future`
     calls with `Either` variants, Cats Effect, fs2, Monix and Ox modules, questions derived from a
     case class, and recorded replies.
   - `hexis` by early-effect: ZIO, cross-built for JVM, Scala.js and Scala Native; not on Maven
     Central.
   - scala-jev-sdk removed its Scala 2.13 cross-build on 2026-09-19, and zio-typesafe-ai has no
     2.13 build: jev4s is the only client with a 2.13 module, and so the only one that runs on
     Spark 4.
   - "One dependency" no longer sets jev4s apart: `typesafe-sdk-scala` has none. The section
     "What jev4s does differently" is rewritten around points 1, 3 and 4.

   Each fact with its source and date, as the section does today.

**The guides.**

8. **The concepts guide's "Checks before sending"** becomes a table of what each module checks
   at compile time and what it checks before sending.
9. **The concepts guide's chapter 10** says that Cloudflare Workers AI serves Jev in another
   shape: `POST /client/v4/accounts/{account_id}/ai/run`, with `"model": "typesafe/jev"` and the
   state and questions inside `input` (Cloudflare's model page, read 2026-09-25). A base URL
   cannot reach it; a `Transport` of the caller's could, but it cannot read the key of a
   `JevConfig`, whose `ApiKey.value` is `private[jev4s]`.
10. **`docs/guide/spark.md` opens with what makes jev4s fit Spark** — the Scala of Spark 4, a
    client built on the executors, errors as rows — before its first chapter.
11. **`mostLikely` in the tutorials.** No guide shows it; only `LiveSuite` reads it. Chapter 6
    of each tutorial matches on it exhaustively, `r.feeling.mostLikely match { case Feeling.Calm
    => …; case Feeling.Annoyed => …; case Feeling.Angry => … }`: a case added to the enum, or to
    the 2.13 sealed class, then shows up at every match that does not handle it.
12. **The caller's `HttpClient` gets a compiled example.** Chapter 10 of each tutorial names
    `httpClient = Some(yours)` in prose only, and no example in `live/` passes one. The example
    uses what JDK 17 has, an executor or a proxy, because CI compiles `live/` on 17; that a
    caller closes it on 21 stays in prose.
13. **The Scala 3 tutorial's compile errors become checked blocks (ADR-0044)**: it says "does not
    compile" in prose four times. The tutorials' § 1 recommend JDK 21, as point 6.
14. **`normalized` divides by the question's levels.** The extension in chapter 6 divides the
    score by `probabilities.size - 1`, the levels in the answer. It gives the right number today:
    the five Score and Choice answers in `golden/` carry every level and option, zero
    probabilities included. The question is the source of the level count, and the guide
    teaches this extension, so it should read it there.

**The examples.**

15. **One client per executor JVM in the Spark example.** `SparkTriage.triage` calls
    `newClient()` once per partition (ADR-0041). Each call builds a `JdkTransport`, and so a
    `java.net.http.HttpClient` with its own threads, which jev4s never closes; the Scaladoc of
    `JevClient` warns against a new client per request for this reason. The usual Spark answer is
    one client per executor JVM, a `lazy val` in an `object`. To settle, in an ADR that amends
    ADR-0041: where the client lives, how the pacer's rate is still shared by the partitions that
    run at once on one executor, and how the tests keep their fake transport and local server.
    Measure, before and after, the threads that the HTTP clients hold on an executor after many
    tasks.
16. **The Vercel example trims the key.** `sys.env.get("AI_GATEWAY_API_KEY")` keeps a final
    newline, which `fromEnv` trims for `TYPESAFE_API_KEY`. Since ADR-0042 it gives `InvalidConfig`
    rather than an exception, but the example should work: `.map(_.trim)`, in both
    `live/*/guide/Gateways.scala`.
17. **`Example.scala` uses `Team`.** Its enum `Dept` is the last example outside the one domain
    that T6 settled.

**Real outputs.** At the end, one run against the real API with `jev-1.13.0` records every output
that changed, and the four that are older than T7: chapters 2, 5, 6 and 7 of
`docs/guide/scala213.md` still show the runs of 2026-09-22. Record which values moved, as T10 and
T17 did. The owner's key and approval are needed: about fifteen calls of a few hundred input
tokens each, under $0.001 in total at $0.042 per million.

Settled by the owner on 2026-09-25: the block of point 3 is checked by a test (ADR-0044: each line
is also a `compileErrors` case in a Scala 3 test suite that reads the document, and
`build/check-docs.py` checks that every line of the block has its case), and this branch makes
ADR-0044 `Accepted` and records the amendment in ADR-0032; the README keeps a 2.13 fragment, not a
whole example. To settle: whether chapter 13 of the tutorials says what JDK 21 changes for its
thread pool, or keeps only the JDK 17 example that ADR-0039 chose.

**Done when:** the README opens on the two claims; the compile errors of the README and the Scala
3 tutorial are checked as ADR-0044 says; the Spark section quotes `live/spark`; the Spark example
holds one client per executor JVM, measured; no recorded output in the README or `docs/guide/`
comes from a run before this task's; the docs check passes; the definition of done holds for
every test this task adds.

**Outcome.** Done on the working tree of `docs/plan-t18-t19`, together with T18, at the owner's
request. The owner accepted ADR-0046 on 2026-09-25, and confirmed the shape of the cases of
ADR-0044 and the rules of ADR-0045 described here.

- **The README** opens on the two claims, each linked to its section. The Scala 3 example keeps
  its questions in a `val` and prints `mostLikely` next to the score. "What the compiler
  refuses" has seven checked lines. "On Spark 4" quotes ten lines of `live/spark` and gives the
  three facts; the `scala-library` 2.13.16 of the pom was checked with `scala213/makePom`. The
  2.13 section is two quoted parts, with `named`. "Run it" names the pause of sign-ups and the
  gateways, and recommends JDK 21 with 17 supported. "Other Scala clients" was checked again on
  2026-09-25: the repository of `typesafe-sdk-scala` is `aoprisan/typesafe-ai-scala-sdk`, and
  0.5.0 reached Maven Central on 2026-09-25 at 08:05, after 0.4.0 on 2026-09-23;
  `rocks.earlyeffect` on Maven Central has no `hexis`.
- **ADR-0044, accepted by the owner's decision above, as built.** The block of documented errors
  is checked by `ReadmeErrorsSuite` and `Scala3GuideErrorsSuite`, in the empty package of the
  `scala3` tests, over `DocumentedErrorsSuite`; the documents reach the tests as
  `documents/README.md` and `documents/scala3.md`, because `golden/README.md` has the same name.
  Found while building it: **a literal passed through an `inline` method to `compileErrors` can
  fail with another message.** `Score("How does the customer feel?")` gave an overload error
  that way, and its `@implicitNotFound` text when `compileErrors` takes the literal directly.
  So each case is `documented("<code>", compileErrors("<code>"))`, and `build/check-docs.py`
  checks that both literals are the same code, and that every entry has its case. A wrong
  message in the README fails the suite (checked by editing one). ADR-0032 is widened by
  ADR-0044.
- **The Scala 3 tutorial**: chapter 3 has a checked block for a state with no `ToState`;
  chapter 4 turns its table into nine checked lines; chapter 6 checks `Probability(1.5)` and
  `Probability(1)`, whose message is `Found: (1 : Int) Required: Double & Singleton`; chapters 4
  and 9 point to the block for an enum of 1 or 11 cases. Both tutorials' § 1 recommend JDK 21.
- **The concepts guide**: "Checks before sending" is a table of eleven mistakes, each with where
  each module finds it. Measured for it: a name used twice in a named tuple is a compile error
  in Scala 3 (`Duplicate tuple element name`), and so is an empty tuple of questions. Chapter
  10 describes Cloudflare Workers AI, from
  `https://developers.cloudflare.com/ai/models/typesafe/jev/`, read on 2026-09-25.
- **`docs/guide/spark.md`** opens with the three reasons, and says why the client lives in an
  `object`, with the numbers below.
- **Chapter 6 of each tutorial** matches exhaustively on `mostLikely` (`reply`), and the helper
  `normalized` took the question and divided by its levels. T20 replaced the helper with a field
  of the answer.
- **Chapter 10 of each tutorial** quotes `throughProxy`, a client over an `HttpClient` with a
  proxy, which compiles on JDK 17. **Chapter 13** says that JDK 21 can give each call a
  virtual thread, as the owner decided on 2026-09-25; the compiled example stays on JDK 17.
- **One client per executor JVM** (point 15): `SparkTriage.client` is a `lazy val`, and `triage`
  takes `client: () => JevClient`. Measured on JDK 21, Spark 4.0.4 in local mode, 48 tickets in
  48 partitions against a local server, counting `HttpClient`s by the JDK's own ids: a client
  per partition built 43 `HttpClient`s, whose 78 threads were alive after the job and gone
  after a GC; the `object` built 1, with 6 threads. A test checks that 12 partitions over an
  `object`'s client start one `HttpClient`. `main`'s function serializes: run without a key,
  the executors fail with `TYPESAFE_API_KEY is not set`, not "Task not serializable". Written
  up as [ADR-0046](../adr/0046-the-spark-example-holds-one-client-per-executor-jvm.md),
  and accepted by the owner: it amends ADR-0041.
- **The Vercel examples trim the key**, and **`Example.scala` uses `Team`** in both modules; the
  Scala 3 one uses the `Team` of `Triage.scala`, since both are in the empty package.
- Spark 4.0.4 does not start on JDK 25 (`UnsupportedOperationException: getSubject is not
  supported`); its tests and the Spark run used JDK 21, as CI does.

**Real outputs.** One run on 2026-09-25 against the real API with `jev-1.13.0`, approved by the
owner, with the key of the local `.env`: every example whose output a document shows, 23 calls,
all answered. Values that moved: the README's Scala 3 feeling 1.78 to 1.8 (the line now also
prints `Angry`); the README's 2.13 feeling 1.8 to 1.81; the Scala 3 first question 0.91 to 0.92;
Scala 3 `threeQuestions` feeling 1.46 to 1.47 and angry 0.46 to 0.48; the 2.13 first question
0.92 to 0.91; 2.13 `ThreeQuestions` feeling 1.51 to 1.52 and angry 0.51 to 0.52. The rest printed
the same values, and `decisions` now also prints `an apology, then the answer` in both modules.
The JSON replies in the concepts guide are API replies quoted from `golden/`, not outputs of the
examples, and were left as they are.

- Measured after T19, JDK 25: `scala3` 161 tests, the 19 documented errors included, and
  `scala213` 132; 100% statement and branch coverage in both; every mutant detected, the same
  236 and 225 as at the end of T18. The guides' tests pass (4 and 4), and the Spark example's 5
  on JDK 21.

### T20 — A Score's answer carries its normalized score

**Status:** Done 2026-09-25 — [ADR-0047](../adr/0047-a-score-answer-carries-its-normalized-score.md)

**Branch:** `docs/plan-t18-t19`, with T18 and T19, as the owner asked.

T19's helper `normalized` needed the question next to the answer: in 2.13 a key does not give
back its `Score`, so chapter 6 made each key in the call only to pass the Score to the helper.
The owner chose on 2026-09-25 to compute it in the library instead, in both modules, rather than
a 2.13 key that keeps its Score.

**Done when:** `ScoreAnswer.normalized` is decoded from the question's levels in both modules,
tested with numbers of its own; the guides use it and teach no helper; the definition of done
holds.

**Outcome.** `ScoreAnswer(score, normalized, mostLikely, confidence, probabilities)` in both
modules; the `Codec` divides the score by the question's levels minus one. `CodecSuite` checks
3.0 of 5 levels as 0.75, 0.4 of 2 as 0.4 and 2.0 of 3 as 1.0, and the golden tests check it on
the real replies. Chapter 6 of both tutorials reads `r.severity.normalized` and `s.normalized`;
the 2.13 keys are made once again, as before T19. The recorded output of `decisions` stays: the
run of T19 used the helper that already divided by the question's levels, and so printed the
same priority.

- Measured, JDK 25: `scala3` 162 tests, `scala213` 133; 100% statement and branch coverage in
  both; every mutant detected, 236 in `scala3` and 225 in `scala213`, as before: Stryker4s has
  no arithmetic mutator, so the new division adds none, and the tests with numbers of their own
  are what checks it.

### T21 — A test kit that answers with typed values

**Status:** Done 2026-09-25 — [ADR-0048](../adr/0048-a-test-kit-answers-with-typed-values.md)

**Branch:** `docs/plan-t18-t19`, with T18 to T20, as the owner asked.

D4 closed on 2026-09-25 with the owner's choice of a separate artifact. The work: a test kit for
each module, its tests, CI, and chapter 12 of both tutorials.

**Done when:** each module has its kit, tested against the real replies of `golden/`, with full
coverage and every mutant detected; CI runs them; chapter 12 of both tutorials tests with the kit.

**Outcome.**

- `testkit/scala3` and `testkit/scala213`, the sbt projects `scala3Testkit` and
  `scala213Testkit`, name `jev4s-testkit`, aggregated by the root. Scala 3:
  `JevTestkit.answering(triage)((team = Team.Billing, urgent = true, feeling = Feeling.Annoyed))`,
  with a match type `FakeAnswer` from each question to what it accepts. 2.13:
  `JevTestkit.answering(team.is(Team.Billing), urgent.is(true), feeling.is(Feeling.Annoyed))`, by
  implicit classes on `Key`. Both have `failing(error)` and `defaultModel`.
- Tested in each kit: the short forms, a whole answer whose `normalized` and `mostLikely` come
  back from the question, the round trip of the real replies of `golden/mixed` and
  `golden/structured` (JSON levels included), the model and the event, a question under another
  name, each wrong answer that throws, and the types that do not compile.
- Found while building it: the default model of the kit is observable only through `onEvent`,
  and a client with no `onEvent` hides it, so Stryker4s would keep a mutant of its literal. The
  kit exposes it as `JevTestkit.defaultModel`, which a test reads; the 2.13 `failing` and
  `answering(answers*)` use it too. The Scala 3 kit compares levels with `equals`, because
  `strictEquality` refuses `==` on `Any`; the 2.13 kit with `==`, because `-Xlint` refuses
  `equals` on `Any`.
- Measured, JDK 25: 11 tests in each kit; 100% statement and branch coverage; every mutant
  detected, 24 in `scala3Testkit` and 25 in `scala213Testkit`. The 2.13 run of Stryker4s sets
  `allowUnsafeScalaLibUpgrade` on `scala213Testkit` as well as on `scala213`, for that run only.
- CI tests the kit and builds its Scaladoc in the test job, covers it in the coverage job, and
  mutates it in the mutation job, of each module. `scala3Live` and `scala213Live` depend on their
  kit in `Test`, and chapter 12 of both tutorials quotes a `RouterSuite` that uses it, with three
  tests instead of two.


---

The items below come from a look at what the first release still lacks, on 2026-09-25. The
owner asked for them on the same working tree as T18 to T21, before M7; what M7 itself needs was
added to its entry.

### T22 — A network error that says what failed

**Status:** Done 2026-09-25 — [ADR-0049](../adr/0049-a-network-error-says-what-failed.md)

**Branch:** `docs/plan-t18-t19`, with T18 to T21, as the owner asked.

T1 noted that `JevError.Network` drops the cause. With a message only, a timeout, a refused
connection and a refused certificate look the same, and all are retried, a certificate that
fails the same way each time included. Changing a public case costs no user anything before the
first release.

**Done when:** `Network` carries a kind that a caller can match on; a refused certificate is not
retried; the mapping is tested on the chains that the JDK throws; the definition of done holds
in both modules.

**Outcome.**

- `JevError.Network(failure: NetworkFailure, message)`, with `Timeout`, `Connect`,
  `Certificate` and `Other`, in both modules; `isRetryable` is false for `Certificate`.
  `JdkTransport.networkError` maps the exception, and writes its own message for a
  `ConnectException`, whose JDK message is empty: `no connection to <host>`, or `the host <host>
  was not found`.
- Measured with the JDK's `HttpClient` on JDK 21.0.9 and 25.0.3, the same on both: the table in
  ADR-0049. The owner chose a kind named `Tls`; the measurement narrowed it to `Certificate`. A
  handshake that the server ends has no cause but its message, and on JDK 21 cannot be told
  from a protocol that the two sides do not share, so only a `CertificateException` among the
  causes marks a failure that the next attempt repeats.
- Tested: each chain of the table through `networkError`, with a message or without; a refused
  connection through the transport, retried twice, as `Connect` with its host; the two timeout
  tests as `Timeout`; `isRetryable` for each kind.
- The concepts guide's table of errors lists the kinds; the tutorials say "such as after a
  timeout" where they said "such as after a network error", which is no longer always retried.

### T23 — A threshold on a Noul's answer

**Status:** Done 2026-09-25 — [ADR-0050](../adr/0050-a-noul-answer-is-confident-on-either-side.md)

**Branch:** `docs/plan-t18-t19`, with T18 to T21, as the owner asked.

T18 noted a threshold on `NoulAnswer`, next to `ChoiceAnswer.ifConfident`.

**Done when:** a Noul's answer gives "yes", "no" or "not sure" against a threshold, in both
modules, tested at the boundaries; the tutorials show it.

**Outcome.**

- `NoulAnswer.ifConfident(min): Option[Boolean]`, a `Probability` in Scala 3 and a `Double` in
  2.13: `Some(isYes)` when `max(p, 1 - p)` is at least `min`. The owner chose it over
  `isYes(min)` on 2026-09-25.
- Tested at 0.8 on both sides: 0.8 and 0.2 are confident, the nearest `Double` inside each is
  not; 0.5 at 0.5 is `Some(true)`; 0.4 at 0.3 is `Some(false)`.
- Chapter 6 of both tutorials shows it, `checkCharge` in `guide/Decisions.scala`, after the
  Choice's `ifConfident`; the answer tables of the tutorials and of the concepts guide list it,
  and the concepts guide says that a Noul's `ifConfident` reads its one probability.
- Measured after T22 and T23, JDK 25: `scala3` 165 tests, `scala213` 136, 11 in each kit; the
  same tests pass on JDK 21, and the Spark tests too. 100% statement and branch coverage in the
  four projects; every mutant detected, 229 of 246 in `scala3` (12 ignored and 5 that do not
  compile, as before) and 234 of 235 in `scala213` (1 ignored), 24 and 25 in the kits.

### T24 — JDK 25, tested and declared

**Status:** Done 2026-09-25

**Branch:** `docs/plan-t18-t19`, with T18 to T21, as the owner asked.

JDK 25 is the long-term-support release after 21. The tests of this repository ran on it for
T18 to T21, on this machine, but CI tested 17 and 21 only, and the README asked for 21 or 17.

**Done when:** CI tests both modules on JDK 25, and the README and the guides say which JDKs
jev4s supports.

**Outcome.**

- The test job of CI runs on JDK 17, 21 and 25, each module with its kit, its Scaladoc and the
  guides' tests; the Spark example's tests skip 25, because Spark 4.0 does not start on it
  (`UnsupportedOperationException: getSubject is not supported`, T19).
- The README says that jev4s works on JDK 17, 21 and 25, and that the Spark example needs 17 or
  21; both tutorials say that CI tests the three; the Spark guide says to run Spark on 17 or 21.
- CI on JDK 25 has not run yet: it runs with the first push of this work.

### T25 — A security policy and issue forms

**Status:** Done 2026-09-25

**Branch:** `docs/plan-t18-t19`, with T18 to T21, as the owner asked.

jev4s handles an API key, and the repository will be public. A reader who finds a problem with
the key needs a private way to report it, and a bug report needs the versions and no key.

**Done when:** `SECURITY.md` says how to report privately and what counts; the issue forms ask
for what a report needs.

**Outcome.**

- `SECURITY.md`: reports through GitHub's private vulnerability reporting, never an issue and
  never the key; fixes go into the latest `0.x`; what counts, with the promises of ADR-0027 and
  ADR-0002; problems in the Jev API go to TypeSafe AI. `CONTRIBUTING.md` points to it.
- `.github/ISSUE_TEMPLATE/`: `bug.yml` asks for the versions, the smallest code (a test with the
  kit, which needs no key), the full error, and the request id from `JevEvent.Responded`;
  `idea.yml` for the problem, the code a user would write, and the module; `config.yml` turns
  off blank issues and links to a private advisory.
- Private vulnerability reporting is a setting of the repository, and works only once it is
  public: M7 lists it for the owner.

### T26 — Scaladoc warnings fail the build

**Status:** Done 2026-09-25

**Branch:** `docs/plan-t18-t19`, with T18 to T25, as the owner asked.

M1 noted that the Scala 3 Scaladoc prints `Flag -classpath set repeatedly`, a warning that
mattered once M7 publishes the documentation jar. Scaladoc 3.9.0 prints it as `Option -classpath
was updated`, in `scala3` and in `scala3Testkit`.

**Done when:** the warning is gone, or it is understood and nothing else can hide behind it.

**Outcome.**

- **The warning is Scaladoc's own, and no option of the build removes it.** It appears in a new
  sbt project with one file, on Scala 3.9.0 and 3.8.1, and with `scala-cli doc`, outside sbt.
  sbt passes `-classpath` once (its debug log). The source of Scaladoc 3.9.0 explains it:
  `ScaladocInternalTastyInspector.inspectorArgs` appends the classpath of the Scaladoc tool to
  the one it was given and parses the arguments again, in a context where `-classpath` is
  already set, so `Settings.setString` warns that the option changed. `-Wconf:msg=...:s` does not
  filter it.
- **Found while looking: a Scala 3 Scaladoc link that does not resolve was only a warning.**
  Scaladoc 3 skips `-Werror` ("Skipping unused scalacOptions: -Werror"), so `[[Nope]]` printed
  `Couldn't resolve a member for the given link query: Nope` and the documentation was built;
  CI's `doc` step passed. The 2.13 Scaladoc takes `-Werror`, and the same link fails it,
  checked in a new 2.13.16 project.
- `build/check-scaladoc.py` deletes the documentation of `scala3` and `scala3Testkit`, builds
  it, and fails on any warning but Scaladoc's own. Checked both ways: it passes on this tree,
  and fails with the link above added to `Answer.scala`. CI runs it in the Scala 3 test job on
  JDK 21. When a Scala release fixes Scaladoc, the accepted warning simply stops appearing.
- No issue for it was found in scala/scala3 on 2026-09-25. Reporting it there, with the project
  of one file, waits until after M7, as the owner decided.
- actionlint 1.7.12 finds no problem in `.github/workflows/build.yml`, with this step and the
  JDK 25 of T24, its shellcheck rule included (ShellCheck 0.11.0). Its pyflakes rule checks
  only steps written in Python, and the workflow has none.

### T27 — Fixes from the review of T18 to T26

**Status:** Done 2026-09-25

**Branch:** `docs/plan-t18-t19`, with T18 to T26, as the owner asked.

A review of the code, the documentation and the examples that T18 to T26 changed, on the working
tree, before M7.

**Done when:** each point found is fixed, and the definition of done holds.

#### Found, and fixed

1. **The 2.13 test kit kept only the last of two answers under one name.**
   `answering(urgent.is(true), urgent.is(false))` built a reply with `"urgent": 0`: ujson's
   `Obj.from` keeps the last value of a repeated key, checked with ujson 4.4.3. A mistake in the
   test passed in silence, where ADR-0048 wants it reported at once; the Scala 3 kit cannot have
   it, because a named tuple has no repeated name. The 2.13 kit now throws
   `IllegalArgumentException("'urgent' has more than one answer")`, also for two different
   questions under one name.
2. **`JdkTransport.networkError` read the causes of an exception to their end.** Java allows a
   chain that loops, and a caller's `HttpClient` (ADR-0038) could throw one; the call would never
   return. It now reads to a depth of 16, and a test gives it a chain that loops.
3. **A connect timeout named the wrong limit.** `HttpConnectTimeoutException` gave
   `no response within <config.timeout>`, while a caller's `HttpClient` can have a shorter
   connect timeout of its own. It is now `no connection within <limit>`, where the limit is the
   shorter of the two (`JdkTransport.connectLimit`). Tested with an `https` request to a socket
   that accepts and never answers: the handshake waits, and a client with a connect timeout of
   200 ms gives `no connection within 200 milliseconds` with a `timeout` of 5 s. ADR-0049 says
   so; it was written on this branch and is not yet on `dev`.
4. **Chapter 9's `explain` called every error that is not retried "a defect to report"**,
   `InvalidConfig`, a 404 for a wrong base URL and a refused certificate included, and no
   tutorial showed `NetworkFailure`. `explain` now has a case for `InvalidConfig`, for
   `Unexpected` and for `Network(NetworkFailure.Certificate, _)`, and the chapter says what each
   `NetworkFailure` means, in both tutorials.
5. **The Spark guide pointed to chapter 12 for "a client over a fake transport"**, which chapter
   12 no longer shows. It now shows the test kit, `() => JevTestkit.answering(urgent.is(true))`,
   and says why the example's own tests also use a `Transport`: they count the calls. A new test
   of `scala213Spark`, which now depends on `scala213Testkit` in `Test`, checks that sentence.
6. **The README named `jev4s-testkit` without a way to use it.** It now links chapter 12 of both
   tutorials.
7. **`InvalidConfig` was "a value that HTTP cannot carry"** in the concepts guide, the tutorials
   and the Scaladoc of `JevConfig`, next to examples, a timeout of zero and a retry jitter above 1,
   that are not about HTTP. It is now "a value that cannot work", or "that the JDK would refuse".
8. **No example tested a whole answer.** The `Router` of chapter 12 now sends a ticket to a person
   when the team's confidence is under 0.8, and a fourth test gives a `ChoiceAnswer` with a
   confidence of 0.6, in both tutorials.
9. Prose lines over 100 columns, not links, in both tutorials, the concepts guide and the README,
   rewrapped; the two code blocks of the Spark guide with no language are `text`.

- Measured, JDK 25 and 21: `scala3` 168 tests, `scala213` 139, 11 in `scala3Testkit` and 12 in
  `scala213Testkit`, 6 and 6 in the guides, 6 in the Spark example on JDK 21. 100% statement and
  branch coverage in the four projects. Every mutant detected: 232 of 249 in `scala3` (12 ignored
  and 5 that do not compile, as before), 237 of 238 in `scala213` (1 ignored), 24 and 26 in the
  kits. Two mutants in each module are detected by a timeout, not a failure: `dropWhile` for
  `takeWhile`, and `drop(16)` for `take(16)`, in `networkError`, loop on the chain of causes
  that loops, which is what the depth of 16 prevents. The Scaladoc check, the docs check and
  actionlint pass.
- `LiveSuite` against the real API after T22 to T27, approved by the owner on 2026-09-25: the
  three tests of each module pass, `jev-1.13.0` (a reply decoded into typed answers, a wrong key
  as `Unauthorized`, an unknown model as `Rejected`).

### T28 — Fixes from a second review of T18 to T27

**Status:** Done 2026-09-25

**Branch:** `docs/plan-t18-t19`, with T18 to T27, before its first commit.

A review of the working tree of T18 to T27 on 2026-09-25. On a copy of that tree, the tests,
the formatting check, coverage, the Spark tests on JDK 21, `check-docs.py` and
`check-scaladoc.py` pass, with the numbers that T27 recorded. Stryker4s, `LiveSuite` and CI on
JDK 25 were not run again.

**To fix**

1. **A caller's `HttpClient` with a very long connect timeout makes `JdkTransport` throw.**
   `connectLimit` converts the client's connect timeout to a Scala duration, and
   `DurationConverters.toScala` throws above `Long.MaxValue` nanoseconds, about 292 years.
   Measured with `ChronoUnit.FOREVER.getDuration`, Scala 3.9.0, JDK 25:
   `IllegalArgumentException: Java duration PT2562047788015215H30M7.999999999S cannot be
   expressed as a Scala duration`. `connecting` is a strict `val`, so the transport throws when
   it is built, and so does `JevClient(config, httpClient)`: the class of defect that ADR-0042
   and ADR-0045 close. Compare the two limits as `java.time.Duration`s, and convert only the
   shorter one. Test it with such a client, in both modules.
2. **`named` refuses a sealed trait that does not extend `Product`.** With `sealed trait Team`
   and case objects, `JevChoice.named(Billing, Sales)` gives three errors, the first
   `inferred type arguments [Team] do not conform to method named's type parameter bounds
   [C <: Product]`. The signature `def named[C](options: (C with Product)*)` accepts both that
   trait and the `sealed abstract class Team extends Product with Serializable` of the guides:
   `C` is inferred from the expected type, and each case object is a `Product`. Checked in a
   project of one file, Scala 2.13.16, `-Xlint -Werror`; the same holds for `JevScale.named`.
   To settle by the owner: the bound `L <: Product` is a decision recorded in ADR-0043, which is
   not yet on `dev`. If the bound stays, § 4 of `docs/guide/scala213.md` must say that `extends
   Product` is needed; today it says that it "keeps the inferred types simple".
3. **The `Branch:` lines of T18 and T19** name `task/library-fixes-and-ergonomics` and
   `docs/readme-guides-examples`, which do not exist: the work is on `docs/plan-t18-t19`. The
   branch has not been pushed. Either rename it before its first commit, to a name for what it
   now holds, and update the `Branch:` line of each task on it, or correct the two lines.

**Smaller**

4. `AGENTS.md`: "The Spark tests need JDK 17 or 21" ends the paragraph on documented compile
   errors; it belongs next to the Spark commands.
5. The README says the module "runs on Spark 4.0, 4.1 and 4.2". The pom gives the reason; the
   Spark example ran on 4.0.4 only. Say which version was tested.
6. The README opens with "the only Scala client for Jev that runs on Spark 4", with no date.
   "Other Scala clients" gives the date of the check; the claim at the top should point there,
   or carry the date too.

Noted for later, not for this task: the test kit cannot show what the code under test asked;
and from a whole `ScoreAnswer` it keeps `score`, `confidence` and `probabilities`, while
`normalized` and `mostLikely` come from the question, as ADR-0048 documents.

**Done when:** points 1 to 6 are fixed, or the owner decided to leave them; point 2 is settled
by the owner, with ADR-0043 changed if the signature changes; the definition of done holds in
both modules.

#### Done

1. `connectLimit` compares the two limits as `java.time.Duration`s and converts only the
   caller's connect timeout, and only when it is the shorter. A test in each module gives it a
   client with `ChronoUnit.FOREVER`, and another asks through `JevClient` with a client whose
   connect timeout is 1000 years, beyond `Long.MaxValue` nanoseconds: it answers. A connect
   timeout equal to `timeout` keeps `timeout` as written (`30000 milliseconds`), as `min` did.
   Found: with `FOREVER`, the JDK's own client connects to nothing. On JDK 25 a request to a
   local server gives a `ConnectException`, which jev4s returns as `no connection to 127.0.0.1`;
   with 1000 years it connects. This is the JDK's behaviour, not jev4s's, and the error is a
   value.
2. Settled by the owner on 2026-09-25: `JevChoice.named[C](options: (C with Product)*)` and
   `JevScale.named[L](levels: (L with Product)*)`. A test of `QuestionSuite` uses a
   `sealed trait Size` that does not extend `Product`. ADR-0043, not yet on `dev`, says so in its
   context and its decision; § 4 of `docs/guide/scala213.md` says that a sealed trait works too,
   and that `extends Product with Serializable` is not needed.
3. The owner kept the branch: the `Branch:` lines of T18 and T19 now name `docs/plan-t18-t19`.
4. The sentence on the Spark tests' JDK follows the build commands in `AGENTS.md`.
5. The README says the module is built for every Spark 4 and tested on 4.0.4, and the Spark
   guide says the same in its opening and in chapter 1.
6. The README's claim at the top carries the date, 2026-09-25, and links "Other Scala clients".

- Measured, JDK 25: `scala3` 170 tests, `scala213` 142, 11 in `scala3Testkit` and 12 in
  `scala213Testkit`, 6 and 6 in the guides. 100% statement and branch coverage in the four
  projects. Every mutant detected: 235 of 252 in `scala3` (12 ignored and 5 that do not compile,
  as before), 240 of 241 in `scala213` (1 ignored). The formatting check, the docs check and the
  Scaladoc check pass. The test kits' mutants, the Spark tests, `LiveSuite` and CI on JDK 21 and
  17 were not run: T28 changes neither the kits nor the Spark example.
