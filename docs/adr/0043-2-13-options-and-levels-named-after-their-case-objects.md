# ADR-0043: 2.13 options and levels named after their case objects

- **Status:** Accepted
- **Date:** 2026-09-25
- **Supersedes:** —
- **Amends:** [ADR-0023](0023-the-scala-2-13-client-api.md), [ADR-0034](0034-a-score-typed-by-the-callers-enum.md)

## Context

Spark 4 runs Scala 2.13 only, so a Spark user of jev4s writes the 2.13 module
([ADR-0036](0036-the-2-13-module-compiles-with-spark-4s-scala.md),
[ADR-0041](0041-the-spark-example-builds-its-client-on-the-executors.md)). In Scala 3 an enum
`derives JevChoice` or `derives JevScale`, and each case's name becomes its key or its level
([ADR-0022](0022-the-scala-3-client-api.md), [ADR-0034](0034-a-score-typed-by-the-callers-enum.md)).
In 2.13 the options and the levels are written by hand, and the README's example writes each
case three times:

```scala
JevScale(ScaleLevel(Calm, "Calm"), ScaleLevel(Annoyed, "Annoyed"), ScaleLevel(Angry, "Angry"))
JevChoice(ChoiceOption(Billing, "billing"), ChoiceOption(Technical, "technical"), ChoiceOption(Sales, "sales"))
```

A case object knows its own name: `productPrefix`. Checked on 2026-09-25 in the 2.13 module's
tests, compiled with Scala 2.13.16 and `-Xlint -Werror`: `Calm` and `VeryAngry` for case objects
nested in the companion object of a sealed class, and `Other` for an instance `Other(1)` of a case
class.

The type of the options is not always a `Product` itself. A `sealed trait Team` whose case
objects extend it is not, while each case object is. Checked on 2026-09-25 with Scala 2.13.16 and
`-Xlint -Werror`: a bound `C <: Product` refuses that trait (`inferred type arguments [Team] do
not conform to method named's type parameter bounds [C <: Product]`), and a parameter
`(C with Product)*` accepts it and `sealed abstract class Team extends Product with Serializable`
alike, with `C` inferred from the expected type.

This ADR amends [ADR-0023](0023-the-scala-2-13-client-api.md) ("choice options are written by
hand") and [ADR-0034](0034-a-score-typed-by-the-callers-enum.md) ("2.13 follows: a `JevScale[L]`
written by hand").

## Forces

- **The showcase bar applies to 2.13 too** ([ADR-0001](0001-a-study-and-portfolio-project.md)).
  Its users are the Spark users, and a call site that repeats each name three times is the
  opposite of compact.
- **The 2.13 module follows Scala 3 and invents no design of its own**
  ([ADR-0009](0009-two-native-modules-no-shared-code.md)). The naming rules must be Scala 3's: the
  key in snake_case, the level's text as the case's name, a description from `Described`.
- **2.13 cannot list the cases of a sealed class without a macro.** A macro needs
  `scala-reflect` and a derivation library such as shapeless is a dependency, against
  [ADR-0005](0005-minimal-dependencies-ujson-and-the-jdk.md). The caller still lists the cases,
  in the order of the scale; only their names stop being repeated.
- **Alternatives considered.** `toString` accepts more types, `Enumeration` values and Java
  enums, but it can be overridden, and for a case class it includes the fields (`Other(1)`). A
  type class `Name[L]` lets a caller choose each name, but it asks for more code than it saves.
  The name `of` would sit next to `Choice.of[C]` and `Score.of[L]`, which build questions, not
  options; `cases` reads well but says less about where the text comes from.

## Decision

- `JevChoice.named[C](options: (C with Product)*)`: one option for each value, in the given
  order. The values must be `Product`s; `C` need not be.
  The key is the value's `productPrefix` in snake_case, with the rules of the Scala 3 module
  (`TechnicalSupport` → `technical_support`, `HTTPError` → `http_error`, `Locale.ROOT`). The
  description is the value's `description` when it extends `Described`, and none otherwise.
- `JevScale.named[L](levels: (L with Product)*)`: one level for each value, from low to high in
  the given order, with the same bound on the values. The level's text is the value's
  `description` when it extends `Described`, and its `productPrefix` otherwise, as in
  `JevScale.derived` of the Scala 3 module.
- A `trait Described { def description: String }` in the 2.13 module, as in Scala 3.
- The snake_case function is written again in the 2.13 module: the modules share no code
  (ADR-0009).
- The forms written by hand stay, for values that are not `Product`s or a name that differs from
  the case's. The counts and the duplicates stay `Validator` checks.

## Consequences

- The README's 2.13 example and `live/scala213` list each case once:
  `JevScale.named(Calm, Annoyed, Angry)`.
- A case left out of the list is not caught: a Scala 3 enum's derivation cannot forget one, and
  2.13 can. This is the cost of having no macro.
- Two instances of one case class share a name. The `Validator` reports it before sending, as
  `DuplicateOptionKey` or `DuplicateLevel`; the compiler does not.
- Renaming a case object changes the key or the level text that Jev reads, and so can change
  its answers, as renaming a case of a Scala 3 enum does.
- Do not replace `productPrefix` with `toString` "to accept more types": an overridden
  `toString` would silently change what Jev reads.
