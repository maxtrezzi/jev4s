# ADR-0023: The Scala 2.13 client API: typed keys over the Scala 3 client's pieces

- **Status:** Accepted — client construction amended by ADR-0024; callback amended by ADR-0025; question match amended by ADR-0028
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** —

## Context

[ADR-0009](0009-two-native-modules-no-shared-code.md) gives the 2.13 module the typed keys of
[ADR-0004](0004-two-apis-typed-keys-and-named-tuples.md), the idiomatic form in 2.13, and says
it follows the Scala 3 module without inventing design of its own.
[ADR-0010](0010-reply-metadata-through-onreply.md) puts `onReply` on `JevClient.create` in
2.13. [ADR-0022](0022-the-scala-3-client-api.md) settled `Transport` and `ToState` for Scala 3.

## Forces

- **A key must not read an answer of the wrong type.** ADR-0004 found that a lookup by name
  alone compiles with a key from a different request and throws `ClassCastException` later,
  because the cast inside the lookup is erased.
- **Scala 2.13 has no named tuples and no match types**, so the answer type cannot be computed
  from a tuple of questions; it can travel with a key.
- **The dynamic API of Scala 3 (`askMap`)** has no separate reason to exist here: a key can be
  built at runtime from any name.

## Decision

- `Key[A <: Answer](name, question)`, built with `question.as("name")`. `Question`'s type
  parameter is bounded by `Answer`, as in Scala 3.
- `JevClient.create(model, transport, onReply = _ => ())`, with a private constructor, and
  `ask(state, keys: Key[_ <: Answer]*)` returning `Either[JevError, Answers]`.
- `Answers.get(key): Option[A]` returns the answer only when the question asked under the key's
  name **equals** the key's question; otherwise `None`. The one cast is inside `get`, after
  that check.
- `Transport` and `ToState` are the traits of ADR-0022, with implicit instances for `String`
  and every `ujson.Value`.
- No `askMap`, no `derives`: choice options are written by hand (`JevChoice.fromOptions`).

## Consequences

- A key from another request gives `None`, never a wrong type or an exception. The test named
  after that case fails if the check is removed.
- Every read is an `Option`, even for a key that was part of the request: the price of a lookup
  the compiler cannot check.
- The two modules share the transport, the state, `onReply`, the validation and the codec, and
  differ only in how answers are asked for and read.
- Do not compare keys by name only "to make `get` cheaper": that brings back the
  `ClassCastException` of ADR-0004.
