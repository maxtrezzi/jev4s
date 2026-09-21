# ADR-0010: Reply metadata through `onReply`

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

Each Jev response carries, besides the answers, metadata: `model` (the version that answered,
such as `jev-1.13.0`) and `usage` (input and output tokens). It is needed for two reasons:

- **logs:** `jev-latest` moves to new versions, and thresholds tuned on one version do not hold
  on the next;
- **cost:** only input is billed, so `input_tokens` is the cost of the request.

In the Scala 3 API the answers come back as a named tuple: `r.dept.choice`. That line is what
must show compactness and type safety ([ADR-0009](0009-two-native-modules-no-shared-code.md)),
so the metadata must not make it longer.

## Forces

- **A `Response(answers, model, usage)` wrapper** is explicit and has no side effect, but every
  access becomes `r.answers.dept`, even for callers who never read the metadata.
- **`model` and `usage` fields next to the answers** (a concatenated named tuple or a
  `Selectable`) give `r.dept` and `r.model` together, but the names collide with questions. A
  question named `model` — "which model should handle this?" — is a typical Jev use case
  (routing between models).
- **Two methods, `ask` and `askWithReply`**, double the API for each variant (static and
  dynamic).
- **Built-in logging** (`System.Logger`) decides for the user where and how to log, and does
  not help with cost.

## Decision

- `ask` and `askMap` return **only the answers**.
- The client takes an optional callback, `onReply: Reply => Unit`, called with the metadata of
  **every successful reply**:

  ```scala
  final case class Reply(model: String, inputTokens: Long)

  val client = JevClient(config, onReply = r => log.info(s"answered by ${r.model}"))
  ```

- Semantics:
  - called **once per successful reply**, after decoding, and **not** once per retry attempt;
  - called **on the calling thread**, before `ask` returns;
  - not called for errors (`Left`): a 429 or 401 response has no `model`;
  - an exception thrown by `onReply` **propagates**. It is a bug in the caller's code and must
    not be hidden. The Scaladoc says so.
  - default: `_ => ()`.
- The 2.13 module uses the same mechanism in its own idiom (a parameter of
  `JevClient.create`), so both modules expose the same information.
- `Reply` carries input tokens only, because output is free. If the API adds metadata, it goes
  into `Reply`.

## Consequences

- Access to answers stays `r.dept.choice`, with no wrapper.
- No reserved names: a question may be called `model` or `usage`.
- Logging and cost tracking are configured **once**, on the client.
- The callback does not know **which** request produced the `Reply`. A caller who must store
  the model next to each single decision (for an audit, say) has no direct way. If that is
  needed, a method returning answers and `Reply` together can be added without changing `ask`.
- A side effect hidden in the client: someone reading an `ask` call does not see that a log
  line is written. The named argument (`onReply = ...`) where the client is created is where
  it shows.
- In direct style ([ADR-0002](0002-direct-style-no-effect-system.md)) the callback is
  synchronous: a slow `onReply` slows every request.
