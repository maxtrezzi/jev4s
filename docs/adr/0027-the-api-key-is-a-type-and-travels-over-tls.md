# ADR-0027: The API key is a type of its own, and travels only over TLS

- **Status:** Accepted
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** [ADR-0024](0024-http-retries-and-configuration.md)

## Context

[ADR-0024](0024-http-retries-and-configuration.md) made `JevConfig(apiKey: String, …)` a case
class whose `toString` hides the key, and let `TYPESAFE_BASE_URL` be any `http` or `https` URL.
`AGENTS.md` states the rule both serve: the API key never appears in a log or a `toString`.

Two gaps remain:

- A printer that shows a case class field by field does not call its `toString`. munit's diff
  of a failed `assertEquals` over two configs prints `apiKey = "secret-…"`; pprint and
  structured loggers derive their output the same way.
- With `TYPESAFE_BASE_URL=http://proxy.example.com`, the `Authorization` header is sent
  unencrypted to another machine.

## Forces

- **An `opaque type ApiKey = String`** is free at runtime, but at runtime it is a `String`: the
  field printer sees the text. It fixes nothing.
- **A case class `ApiKey(value)`** is printed field by field too.
- **A plain class with its own `toString`** is opaque to every printer that does not know it.
  It costs one wrapper at construction: `JevConfig(ApiKey(key), model)`.
- **Refusing plain `http`** everywhere breaks the legitimate use: a local proxy or a test
  server on the same machine.
- **Checking the URL in `JevConfig` or in `JdkTransport`** would catch a config built by hand
  too, but a case class constructor cannot return an error value, and the transport's errors
  are HTTP outcomes, some retried. The environment is where a URL arrives as untrusted text.

## Decision

- **`ApiKey`** is a `final class`, not a case class, in both modules. `toString` is
  `<hidden>`, equality and `hashCode` follow the text, and the text is readable only inside
  the library (`private[jev4s] val value`).
- **`JevConfig(apiKey: ApiKey, …)`**, without its own `toString`: the case class's default
  prints `<hidden>` for the key.
- **`JevConfig.fromEnv`** accepts a `TYPESAFE_BASE_URL` that is `https`, or `http` to
  `localhost`, `127.0.0.1` or `[::1]`. Anything else is `ConfigError.InvalidBaseUrl`.

## Consequences

- A config printed by `toString`, a test diff or a derived logger shows `<hidden>`. A test
  prints one through munit's printer and fails if the key appears.
- A config built by hand takes `ApiKey(key)`; `apiKey = "…"` no longer compiles.
- A config built by hand with an `http` URL to another host is still accepted: the check covers
  text read from the environment, not a URL a program writes.
- Do not turn `ApiKey` into a case class "to get equality for free": the field printers would
  show the key again.
