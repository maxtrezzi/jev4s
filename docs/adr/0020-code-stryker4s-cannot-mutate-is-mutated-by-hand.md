# ADR-0020: Code Stryker4s cannot mutate is excluded, and its mutants are applied by hand

- **Status:** Accepted — static mutants widened by ADR-0024
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** [ADR-0008](0008-full-coverage-and-mutation-testing.md)

## Context

[ADR-0008](0008-full-coverage-and-mutation-testing.md) allows a mutant to be excluded with
`@SuppressWarnings` only when it is equivalent, and left "how both tools treat `inline` code"
to verify. [ADR-0019](0019-probability-literals-and-questions-compared-by-value.md) adds the
first `inline` code: the `Probability(...)` constructor, whose body runs in the compiler.

Stryker4s 1.1.1, the latest release on Maven Central on 2026-09-22, cannot mutate it. It places
each mutant behind a runtime switch and prints the switch as source. Printing a switch that
contains an `inline` modifier fails with *"Scala212 doesn't support inline modifiers"*, and the
whole run stops: no report, no result for any other file. The printer does not use the
`scala-dialect` setting; `strykerScalaDialect := scala.meta.dialects.Scala3` gave the same
failure. A runtime switch is also the wrong shape for this code: an `inline if` needs a
condition that is constant at compile time, and a switch never is.

scoverage has no such problem: the constructor's body is not a runtime statement, and the
module stays at 100%.

## Forces

- **No `inline` code in the library.** Gives up compile-time checks, which is what the Scala 3
  module is for, and M3 relies on them (`derives JevChoice`, the named-tuple API).
- **Exclude the file from Stryker4s.** Also excludes everything else in `Probability.scala`,
  which Stryker4s can mutate.
- **Exclude only the mutations inside the `inline` method, with `@SuppressWarnings`**, and
  apply the same mutations by hand. The tests run at compile time for this code
  (`compileErrors` in munit), so a hand-made mutant is killed when the build fails or a test
  does, the same outcome Stryker4s reports as `CompileError` or `Killed`.
- **Wait for Stryker4s to fix it.** The run fails today, and the rule of ADR-0008 cannot wait.

## Decision

- A mutation that Stryker4s cannot build, because it falls inside `inline` code, is excluded
  with `@SuppressWarnings(Array("stryker4s.mutation.<Name>", ...))` on the `inline` method,
  naming only the mutation types that occur in it.
- **Every excluded mutant is applied by hand**, the tests are run, and the result is recorded
  in [`docs/testing/equivalent-mutants.md`](../testing/equivalent-mutants.md), in a section of
  its own, with the date and how each mutant was detected. A mutant that survives by hand is
  treated like a survivor in a report: kill it with a test.
- The hand check is repeated whenever the `inline` method or its tests change.
- Stryker4s reports these mutants as `Ignored`; `build/check-mutants.py` does not count them
  as detected or as undetected.

## Consequences

- `inline` code can be used, and is held to the same standard as the rest.
- The check of `inline` code depends on a person repeating it. Nothing in CI fails if the
  `inline` method changes and the table is not updated.
- The `@SuppressWarnings` list must match the method's content. A mutation type left off the
  list stops the whole Stryker4s run, which is loud, not silent.
- M3 adds much more `inline` code. If the hand check grows past a few methods, the cost goes
  to [`../tasks/`](../tasks/README.md), and a scripted version of it is the next decision.
- Do not remove the annotation to "get Stryker4s to cover it": the run fails for every file.
