# ADR-0037: The 2.13 client answers with a typed tuple

- **Status:** Accepted
- **Date:** 2026-09-23
- **Supersedes:** [ADR-0028](0028-a-key-matches-a-question-of-the-same-classes.md)
- **Amends:** [ADR-0023](0023-the-scala-2-13-client-api.md)

## Context

In 2.13, `ask(state, keys*)` returned `Answers`, and `Answers.get(key)` returned `Option[A]`
([ADR-0023](0023-the-scala-2-13-client-api.md)). The `Option` existed for a key that was not
asked in this call, or asked with a different question; to rule out a wrong cast,
`Answers.get` compared the two questions part by part, classes included
([ADR-0028](0028-a-key-matches-a-question-of-the-same-classes.md)). A caller who had just asked
three keys still unwrapped three `Option`s: the README's 2.13 example was a `for` over them.

## Forces

- **An answer read by position cannot belong to another question.** With one overload per
  number of keys, `ask(state, k1, k2): Either[JevError, (A1, A2)]`, the answer comes from the
  call's own list: no `Option`, no comparison of questions, one cast per position.
- **Overloads by arity resolve cleanly.** Checked 2026-09-23 on Scala 2.13.18 and again
  2026-09-24 on 2.13.16, with `-Werror -Xlint`: next to a varargs form, two keys pick the
  two-key overload and three the varargs one. With no varargs `ask` left, each call has exactly
  one candidate.
- **Scala 3 first ([ADR-0009](0009-two-native-modules-no-shared-code.md)).** A tuple is the
  Scala 3 named tuple without its names, and Scala 3 already has a dynamic form, `askMap`,
  returning `Map[String, Answer]`. A 2.13 name of its own for the same thing (`askAll`, in the
  proposal) would be design the 2.13 module invents.
- **Overloads by hand or generated.** A source generator in sbt saves about ten short methods
  and adds a build step; methods written by hand read directly in the Scaladoc and in a review.
- **Coverage ([ADR-0008](0008-full-coverage-and-mutation-testing.md))**: every overload needs a
  test, and a test that reads each position.

## Decision

- `ask(state, k1, …, kN)` for 1 to 10 keys, written by hand, each one line over a private
  method that returns the answers in the order of the keys. With one key it returns the answer,
  `Either[JevError, A1]`; with 2 to 10, a tuple, `Either[JevError, (A1, …, AN)]`.
- The dynamic form is `askMap(state, questions: Map[String, Question[_]])`, returning
  `Either[JevError, Map[String, Answer]]`, as in Scala 3.
- `Answers`, `Answers.get` and the comparison of questions go: no form reads answers by key.
  `ask(state, keys*)` goes with them.
- The cast of each answer is inside the key (`Key.answer`, private to the library), and it cannot
  fail: the codec decodes the answer of a question into that question's answer type.

## Consequences

- The 2.13 README and guide read their answers from a tuple: `case Right((t, d, f))`.
- `ask` with a fixed list of keys changed type: a source-breaking change, made before the first
  release.
- More than 10 keys in one call do not compile; they need `askMap`. A test checks it.
- `askMap` answers are `Answer`s, and a caller matches on them, as in Scala 3.
- Do not add a comparison of questions back to the typed overloads: they cannot mix up
  questions. Do not add a varargs `ask` next to them: `askMap` is the dynamic form.
