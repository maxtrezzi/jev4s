# ADR-0018: Mutation testing runs in CI on every pull request

- **Status:** Accepted
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** [ADR-0008](0008-full-coverage-and-mutation-testing.md)

## Context

[ADR-0008](0008-full-coverage-and-mutation-testing.md) runs Stryker4s on every pull request
*while that is affordable, otherwise nightly*, and leaves the choice to the first measurement.
M1 made it: about 11 s of wall time per module on the development machine, sbt start-up
included, for 54 mutants each. On `ubuntu-latest` each module job — tests with coverage,
Stryker4s and `build/check-mutants.py` — took 1 min 35 s. The two module jobs run in parallel.

## Forces

- **Keep it on every pull request.** A mutant that survives is caught in the pull request that
  introduced it, while the change is still in the author's head. The cost is small today.
- **Nightly.** A shorter wait on each pull request, but a failure lands on `dev` after the
  merge, detached from the change that caused it, in a repository with one maintainer who is
  not obliged to read a nightly report.
- **Out of CI, run by hand before a pull request.** A sibling project chose this, for reasons
  that do not hold here:
  - its job could only be green, because its threshold was 0; here
    [ADR-0016](0016-undetected-mutants-are-checked-from-the-report.md)'s
    `check-mutants.py` fails on a single undetected mutant, so the job is a real check;
  - it had an equivalent mutant that made 100% unreachable; here an equivalent mutant is
    excluded with `@SuppressWarnings` and recorded, so 100% stays reachable;
  - its run tripled the wait of a pull request; here it adds about 11 s to a 1 min 35 s job.
- **The cost grows with the code.** M2 to M5 add the codec, two APIs and the HTTP transport,
  and Stryker4s runs the covering tests once per mutant.
- **Tests against the real API cost money.** They live in separate sbt projects
  ([ADR-0008](0008-full-coverage-and-mutation-testing.md)), and Stryker4s runs per module, so
  it never reaches them. A mutation run over a paid suite would repeat the paid calls once per
  mutant.

## Decision

- Stryker4s and `build/check-mutants.py` run **in each module job, on every pull request and
  every push to `dev` and `main`**, as the M1 workflow already does. The nightly alternative of
  ADR-0008 is not taken.
- They stay a failing check: a pull request with an undetected mutant does not merge.
- Stryker4s runs only on the published modules (`scala3`, `scala213`), never on a project that
  calls the real API.
- The decision is reopened, by a new ADR, if one module job grows past **5 minutes** on
  `ubuntu-latest`. The measurement goes to [`../tasks/`](../tasks/README.md) first.

## Consequences

- A surviving mutant is found before the merge, next to the change that made it.
- Every pull request waits for the mutation run, including one that changes only documentation.
- The wait grows with each milestone. Nothing reduces it automatically; the 5-minute mark is
  the trigger to decide again.
- Do not add a live-test project to the Stryker4s run, or aggregate it into a module that
  Stryker4s mutates, "to get coverage of the real API": every mutant would repeat the paid calls.
