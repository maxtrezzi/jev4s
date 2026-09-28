# ADR-0035: A reply without input tokens still answers

- **Status:** Accepted
- **Date:** 2026-09-23
- **Supersedes:** —
- **Amends:** ADR-0021, ADR-0025

## Context

`Codec.decode` requires `model` and `usage.input_tokens`. If either is missing, the call is
`Left(Decoding(...))`, although the answers are there and the input tokens were billed.

The API's own OpenAPI contract makes `model`, `usage.input_tokens` and `usage.output_tokens`
required: the models the official Python SDK 0.7.1 generates from
`https://api.typesafe.ai/openapi.json` say so (PyPI, read 2026-09-23). The same SDK's public
`Usage` type makes both token counts optional, "None when the API did not report it". Jev is
also served through gateways now (Vercel AI Gateway, OpenRouter), which relay the response.

This ADR amends [ADR-0021](0021-what-the-codec-keeps-from-a-jev-reply.md) (what the codec
requires) and [ADR-0025](0025-client-events-through-onevent.md) (what `Replied` carries).

## Forces

- **The answers are what the caller paid for.** Metadata that is missing should not cost the
  answers.
- **The contract says required, the vendor's client tolerates.** Following the contract strictly
  is correct today; tolerating is what the vendor chose for its own users.
- **`model` is different.** It tells the caller which model answered an alias such as
  `jev-latest`; an answer with no model is harder to trust.

## Decision

- `model` stays required.
- `Reply(model: String, inputTokens: Option[Long])`: `None` when `usage` or `input_tokens` is
  missing or not a whole number.
- `JevEvent.Replied` still carries a `Reply`, once per successful call.
- The shape is `Option[Long]`, not a type of its own: absence is the only case to model, and
  `Option` says it with no new name.

## Consequences

- A reply without `usage` answers `Right`, and `Replied` says the tokens were not reported.
- Callers who sum `inputTokens` must handle `None`.
- The case is tested with JSON written by hand: the API does not send it today, and `golden/`
  holds only real replies.
- Do not make `model` optional "for symmetry": it is the one field that says which model
  answered.
