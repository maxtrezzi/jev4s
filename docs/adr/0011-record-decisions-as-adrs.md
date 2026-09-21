# ADR-0011: Record decisions as ADRs, discussions outside the repository

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

ADR-0001 to ADR-0010 were first written during design, before this repository existed. They
are rewritten here so that the reasoning behind the library lives next to its code. This ADR
fixes how decisions are recorded from now on.

## Forces

- A decision without its reasons gets reopened, or "simplified" away by someone who does not
  know what it protects.
- Design discussions are long, repetitive and full of rejected ideas. Committing them makes the
  history hard to read, and a repository that will be public must not carry half-formed
  material.
- An ADR that is edited after the fact stops being evidence of what was decided and why.

## Decision

- Two artifacts with different audiences:
  - **the discussion log**, one dated file per discussion, kept by the owner **outside this
    repository**. Every substantive design discussion is logged: what was asked, what was
    weighed, what was rejected, what is still open;
  - **`docs/adr/NNNN-title.md`**, tracked and publishable. Whenever a discussion settles
    something that constrains future code, it gets an ADR in the
    Context → Forces → Decision → Consequences shape, with a row in the index.
- Content moves from the log to an ADR by **rewriting**, never by copying.
- Accepted ADRs are immutable in their substance: a change is a new ADR that supersedes or
  amends the old one, and only the old one's `Status` line changes. Later findings go to
  [`docs/tasks/`](../tasks/README.md) ([ADR-0012](0012-track-work-items-in-docs-tasks.md)).
- Nothing tracked in this repository refers to where the discussion log is kept.
- [`build/check-docs.py`](../../build/check-docs.py) checks the index against the files, the
  status shapes, both ends of every amendment, and every link against what a fresh clone
  contains. It runs in CI.

## Consequences

- The reasons for each constraint are one click from the code.
- Writing an ADR costs time on every settled discussion. Recording too little is the failure
  to avoid: a short ADR beats none.
- The ADRs must be written to be read by strangers, even while the repository is private.
