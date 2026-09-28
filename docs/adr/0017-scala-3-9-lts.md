# ADR-0017: Compile the Scala 3 module with Scala 3.9 LTS

- **Status:** Accepted
- **Date:** 2026-09-22
- **Supersedes:** [ADR-0006](0006-scala-3-7-rather-than-3-3-lts.md)
- **Amends:** —

## Context

[ADR-0006](0006-scala-3-7-rather-than-3-3-lts.md) compiled the Scala 3 module with 3.7.3,
because named tuples are standard only from 3.7 and the 3.3 LTS line does not have them. That
excluded every user on 3.3 LTS, and ADR-0006 accepted the cost because the only LTS line then
known had no named tuples.

Since [ADR-0009](0009-two-native-modules-no-shared-code.md) the Scala 3 module is the showcase,
which makes its target version weigh more. The download page of scala-lang.org, read on
2026-09-22, gives **"Scala LTS currently 3.9.0 — advised to be used for publishing
libraries"** (3.9.0 released 2026-09-03), with 3.3.x listed as the older LTS line, and
`org.scala-lang:scala3-compiler_3:3.9.0` is on Maven Central. 3.9 has named tuples.

Fixed: an artifact compiled with a Scala 3 minor version can be used only from a project on
that minor version or a later one. Whatever version the module is compiled with is the lowest
version its users can be on.

## Forces

- **Keep 3.7.3.** Reaches users on 3.7 and 3.8 as well as 3.9 and later. But 3.7 is not an
  LTS line, so the library would sit on a version the Scala team does not maintain long term,
  and move again later.
- **3.9 LTS.** The line meant for libraries that want a stable, long-supported base, and it
  has named tuples: the reason ADR-0006 could not use an LTS no longer applies. It excludes
  3.7 and 3.8 users, who are few while nothing is published.
- **3.3 LTS** still has no named tuples, so it still loses for the reason ADR-0006 gave.
- **Nothing is published yet**, so moving the lower bound now costs no user anything. After
  M7 raising it would break every user below the new version.

## Decision

The Scala 3 module is compiled with **Scala 3.9.0**, the first 3.9.x LTS release. It follows
the 3.9.x line in patch releases; moving to a later minor version is a new decision.

## Consequences

- The named-tuple API stays available, now on an LTS line.
- Users on Scala 3.3 to 3.8 cannot use the Scala 3 artifact. The README says so plainly.
- The lower bound is set before the first release, so M7 does not have to raise it.
- Do not "upgrade" to the newest Scala 3 (3.10 and later) as routine maintenance: every minor
  step raises the version users need. Patch releases of 3.9.x are free to take.
