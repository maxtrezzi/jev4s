# ADR-0030: A call retries for at most `maxElapsed`

- **Status:** Accepted
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** [ADR-0024](0024-http-retries-and-configuration.md)

## Context

[ADR-0024](0024-http-retries-and-configuration.md) took the retries of the official SDKs and
left out the Python SDK's 30 s budget per call, on the grounds that the total was already
bounded. It is bounded, but not tightly: with the defaults, three attempts of up to 10 s each
and two waits the server may set up to 60 s each keep a caller's thread for up to 150 s. A
caller who raises `maxRetries` or `maxRetryAfter` raises that bound with it.

## Forces

- **The JavaScript SDK has no budget**, and matching it was a reason in ADR-0024. The Python
  SDK has one, so the vendor's clients already differ here.
- **A budget needs a clock** in the retry loop. Injected like the `Sleeper` and the random
  source, it keeps every test deterministic.
- **What to do with a wait longer than what is left.** Falling back to a shorter backoff
  ignores what the server asked for; waiting part of it and retrying early is the same. Not
  retrying returns `RateLimited(Some(delay))`, which carries the server's delay to the caller.
- **What the budget covers.** A wait is known before it starts, a request is not: a budget
  checked before each wait can be exceeded by the last attempt, by at most one `timeout`.

## Decision

- `RetryPolicy(…, maxElapsed = 30.seconds)`, the Python SDK's value.
- `delayFor(retry, random, retryAfter, elapsed)` returns `None` when `elapsed` plus the wait
  would be more than `maxElapsed`; a wait that ends exactly at the limit is made.
- `JdkTransport` measures `elapsed` from the start of the first attempt with an injected
  `nanoTime: () => Long`, `System.nanoTime` by default, and returns the last error when
  `delayFor` gives `None`.

## Consequences

- With the defaults, a call keeps its thread for at most about 40 s: 30 s of retries, then one
  more attempt of up to 10 s.
- `maxRetryAfter` above `maxElapsed` has no effect: a server's delay that long ends the
  retries. Raise both to honour it.
- `delayFor` has a fourth, required parameter; M5's three-argument calls no longer compile.
- Do not drop the budget "to behave like the JavaScript SDK": the worst case goes back to
  150 s with the defaults, and grows with every setting a caller raises.
