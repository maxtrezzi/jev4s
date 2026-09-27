# ADR-0045: A retry policy whose wait can be negative is an error

- **Status:** Accepted
- **Date:** 2026-09-25
- **Supersedes:** —
- **Amends:** [ADR-0042](0042-a-config-java-net-http-refuses-is-an-error.md)

## Context

[ADR-0042](0042-a-config-java-net-http-refuses-is-an-error.md) made a `JevConfig` that
`java.net.http` refuses a `JevError.InvalidConfig`, so that no call throws. Its check does not
look at `config.retry`. `RetryPolicy.delayFor` returns a negative delay when `jitter` is above 1
or `backoffInitial` or `backoffMax` is negative, and `Sleeper.thread` passes it to
`Thread.sleep`, which throws `IllegalArgumentException: timeout value is negative`. Measured on
`0b462c5`, Scala 3, a local server answering 503: `RetryPolicy(jitter = 2.0)` and
`RetryPolicy(backoffInitial = FiniteDuration(-1, SECONDS))` make `JdkTransport.send` throw.
`maxRetries = -1` returns the first error, and `jitter = Double.NaN` retries with no wait:
neither throws.

TypeSafe's Python SDK 0.7.1 refuses these values when its `RetryConfig` is built: a negative or
non-finite backoff, and a jitter outside [0, 1] (`_core/retry.py`, read 2026-09-25).

## Forces

- **The API does not throw** ([ADR-0002](0002-direct-style-no-effect-system.md)). A negative
  wait is the same class of defect as the timeout of zero that ADR-0042 closed.
- **Clamping the delay at zero in `delayFor`** throws nothing and needs no new error, but a
  policy with a jitter of 2 then retries with no wait at all, and nothing tells the caller that
  the value was wrong.
- **A check when the `RetryPolicy` is built** (`require`) still throws, earlier: the reason
  ADR-0042 rejected it for `JevConfig`.
- **ADR-0042's frame is `java.net.http`.** The method that throws here is `Thread.sleep`, so the
  check widens what ADR-0042 covers from `java.net.http` to the JDK.
- **A negative jitter or a jitter that is not a number throws nothing**: the first makes each
  wait longer, the second makes it zero. The Python SDK refuses both, and so a caller who moves
  a policy from Python gets the same answer.

## Decision

- `JdkTransport.problems` checks the config's `RetryPolicy` too, in both modules: `backoffInitial`
  and `backoffMax` of zero or more, and `jitter` between 0 and 1, both included. `NaN` is outside.
- Each value outside its rule is one problem that names the field and its value, joined to the
  others by `; ` in one `JevError.InvalidConfig`, as ADR-0042 says.
- `maxRetries`, `maxRetryAfter` and `maxElapsed` are not checked: a negative value of any of them
  only means no retry, or no honoured `Retry-After`, and throws nothing.
- `delayFor` does not change: it is a pure function of its arguments, and the transport never
  calls it with a policy that failed the check.

## Consequences

- No `RetryPolicy` makes `ask` throw. A policy with a negative backoff or a jitter above 1 fails
  every call with `InvalidConfig`, and sends nothing.
- A policy that ran before with a negative jitter, or with `NaN`, now fails. There is no release
  yet, so no caller to break.
- `RetryPolicy.delayFor`, called directly, can still return a negative delay for such a policy.
  Its Scaladoc says which values the transport accepts.
- Do not replace the check with a clamp in `delayFor`: a wrong value would then pass in silence.
