# ADR-0034: A Score typed by the caller's enum

- **Status:** Proposed
- **Date:** 2026-09-23
- **Supersedes:** —
- **Amends:** —

## Context

A `Choice` answers with a value of the caller's own type, from `derives JevChoice`. A `Score`
does not: its levels are `ujson.Value`s, and `ScoreAnswer.probabilities` is keyed by them, so
`probabilities("Calm")` compiles through ujson's conversion from `String` and a misspelt level
fails at runtime. The number of levels, 2 to 10, is checked only by the `Validator`, at
runtime. T6 found that a `Score(...)` has the type `Question[ScoreAnswer]`, so its levels cannot
be read back. The Score is the part of the API furthest from the showcase bar of
[ADR-0001](0001-a-study-and-portfolio-project.md).

When accepted, this ADR amends [ADR-0021](0021-what-the-codec-keeps-from-a-jev-reply.md) (the
shape of `ScoreAnswer`). The branch that implements it sets `Amends` here and records the
amendment in ADR-0021's status.

## Forces

- **Callers who pass text today must keep compiling.** `Score("…", "Calm", "Angry")` is in the
  README, the guides and `live/`.
- **A compile-time check of the level count without `inline`.** Stryker4s cannot mutate
  `inline` code ([ADR-0020](0020-code-stryker4s-cannot-mutate-is-mutated-by-hand.md)). A given
  that exists only when `(N >= 2 && N <= 10) =:= true`, with `N` the number of cases, needs no
  `inline`: one case and eleven cases fail to compile, ten compile (checked 2026-09-23 on
  Scala 3.9.0).
- **Which level to report.** A "nearest level to `score`" looks natural but misleads: with
  probabilities 0.5, 0.0, 0.5 the score is 1.0 and the nearest level is the middle one, whose
  probability is zero. The level with the highest probability has no such trap.
- **Two `apply`s, and `Score("How?")` with no level.** With a text overload and a typed
  overload, the typed one wins and the compiler reports the missing `JevScale`. The default
  message names a type variable (`Score[L]`). A message that shows both forms, and names no
  type parameter, is clear. Requiring at least two levels in the text overload's signature was
  tried and rejected: one level, or a spread `List`, then gives an unreadable overload error.

## Decision

Proposed, Scala 3 first:

- `Question.Score[L](instructions, levels: List[ScaleLevel[L]])`, with
  `ScaleLevel[L](value: L, text: ujson.Value)`.
- `object Score` with two `apply`s: the text form, `Score(instructions, levels: ujson.Value*)`,
  which is a `Score[ujson.Value]`, and the typed form, `Score[L](instructions)(using
  JevScale[L])`.
- `JevScale[L]`, derived for an enum whose cases have no parameters, with the machinery of
  `JevChoice`'s derivation reused, not copied. A level's text is its case name, or its
  `Described` description. The order of the cases is the order of the scale.
- The level count is checked at compile time by a given evidence, without `inline`.
- `ScoreAnswer[L](score: Double, mostLikely: L, confidence: Probability, probabilities: Map[L,
  Probability])`. No "nearest level" field.
- When two levels have the same highest probability, `mostLikely` is the first of them in the
  order of the scale. To confirm when implementing.
- The `@implicitNotFound` text of `JevScale`:
  `a Score needs its levels: give them, as in Score("How?", "Calm", "Angry"), or name an enum that derives JevScale, as in Score[Mood]("How?")`.
- 2.13 follows: a `JevScale[L]` written by hand, as `JevChoice` is, and the level count stays a
  `Validator` check.

## Consequences

- `r.feeling.probabilities(Mood.Angry)` and `r.feeling.mostLikely: Mood` are typed; a string key
  on a typed Score is a compile error.
- `ScoreAnswer` takes a type parameter: code that names the type `ScoreAnswer` must write
  `ScoreAnswer[ujson.Value]` or its own `L`.
- `Score("How?")` becomes a compile error instead of a `Validator` problem.
- The text form keeps its runtime checks, and its probabilities stay keyed by the `ujson.Value`
  levels, which are mutable ([ADR-0031](0031-instructions-and-criteria-are-ujson-values.md)).
- An enum declared in the wrong order is a wrong scale, and no check can see it.
- Do not add a "nearest level" field "for convenience": it reports levels the model judged
  impossible.
