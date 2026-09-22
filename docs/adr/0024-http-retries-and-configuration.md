# ADR-0024: HTTP over java.net.http, the official SDKs' retries, and configuration from the environment

- **Status:** Accepted — 400 mapping amended by ADR-0026; API key and base URL amended by ADR-0027; server error message amended by ADR-0029; retry budget amended by ADR-0030
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** [ADR-0020](0020-code-stryker4s-cannot-mutate-is-mutated-by-hand.md), [ADR-0022](0022-the-scala-3-client-api.md), [ADR-0023](0023-the-scala-2-13-client-api.md)

## Context

M5 connects the client to the real API. [ADR-0005](0005-minimal-dependencies-ujson-and-the-jdk.md)
fixes `java.net.http`; [ADR-0022](0022-the-scala-3-client-api.md) puts status codes and retries
inside the `Transport`. The milestone asks for the retry defaults of the official SDKs, checked
at the source: https://docs.typesafe.ai/sdk/python/api/retries.md and
https://docs.typesafe.ai/sdk/javascript/api/interfaces/RetryPolicy.md, read 2026-09-22. Both
SDKs retry 408, 429 and 5xx up to 2 times, back off from 0.5 s doubling up to 5 s with up to
25% taken off at random, honour `Retry-After` and `retry-after-ms` (the JavaScript SDK up to
60 s), retry connection failures and timeouts, and time out each request after 10 s.

## Forces

- **Other defaults than the official SDKs'** would make jev4s behave differently from the
  vendor's clients under the same rate limits, for no gain.
- **The Python SDK's 30 s budget per call** adds a clock to the retry loop. With 2 retries, a
  5 s backoff ceiling and a 10 s timeout per request, the total is already bounded.
- **Statuses the API does not document** (400, 403, 404) had no `JevError`, and 408 had no home.
- **A pure `delayFor`** makes every delay testable with no clock and no randomness; the sleep and
  the random source are injected.
- **The client's constructor.** In Scala 3 a companion `apply` hides the constructor proxy, and
  Scala allows default arguments on one overloaded alternative only, so
  `JevClient(model, transport, onReply = …)` and `JevClient(config, onReply = …)` cannot both
  exist.
- **A value initialised once** (`val defaultBaseUrl`) is a "static" mutant: Stryker4s marks it
  `Ignored` on its own, and `build/check-mutants.py` accepted that in silence.
- **Tests against the real API cost money** and depend on the network.

## Decision

- `RetryPolicy(maxRetries = 2, backoffInitial = 500.millis, backoffMax = 5.seconds, jitter =
  0.25, maxRetryAfter = 60.seconds)`, with a pure `delayFor(retry, random, retryAfter)`. The
  server's delay wins up to `maxRetryAfter`; beyond it, the backoff applies. No total budget.
- `JdkTransport(config, sleeper, random)` retries exactly the errors whose `isRetryable` is
  true. Status mapping: 2xx → body; 401 → `Unauthorized`; 422 → `Rejected` with the codec's
  message; 429 → `RateLimited` with `retry-after-ms`, else `retry-after`; 529 → `Overloaded`;
  408 and other 5xx → `ServerError`; anything else → the new `Unexpected(status, body)`, not
  retried. A timeout or an `IOException` → `Network`. An `InterruptedException` propagates.
- `JevConfig(apiKey, model, baseUrl, timeout = 10.seconds, retry)`, with `toString` hiding the
  key, and `JevConfig.fromEnv(model): Either[ConfigError, JevConfig]` reading
  `TYPESAFE_API_KEY` and `TYPESAFE_BASE_URL`, the official SDKs' variable names.
- **Construction, amending ADR-0022:** `JevClient(config, onReply = …)` over `JdkTransport`, and
  `JevClient.withTransport(model, transport, onReply = …)` for a transport of one's own. The
  constructor is private. The 2.13 module has `JevClient.create(config, …)` and
  `JevClient.withTransport(…)`.
- **Static mutants, amending ADR-0020:** a mutant Stryker4s ignores on its own is treated like
  one inside `inline` code: excluded with `@SuppressWarnings`, applied by hand and recorded.
  `check-mutants.py` now fails on an `Ignored` mutant that no `@SuppressWarnings` excluded.
- Live tests and one runnable example per module live in `live/scala3` and `live/scala213`,
  sbt projects the root does not aggregate. Each test is skipped unless `TYPESAFE_API_KEY` is
  set.

## Consequences

- jev4s retries as the vendor's own clients do, and the delays are tested exactly.
- Every HTTP status becomes a `JevError`; a new status the API adds lands in `Unexpected` until
  it gets a case.
- A caller who sets a large `maxRetryAfter` can be made to wait that long by the server.
- A test of code that uses jev4s writes `JevClient.withTransport(...)`; M3's
  `JevClient(model, transport)` no longer compiles.
- `sbt test`, coverage and Stryker4s never reach the network or the real API; the live suites
  run only by name.
- Do not aggregate the live projects into the root "so that everything runs": `sbt test` would
  then call a paid API whenever the key is set.
