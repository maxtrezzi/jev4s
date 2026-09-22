# ADR-0025: Client events through `onEvent`, replacing `onReply`

- **Status:** Accepted
- **Date:** 2026-09-22
- **Supersedes:** [ADR-0010](0010-reply-metadata-through-onreply.md)
- **Amends:** [ADR-0022](0022-the-scala-3-client-api.md), [ADR-0023](0023-the-scala-2-13-client-api.md)

## Context

[ADR-0010](0010-reply-metadata-through-onreply.md) gave the client one callback, `onReply`,
called with the `Reply` of every successful reply, and rejected built-in logging. M5 added
retries inside `JdkTransport` ([ADR-0024](0024-http-retries-and-configuration.md)), and they are
invisible: a call that succeeds on its third attempt after two 429s looks like any other, and
rate-limit trouble shows only once it becomes a failure.

The common practice for a library, in Scala and on the JVM, is not to pick a logging backend
for the application, and to expose what it does as typed events the caller routes to a logger,
metrics or tracing; cats-retry's `onError` hook for each retry is an example.

## Forces

- **Built-in logging**, through `System.Logger` or `slf4j-api`, still decides for the caller
  where and how events are written, and `slf4j-api` would be a second runtime dependency against
  [ADR-0005](0005-minimal-dependencies-ujson-and-the-jdk.md).
- **A second callback, `onRetry`, next to `onReply`**, changes nothing that exists, but every
  new kind of event adds a parameter to every constructor, in both modules.
- **One callback taking an `enum` of events** is one parameter for good, and a caller handles
  events with an exhaustive match. Adding a kind of event later breaks that match — as a
  warning at each place that must handle the new event.
- **A trailing `case _`**, the usual defence against new cases, is flagged as unreachable by
  Scala 3 when the match is already exhaustive, and fails a build with `-Werror`.
- Nothing is published, so replacing `onReply` costs no user anything.

## Decision

- `JevEvent` is an `enum` in Scala 3 and a `sealed abstract class` in 2.13, with two cases:
  - `Replied(reply: Reply)` — once per successful call, after decoding, not once per attempt;
  - `Retrying(error: JevError, retry: Int, delay: FiniteDuration)` — sent by `JdkTransport`
    just before the wait for retry number `retry`.
- The client takes `onEvent: JevEvent => Unit`, default `_ => ()`, in place of `onReply`:
  `JevClient(config, onEvent = …)` and `JevClient.withTransport(model, transport, onEvent = …)`,
  and `JevClient.create(…)` in 2.13. `JevClient(config)` passes the same `onEvent` to its
  `JdkTransport`. A transport of the caller's own sends no `Retrying`.
- ADR-0010's semantics stay: events run on the calling thread before `ask` returns, none is
  sent for an error, and an exception thrown by `onEvent` propagates.
- The Scaladoc tells callers to match exhaustively, with no `case _`.
- The library still never logs. The examples in `live/` route events to `System.Logger`.

## Consequences

- A caller sees each retry, with its cause and its delay, and can log it or count it.
- Logging and metrics are wired once, where the client is built, as ADR-0010 intended.
- A new kind of event is a source-visible change: each exhaustive match warns until it handles
  it. The CHANGELOG must say so.
- `Reply` no longer reaches the caller directly; it is inside `Replied`.
- Do not add a `case _` to the examples or the Scaladoc "to be safe": Scala 3 rejects it under
  `-Werror`, and it would hide the new event the exhaustivity check exists to show.
