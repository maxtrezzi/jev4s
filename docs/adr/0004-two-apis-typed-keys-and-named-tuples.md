# ADR-0004: Two APIs: typed keys and named tuples

- **Status:** Superseded by ADR-0009
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

Under the cross-build of [ADR-0003](0003-cross-build-scala-2-13-and-3-from-m1.md), named tuples
exist only in Scala 3, but Scala 2.13 still needs a typed API.

## Forces

- **Keys only** would work on both versions, but would teach less.
- **One name, `ask`, with overloads** for varargs and for a named tuple risks ambiguity, and
  was not verified.
- A key carries a name and a question. With a key built for a different request, a lookup by
  name alone compiled and then threw `ClassCastException` at runtime, because the cast inside
  the lookup is erased.

## Decision

- `ask(state, keys*)` with **typed keys** (`Key[A]`), in 2.13 and 3.
- `askNamed(state, namedTuple)` with **named tuples**, in Scala 3 only.
- Different names, to avoid overload problems between varargs and named tuples.
- All logic lives in one shared `runAll`; the two APIs are thin entry points.
- `Answers.get` returns the answer only when the key's question equals the one in the request;
  otherwise it returns `None`.

## Consequences

- 2.13 is supported; Scala 3 has the more convenient API.
- Two APIs to document and to test.
- `get` returns `Option[A]`, even for a key that was part of the request.
