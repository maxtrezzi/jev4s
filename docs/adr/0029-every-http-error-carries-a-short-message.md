# ADR-0029: Every HTTP error carries a short, readable message

- **Status:** Accepted
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** [ADR-0024](0024-http-retries-and-configuration.md), [ADR-0026](0026-http-400-is-a-rejected-request.md)

## Context

[ADR-0026](0026-http-400-is-a-rejected-request.md) gave `Rejected` and `Unexpected` the message
that `Codec.errorMessage` reads from a body, and the body unchanged when it is not the
documented shape. `ServerError(status, body)`, from
[ADR-0024](0024-http-retries-and-configuration.md), still carried the raw body.

A 5xx often comes from a proxy or a load balancer in front of the API, not from the API, and
its body is then an HTML page. `ServerError` is retryable, so it reaches `onEvent` in each
`JevEvent.Retrying`, which a caller sends to a log: one failed call writes the page once for
each retry, and once more if the caller logs the final error.

## Forces

- **Keeping the raw body** loses nothing, but makes the log the place to read HTML.
- **Dropping the body** of a non-JSON error loses the one clue a proxy gives, such as
  `Bad Gateway`.
- **A bound on the body** keeps the start of it, which is where a status line or a title is,
  and keeps each error to a size a log line can hold.

## Decision

- `JevError.ServerError(status, message)`: the message comes from `Codec.errorMessage`, like
  `Rejected` and `Unexpected`.
- `Codec.errorMessage` returns a body that is not the documented shape cut to its first 200
  characters, with `…` after a cut. A shorter body comes back unchanged.

## Consequences

- Every `JevError` built from an HTTP response carries a message of at most 201 characters,
  unless the API itself sends a longer one in the documented shape.
- A pattern that named the field, `ServerError(status = s, body = b)`, no longer compiles;
  positional patterns are unchanged.
- The full body of a failed response is not kept anywhere. A caller who needs it can read it
  with a `Transport` of their own.
