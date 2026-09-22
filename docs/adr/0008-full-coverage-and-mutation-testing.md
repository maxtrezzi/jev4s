# ADR-0008: Full coverage and mutation testing

- **Status:** Accepted — mutation threshold amended by ADR-0016; CI schedule amended by ADR-0018; mutation exclusions widened by ADR-0020
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

The owner wants test quality that can be measured. Coverage alone does not measure it. In a
hand-run experiment on the rule "a Score needs 2 to 10 levels" (`n < 2 || n > 10`), a suite
with 100% line and branch coverage — sizes 1, 5 and 20 — let 2 of 5 mutants survive: `n <= 2`
and `n >= 10`. Adding the boundary sizes 2, 10 and 11 killed all five.

## Forces

- **A threshold below 100%**, such as 90%, is more comfortable, but leaves room for gaps that
  nobody explains.
- **PIT (pitest)** is the Java standard, but it mutates bytecode, and on Scala it produces many
  mutants in compiler-generated code. **Stryker4s** mutates Scala source.
- Mutation testing is slow: it runs the tests many times.
- Some mutants are equivalent — they change nothing observable — and no test can kill them.

## Decision

1. **100% statement and branch coverage** with sbt-scoverage. Below 100% the build fails
   (`coverageFailOnMinimum := true`).
2. **Mutation testing** with Stryker4s, `break = 100`.
3. **No surviving mutant without a reason.** Each survivor is either killed with a new test,
   or is an equivalent mutant: excluded with `@SuppressWarnings` in the code **and** recorded
   in [`docs/testing/equivalent-mutants.md`](../testing/equivalent-mutants.md) with the
   explanation.
4. **No coverage exclusions.** Code that cannot be tested deterministically does not go into
   a published artifact: examples and tests against the real API live in separate sbt
   projects.
5. **Design for testability:** waiting and randomness are injected (`Sleeper`, a random
   source), so retries are tested without real waits.

## Consequences

- Tests check behaviour, not only that code ran.
- The register of equivalent mutants is study material of its own.
- Running mutation testing on every pull request lasts only while it is affordable;
  otherwise it runs nightly. Decide after the first measurement.
- To verify: coverage exclusions with Scala 3, and how both tools treat `inline` code.

Sources:
- https://github.com/scoverage/sbt-scoverage
- https://github.com/stryker-mutator/stryker4s
- https://stryker-mutator.io/blog/stryker4s-v1/
- https://stryker-mutator.io/docs/stryker4s/configuration/
