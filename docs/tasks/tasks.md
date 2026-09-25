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
