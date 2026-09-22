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

## Mutants Stryker4s cannot build, applied by hand

Stryker4s cannot build a mutant inside `inline` code. These mutants are excluded with
`@SuppressWarnings` on the `inline` method, applied by hand, and the tests run
([ADR-0020](../adr/0020-code-stryker4s-cannot-mutate-is-mutated-by-hand.md)). Repeat the check
when the method or its tests change.

"Compile error" means the module or its tests no longer compile: a literal that the tests use
is now rejected, or one they expect to be rejected is accepted. Stryker4s would report it as
`CompileError`, which counts as detected
([ADR-0016](../adr/0016-undetected-mutants-are-checked-from-the-report.md)).

**`scala3`, `Probability.scala`, `Probability.apply`** — checked 2026-09-22, 11 mutants, all
detected.

| Line | Mutation | Detected by |
|---|---|---|
| 34 | `d >= 0.0` → `d > 0.0` | compile error: `Probability(0.0)` is rejected |
| 34 | `d >= 0.0` → `d == 0.0` | compile error: `Probability(0.8)` is rejected |
| 34 | `d >= 0.0` → `d < 0.0` | compile error: `Probability(0.8)` is rejected |
| 34 | `d <= 1.0` → `d < 1.0` | compile error: `Probability(1.0)` is rejected |
| 34 | `d <= 1.0` → `d == 1.0` | compile error: `Probability(0.8)` is rejected |
| 34 | `d <= 1.0` → `d > 1.0` | compile error: `Probability(0.8)` is rejected |
| 34 | `&&` → `\|\|` | `ProbabilitySuite`: `Probability(1.5)` compiles |
| 34 | condition → `true` | `ProbabilitySuite`: `Probability(1.5)` compiles |
| 34 | condition → `false` | compile error: every literal is rejected |
| 35 | range message → `""` | `ProbabilitySuite`: the message is compared |
| 32 | literal message → `""` | `ProbabilitySuite`: the message is compared |

**`scala3`, `JevConfig.scala`, `JevConfig.defaultBaseUrl`** — checked 2026-09-22, 1 mutant,
detected. A value initialised once is a "static" mutant, which Stryker4s cannot test.

| Line | Mutation | Detected by |
|---|---|---|
| 20 | `"https://api.typesafe.ai"` → `""` | `JevConfigSuite`: the default base URL |

**`scala213`, `JevConfig.scala`, `JevConfig.defaultBaseUrl`** — checked 2026-09-22, 1 mutant,
detected, for the same reason.

| Line | Mutation | Detected by |
|---|---|---|
| 20 | `"https://api.typesafe.ai"` → `""` | `JevConfigSuite`: the default base URL |
