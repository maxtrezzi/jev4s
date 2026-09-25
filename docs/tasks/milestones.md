# Milestones

Two native modules with no shared code
([ADR-0009](../adr/0009-two-native-modules-no-shared-code.md)): `scala3/` → `jev4s_3` and
`scala213/` → `jev4s_2.13`. In each milestone the Scala 3 module goes first. M1 to M4 need no
API key.

## Definition of done

Applies to **every** milestone and to **both** modules
([ADR-0008](../adr/0008-full-coverage-and-mutation-testing.md)):

- `sbt test` green, with **zero warnings**, in `scala3` and in `scala213`.
- **100%** statement and branch coverage (scoverage) in both modules.
- Mutation testing (Stryker4s) in both modules: every surviving mutant is **killed**, or is
  **recorded** as equivalent in [`../testing/equivalent-mutants.md`](../testing/equivalent-mutants.md).
- The entry here records what was found.

---

### M1 — Base: two modules and the model

**Status:** Done 2026-09-21

- Two-module `build.sbt`: `scala3` and `scala213`, the same `name := "jev4s"`, and `golden/`
  as a test resource of both.
- Check that both modules publish locally (`publishLocal`) as `jev4s_3` and `jev4s_2.13`
  without a conflict. This is the one point of ADR-0009 not yet verified.
- sbt-scoverage (`coverageMinimumStmtTotal := 100`, `coverageMinimumBranchTotal := 100`,
  `coverageFailOnMinimum := true`) and sbt-stryker4s with no undetected mutant
  ([ADR-0016](../adr/0016-undetected-mutants-are-checked-from-the-report.md)), active in both
  modules.
- **Scala 3:** the native model — `Question[A]` as an `enum`, `Probability` as an
  `opaque type`, `JevError` and `Problem` as `enum`s, and the `Validator` with accumulated
  problems.
- **2.13:** the same model in 2.13 style.
- munit tests on the **boundaries**: 1, 2, 10 and 11 levels; 0, 1, 255 and 256 options; `NaN`.
- CI runs the tests, the coverage check and the docs check on every pull request.

**Done when:** the definition of done holds for the model and the `Validator` in both modules.

#### Built

sbt 1.13.0, with sbt-scoverage 2.4.4, sbt-stryker4s 1.1.1 and munit 1.3.6 (the latest releases
in each `maven-metadata.xml` on Maven Central, read on 2026-09-21).

| Module | Scala | Tests | Statements | Branch statements | Mutants |
|---|---|---|---|---|---|
| `scala3` | 3.7.3 | 22 | 83, all covered | 17, all covered | 54: 52 killed, 2 compile errors |
| `scala213` | 2.13.18 | 22 | 79, all covered | 18, all covered | 54: 54 killed |

Both modules compile with `-Werror`; `scala3` also with `-Wunused:all -language:strictEquality`.
2.13.18 is the current 2.13 patch release; no ADR pins the patch.

#### Found

- **Stryker4s cannot be set to `break = 100`.** It requires `low > break`, and `low` cannot
  exceed 100. [ADR-0016](../adr/0016-undetected-mutants-are-checked-from-the-report.md) keeps
  `break = 99` and adds `build/check-mutants.py`, which fails on any undetected mutant in the
  JSON report. The script was checked both ways: it passes on the real reports and fails on a
  copy with one mutant marked `Survived`.
- **Stryker4s works per module** with `sbt "project scala3" stryker`, and reads
  `stryker4s.conf` from the build root: a deliberately invalid value there made the run in
  `scala3` fail on that value.
- **Mutation testing found a real gap in the first test suite.** `p >= t` mutated to
  `p == t` survived in `Probability`, because the tests compared only at and below the
  threshold. The same for `<=`. Each comparison is now also tested strictly inside its range.
- **`strictEquality` rejects two mutants before any test runs.** In `scala3`, `>=` mutated to
  `==` between a `Probability` and a `Double` does not compile. In `scala213` the same mutants
  compile and the tests kill them.
- **Both modules publish without a conflict.** `sbt publishLocal` wrote `jev4s_3` and
  `jev4s_2.13` under `io.github.maxtrezzi`. A Scala 3.7.3 project and a Scala 2.13.18 project
  each resolved `io.github.maxtrezzi::jev4s:0.1.0-SNAPSHOT` to their own artifact and used it.
  This closes the open point of [ADR-0009](../adr/0009-two-native-modules-no-shared-code.md).
- **Scaladoc for Scala 3 prints `Flag -classpath set repeatedly`** during `publishLocal`, as a
  warning that does not fail the build. It matters in M7, when the documentation jar is
  published.
- **Mutation testing is fast:** about 11 s of wall time per module, sbt start-up included, for
  54 mutants each. Input for [D3](open-decisions.md#d3--when-mutation-testing-runs-in-ci).
- **CI reproduces the local numbers.** On the pull request of M1 each module job — tests with
  coverage, Stryker4s and the mutant check — took 1 min 35 s on `ubuntu-latest` with JDK 21,
  and reported the same 22 tests, 100% coverage and 54 detected mutants per module.

### M2 — JSON (`Codec`)

**Status:** Done 2026-09-22

- Make one real call and save the responses in `golden/`: golden tests on invented JSON prove
  little.
- **Scala 3**, then **2.13:** `Codec.encode` (state and questions → request JSON) and
  `Codec.decode` (response JSON → answers, in question order).
- Choice: JSON key → the value `A`, through `ChoiceOption`.
- Golden tests on `golden/`, the same files in both modules.

**Done when:** the golden tests pass in both modules, and a JSON with a missing answer gives
`Left(Decoding(...))`.

#### Built

`Codec.encode`, `Codec.decode` and `Codec.errorMessage` in both modules, with ujson 4.4.3
([ADR-0005](../adr/0005-minimal-dependencies-ujson-and-the-jdk.md)), and `ScoreAnswer`'s
per-level probabilities with the `DuplicateLevel` check
([ADR-0021](../adr/0021-what-the-codec-keeps-from-a-jev-reply.md)).

| Module | Tests | Coverage | Mutants |
|---|---|---|---|
| `scala3` | 54 | 100% statements and branches | 122: 111 killed, 11 `Ignored` and applied by hand (ADR-0020) |
| `scala213` | 49 | 100% statements and branches | 111: 111 killed |

`scala3` also passes on Scala 3.9.0.

#### Found

- **The API is documented at [docs.typesafe.ai](https://docs.typesafe.ai/api.md)**, read on
  2026-09-22: `POST https://api.typesafe.ai/v1/systemone`, `Authorization: Bearer <key>`, and
  `jev-1.13.0` behind both aliases. Input tokens cost $0.042 per million; output tokens are
  free.
- **Seven real calls recorded** into `golden/` by `build/capture-golden.py`, on
  `jev-1.13.0`: 1,667 input tokens for the five that succeeded, about $0.00007.
- **The fields of an answer come in a different order than in the documentation**
  (`confidence` before `probabilities`), so the codec reads fields by name only.
- **In `mixed`, the answers came back in question order.** One sample proves nothing: the
  codec still orders answers by the questions.
- **The two error bodies have different shapes**, and the documentation shows neither. A 401
  has `{"detail": {"error_type", "message"}}`; a 422 has `{"detail": [{"type", "loc", "msg",
  "input"}]}`, where `loc` names the field.
- **A Score answer carries more than `ScoreAnswer` holds:** a `legend` (level index → text) and
  `probabilities` per level, keyed `"0"`, `"1"`, ….
- **The response names the model that answered** (`"model": "jev-1.13.0"`), next to `usage`.
- **`instructions` and `criteria` accept JSON objects and arrays** as well as strings; the
  model holds strings only. Left for [T2](tasks.md#t2--structured-instructions-and-criteria).
- **A 422 location holds list indexes as numbers**, and `ujson.Num(0)` prints as `0.0`. The
  codec writes a whole number as an integer, so a location reads `questions.0.criteria`.
- **In Scala 3 the answer types need no cast.** Matching on the GADT `Question[A]` refines `A`
  in each branch; bounding `A` by `Answer` lets a list of `Question[?]` decode to a list of
  `Answer`. The 2.13 module returns `Answer` from a `sealed trait`, and leaves its typed cast to
  the keys of M4.
- **Each module now has more than 100 mutants**, so Stryker4s's `break = 99` alone would let
  one survivor through; `build/check-mutants.py` is what holds the line
  ([ADR-0016](../adr/0016-undetected-mutants-are-checked-from-the-report.md)).

### M3 — Scala 3 API

**Status:** Done 2026-09-22

- `Transport` and a `FakeTransport` for tests.
- **Static API** with named tuples: answer types computed with `Tuple.Map`, any number of
  questions, no `Option` and no visible cast.
- **Dynamic API**: `Map[String, Question[?]]` → `Map[String, Answer]` for questions built at
  runtime.
- `derives JevChoice`, with snake_case keys and `compiletime.error` for cases with
  parameters.
- `onReply` ([ADR-0010](../adr/0010-reply-metadata-through-onreply.md)) and state as
  `ujson.Value`.
- The expected compile errors are tests (`compileErrors` in munit).

**Done when:** tests cover both APIs and `derives`, and the typical mistakes — a missing name,
a wrong type, a value that is not a question, a state type with no `ToState` — are compile
errors with a readable message.

#### Built

`JevClient` with `ask` (named tuples) and `askMap`, `Transport`, `ToState`, `derives JevChoice`
and `onReply`, in the shape of [ADR-0022](../adr/0022-the-scala-3-client-api.md). The tests
run the client against the recorded replies of `golden/` through a `FakeTransport`.

| Module | Tests | Coverage | Mutants |
|---|---|---|---|
| `scala3` | 74 | 100% statements and branches | 126: 115 killed, 11 `Ignored` and applied by hand (ADR-0020) |

#### Found

- **No `inline` code was needed.** The names of a named tuple come from `ValueOf` on their
  literal types, and the cases of an enum from `ValueOf` on their singleton types, through
  recursive givens. `@implicitNotFound` on those type classes gives the custom messages the
  plan expected from `compiletime.error`, and the code stays under mutation testing.
- **Every typical mistake is a compile error.** Seven are tested with `compileErrors`. Four
  messages are jev4s's own: a value that is not a question, a state type with no `ToState`, a
  Choice over a type with no `JevChoice`, and a derived `JevChoice` for a case with parameters.
  Three are the compiler's and clear as they are: a name that was not asked (`value nope is not
  a member of …`), an answer read as the wrong type (`Found: NoulAnswer, Required:
  ScoreAnswer`), and a tuple without names (`Required: NamedTuple.AnyNamedTuple`).
- **`compileErrors` puts a multi-line message on the line after `error:`**, so the tests read
  the first line that is not empty.
- **`Tuple.fromArray` plus one cast** turns the list of answers into the typed named tuple; the
  cast is sound because a named tuple is its values at runtime, in the order of the names.
- **Only 4 new mutants**: the client is mostly types, and its logic is the `Validator` and the
  `Codec`, already covered.

### M4 — Scala 2.13 API

**Status:** Done 2026-09-22

- `Transport` and a `FakeTransport`.
- `JevClient.ask(state, keys: _*)` and `Answers`, whose `get` checks the question.
- Choice options written by hand (`JevChoice.fromOptions`).
- The M3 decisions on state and `onReply`, in 2.13 idiom.

**Done when:** tests cover "a key with the same name but a different question gives `None`".

#### Built

`Key`, `Answers`, `JevClient.create` and `ask(state, keys*)`, `Transport` and `ToState`, in the
shape of [ADR-0023](../adr/0023-the-scala-2-13-client-api.md), tested against the recorded
replies of `golden/` through a `FakeTransport`.

| Module | Tests | Coverage | Mutants |
|---|---|---|---|
| `scala213` | 61 | 100% statements and branches | 112: 112 killed |

#### Found

- **Stryker4s makes one mutant of the check in `Answers.get`** (`==` → `!=`), not one that
  removes it. Removing it by hand (the guard becomes `true`) makes exactly the test "a key with
  the same name but a different question gives None" fail.
- **munit's `compileErrors` works in 2.13 too**: the missing `ToState` is tested as a compile
  error, with the message of `@implicitNotFound`.

### M5 — Real HTTP

**Status:** Done 2026-09-22

- **Scala 3**, then **2.13:** `JdkTransport` on `java.net.http`, tested against a local HTTP
  server (`com.sun.net.httpserver.HttpServer`, in the JDK). No network in module tests.
- `RetryPolicy` with a **pure** `delayFor(attempt, random, retryAfter)`, and an injectable
  `Sleeper`. Check the defaults in the official SDKs first.
- `JevConfig.fromEnv` with a required model; `toString` hides the key.
- Live tests in **separate sbt projects**, active only when `TYPESAFE_API_KEY` is set, and
  **outside** coverage and mutation testing.

**Done when:** an example per module calls the real API and handles 401, 422 and 429.

#### Built

`RetryPolicy` and `Sleeper`, `JevConfig.fromEnv` and `ConfigError`, `JdkTransport`,
`JevError.Unexpected`, `JevClient(config)` and `JevClient.withTransport` (2.13: `create` and
`withTransport`), in the shape of [ADR-0024](../adr/0024-http-retries-and-configuration.md);
`JevEvent` and `onEvent` in place of `onReply`
([ADR-0025](../adr/0025-client-events-through-onevent.md)); HTTP 400 as `Rejected`
([ADR-0026](../adr/0026-http-400-is-a-rejected-request.md)).
`JdkTransport` is tested against a `com.sun.net.httpserver.HttpServer` on localhost. `live/`
holds a runnable example and a live suite per module.

| Module | Tests | Coverage | Mutants |
|---|---|---|---|
| `scala3` | 104 | 100% statements and branches | 169: 156 killed, 1 compile error, 12 `Ignored` and applied by hand |
| `scala213` | 91 | 100% statements and branches | 155: 154 killed, 1 `Ignored` and applied by hand |

**Live run, 2026-09-22, `jev-1.13.0`:** `scala3Live/test` and `scala213Live/test` 3 of 3 each —
a real ask with typed answers and a `Replied` event, a wrong key as `Unauthorized`, an unknown
model as `Rejected`. `scala3Live/run` and `scala213Live/run` each answered "Billing, urgent",
with the `Replied` event logged through `System.Logger`: 324 input tokens per call.

#### Found

- **The official SDKs agree on their retry defaults** (docs read 2026-09-22): 2 retries, 0.5 s
  doubling to 5 s, 25% jitter, statuses 408, 429 and 5xx, `Retry-After` and `retry-after-ms`
  honoured; the JavaScript SDK caps that at 60 s. Each request times out after 10 s. jev4s uses
  the same, without the Python SDK's 30 s budget per call.
- **A companion `apply` in Scala 3 removes the constructor proxy**, so `JevClient(model,
  transport)` stopped compiling once `JevClient(config)` existed; and Scala allows default
  arguments on one overloaded alternative only. The transport form is now
  `JevClient.withTransport`.
- **A stopped `HttpServer` still accepts connections for a while**: a request to it timed out
  instead of being refused, and the test of a refused connection was really testing a timeout.
  A plain `ServerSocket`, bound and closed, gives a real refusal.
- **Stryker4s ignores a "static" mutant on its own** — `val defaultBaseUrl`, initialised once —
  and `check-mutants.py` let it pass in silence. The script now fails on it; the mutant is
  excluded on purpose and applied by hand in both modules.
- **Mutation testing found one gap:** no test gave an `https` base URL through the
  environment, so the string `"https"` could become `""` unnoticed.
- **The test suites stay fast and offline**: `JdkTransport` waits through a recording
  `Sleeper`, and the only real wait is a 200 ms timeout test.
- **An unknown model is HTTP 400, which the API documentation does not list**, with a body
  shaped like a 401's (`{"detail": {"error_type": "api_usage_error", "message": …}}`). It is
  recorded in `golden/error-400` and mapped to `Rejected`
  ([ADR-0026](../adr/0026-http-400-is-a-rejected-request.md)).
- **The client's retries were invisible**: `onReply` saw only the final success. `onEvent`
  now receives a `Retrying` before each wait
  ([ADR-0025](../adr/0025-client-events-through-onevent.md)).
- **Scala 3 flags `case _` after an exhaustive match on an enum as unreachable**, so the usual
  advice to end a match with it fails under `-Werror`. The Scaladoc asks for exhaustive matches
  instead, and lets the compiler point at a new kind of event.
- **Most 422s never reach the API**: the `Validator` refuses the requests that would cause
  them. The live suites provoke a rejection with an unknown model (400); 422 and 429 are handled
  in the examples, and tested against the local server.

### M6 — Quality and documentation

**Status:** Done 2026-09-22

- scalafmt, configured per dialect.
- CI: tests of both modules on JDK 17 and 21; coverage and mutation jobs per module, with the
  HTML reports as artifacts.
- README with a section for Scala 3 and one for 2.13; `examples/`.

**Done when:** CI is green and a new person makes a first call reading only the README, with
Scala 3 or 2.13.

#### Built

- **scalafmt 3.11.5** (sbt-scalafmt 2.6.2, the latest releases on Maven Central on 2026-09-22):
  one `.scalafmt.conf`, the Scala 3 dialect by default and the 2.13 dialect for every
  `scala213` path, 120 columns, trailing commas in Scala 3 only.
- **CI** (`.github/workflows/build.yml`): a format job; tests of each module on JDK 17 and 21,
  with Scaladoc and the live project compiled; a coverage job and a mutation job per module on
  JDK 21, each keeping its HTML report as an artifact; the docs check. `actionlint` 1.7.12
  finds no problem in it.
- **README**: what a caller needs, a first call in Scala 3 and one in 2.13, the errors, retries
  and events, testing with a transport of one's own, and where the examples are.
- **Examples**: the runnable examples of M5 in `live/scala3` and `live/scala213` are the
  examples; a separate `examples/` would repeat them.

#### Found

- **Every test passes on JDK 17 and on JDK 21 with no change.** JDK 17 was fetched into a
  temporary directory to check it; the development machine has 21 and 25.
- **The README was followed as a newcomer would**: `sbt publishLocal`, then a new project with
  only the dependency line and the README's code, in Scala 3.7.3 and in 2.13.18. Both compile,
  and both made a real first call with the key set, answering "Billing, urgent". The README's
  three snippets also compile under `-Werror` inside the build.
- **That walk-through found a defect no test could.** Scaladoc for 2.13 reads `$name` in a
  comment as one of its own variables, and the `onEvent` example added in M5 made `scala213/doc`,
  and so `publishLocal`, fail. The `$` signs are escaped, and CI now builds the Scaladoc.
- **Formatting moved the lines of `Probability.apply`**, so the table of mutants applied by hand
  was updated; Stryker4s still reads the reformatted `@SuppressWarnings` list.
- **Merging D1 (Scala 3.9.0) with M2–M6, written on 3.7.3, found one gap.** Scala 3.9 instruments
  the default `onEvent = _ => ()` of `JevClient.withTransport` as a statement of its own, and no
  test built a client without `onEvent` and got an answer: `scala3` fell to 99.80% statement
  coverage. A test of that common case, in both modules, brings it back to 100%. Everything
  else — formatting, tests on JDK 17 and 21, Scaladoc, mutation testing, the docs check — passed
  on the merged tree unchanged.
- **The README's Scala version is checked, not only stated**: after `publishLocal` of the merged
  tree, a new project on Scala 3.9.0 compiles, and on 3.7.3 it fails with *"TASTy file … could
  not be read"*. The README now lists the Scala version with the JDK.
- **CI is green on the first run, and matches the local results.** All 10 jobs of pull request
  #2 passed: the format check, the tests of each module on JDK 17 and 21 with the Scaladoc and
  the live projects compiled, coverage and mutation testing per module with the mutant check,
  and the docs check. Each job took between 6 s and 1 min 45 s. JDK 17, which this machine does
  not have, gives the same 111 and 100 tests as JDK 21 and 25.
- **Newer major versions of the GitHub actions exist** (checkout v7, setup-java v6,
  upload-artifact v7). The workflow keeps the v4 versions proven green in M1; moving is
  [T3](tasks.md#t3--newer-major-versions-of-the-github-actions).

### M7 — Publishing

**Status:** Not started

- Maven Central (for example with sbt-ci-release): `jev4s_3` and `jev4s_2.13`.
- CHANGELOG; `0.x` versions while the Jev API is in early access.

**Done when:** `libraryDependencies += "io.github.maxtrezzi" %% "jev4s" % "0.1.0"` works in a
new project, Scala 3 or 2.13.

### M8 — Optional

**Status:** In progress — [ADR-0041](../adr/0041-the-spark-example-builds-its-client-on-the-executors.md)
proposed, waiting on the owner

- An Apache Spark example (2.13 module).

**Done when:** an example asks Jev about each row of a Spark `Dataset`, CI compiles and tests it
without the API key, and a guide explains it.

#### Built

- **`live/spark`** (`scala213Spark`): `SparkTriage.triage` asks if each ticket is urgent, with
  one client per partition built on the executors, the guide's `Pacer` at the account's rate
  divided by the partitions, and each answer or error as columns of a `Triaged` row. Spark 4.0.4
  is `Provided`; `run`, `runMain` and the tests fork a JVM with it.
- **Four tests** on Spark in local mode, with a fake transport or a local server that answers 429
  above 10 requests per second: answers and an error in their own rows, the calls each action
  makes with and without `cache`, 429s without a pacer, none with the shared rate. The rate test
  fails when the pacer is not divided by the partitions (applied by hand).
- **CI** checks the project's formatting and runs its tests in the 2.13 test job.
- **`docs/guide/spark.md`**, quoting the example, and a row in the README's table of guides.

#### Found

- **Each action on the result calls Jev again.** For 10 tickets: `collect` 10 calls, `count` 10,
  `orderBy("id").collect` 20, because a sort samples its input first; after `cache`, a first
  `count` 10 and then none. The example makes one action and sorts on the driver.
- **Spark's UI shows `spark.executorEnv.TYPESAFE_API_KEY`**: the default `spark.redaction.regex`
  of spark-core 4.0.4, `(?i)secret|password|token|access[.]?key`, does not match `API_KEY`.
- **Two test traps**: a fake transport that called a method of the suite failed with "Task not
  serializable", and a counter captured by the fake transport stayed at 0, because the tasks got
  a copy. Both now live in the suite's companion object.
- **No `--add-opens` is needed** for these tests: they pass on JDK 21 and on JDK 17 (Temurin
  17.0.16, fetched into a temporary directory).
- **sbt 1.13 leaves `Provided` classes off the `run` classpath**: `runMain` failed with
  `NoClassDefFoundError: SparkSession$` until `run` and `runMain` used `Compile / fullClasspath`.
