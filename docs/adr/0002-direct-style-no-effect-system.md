# ADR-0002: Direct style, no effect system

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

A Scala HTTP client has to choose how it exposes blocking work. `scala-jev-sdk` is independent
of the effect system through sttp backends; `zio-typesafe-ai` uses ZIO.

## Forces

- Simplicity and a small footprint are goals of their own here
  ([ADR-0005](0005-minimal-dependencies-ujson-and-the-jdk.md)).
- On JDK 21 and later, virtual threads make blocking code scale without an effect runtime.
- Tagless final (`F[_]`) is more flexible, but more complex to read and to test, and it would
  make every signature longer.
- sttp backends are already done well by `scala-jev-sdk`; doing the same again teaches little.

## Decision

The API is synchronous and returns `Either[JevError, A]`. There is no `F[_]` and no effect
system dependency.

## Consequences

- Easy to understand and to test.
- A cats-effect or ZIO user wraps a call themselves: `IO.blocking(client.ask(...))` or
  `ZIO.attemptBlocking(...)`.
- A request cannot be cancelled by the library.
- An optional `F[_]` module stays possible later without changing this API.
