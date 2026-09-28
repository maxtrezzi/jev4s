# ADR-0028: In 2.13, a key reads an answer only for a question of the same classes

- **Status:** Superseded by ADR-0037
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** [ADR-0023](0023-the-scala-2-13-client-api.md)

## Context

[ADR-0023](0023-the-scala-2-13-client-api.md) lets `Answers.get(key)` return an answer only when
the question asked under the key's name **equals** the key's question, and promises that a key
from another request gives `None`, never a wrong type or an exception.

Scala's `==` on numbers is cooperative: `1 == 1L`, `Some(1) == Some(1L)`, `Vector(1) ==
Vector(1L)`. A `Choice[Int]` and a `Choice[Long]` with the same options are therefore equal,
and a `Choice[Long]` key read the `Int` answer; the first use of its value threw a
`ClassCastException`.

## Forces

- **Reference equality (`eq`)** is exact, but a key built again with the same question, as a
  caller does in a second place, would stop reading its answer.
- **A type tag in the key** would be exact about types, but a `ClassTag` erases type
  arguments, and a `TypeTag` needs `scala-reflect`, against
  [ADR-0005](0005-minimal-dependencies-ujson-and-the-jdk.md).
- **Comparing runtime classes part by part** catches every case of cooperative equality, which
  is always between boxed numbers or characters of different classes, at any depth of case
  classes and collections. It cannot tell apart types that erase to the same classes, but those
  never compare equal by cooperation.

## Decision

`Answers.get` reads the answer when the two questions are `==` **and** each part of one has the
class of the same part of the other, walking case classes (`Product`) and collections
(`Iterable`). `null` matches only `null`.

## Consequences

- A `Choice[Long]` key over a `Choice[Int]` answer gives `None`; a test checks it for plain
  numbers, `Option`s and `Vector`s, and checks that `==` alone says the questions are equal.
- A key built again with an equal question still reads its answer.
- Do not go back to `question == key.question` "because it is simpler": the
  `ClassCastException` comes back, and the test fails.
- The Scala 3 module needs none of this: named tuples type each answer at compile time, and
  `askMap` returns `Answer`, which a caller matches on.
