# ADR-0039: The library does not limit the request rate

- **Status:** Accepted
- **Date:** 2026-09-24
- **Supersedes:** —
- **Amends:** —

## Context

Jev limits each **account** to 1,200 requests per minute and 250,000 input tokens per second;
above either limit it answers 429. TypeSafe publishes these numbers on
`https://docs.typesafe.ai/models`, and says there that the limits adjust dynamically, can change
without notice under heavy demand, and are higher on custom and enterprise plans (read on
2026-09-24). TypeSafe states a latency of 70 to 500 ms per request.

jev4s retries a 429, honouring `retry-after-ms` and `retry-after`, and a 529, as the official
SDKs do ([ADR-0024](0024-http-retries-and-configuration.md),
[ADR-0030](0030-a-call-retries-for-at-most-maxelapsed.md)). Those retries are for an occasional
429, not for a caller that keeps sending too fast. Measured on 2026-09-23 with the jev4s
client against a local server that answers in 100 ms and accepts 20 requests in any second
(the published 1,200 per minute), 200 requests each run:

| Threads | Rate control | Answers lost to `RateLimited` |
|---|---|---|
| 2 | none | 0 (13.9 answers/s) |
| 4 | none | 0 (19.8 answers/s, 36 retries) |
| 32 | none | 67 to 92 |
| 32 | a pacer at 19 requests/s | 0 |

## Forces

- **A published number is not the account's number.** The limit depends on the plan, and
  TypeSafe says it moves. A default taken from the page is too low for a custom or enterprise
  account, and too high the day TypeSafe lowers it.
- **The limit is per account, a limiter is per process.** A limiter inside `JevClient` sees only
  its own JVM: two JVMs, or two clients built separately, on one account still exceed the
  limit together. Only the caller knows how its work is spread.
- **The alternative exists.** lingoda/ai-sdk ships a limiter set by default to 90% of the
  published limits (1,080 requests and 13.5 million tokens per minute). It is convenient for one
  process on the standard plan, and wrong in the two cases above, silently: it looks like a
  guarantee.
- **A pacer is short**: each caller waits for its own slot before it calls. About twenty lines,
  on JDK 17 APIs only.
- **Small and dependency-free** ([ADR-0005](0005-minimal-dependencies-ujson-and-the-jdk.md)), and
  synchronous ([ADR-0002](0002-direct-style-no-effect-system.md)).

## Decision

The library ships no rate limiter. The user guides show how to send many requests: a
small thread pool, a pacer whose rate is a parameter, and what to do with the `RateLimited`
errors that remain. The guides may quote the published limits, with the date they were read and
a link to the page, as an example of a rate to start from; never as a constant of the library.

## Consequences

- A caller who sends many requests writes, or copies from the guide, its own pacer, and sets its
  rate from its own plan.
- The guides carry an example that CI compiles on JDK 17 and 21, and a test that runs it
  against a local server without the API key.
- When the published limits change, only the guides' dated sentence goes stale; no code does.
- Do not add a rate limiter with a default rate: the default would be wrong for some accounts,
  blind to other processes on the same account, and would look authoritative.
