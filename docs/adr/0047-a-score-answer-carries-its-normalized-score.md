# ADR-0047: A Score's answer carries its normalized score

- **Status:** Accepted
- **Date:** 2026-09-25
- **Supersedes:** —
- **Amends:** [ADR-0034](0034-a-score-typed-by-the-callers-enum.md)

## Context

A Score's `score` goes from 0 to its highest level: 0 to 2 with 3 levels, 0 to 4 with 5. To
compare or combine two Scores, a caller divides each one by its highest level, and chapter 6 of
both tutorials taught a helper that does it. T19 made the helper divide by the question's levels
rather than by the size of `probabilities`, because the question is the source of the level
count. In Scala 3 the helper then takes the question, `r.severity.normalized(questions.severity)`.
In 2.13 a key does not give back its `Score`, so the guide kept each Score apart from its key and
made the key in the call, `severity.as("severity")`, only to pass the Score to the helper.

The `Codec` already has the question when it decodes an answer: it maps each level index back to
the caller's value with the question's levels.

## Forces

- **The showcase bar** ([ADR-0001](0001-a-study-and-portfolio-project.md)): a helper that every
  caller copies, and that needs the question next to the answer, is the opposite of compact.
- **The 2.13 module follows Scala 3** ([ADR-0009](0009-two-native-modules-no-shared-code.md)).
  A key that keeps its `Score[L]`, only in 2.13, would be 2.13 design of its own.
- **A field changes the answer's shape.** `ScoreAnswer` gains a constructor parameter, and a
  caller's positional pattern or construction breaks. There is no release yet, so no caller to
  break.
- **Field or method.** A method needs the level count in the answer anyway; a field keeps the
  answer a plain value, compared by `==` as before.

## Decision

- `ScoreAnswer[L](score, normalized, mostLikely, confidence, probabilities)`, in both modules:
  `normalized: Double` is `score / (levels - 1)`, where `levels` is the number of levels of the
  question, computed by the `Codec` when it decodes the answer.
- The `Validator` already refuses a Score with fewer than 2 levels, so the division is never by
  zero on an answer that jev4s decodes.
- The guides read `r.severity.normalized` in Scala 3 and `s.normalized` in 2.13, and teach no
  helper.

## Consequences

- Scores with different numbers of levels compare and combine with no code of the caller's.
- `ScoreAnswer` has five fields; code that builds one by hand, such as a test, passes
  `normalized` too, and nothing checks that it agrees with `score`.
- `normalized` is exact only as far as Jev's `score` stays within 0 and the highest level, as
  the API documents it.
- Do not compute it from the size of `probabilities`: a reply that leaves out a level would give
  another number. The question is the source of the level count.
