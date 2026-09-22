# ADR-0021: What the codec keeps from a Jev reply

- **Status:** Accepted — instructions and criteria amended by ADR-0031
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** —

## Context

M2 writes the codec against the documented format (https://docs.typesafe.ai/api.md, read
2026-09-22) and the real replies recorded in `golden/`. The format carries more than the M1
model holds, in two places:

- A Score answer has, besides `score` and `confidence`, a `legend` (level index → the level's
  text, as sent) and `probabilities` per level, keyed `"0"`, `"1"`, …. `ScoreAnswer` held
  `score` and `confidence` only.
- `instructions` and every `criteria` value accept a JSON object or array as well as a string.
  The model holds strings only.

The model that answered and the input tokens are already settled by
[ADR-0010](0010-reply-metadata-through-onreply.md): they go to `Reply`, through `onReply`.

## Forces

- **Per-level probabilities are the Score's distribution.** Without them a caller sees `1.14`
  and cannot tell "mostly Frustrated" from "split between Calm and Very angry", which
  `confidence` only summarises.
- **The key of those probabilities.** The level's position (`List[Probability]`) is compact
  but makes the caller count; the level's text (`Map[String, Probability]`) reads like
  `ChoiceAnswer`'s map. Text only works if a Score's levels are distinct, which nothing
  checked.
- **`legend`** repeats the levels the caller wrote in the question.
- **Structured instructions and criteria** are a real feature of the API, but they change the
  public types of every question (`String` becomes `String | ujson.Value`, or a type of our
  own), and M2 is about the codec. Adding them later widens a type without breaking a caller
  that passes strings.

## Decision

- `ScoreAnswer(score, confidence, probabilities: Map[String, Probability])`, each probability
  keyed by the **text of its level**. The codec maps the API's index keys through the
  question's levels, not through `legend`, and rejects an index that is not a level.
- **A Score may not repeat a level**: the `Validator` reports `Problem.DuplicateLevel`, as it
  reports a repeated option key of a Choice.
- `legend` is read by nothing.
- **Instructions and criteria stay strings** for now. Structured ones are future work, recorded
  in [`docs/tasks/`](../tasks/README.md).

## Consequences

- `answer.probabilities("Very angry")` reads the distribution in the caller's own words.
- A request with two identical levels is refused before it is sent, although the API would
  accept it.
- Callers cannot send JSON structure in `instructions` or `criteria` yet; `state` accepts any
  `ujson.Value`, so structured data has a place to go meanwhile.
- If the API changes the meaning of `legend`, nothing here notices: the codec trusts that
  level `i` is the `i`-th level sent. The golden tests check that against real replies.
- Do not key `probabilities` by `legend` "to use what the API sends": the question's levels are
  the caller's source of truth, and `legend` could only repeat them or disagree.
