# ADR-0033: Publish as jev4s, after asking TypeSafe AI and without waiting for an answer

- **Status:** Accepted
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** ADR-0007

## Context

[ADR-0007](0007-name-coordinates-and-package.md) chose the name `jev4s`, the coordinates
`io.github.maxtrezzi` and the package `io.github.maxtrezzi.jev4s`, and closed with one sentence:
if TypeSafe AI asks, the library changes its name. D2 asked what to do before publishing, and
the owner kept it open until M7, because a name on Maven Central cannot be taken back: an
artifact is never deleted, only deprecated, and a rename changes the coordinates, the package
and every import of every user.

What the sources say, read on 2026-09-22:

- TypeSafe AI publishes no brand or trademark guidelines for third-party projects. The Legal
  page lists only the Data Processing Agreement, the Master Customer Agreement and the Privacy
  Policy.
- The Master Customer Agreement, 16.4 "Publicity", says that nothing in it grants either party
  the right to use the name, brand or logo of the other. It does not forbid naming them; it
  grants nothing.
- The community already publishes clients whose names carry the marks: `scala-jev-sdk` is on
  Maven Central, and `zio-typesafe-ai` carries the company name.

## Forces

- The name says which API the library speaks. "4s" is the Scala convention for "for Scala", and
  a name without "jev" makes the library hard to find for the people who need it.
- The project is not commercial, it is Apache 2.0, and the README states that it is unofficial,
  which is the ordinary shape of using a mark to say what a library is compatible with.
- Asking first is the polite move, but an answer may never come, and waiting blocks M7 without
  reducing the risk.
- Renaming after release is expensive for users; renaming before the first release costs almost
  nothing.

## Decision

- **The library keeps the name `jev4s`** and publishes under it, without waiting for an answer.
- **The owner asked TypeSafe AI first**, on 2026-09-22, whether the name is allowed and whether
  guidelines exist, and told them that the library publishes as `jev4s` and changes its name at
  their request.
- **If TypeSafe AI asks for a change**, the library takes a new name, new coordinates and a new
  package, and the old artifact is deprecated on Maven Central, not deleted.
- **No mark beyond the name**: no logo, no `com.typesafe` group or package, and the README keeps
  the sentence that the project is not affiliated with, endorsed by, or part of TypeSafe AI.

## Consequences

- M7 is unblocked, and the first release carries the name the documentation already uses.
- The risk of a rename stays, and it now falls on the users of the first releases. The `0.x`
  versions of M7 say that the library is early, which makes that cost smaller.
- An answer from TypeSafe AI, of either kind, is recorded in `docs/tasks/open-decisions.md`
  under D2. This ADR is not edited: a change of the decision needs a new one.
