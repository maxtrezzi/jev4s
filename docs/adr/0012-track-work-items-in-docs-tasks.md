# ADR-0012: Track work items in `docs/tasks/`

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

The milestones M1 to M8 were planned before this repository existed. Work needs a place that
says what to do next, whether it is done, and what was found while doing it.

## Forces

- ADRs answer *why*; they are closed to later findings
  ([ADR-0011](0011-record-decisions-as-adrs.md)), so they cannot hold status.
- An issue tracker keeps status away from the code and from the branch that changes it.
- A verification whose answer is lost has to be done again.

## Decision

- [`docs/tasks/`](../tasks/README.md) holds the work items: milestones (`M1`, `M2`, …), open
  decisions waiting on the owner (`D1`, `D2`, …), and later items as they appear.
- Each entry has a status (`Not started`, `In progress`, `Blocked`, `Needs decision`, `Done`),
  and closing an entry records what was **found**, not only that it finished.
- The status is updated **in the same commit** as the work it describes.
- Identifiers never change: an item is retired in place, and new work takes the next free
  number.
- A decision waiting on the owner is not guessed: it blocks the code that depends on it.

## Consequences

- One place answers "what next?" and "is it done?".
- Keeping the status current is a discipline; nothing enforces it.
- `docs/tasks/` wins on status, the ADRs win on a decision.
