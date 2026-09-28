# ADR-0022: The Scala 3 client API, with compile-time checks and no inline code

- **Status:** Accepted — client construction amended by ADR-0024; callback amended by ADR-0025
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** —

## Context

[ADR-0009](0009-two-native-modules-no-shared-code.md) fixes the two APIs of the Scala 3 module:
a static one over named tuples, with answer types computed from the questions, and a dynamic
one over `Map[String, Question[?]]`. [ADR-0010](0010-reply-metadata-through-onreply.md) fixes
`onReply`. M3 settles the rest of the shape: how the client is built, how it reaches HTTP, how
a state of the caller's own type is sent, how the compiler checks a call, and how
`derives JevChoice` works.

Fixed: Stryker4s cannot mutate `inline` code, and such code is checked by hand
([ADR-0020](0020-code-stryker4s-cannot-mutate-is-mutated-by-hand.md)). Every `inline` method
added here is a cost that lasts.

## Forces

- **Compile-time checks with `inline` and `compiletime.error`**, as M3 first planned, give
  custom messages anywhere, but put the whole static API outside mutation testing.
- **Compile-time checks with type classes** found by the compiler (`ValueOf` for the names of a
  named tuple and for the cases of an enum, recursive givens over tuples) run as ordinary code,
  which scoverage and Stryker4s see, and `@implicitNotFound` gives a custom message where it
  matters.
- **Mistakes whose message comes from the compiler**: a name that was not asked, an answer read
  as the wrong type, a tuple without names. Its messages there are clear, and wrapping them
  would need `inline`.
- **The transport.** HTTP, status codes, `Retry-After` and retries are M5. A client that
  depends on a body-in, body-out interface can be tested now with a fake, and callers can fake
  it too.
- **The state.** Jev takes text or any JSON. A type class lets a caller send their own type
  without converting it at every call.

## Decision

- **`JevClient(model, transport, onReply = _ => ())`.** The model has no default. M5 adds the
  way to build one from the environment; this constructor stays.
- **`Transport`** is a public trait with one method, `send(body: String): Either[JevError,
  String]`. It returns the body of a successful response, or the error; status codes and
  retries live inside it.
- **`ToState[S]`**, a type class with givens for `String` (sent as text) and every
  `ujson.Value`. A missing instance fails with *"no ToState[S]: give one, …"*.
- **`ask(state, namedTuple)`** returns `Either[JevError, NamedTuple.Map[Q, AnswerOf]]`, where
  `AnswerOf` is a match type from a question to its answer. The names come from
  `QuestionNames`, and `AllQuestions` checks that every value is a question: *"every value in
  the named tuple must be a question: Noul, Score or Choice"*. Both are given instances, not
  `inline` code.
- **`askMap(state, Map[String, Question[?]])`** returns `Map[String, Answer]`.
- The answers become the typed named tuple through **one cast, inside the client**: a named
  tuple is its tuple of values at runtime, and the order of the answers is the order of the
  questions (M2). No cast is visible to a caller.
- **`derives JevChoice`** is `JevChoice.derived`, built from the enum's `Mirror` and a `Cases`
  type class over `ValueOf` of each case. Keys are the case names in snake_case, computed with
  `Locale.ROOT`; a description comes from `Described`. A case with parameters has no
  `ValueOf`, so derivation fails with *"JevChoice can be derived only for an enum whose cases
  have no parameters; …"*.
- Invalid requests are not sent: the `Validator` runs first and returns `InvalidRequest`.

## Consequences

- The static API and the derivation have no `inline` code, so the table of ADR-0020 does not
  grow, and the whole client is under mutation testing.
- A call with a mistake does not compile, and the tests prove it with `compileErrors`. Three of
  those messages are the compiler's, not ours, and could change with a Scala release; the
  tests check their key words only.
- A caller can test code that uses jev4s with a `Transport` of their own, without a server.
- An error that says what to fix needs `@implicitNotFound` on a type class; the price is a few
  public types that exist only as evidence (`AllQuestions`, `QuestionNames`, `Cases`).
- Do not turn `ask` into an `inline` method "to get a nicer message for a missing name": it
  takes the whole static API out of mutation testing (ADR-0020).
