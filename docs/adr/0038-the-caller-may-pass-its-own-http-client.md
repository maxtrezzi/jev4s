# ADR-0038: The caller may pass its own HTTP client

- **Status:** Proposed
- **Date:** 2026-09-23
- **Supersedes:** —
- **Amends:** —

## Context

Each `JdkTransport` builds its own `java.net.http.HttpClient`, with its own threads, and never
closes it. T4 measured it: 50 clients took a JVM from 7 threads to 108. The only defence today
is the Scaladoc: build one client and share it. `HttpClient.close()` exists only from JDK 21,
and the library supports JDK 17.

When accepted, this ADR amends [ADR-0024](0024-http-retries-and-configuration.md) (how the
transport is built). The branch that implements it sets `Amends` here and records the
amendment in ADR-0024's status.

## Forces

- **A caller that owns the client controls it**: its threads, its executor, a proxy, the HTTP
  version, and on JDK 21 when it closes.
- **Callers who pass nothing** must keep today's behaviour.
- **`JevConfig` is data** ([ADR-0027](0027-the-api-key-is-a-type-and-travels-over-tls.md)): a
  live `HttpClient` does not belong in it.
- **`JevConfig.timeout`** sets both the connect timeout of the client jev4s builds and each
  request's timeout. A caller's client keeps its own connect timeout.

## Decision

Proposed: `JdkTransport` and `JevClient(config, …)` (2.13: `JevClient.create`) take an optional
`HttpClient`. Without it, jev4s builds one as today. The Scaladoc says that a caller's client
keeps its own connect timeout, that the request timeout still applies, and that the caller
closes the client it passed.

## Consequences

- One `HttpClient` can serve many jev4s clients, and a caller on JDK 21 can close it.
- One more parameter on the client's constructor.
- Do not move the client into `JevConfig`: a config is compared, printed and built from the
  environment, and a live resource is none of these.
