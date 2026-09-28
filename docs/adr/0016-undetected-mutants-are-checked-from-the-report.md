# ADR-0016: Undetected mutants are checked from the report, not by Stryker's threshold

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** ADR-0008

## Context

[ADR-0008](0008-full-coverage-and-mutation-testing.md) allows no unexplained surviving mutant,
and enforces it with a Stryker4s `break` threshold of 100. Stryker4s 1.1.1 refuses that
configuration: it requires `low` to be greater than `break`, and `low` cannot exceed 100, so
loading `break = 100` fails with *"'low' (100) must be greater than 'break' (100)"*. The
highest `break` it accepts is 99.

## Forces

- With `break = 99`, a module with more than 100 mutants passes with one survivor: 1 in 150 is
  a score of 99.3%. The rule of ADR-0008 would then hold only while a module is small.
- Stryker4s writes a JSON report with the status of every mutant, in the
  mutation-testing-report schema.
- An equivalent mutant is excluded in the source with `@SuppressWarnings`, so it never appears
  in the report as a survivor.
- Asking the tool maintainers for `break = 100` is possible, but the rule should not wait on
  it.

## Decision

- `stryker4s.conf` keeps `break = 99`, the strictest value Stryker4s accepts, and adds the JSON
  reporter.
- [`build/check-mutants.py`](../../build/check-mutants.py) reads a module's latest JSON report
  and **fails on any `Survived` or `NoCoverage` mutant**, listing each one with its file, line
  and mutation.
- CI runs it after Stryker4s in each module job. Locally:
  `sbt "project scala3" stryker && python3 build/check-mutants.py scala3`.
- `CompileError` mutants count as detected: the compiler refused the change. Under
  `-language:strictEquality` in the Scala 3 module, a mutant that compares a `Probability`
  with a `Double` using `==` does not compile.

## Consequences

- ADR-0008's rule holds at any module size.
- One more script to maintain, and it depends on the report schema. If Stryker4s changes the
  schema, the script fails loudly rather than passing in silence, because it reads fields by
  name.
- Running Stryker4s alone, without the script, is not the full check. `AGENTS.md` and CI say
  so.
