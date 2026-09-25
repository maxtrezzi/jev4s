# ADR-0042: A config that `java.net.http` refuses is an error, not an exception

- **Status:** Proposed
- **Date:** 2026-09-25
- **Supersedes:** —
- **Amends:** —

## Context

The API returns `Either[JevError, A]` and does not throw
([ADR-0002](0002-direct-style-no-effect-system.md)), and the API key never appears in a log
([ADR-0027](0027-the-api-key-is-a-type-and-travels-over-tls.md)). The review of T17 found both
broken by a `JevConfig` built by hand. `java.net.http` throws `IllegalArgumentException` for:

- a base URL whose scheme is not `http` or `https`, or that has no host, such as
  `URI.create("api.typesafe.ai")`: on `ask`;
- a `timeout` of zero or less: when `JdkTransport` builds its `HttpClient`, so
  `JevClient(config)` itself throws;
- an `Authorization` header with a character it refuses. `ApiKey("abc\n")`, a key read from a
  file with its final newline, throws `invalid header value: "Bearer abc…"`: **the message holds
  the whole key**, and a message is what a logger prints.

Measured on JDK 21: the scheme is compared without case, and a header value may hold a tab and
the characters from space to `ÿ`, except `\u007f`. `JevConfig.fromEnv` trims the key and
checks the URL, so only a config built in code gets here.

## Forces

- **ADR-0027 left a config built by hand unchecked**, because a case class constructor cannot
  return an error value, and because the transport's errors were HTTP outcomes, some retried.
  Its concern was the `https` rule, a policy. These checks are not a policy: each one stops an
  exception that would otherwise reach the caller.
- **Checking in the `JevConfig` constructor** (`require`) still throws, earlier.
- **Trimming the key in `ApiKey`** fixes the newline alone, and changes a value the caller gave
  without saying so.
- **Catching `IllegalArgumentException` around the request** gives no message of its own, and
  the one it catches holds the key.
- **A problem in `JevError.InvalidRequest`** reuses a type, but its problems are about the
  questions, and the `Validator` finds them before the transport runs.
- **A new `JevError` case** breaks an exhaustive match of a caller. There is no release yet, so
  no caller to break.

## Decision

- `JevError.InvalidConfig(message: String)`, in both modules: the request was not sent, because
  the config has a value that HTTP cannot carry. It is not retryable.
- `JdkTransport` checks its config when it is built, with the JDK's rules above, and builds its
  own `HttpClient` only when it first sends. `send` returns `InvalidConfig` with every problem,
  joined by `; `, without sending and without an event. A problem names the base URL or the
  timeout; it never shows the key.
- The `https` rule stays where ADR-0027 put it: in `JevConfig.fromEnv` only.

## Consequences

- A config built by hand no longer makes `JevClient(config)` or `ask` throw, and the key never
  reaches an exception message. What still throws is what ADR-0002's consequences and the
  Scaladoc name: an `InterruptedException`, and an exception from the caller's own `onEvent`.
- A client over a bad config is built without error, and fails on each call. The `Validator`
  runs first, so a request with problems in its questions reports those, not the config.
- The checks copy rules of the JDK. A later JDK that refuses more makes `ask` throw again for
  the new case; a JDK that refuses less makes jev4s refuse a config the JDK would take.
- Do not replace the checks with a `catch` of `IllegalArgumentException`: its message holds
  the key.
