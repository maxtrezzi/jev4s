# ADR-0019: Probability literals checked by the compiler, and questions compared by value

- **Status:** Accepted
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** —

## Context

M2 builds `Probability` and `ChoiceAnswer` in the codec, so their contract is settled before M2
starts ([T1](../tasks/tasks.md#t1--scala-3-model-polish-before-m2)). The M1 model of the Scala 3
module had three flaws against the showcase bar of
[ADR-0009](0009-two-native-modules-no-shared-code.md):

- `Question.Choice` took its options from a `using` clause, the second parameter list. A case's
  `equals` and `hashCode` read only the first, so two Choices with the same instructions and
  different options were equal and had the same hash. `Question` had no `CanEqual`, so under
  `strictEquality` two questions could not be compared at all.
- `Probability` compared only with a `Double`, so two probabilities could not be compared with
  each other, and `ChoiceAnswer.ifConfident(min: Double)` accepted `-3` and `1.5`.
- `Probability.from` returned `Either[String, Probability]`, the only error in the library that
  was a `String`.

Fixed: `Probability` is an `opaque type` over `Double` (ADR-0009), so its extension methods
erase to methods on `Double`. An overload that takes a `Probability` and one that takes a
`Double` have the same erased signature and cannot both exist.

## Forces

- **Keep `Double` thresholds** and add comparisons with a `Probability` under other names.
  Two ways to do one thing, and the unchecked one is the shorter.
- **Thresholds are `Probability`, built with `from`.** Type-safe, but `from` returns an
  `Option`, so a threshold written in code becomes `Probability.from(0.8).get`.
- **Thresholds are `Probability`, and a literal is checked by the compiler.** An `inline`
  constructor checks a literal with `compiletime.error`: `Probability(0.8)` compiles and
  `Probability(1.5)` does not. This is what Scala 3 can do and 2.13 cannot, which is the
  point of the showcase. A literal must be a `Double`: `Probability(1)` does not compile.
- **The error type of `from`.** A typed error (`ProbabilityOutOfRange(d)`) carries only the
  value the caller passed in. `Option` loses nothing, and is how the standard library
  returns a failed conversion (`toIntOption`).
- **A Choice's options.** An `override def equals` is not possible on an `enum` case, and
  leaving the options in a `using` clause keeps the defect. Moving them into the case, as the
  2.13 module already does, fixes equality with no custom code. The given `JevChoice[C]` moves
  to a constructor.

## Decision

In the Scala 3 module:

- **`Probability(0.8)`** is an `inline` constructor for a `Double` literal, checked by the
  compiler. A literal outside [0, 1], `NaN` included, gives *"a Probability must be between 0
  and 1"*. A value that is not a literal gives *"Probability(...) takes a number literal; use
  Probability.from for a value known only at runtime"*.
- **`Probability.from(d): Option[Probability]`** for values known only at runtime.
- `Probability` compares only with another `Probability` (`<`, `<=`, `>`, `>=`), and has a
  given `Ordering` and a given `CanEqual`. Thresholds are `Probability`:
  `answer.ifConfident(Probability(0.8))`.
- **`Question.Choice(instructions, options: List[ChoiceOption[C]])`** holds its options. The
  top-level `Choice[C](instructions)(using JevChoice[C])` builds one from the given options, so
  `Choice[Dept]("Which team?")` reads as before, and `type Choice[C]` names the case.
- `Question` has a given `CanEqual[Question[?], Question[?]]`, and the three answer types
  derive `CanEqual`. Two questions are equal when every part of them is equal.

In the 2.13 module, which follows as far as the language allows:

- `Probability.from` returns `Option[Probability]`, and `Probability` has an implicit
  `Ordering`.
- Thresholds stay `Double`: 2.13 has no `inline`, so it cannot check a literal, and a
  `Probability` threshold would need `from(...).get` at every use.
- `Choice` already held its options, so its equality needed no change.

## Consequences

- A threshold in Scala 3 code is checked when it compiles, and the answer API takes nothing
  outside [0, 1].
- `Probability(1)` and `Probability(0)` do not compile; they must be written `1.0` and `0.0`.
  The error message is the compiler's type mismatch, not ours.
- The two modules now differ on thresholds: `Probability` in Scala 3, `Double` in 2.13. The
  golden tests do not see it, because it is not part of the JSON.
- The codec of M2 turns a `None` from `from` into `JevError.Decoding` with the value in the
  message.
- The body of the `inline` constructor runs in the compiler, not at runtime. Stryker4s cannot
  mutate it; [ADR-0020](0020-code-stryker4s-cannot-mutate-is-mutated-by-hand.md) covers how
  it is still checked.
- Do not move a Choice's options back into a `using` clause "to make construction shorter":
  equality stops seeing them again, and no compiler error says so. A test in
  `QuestionSuite` fails if it happens.
