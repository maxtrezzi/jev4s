# ADR-0049: A network error says what failed

- **Status:** Accepted
- **Date:** 2026-09-25
- **Supersedes:** —
- **Amends:** [ADR-0024](0024-http-retries-and-configuration.md)

## Context

[ADR-0024](0024-http-retries-and-configuration.md) maps a timeout or any `IOException` to
`JevError.Network(message)`, and retries it. T1 noted that this drops the cause, and T22 took it
up before the first release, while changing a public case costs no user anything.

With a message only, a caller cannot tell a timeout from a refused connection or a certificate
that TLS refused, except by reading text that the JDK writes. And every one of them is retried:
a certificate that is not trusted, or is for another host, fails the same way on each attempt,
so the retries only add their waits before the same error.

Measured on JDK 21.0.9 and 25.0.3, with the JDK's `HttpClient`, the same on both:

| Failure | Exception thrown by `send` |
|---|---|
| no response within the timeout | `HttpTimeoutException` |
| no connection within the timeout, a TLS handshake that stalls included | `HttpConnectTimeoutException`, a subclass of `HttpTimeoutException` |
| a refused connection | `ConnectException` with no message, caused by a `ConnectException` caused by a `ClosedChannelException` |
| a host whose name is not found | `ConnectException` with no message, caused by a `ConnectException` caused by an `UnresolvedAddressException` |
| a certificate that is not trusted, or has no name for the host | `SSLHandshakeException`, caused by a `CertificateException` (`ValidatorException`, its subclass, for an untrusted one) |
| the server closes the connection during the handshake | `SSLHandshakeException: Remote host terminated the handshake`, no further cause |
| no protocol in common | on JDK 21 the same as the line above; on 25 `SSLHandshakeException: (protocol_version) …`, no further cause |
| the connection closes before the whole response | `IOException`, such as `HTTP/1.1 header parser received no bytes` caused by an `EOFException` |

## Forces

- **Errors are values that compare** ([ADR-0002](0002-direct-style-no-effect-system.md)). Tests,
  the tutorials and `JevTestkit.failing` build a `JevError` and compare it with `==`. A field of
  type `Throwable` would compare by reference, and `Left(Network(...))` would never equal
  another.
- **What a caller acts on is the kind, not the stack trace.** A timeout suggests a longer
  `timeout`; a refused connection or an unknown host a wrong base URL or a network that is down;
  a refused certificate a proxy that intercepts TLS, or a wrong host.
- **Only a certificate is known to fail again.** A handshake that the server ends is often
  transient, and on JDK 21 it cannot be told apart from a protocol that the two sides do not
  share. Only the `CertificateException` in the causes is a reliable sign that the next attempt
  fails too.
- **Alternatives considered.** Keeping the exception, `Network(message, cause: Throwable)`: the
  whole stack trace, but equality by reference, as above. `Timeout` as a `JevError` of its own,
  and `Network(message)` for the rest: simpler, but a refused certificate stays retried and
  indistinguishable. The owner chose a kind of failure on 2026-09-25, in the shape of a
  `NetworkFailure` of `Timeout`, `Connect`, `Tls` and `Other`; the measurement above narrowed
  `Tls` to `Certificate`, the one TLS failure that can be recognised.

## Decision

- `JevError.Network(failure: NetworkFailure, message: String)`, in both modules. `NetworkFailure`
  is an `enum` in Scala 3 and a sealed class with case objects in 2.13:
  - `Timeout`: an `HttpTimeoutException`; the message is `no response within <timeout>`. For its
    subclass `HttpConnectTimeoutException` it is `no connection within <limit>`, where the limit is
    the config's `timeout`, or the connect timeout of the caller's `HttpClient` when that is
    shorter: a message that named the config's `timeout` would be wrong for a client of one's own.
  - `Connect`: a `ConnectException`; the message is `the host <host> was not found` when an
    `UnresolvedAddressException` is among its causes, else `no connection to <host>`. The JDK's
    own message is empty.
  - `Certificate`: an `SSLException` with a `CertificateException` among its causes; the JDK's
    message.
  - `Other`: any other `IOException`, other TLS failures included; the JDK's message, or the
    name of the exception's class when it has none.
- `isRetryable` is false for `Certificate`, true for the three others.
- The mapping is `JdkTransport.networkError`, a pure function tested with the chains of the table.
  It reads the causes to a depth of 16: Java allows a chain of causes that loops, which a
  caller's `HttpClient` could throw, and reading it to its end would never return.

## Consequences

- A caller can `match` on `JevError.Network(NetworkFailure.Certificate, _)`, and a refused
  certificate returns at once instead of after two retries.
- The stack trace is still not kept. A caller who needs it can pass a `Transport` of their own.
- The table is the JDK's behaviour, not a promise of it: a later JDK that changes an exception
  moves a failure to `Other`, which is retried, as every network error was before.
- Do not add `Tls` for every `SSLException`: a handshake the server ends is often transient,
  and would stop being retried.
