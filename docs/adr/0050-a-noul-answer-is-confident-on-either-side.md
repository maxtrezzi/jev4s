# ADR-0050: A Noul answer is confident on either side

- **Status:** Accepted
- **Date:** 2026-09-25
- **Supersedes:** —
- **Amends:** —

## Context

`ChoiceAnswer.ifConfident(min)` returns the choice when the confidence is at least `min`
([ADR-0019](0019-probability-literals-and-questions-compared-by-value.md)). A `NoulAnswer` has only
`probability` and `isYes`, fixed at 0.5, so a caller who wants three outcomes — act on "yes",
act on "no", or ask a person — writes two comparisons, one of them against `1 - p`. T18 noted a
threshold on `NoulAnswer`; `typesafe-sdk-scala` has `isYes(0.8)`. T23 took it up before the first
release.

## Forces

- **The same word for the same idea.** Jev gives no confidence for a Noul: its one probability
  says how sure it is, on the side of "yes" or of "no". `ifConfident` on a Choice already means
  "the answer, when Jev is sure enough".
- **"No" is an answer too.** `isYes(min)` says whether "yes" is likely enough; its `false` mixes
  a likely "no" with an unsure answer, which is the case a threshold is for.
- **Alternatives considered.** `isYes(min): Boolean`, as `typesafe-sdk-scala`: shorter, but only
  one side. A `confidence` method on `NoulAnswer`: the word means a value that Jev computes on a
  Choice and a Score, and would mean a formula here. The owner chose `ifConfident` on
  2026-09-25.

## Decision

- `NoulAnswer.ifConfident(min): Option[Boolean]` in both modules, taking a `Probability` in
  Scala 3 and a `Double` in 2.13, as `ChoiceAnswer.ifConfident` does in each.
- It is `Some(isYes)` when the probability of the more likely answer, `max(p, 1 - p)`, is at
  least `min`, and `None` otherwise. At `p = 0.5` the more likely answer is "yes", as for
  `isYes`.
- The tutorials show it in chapter 6, next to `ChoiceAnswer.ifConfident`.

## Consequences

- `urgent.ifConfident(Probability(0.8))` matches on `Some(true)`, `Some(false)` and `None`.
- With `min` of 0.5 or less, every answer is confident, and `ifConfident` is `Some(isYes)`.
- `1 - p` is computed as a `Double`: `1 - 0.2` is exactly 0.8, and the tests check both sides of
  0.8 with the nearest `Double`s.
