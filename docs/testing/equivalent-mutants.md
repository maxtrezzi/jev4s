# Equivalent mutants

An **equivalent mutant** changes the code without changing any observable behaviour, so no
test can kill it. Every mutant excluded with `@SuppressWarnings` has **one row here**, with the
reason ([ADR-0008](../adr/0008-full-coverage-and-mutation-testing.md)).

| Date | Module, file and line | Mutation | Why it is equivalent | Checked by |
|---|---|---|---|---|
| — | — | — | *(none yet)* | — |

## Before adding a row: look for the edge case

A mutant that looks equivalent often is not. Example, not from this code base:

```scala
def clamp(d: Double): Double = if d < 0 then 0.0 else if d > 1 then 1.0 else d
// mutant: `d < 0` becomes `d <= 0`
```

With `d = 0.0` both versions return `0.0`. With `d = -0.0` the original returns `-0.0` and the
mutant returns `0.0`. The two are equal under `==`, but `java.lang.Double.equals` tells them
apart, so a test can kill this mutant. Search for such cases before calling a mutant
equivalent.
