# ADR-0003: Cross-build Scala 2.13 and Scala 3, from M1

- **Status:** Superseded by ADR-0009
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

Scala 2.13 is still widely used, for example by Apache Spark. The Scala 2.13 TASTy reader lets
a 2.13 project use a Scala 3 library, but it does not support match types or `inline`, and
Jev4s uses both. In February 2026 the Scala team recommended cross-compilation for libraries,
and reported compatibility problems from Scala 3.8 on.

## Forces

- **Scala 3 only** is simpler, but leaves 2.13 users out.
- **The TASTy reader** does not work with the API Jev4s wants.
- **Adding the cross-build at the end** would force a rewrite of every `enum`, `opaque type`
  and `given` written before it.
- Starting now costs more on the first milestone.

## Decision

Cross-build **2.13.16 and 3.7.3 from M1**. Shared code lives in `src/main/scala`;
version-specific mix-ins live in `src/main/scala-2.13` and `src/main/scala-3`.

## Consequences

- Native artifacts for both versions: `jev4s_2.13` and `jev4s_3`.
- Shared code may not use `enum`, `opaque type`, `given`, or significant indentation.
- CI must run `sbt +test`.

Sources:
- https://docs.scala-lang.org/scala3/guides/migration/compatibility-classpath.html
- https://www.scala-lang.org/blog/state-of-tasty-reader.html
