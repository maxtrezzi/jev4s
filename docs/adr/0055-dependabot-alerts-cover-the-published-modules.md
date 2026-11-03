# ADR-0055: Dependabot alerts cover the published modules, and a task fixes them

- **Status:** Accepted
- **Date:** 2026-10-27
- **Supersedes:** —
- **Amends:** —

## Context

jev4s is public, and `0.1.0` is on Maven Central. Until 2026-10-27 the only security feature on
the repository was private vulnerability reporting (`SECURITY.md`, T25). Dependabot alerts,
secret scanning and push protection were off, and nothing told the maintainer when a dependency
of a published module got a known vulnerability.

Dependabot scans the dependency graph of the default branch, `dev`. GitHub does not read an sbt
build: on 2026-10-27 the graph of jev4s held only the GitHub Actions of its workflows. An sbt
build reaches the graph through the dependency submission API, which
`scalacenter/sbt-dependency-submission` calls with a snapshot that sbt resolves.

Two decisions limit what a fix can be. The 2.13 module compiles with Scala 2.13.16, and a
dependency that needs a newer `scala-library` fails the build
([ADR-0036](0036-the-2-13-module-compiles-with-spark-4s-scala.md)). The published modules have
one runtime dependency, ujson ([ADR-0005](0005-minimal-dependencies-ujson-and-the-jdk.md)).

## Forces

- **The whole build has 242 packages, and 25 have known vulnerabilities.** That is the count on
  osv.dev of a snapshot of every project, on 2026-10-27. All 25 come from Spark 4.0.4 in the
  Spark example, or from the compiler, the REPL and Scaladoc in the Scala 3 tool configurations.
  None of them is in a published jar. Spark is `Provided`, but the snapshot gives it the
  `runtime` scope, because sbt resolves `Provided` in `compile-internal`.
- **jev4s cannot fix those alerts.** The Spark example uses the oldest supported Spark 4 on
  purpose (ADR-0036), and the tool configurations are the compiler's own. An alert that nobody
  can act on hides the one that matters.
- **The four published modules, with their tests, have 19 packages and none with a known
  vulnerability**, on the same day. Those are the packages that a user of jev4s receives:
  `scala-library`, ujson, `upickle-core` and geny, and munit for the tests.
- **A pull request from a bot does not know the constraints.** A newer ujson can need a newer
  `scala-library`, against ADR-0036, and every change goes through a work item and its branch
  ([ADR-0012](0012-track-work-items-in-docs-tasks.md),
  [ADR-0013](0013-one-branch-per-task-work-lands-on-dev.md)).

## Decision

- **Dependabot alerts, secret scanning and push protection are on.** Dependabot security
  updates and version updates stay off: Dependabot opens no pull requests.
- **`.github/workflows/dependency-graph.yml` submits the dependency graph on each push to `dev`**,
  with `scalacenter/sbt-dependency-submission`. It submits the four published modules and their
  `compile` and `test` dependencies. It ignores the projects that are not published (`root`, the
  live projects and the Spark example) and the Scala tool configurations (`scala-tool`,
  `scala-doc-tool`, `scala-repl-tool`).
- **An alert on a published module becomes a task**, which fixes it within ADR-0005 and
  ADR-0036, or records why the vulnerable code is never called. A fix to a released version
  is a patch release (ADR-0052).

## Consequences

- An alert means that a jar on Maven Central, or the tests of one, has a dependency with a known
  vulnerability. On 2026-10-27 there are none.
- Vulnerabilities in Spark and in the Scala tools are not reported. A user who runs jev4s on
  Spark gets the alerts of their own Spark build.
- A new published module is submitted without any change to the workflow. A new project that
  is not published must be added to `modules-ignore`, or its alerts come back.
- Do not remove `modules-ignore` or `configs-ignore` "to see everything": that brings back the 25
  alerts that jev4s cannot fix. Do not turn on Dependabot security updates "to fix alerts
  faster": the fix must stay within ADR-0005 and ADR-0036.
- The workflow does not run on a pull request, so a pull request that changes it shows the
  result only after the merge into `dev`. `workflow_dispatch` runs it again by hand.
