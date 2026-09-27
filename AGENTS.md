# AGENTS.md

Guidance for a coding agent working in this repository
([ADR-0014](docs/adr/0014-agent-guidance-lives-in-agents-md.md)). `CLAUDE.md` points here and
holds nothing of its own. Read this file in full before doing anything.

## Project state

**M1 to M6, M8 and T1 to T29 are done, and M7 is in progress; nothing is published yet.** Both modules build and hold the model — the questions, the answers, `Probability`,
`Reply`, `JevError`, `Problem` — the `Validator` and the JSON `Codec`, tested against real replies
in `golden/`, with full coverage and every mutant detected. Instructions, criteria, options and
levels are text or JSON (`ujson.Value`, ADR-0031). The levels of a Score can also be the caller's own type, from
`derives JevScale` in Scala 3 or a `JevScale` written by hand in 2.13, and its answer names the
`mostLikely` level (ADR-0034) and the score on a scale from 0 to 1, `normalized` (ADR-0047). A Noul's answer has `ifConfident`, "yes" or "no" when its probability is high enough (ADR-0050). In 2.13, `JevChoice.named` and `JevScale.named` list case objects
once, named after them (ADR-0043); `Choice.keys` builds a Choice over runtime keys in both. A reply without its input tokens still answers, with
`Reply.inputTokens` as `None` (ADR-0035). The 2.13 module compiles with Scala 2.13.16, the Scala
of Spark 4.0 (ADR-0036). Each module also has its client, `JevClient`:
named tuples in Scala 3; in 2.13, typed keys answered as a tuple, 1 to 10 per call (ADR-0037);
`askMap` in both for questions built at runtime; over `JdkTransport` with the official SDKs' retries
and a 30 s budget per call (ADR-0030), optionally with the caller's own `HttpClient` (ADR-0038), or over any `Transport`, and reports replies, retries and each HTTP response with its request id as
`JevEvent`s (ADR-0040). The library has no rate limiter; the tutorials show a pacer (ADR-0039). Each module has a test
kit, `jev4s-testkit` in `testkit/`, whose client answers with typed values (ADR-0048). The API key is an `ApiKey` (ADR-0027), and a config that `java.net.http` would refuse, such as a key with a newline, is `JevError.InvalidConfig`, never an exception (ADR-0042), as is a `RetryPolicy` whose wait could be negative (ADR-0045). A request with no response is `JevError.Network` with its `NetworkFailure`, and a certificate that TLS refused is not retried (ADR-0049). Live tests and examples are in `live/`,
outside the root build, and the live tests and the examples run against the real API. The README opens with one
example for each Scala version, and `docs/guide/` holds the Jev concepts and a tutorial for each
version, all quoting the examples in `live/` (ADR-0032), and the compile errors that the README and the
Scala 3 tutorial show are checked by a test (ADR-0044); the concepts guide also shows how to reach Jev
through OpenRouter or Vercel AI Gateway. An Apache Spark example of the 2.13 module is in `live/spark`
(`scala213Spark`, ADR-0041), with one client per executor JVM (ADR-0046), and its guide
`docs/guide/spark.md`. Everything done is on `dev`, where CI
runs it on JDK 17, 21 and 25, the Spark example on 17 and 21. The library publishes as `jev4s` and renames on request (ADR-0033), to Maven Central with sbt's own
Central Portal support, from a tag on `main`, at the version written in `build.sbt` (ADR-0051);
its jars use JDK 17's API. MiMa checks each patch version against the earlier patches of its
minor version, and TASTy-MiMa does too for `jev4s_3` (ADR-0052).
The repository is **private** for now.

**This file is tracked.** Keep it current **in the same commit as the work it describes**, and
treat a stale instruction here as a defect: the next session will follow it.

Three documents matter, with different jobs:

- **[`docs/tasks/`](docs/tasks/README.md)** — what to do next and whether it is done:
  milestones M1–M8, the tasks between them (T1…) and the decisions waiting on the owner (D1…).
  **Start here.**
- **[`docs/adr/`](docs/adr/README.md)** — why the work is shaped this way. Read the index for
  what governs what.
- **The discussion log** — kept by the owner outside this repository. Ask the owner for intent
  that the ADRs do not cover.

Where they differ: the ADR wins on a *decision*, `docs/tasks/` wins on *status*.

## What Jev4s is

A Scala client for **Jev**, TypeSafe AI's "System One" model, which returns typed decisions
with probabilities instead of text. It is **unofficial**: not affiliated with TypeSafe AI.
Never use `com.typesafe` in a package or coordinate
([ADR-0007](docs/adr/0007-name-coordinates-and-package.md)).

It is a study and portfolio project ([ADR-0001](docs/adr/0001-a-study-and-portfolio-project.md)),
and it is meant to be a **showcase for Scala**: idiomatic and ergonomic, where compactness,
clarity and type safety stand out. Judge every API choice against that bar.

## Decision workflow — follow this every session

- **Discussion log** — outside this repository. Every substantive design discussion is logged
  there by the owner. Never create a folder for it here, and never name, link or describe where
  it is kept in any tracked file, commit message or pull request.
- **`docs/adr/NNNN-title.md`** — whenever a discussion *settles* something that constrains
  future code, write an ADR ([ADR-0011](docs/adr/0011-record-decisions-as-adrs.md)): copy
  `docs/adr/0000-template.md`, take the next number, follow
  Context → Forces → Decision → Consequences, and add a row to the index. Content moves from
  the log by **rewriting**, never by copying.
- **`docs/tasks/`** — update the status of whatever you worked on, in the entry and in the
  status board, in the same commit as the work
  ([ADR-0012](docs/adr/0012-track-work-items-in-docs-tasks.md)). Record what was *found*. Never
  renumber an item.

Accepted ADRs are immutable in their substance: to change a decision, write a new ADR and mark
the old one `Superseded by ADR-NNNN` (or `Accepted — <aspect> amended by ADR-NNNN`). A later
finding is never appended to an ADR; it goes to `docs/tasks/`.

**`docs/tasks/open-decisions.md` needs the owner.** Ask; do not decide alone.

## Branches — follow this every session

([ADR-0013](docs/adr/0013-one-branch-per-task-work-lands-on-dev.md))

- **`dev` is the default branch. `main` carries releases only.** Never commit to either
  directly, never branch from `main`, never merge `main` into `dev`, never rebase `dev` onto it.
- **Branch from `dev` before starting**, one branch per work item, named after it:
  `milestone/m1-base`, `task/<slug>`, `decision/d1-<slug>`, `docs/<slug>`. The branch carries
  the work, its status in `docs/tasks/`, and any ADR it produces. Every pull request targets
  `dev`.
- **Nothing enforces this while the repository is private**: GitHub's free plan offers no
  branch protection on private repositories. Keep the rule anyway.
- **Pushing, merging and rebasing are asked for every time**, including merging a pull
  request. Finish the work, commit it, say what you would push or merge, and wait for a yes.
- **An ADR number is only safe once it is on `dev`.** After changing one, search the whole tree,
  source included, for the old `ADR-NNNN`.
- **No `Claude-Session:` trailer and no session link** in a commit message, a pull request, an
  issue or a comment. `Co-Authored-By:` stays.
- A commit message says what the code now does and why it is shaped that way, never how the
  work went.

## Build and test

sbt 1.13, two modules with no shared code
([ADR-0009](docs/adr/0009-two-native-modules-no-shared-code.md)): `scala3` (Scala 3.9.0,
[ADR-0017](docs/adr/0017-scala-3-9-lts.md)) and `scala213` (Scala 2.13.16,
[ADR-0036](docs/adr/0036-the-2-13-module-compiles-with-spark-4s-scala.md)). Both compile with
`-Werror`, so a warning is a failed build.

```bash
sbt test                                              # both modules and their test kits
sbt scala3/test                                       # one module
sbt "scala3/testOnly *ValidatorSuite"                 # one suite
sbt clean coverage test coverageReport                # coverage; fails below 100%
sbt "project scala3" clean stryker                    # mutation testing, one module
sbt "project scala213" "set allowUnsafeScalaLibUpgrade := true" clean stryker   # the 2.13 module
sbt "project scala3Testkit" clean stryker             # a test kit (ADR-0048); the 2.13 one also sets
                                                      # allowUnsafeScalaLibUpgrade on scala213 and scala213Testkit
sbt mimaReportBinaryIssues scala3/tastyMiMaReportIssues   # the API of earlier patches kept (ADR-0052)
python3 build/check-mutants.py scala3                 # fails on any undetected mutant
python3 build/check-mutants.py testkit/scala3         # the same, for a test kit
python3 build/check-docs.py                           # ADR index, status lines, links, quoted examples
python3 build/check-scaladoc.py                       # Scala 3 Scaladoc with no warning but its own
python3 build/check-docs.py --write-snippets          # copy each quoted example into its document
sbt scalafmtAll scalafmtSbt scala3Live/scalafmtAll scala213Live/scalafmtAll scala213Spark/scalafmtAll   # format; CI checks all
python3 build/capture-golden.py                       # golden/ plan only; --run makes paid calls
sbt scala3Live/test scala213Live/test                 # real API, paid; skipped without the key
sbt "scala3Live/testOnly guide.*"                     # the guides' tests only: free, CI runs them
sbt scala3Live/run                                    # the example, one paid call
sbt "scala3Live/runMain guide.firstQuestion"          # one example of a guide, one paid call
sbt scala213Spark/test                                # the Spark example on local Spark: free, CI runs it
sbt "scala213Spark/runMain spark.SparkTriage"         # the Spark example, three paid calls
```

The Spark tests need JDK 17 or 21: Spark 4.0 does not start on 25.

**The code in the README and in `docs/guide/` is quoted from `live/` (ADR-0032).** Edit the
example, between its `// snippet: <name>` and `// end: <name>` lines, then run
`--write-snippets`; never edit a quoted code block by hand. The docs check fails on a block that
differs from its example.

**A block after `<!-- compile-errors: <suite> -->` lists code that must not compile (ADR-0044).**
Each entry has a case in that suite, `documented("<code>", compileErrors("<code>"))`, with the
code written in both literals: never pass it through an `inline` method, which can change the
compiler's message. Change an entry and its case together; the suite checks the message, the docs
check that the case exists.

**Mutation testing is two steps, and the second is the check (ADR-0016).** Stryker4s cannot be
set to fail on a single survivor; `build/check-mutants.py` reads its JSON report and does.
Stryker4s runs per module (`project scala3`, `project scala213`) and reads `stryker4s.conf` from
the root. Its test runner is built with scala-library 2.13.18, and sbt stops a 2.13.16 build that
sees a newer library (SIP-51), so the 2.13 run sets `allowUnsafeScalaLibUpgrade` for that run
only. Never set it in `build.sbt`: the check is what keeps the published module on 2.13.16.

**A release follows ADR-0051.** The version is `ThisBuild / version` in `build.sbt`, a
`-SNAPSHOT` on `dev`: the release pull request sets it and the CHANGELOG, and a tag
`v<version>` on the squashed commit on `main` starts `.github/workflows/release.yml`. Pushing a
tag is the owner's, like any push. Never add sbt-dynver or sbt-ci-release, and never drop the
`-release` options: the published jar is built by one JDK, not by CI's matrix.

**A patch version keeps the API of the earlier patches of its minor version (ADR-0052).**
`compatibleReleases` in `build.sbt` derives them from the version; `x.y.0` has none. A break
that must ship goes into a new minor version, never into a MiMa or TASTy-MiMa filter. The TASTy
check needs its overrides, core 1.4.1 and tasty-query 1.9.0, to read Scala 3.9, and leaves out
the Scala 3 test kit, whose `FakeAnswer` makes tasty-query fail.

**Run `sbt clean` after a coverage run** before anything else that compiles: coverage
instruments the classes, and a `publishLocal` from an instrumented build ships the
instrumentation.

CI (`.github/workflows/build.yml`) checks the formatting, runs the tests of each module on JDK 17,
21 and 25 with Scaladoc, the live project compiled and the guides' tests run (never `LiveSuite`),
the Spark example's tests in the 2.13 job, runs coverage and Stryker4s with the mutant check per
module, the compatibility checks of ADR-0052 on JDK 21, and runs the docs check and, for Scala 3,
the Scaladoc check. Mutation testing runs on every pull request
([ADR-0018](docs/adr/0018-mutation-testing-runs-on-every-pull-request.md)); never point it at a
project that calls the real API.

## Load-bearing constraints

Read the ADR before changing anything below; where a summary here and an ADR disagree, the ADR
wins and the summary is the bug.

- **Two native modules, no shared code (ADR-0009).** `scala3/` uses the whole of Scala 3
  (`enum`, `opaque type`, `given`, `extension`, named tuples, `derives`); `scala213/` is
  idiomatic 2.13. Do not introduce a shared source directory or a cross-build to "remove
  duplication": that is the decision ADR-0009 reversed. What the modules share is `golden/`,
  and the golden tests are what keeps them in step.
- **Scala 3 first.** The Scala 3 module settles the design in each milestone; the 2.13 module
  follows it and does not invent design of its own.
- **The 2.13 module compiles with the Scala of the oldest supported Spark 4, 2.13.16
  (ADR-0036).** Do not raise the patch "to the latest", and do not add a runtime dependency that
  needs a newer `scala-library`: sbt fails the build then, and the answer is not to silence it.
  munit needs 2.13.18, so the 2.13 tests run on it; sbt does not check the test classpath, and
  the main code still compiles against 2.13.16.
- **100% coverage and zero unexplained mutants in both modules (ADR-0008).** No coverage
  exclusions: what cannot be tested deterministically does not go into a published module.
  An equivalent mutant is excluded with `@SuppressWarnings` **and** recorded in
  [`docs/testing/equivalent-mutants.md`](docs/testing/equivalent-mutants.md). Zero undetected mutants is
  checked by `build/check-mutants.py`, not by Stryker's threshold (ADR-0016).
- **Stryker4s cannot mutate `inline` code or a value initialised once (ADR-0020, ADR-0024).** A
  mutation type that occurs there is listed in `@SuppressWarnings` on that definition; for
  `inline` code the whole run fails otherwise, and `check-mutants.py` fails on a static mutant
  left `Ignored`. Each excluded mutant is applied by hand and recorded in
  `equivalent-mutants.md`; redo it when the definition or its tests change.
- **Errors are values (ADR-0002).** The API returns `Either[JevError, A]` and is synchronous.
- **One runtime dependency, ujson (ADR-0005).** HTTP is `java.net.http`.
- **Reply metadata and retries go to `onEvent` as `JevEvent`s, never next to the answers, and the
  library never logs (ADR-0025).**
- **No default model.** `jev-latest` moves; the caller names the model.
- **The API key never appears in a log or a `toString`.** It is an `ApiKey`, never a case class
  field of type `String` (ADR-0027). It is read from `TYPESAFE_API_KEY`, and never written into
  a tracked file.

## Working practices

- **Verify against the source, never from memory.** The Jev API was launched on 2026-09-15 and
  changes quickly: read the documentation or a real response, and keep all JSON handling in
  `Codec`. The same goes for library versions and tool behaviour — run the command.
- **Measure before you write a number**, and prefer the measurement to an adjective.
- **When your change touches a file, read that file end to end.** The sentences describing the
  thing you changed are the ones a diff hides.
- **User-facing prose is for a non-native reader at about B2 English
  ([ADR-0015](docs/adr/0015-user-facing-prose-for-a-non-native-reader.md)).** That covers the
  README, `docs/guide/`, Scaladoc, `CONTRIBUTING.md` and the CHANGELOG. `docs/adr/`, `docs/tasks/` and this
  file are exempt.
- **Tests against the real API cost money.** They live in separate sbt projects, run only when
  `TYPESAFE_API_KEY` is set, and never run in a loop without a limit.
