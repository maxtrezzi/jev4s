# ADR-0005: Minimal dependencies: ujson and the JDK

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

The client needs JSON and HTTP. Every runtime dependency becomes a dependency of every user,
and must be published for each Scala version Jev4s supports.

## Forces

- **sttp and upickle** give a choice of HTTP backends; that is what `scala-jev-sdk` already
  offers.
- **circe** is heavier than the job needs.
- **jsoniter-scala** is very fast, but macro-based, which is more complex across two Scala
  versions.
- `java.net.http` is part of the JDK and needs no dependency.

## Decision

- JSON: **ujson**, which is published for 2.13 and 3.
- HTTP: **`java.net.http`** from the JDK.
- Tests: **munit**.

## Consequences

- One runtime dependency.
- One HTTP backend, with no choice for the user.
