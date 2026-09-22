# ADR-0006: Scala 3.7 rather than 3.3 LTS

- **Status:** Superseded by ADR-0017
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

Named tuples are a standard feature from Scala 3.7. The Scala team advises publishing libraries
on the 3.3 LTS line for the widest compatibility: an artifact compiled with a newer Scala 3
cannot be used from a project on an older one.

## Forces

- **3.3 LTS** reaches the most users, but has no named tuples, so no named-tuple API.
- **Two Scala 3 artifacts** (3.3 and 3.7) is too much complexity for a study project.

## Decision

The Scala 3 artifact is compiled with **Scala 3.7.3**.

## Consequences

- The named-tuple API is available.
- Users on Scala 3.3 to 3.6 cannot use the library. The README must say so plainly.
