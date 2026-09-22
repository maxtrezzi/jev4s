# ADR-0026: HTTP 400 is a rejected request, and other statuses carry a readable message

- **Status:** Accepted — raw body length amended by ADR-0029
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** [ADR-0024](0024-http-retries-and-configuration.md)

## Context

[ADR-0024](0024-http-retries-and-configuration.md) mapped 422 to `Rejected` and every status it
did not name, 400 included, to `Unexpected(status, body)`. The API documentation lists 401,
422, 429 and 529 only. A live call with an unknown model returned **400**, recorded in
`golden/error-400`:

```json
{"detail":{"error_type":"api_usage_error","message":"Unknown model: jev-0.0.0"}}
```

That body has the shape of a 401's, which `Codec.errorMessage` already reads.

## Forces

- **Errors modelled on what the caller can do**, not on HTTP statuses one by one: a 400 for an
  unknown model and a 422 for a malformed question both mean "the request is wrong; sending it
  again gives the same answer".
- **Keeping 400 in `Unexpected`** mirrors the documentation, which does not list it, but hides
  a common mistake behind a catch-all.
- **The raw body in `Unexpected`** is a JSON string a person has to read through.

## Decision

- HTTP **400 and 422** become `JevError.Rejected(message)`, with the message from
  `Codec.errorMessage`.
- `JevError.Unexpected(status, message)` carries the message from `Codec.errorMessage` instead
  of the raw body; for a body that is not the documented shape, that is the body unchanged.
- `golden/error-400` records the real reply, and the golden tests read it in both modules.

## Consequences

- `Rejected("Unknown model: jev-0.0.0")` says what to fix, and is not retried.
- A 400 the API may send for other reasons is also `Rejected`; its message says why.
- `Unexpected` is left for statuses such as 403 and 404, which point at a proxy or a wrong base
  URL rather than at the request.
- If the API documents a 400 whose meaning is not "fix the request", this mapping needs a new
  decision.
