# ADR-0015: User-facing prose is written for a non-native reader

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

Jev4s is written in English, and many Scala developers do not read English as a first
language. The owner is one of them.

## Forces

- Short text is good, but text compressed into metaphor or idiom is hard to parse without a
  dictionary.
- Records written for people who already hold the context — ADRs, work items, agent guidance —
  can be denser, and an accepted ADR's body cannot be edited anyway.

## Decision

- The README, the Scaladoc, `CONTRIBUTING.md`, the CHANGELOG and any user manual are written for
  a technical reader at about **B2 English** who does not read it as a first language.
- Two tests for each sentence: would a reader who does not yet know the mechanism parse it,
  and would a non-native reader parse it without a dictionary?
- Brevity stays the default. What the rule forbids is compressing meaning into metaphor or
  idiom, not length.
- `docs/adr/`, `docs/tasks/` and `AGENTS.md` are exempt.

## Consequences

- User-facing text is longer in places, and plainer everywhere.
- A reviewer checks the register as well as the facts.
