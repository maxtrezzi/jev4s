# ADR-0031: Instructions and criteria are `ujson.Value`s

- **Status:** Accepted
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** [ADR-0021](0021-what-the-codec-keeps-from-a-jev-reply.md)

## Context

The API accepts a string, a JSON object, a JSON array or `null` in `instructions`, in a Noul's
`criteria.true` and `criteria.false`, in each level of a Score and in each option description of
a Choice (`EntryType`, https://docs.typesafe.ai/primitives/advanced.md, read 2026-09-22).
[ADR-0021](0021-what-the-codec-keeps-from-a-jev-reply.md) kept all of them strings and left
structure to [T2](../tasks/tasks.md#t2--structured-instructions-and-criteria), on the condition
that a caller who passes strings keeps compiling.

A real call with JSON in every one of those places, recorded in `golden/structured`, returns the
JSON levels of a Score in `legend` as they were sent.

## Forces

- **`ujson.Value`** is already in the public API (`ToState`, [ADR-0022](0022-the-scala-3-client-api.md)),
  and ujson converts a `String` to it implicitly, with no import, in both Scala versions.
  `Noul("Is it urgent?")`, `Noul("x", whenTrue = Some("yes"))`, `Score("How?", "Calm", "Angry")`
  and `probabilities("Calm")` compile unchanged under `-Werror`.
- **A type of our own (`Entry`)** over `ujson.Value` names the concept as the SDKs do, but adds a
  type and two conversions, and breaks exactly the same callers.
- **Scala 3.9's `into` parameters** (`scala.Conversion.into`) accept a converted argument without
  a language import, but not inside `Some(...)` and not for a spread `List[String]`, so they do
  not help.
- **What still breaks** with either type: passing a collection or an `Option` already typed with
  `String` (`levels*` from a `List[String]`), reading `instructions` as a `String`, and comparing
  a Score's keys with a `Set[String]`.

## Decision

- `instructions`, `Noul.whenTrue` and `whenFalse`, Score levels and `ChoiceOption.description`
  are `ujson.Value` (in options, `Option[ujson.Value]`), in both modules.
- `ScoreAnswer.probabilities` is a `Map[ujson.Value, Probability]`, keyed by each level as the
  question gives it; ADR-0021's rule of keying by the question's levels, not by `legend`, is
  unchanged.
- `Described.description` stays a `String`: an enum case's description is text.
- `Problem.DuplicateLevel` keeps a `String`: a text level as it is, a JSON level rendered as
  compact JSON.

## Consequences

- A question can carry a rubric, a schema or a taxonomy as JSON, and the golden tests check the
  request byte for byte and the reply against the real API.
- `probabilities("Calm")` still reads a text level; a JSON level is read with the same JSON value.
- Code that reads `instructions` as a `String`, or builds levels from a `List[String]`, must
  change: `levels.map(ujson.Str(_))*`.
- Do not replace `ujson.Value` with `String | ujson.Value` "to be explicit": a `String` then has
  two representations, `"x"` and `ujson.Str("x")`, and two equal questions can compare unequal.
