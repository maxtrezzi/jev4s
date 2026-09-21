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

**Status:** In progress — done locally 2026-09-21; waiting for CI on the pull request

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

### M2 — JSON (`Codec`)

**Status:** Not started

- Make one real call and save the responses in `golden/`: golden tests on invented JSON prove
  little.
- **Scala 3**, then **2.13:** `Codec.encode` (state and questions → request JSON) and
  `Codec.decode` (response JSON → answers, in question order).
- Choice: JSON key → the value `A`, through `ChoiceOption`.
- Golden tests on `golden/`, the same files in both modules.

**Done when:** the golden tests pass in both modules, and a JSON with a missing answer gives
`Left(Decoding(...))`.

### M3 — Scala 3 API

**Status:** Not started

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

### M4 — Scala 2.13 API

**Status:** Not started

- `Transport` and a `FakeTransport`.
- `JevClient.ask(state, keys: _*)` and `Answers`, whose `get` checks the question.
- Choice options written by hand (`JevChoice.fromOptions`).
- The M3 decisions on state and `onReply`, in 2.13 idiom.

**Done when:** tests cover "a key with the same name but a different question gives `None`".

### M5 — Real HTTP

**Status:** Not started

- **Scala 3**, then **2.13:** `JdkTransport` on `java.net.http`, tested against a local HTTP
  server (`com.sun.net.httpserver.HttpServer`, in the JDK). No network in module tests.
- `RetryPolicy` with a **pure** `delayFor(attempt, random, retryAfter)`, and an injectable
  `Sleeper`. Check the defaults in the official SDKs first.
- `JevConfig.fromEnv` with a required model; `toString` hides the key.
- Live tests in **separate sbt projects**, active only when `TYPESAFE_API_KEY` is set, and
  **outside** coverage and mutation testing.

**Done when:** an example per module calls the real API and handles 401, 422 and 429.

### M6 — Quality and documentation

**Status:** Not started

- scalafmt, configured per dialect.
- CI: tests of both modules on JDK 17 and 21; coverage and mutation jobs per module, with the
  HTML reports as artifacts.
- README with a section for Scala 3 and one for 2.13; `examples/`.

**Done when:** CI is green and a new person makes a first call reading only the README, with
Scala 3 or 2.13.

### M7 — Publishing

**Status:** Not started

- Maven Central (for example with sbt-ci-release): `jev4s_3` and `jev4s_2.13`.
- CHANGELOG; `0.x` versions while the Jev API is in early access.

**Done when:** `libraryDependencies += "io.github.maxtrezzi" %% "jev4s" % "0.1.0"` works in a
new project, Scala 3 or 2.13.

### M8 — Optional

**Status:** Not started

- An Apache Spark example (2.13 module).
