# ADR-0038: The caller may pass its own HTTP client

- **Status:** Accepted
- **Date:** 2026-09-23
- **Supersedes:** —
- **Amends:** [ADR-0024](0024-http-retries-and-configuration.md)

## Context

Each `JdkTransport` builds its own `java.net.http.HttpClient`, with its own threads, and never
closes it. T4 measured it: 50 clients took a JVM from 7 threads to 108. Sharing was already
possible: one `JdkTransport` passed to many clients with `JevClient.withTransport`. What a caller
could not do is **control** that `HttpClient`: its executor and threads, a proxy, the HTTP
version, or, from JDK 21, when it closes. `HttpClient.close()` exists only from JDK 21, and the
library supports JDK 17, so jev4s cannot close the client for the caller.

## Forces

- **A caller that owns the client controls it**: its threads, its executor, a proxy, the HTTP
  version, and on JDK 21 when it closes.
- **Callers who pass nothing** must keep today's behaviour.
- **`JevConfig` is data** ([ADR-0027](0027-the-api-key-is-a-type-and-travels-over-tls.md)): a
  live `HttpClient` does not belong in it.
- **`JevConfig.timeout`** sets both the connect timeout of the client jev4s builds and each
  request's timeout. A caller's client keeps its own connect timeout.
- **The form of the parameter.** `Option[HttpClient] = None` is the idiomatic optional value; a
  `null` default is not Scala, and a second factory method is a second way to build the same
  client.

## Decision

- `JdkTransport` and the client's factory (`JevClient(config, …)` in Scala 3,
  `JevClient.create` in 2.13) take `httpClient: Option[HttpClient] = None`, a named parameter
  after the existing ones, in both modules.
- Without it, jev4s builds one as before. With it, every request and every retry goes through
  the caller's client, with `JevConfig.timeout` as the request timeout.
- The Scaladoc says that a caller's client keeps its own connect timeout, that the request
  timeout still applies, and that jev4s never closes it: the caller does.

## Consequences

- One `HttpClient` can serve many jev4s clients, with the caller's executor or proxy, and a
  caller on JDK 21 can close it.
- One more parameter on the client's factory and on `JdkTransport`.
- A test sends through an `HttpClient` that counts its requests, retries included, and checks
  that the request timeout still applies to it.
- Do not move the client into `JevConfig`: a config is compared, printed and built from the
  environment, and a live resource is none of these.
