# ADR-0001: Jev4s is a study and portfolio project

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

Jev is TypeSafe AI's "System One" model, launched on 2026-09-15. It returns typed decisions
with probabilities rather than text. In its first week the community published at least four
Java clients and two Scala clients: `scala-jev-sdk`, which is independent of the effect system
through sttp backends, and `zio-typesafe-ai`, built on ZIO. The official SDKs are for Python
and TypeScript.

## Forces

- Another general-purpose client adds little for users: the Scala ground is already covered.
- The owner wants to learn advanced Scala 3 (named tuples, match types, `inline`,
  derivation), publishing to Maven Central, and a demanding test discipline, and wants a
  public piece of work that shows it.
- Contributing to `scala-jev-sdk` teaches less of that, because its main shape is already
  decided. It stays possible for single features, such as deriving Choice options from an
  `enum`.
- Using Jev inside a work project is not the goal now.

## Decision

Jev4s is first a project for **learning** and for the **portfolio**. Quality — tests,
documentation, continuous integration, and an API that shows Scala at its best — counts more
than novelty. [ADR-0009](0009-two-native-modules-no-shared-code.md) makes the last point
explicit: the library is meant to be a showcase for Scala, where compactness, clarity and type
safety stand out.

## Consequences

- There is room to experiment with the language, and to choose a design because it teaches
  something.
- The value lies in the quality and in the recorded reasoning (these ADRs), not in the
  number of users. Probably few people will use it, and that is acceptable.
- The public README must credit the projects that inspired the design.
