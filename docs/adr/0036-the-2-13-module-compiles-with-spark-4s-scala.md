# ADR-0036: The 2.13 module compiles with the Scala of the oldest Spark 4

- **Status:** Accepted
- **Date:** 2026-09-23
- **Supersedes:** —
- **Amends:** —

## Context

The 2.13 module compiles with Scala 2.13.18, the current patch release when M1 began; no ADR
pins the patch (M1). The main reason for a 2.13 module is Apache Spark (M8): Spark 4 is
published for Scala 2.13 only, and there is no `spark-core_3`.

The Scala versions of Spark 4, from the `spark-parent_2.13` poms on Maven Central (read
2026-09-23):

| Spark | Scala |
|---|---|
| 4.0.4 | 2.13.16 |
| 4.1.3 | 2.13.17 |
| 4.2.0 | 2.13.18 |

A Spark cluster puts its own `scala-library` on the classpath. The 2.13 standard library is
backward compatible across patch releases, not forward: code compiled with 2.13.18 may call a
method that 2.13.16 does not have.

## Forces

- **Supporting Spark 4.0 and 4.1** means compiling against their `scala-library`, 2.13.16.
- **The dependencies allow it.** `ujson_2.13` and `upickle-core_2.13` 4.4.3 depend on
  `scala-library` 2.13.16, and `geny_2.13` 1.1.1 on 2.13.8 (their poms, read 2026-09-23).
- **The test tools must exist for that patch**: the scoverage compiler plugin is published per
  full Scala version. To check when implementing.
- **Newer patches fix bugs.** Staying on 2.13.16 gives them up until Spark 4.0 is no longer
  supported.

## Decision

The `scala213` module, and so `scala213Live`, compile with **Scala 2.13.16**, the
Scala of the oldest supported Spark 4 release. The patch moves up when the oldest supported
Spark release moves up.

## Consequences

- A project on Spark 4.0, 4.1 or 4.2 can use `jev4s_2.13` without a newer `scala-library` than
  its cluster's.
- The README, `docs/guide/scala213.md` and `AGENTS.md` say 2.13.16.
- Do not raise the patch "to the latest" without checking the oldest Spark release still
  supported: that is what this ADR protects.
