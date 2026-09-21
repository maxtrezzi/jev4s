# ADR-0009: Two native modules, Scala 3 and Scala 2.13, with no shared code

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** ADR-0003, ADR-0004
- **Amends:** —

## Context

The goal of [ADR-0001](0001-a-study-and-portfolio-project.md) gained a sharper form: Jev4s must
be a **showcase for Scala**, idiomatic and ergonomic, where compactness, clarity and type
safety stand out.

The cross-build of [ADR-0003](0003-cross-build-scala-2-13-and-3-from-m1.md) writes shared code
in the subset common to 2.13 and 3, and the cost shows from the outside:

- `Probability` is an `AnyVal`, which is boxed inside `Option`, `List` and generic code,
  instead of an `opaque type`;
- `sealed trait` and `case class` instead of `enum`; `implicit` instead of `given`; no
  `extension`;
- half of the API exists only for 2.13: `Key[A]`, `as(...)`, and `Answers.get: Option[A]` with
  its cast and its "same question" check ([ADR-0004](0004-two-apis-typed-keys-and-named-tuples.md));
- empty mix-ins per version, and rules about shared code to remember.

2.13 support is still useful, for Apache Spark for example, but it must not lower the Scala 3
version.

## Forces

- **Cross-build with shared code** lowers the Scala 3 module to the common subset.
- **Scala 3 only** is the best showcase, but loses 2.13 and Spark.
- **Scala 3 plus the TASTy reader** for 2.13 does not work: the reader supports neither
  `inline` nor match types.
- **Two public repositories** would duplicate the golden tests and CI, and give one library two
  artifact names.
- **Truly parallel development**, where a milestone is done only when both modules are, is
  slower, and would pull the design back towards what 2.13 can express.

## Decision

- One repository with **two sbt modules**, each written in the **native** style of its version:
  - `scala3/` → `jev4s_3`: `enum`, `opaque type`, `given`, `extension`, named tuples,
    `derives`;
  - `scala213/` → `jev4s_2.13`: `sealed trait`, `AnyVal`, `implicit`, typed keys.
- **No shared code.** The modules share only `golden/` (real Jev JSON, a test resource of
  both) and CI.
- The same `name := "jev4s"` in both modules, so that `%% "jev4s"` picks the right artifact for
  the user's Scala version.
- **Scala 3 first:** the Scala 3 module settles the design in each milestone; the 2.13 module
  follows one step behind.
- The Scala 3 API has a **static API** with named tuples (answer types computed with
  `Tuple.Map`, any number of questions) and a **dynamic API** (`Map[String, Question[?]]` →
  `Map[String, Answer]`) for questions built at runtime.
- The 2.13 API keeps the typed keys of ADR-0004, which are the idiomatic form in 2.13.
- [ADR-0008](0008-full-coverage-and-mutation-testing.md) applies to **both** modules.

## Consequences

- The Scala 3 module uses the whole language: it is the showcase.
- The 2.13 module is idiomatic 2.13, not a reduced Scala 3; a Spark example stays possible.
- No rules about shared code, and no version mix-ins.
- About twice the code and tests to write and to mutate.
- The two APIs differ, so README and examples are split by version.
- The modules can drift apart (JSON format, errors, retries). Shared golden tests are the main
  defence.
- To verify in M1: two modules with the same `name` and different Scala versions publish as
  `jev4s_3` and `jev4s_2.13` without a conflict.
- [ADR-0006](0006-scala-3-7-rather-than-3-3-lts.md) weighs more now that the Scala 3 module is
  the showcase; whether a Scala 3 LTS with named tuples exists is an open question.
