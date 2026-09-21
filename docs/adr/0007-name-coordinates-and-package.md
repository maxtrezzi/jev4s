# ADR-0007: Name, coordinates and package

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

The library needs a name, Maven coordinates, a package and a licence before any code is
published.

## Forces

- The "4s" suffix is the Scala convention for "for Scala", as in http4s and json4s.
- `scala-jev` is too close to the existing `scala-jev-sdk`.
- On Maven Central a namespace of the form `io.github.<user>` is verified through the GitHub
  account, with no domain to own.
- `com.typesafe` looks natural for a TypeSafe AI client, but in the Scala world it belongs to
  Lightbend, formerly Typesafe (Typesafe Config, for example).

## Decision

- Name: **jev4s**.
- groupId: **`io.github.maxtrezzi`**; package: **`io.github.maxtrezzi.jev4s`**.
- Never `com.typesafe`.
- The README states: "Not affiliated with TypeSafe AI".
- Licence: Apache 2.0.

## Consequences

- "Jev" is the name of a TypeSafe AI product. If they ask, the library changes its name.
