# ADR-0037: The 2.13 client answers with a typed tuple

- **Status:** Proposed
- **Date:** 2026-09-23
- **Supersedes:** —
- **Amends:** —

## Context

In 2.13, `ask(state, keys*)` returns `Answers`, and `Answers.get(key)` returns `Option[A]`
([ADR-0023](0023-the-scala-2-13-client-api.md)). The `Option` exists for a key that was not
asked in this call, or asked with a different question; to rule out a wrong cast,
`Answers.get` compares the two questions part by part, classes included
([ADR-0028](0028-a-key-matches-a-question-of-the-same-classes.md)). A caller who has just asked
three keys still unwraps three `Option`s: the README's 2.13 example is a `for` over them.

When accepted, this ADR amends ADR-0023 and ADR-0028 (how answers are read). The branch that
implements it sets `Amends` here and records the amendment in both.

## Forces

- **An answer read by position cannot belong to another question.** With one overload per
  number of keys, `ask(state, k1, k2): Either[JevError, (A1, A2)]`, the answer comes from the
  call's own list: no `Option`, no comparison of questions, one cast per position.
- **The overloads live next to the varargs form.** Checked 2026-09-23 on Scala 2.13.18 with
  `-Werror -Xlint`: with two keys Scala picks the two-key overload, with three the varargs one.
- **Scala 3 first ([ADR-0009](0009-two-native-modules-no-shared-code.md)).** A tuple is the
  Scala 3 named tuple without its names, so the 2.13 module does not invent a design of its own.
- **Coverage ([ADR-0008](0008-full-coverage-and-mutation-testing.md))**: every overload needs a
  test.
- **Questions built at runtime** still need a dynamic form.

## Decision

Proposed:

- `ask(state, k1, …, kN)` for 1 to N keys, returning `Either[JevError, (A1, …, AN)]`. N is
  settled when implementing (proposal: 10), with the overloads written by hand or generated.
- A dynamic form, `askAll(state, keys*)`. Whether it returns `Answers` as today or
  `Map[String, Answer]` like Scala 3's `askMap` is settled when implementing.
- If no form reads answers by key any more, `Answers.get` and its comparison of questions go.

## Consequences

- The 2.13 README reads its answers from a tuple, with no `Option`.
- `ask(state, keys*)` with a fixed list of keys changes type: a source-breaking change, made
  before the first release.
- More than N questions in one typed call need the dynamic form.
- Do not add the class comparison back to the typed overloads: they cannot mix up questions.
