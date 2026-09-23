# ADR-0039: The library does not limit the request rate

- **Status:** Proposed
- **Date:** 2026-09-23
- **Supersedes:** —
- **Amends:** —

## Context

Jev limits each account's requests per minute and input tokens per second; above either
limit it answers 429. TypeSafe's documentation says the limits change without notice, and
names no fixed value (read 2026-09-23 through search excerpts; the page itself was not
reachable). TypeSafe states a latency of 70 to 500 ms per request.

jev4s retries a 429 as the official SDKs do
([ADR-0024](0024-http-retries-and-configuration.md),
[ADR-0030](0030-a-call-retries-for-at-most-maxelapsed.md)). Those retries are for an occasional
429, not for a caller that keeps sending too fast. Measured on 2026-09-23 with the jev4s
client against a local server that answers in 100 ms and accepts 20 requests in any second,
200 requests each run:

| Threads | Rate control | Answers lost to `RateLimited` |
|---|---|---|
| 2 | none | 0 (13.9 answers/s) |
| 4 | none | 0 (19.8 answers/s, 36 retries) |
| 32 | none | 67 to 92 |
| 32 | a pacer at 19 requests/s | 0 |

## Forces

- **The right rate is the account's, and it moves.** A limiter in the library would need a
  number the library cannot know.
- **A pacer is short**: each caller waits for its own slot before it calls. About twenty lines,
  on JDK 17 APIs only.
- **Small and dependency-free** ([ADR-0005](0005-minimal-dependencies-ujson-and-the-jdk.md)), and
  synchronous ([ADR-0002](0002-direct-style-no-effect-system.md)).

## Decision

Proposed: the library ships no rate limiter. The user guides show how to send many requests: a
small thread pool, a pacer whose rate is a parameter, and what to do with the `RateLimited`
errors that remain. The guides give no fixed rate as if it were stable.

## Consequences

- A caller who sends many requests writes, or copies from the guide, its own pacer.
- The guides carry an example that CI compiles on JDK 17 and 21, and a test that runs it
  against a local server without the API key.
- Do not add a rate limiter with a default rate: the default would be wrong for most accounts,
  and would look authoritative.
