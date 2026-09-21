# jev4s

A Scala client for **Jev**, the "System One" model from TypeSafe AI. Jev does not write text:
you give it a state and some typed questions, and it returns typed answers with probabilities.
jev4s keeps those types in Scala, so the compiler knows what each answer is.

```scala
client.ask(ticket, (
  dept   = Choice[Dept]("Which team should handle this?"),
  urgent = Noul("The message conveys urgency"),
)).map(r => route(r.dept.choice, r.urgent.isYes))   // r.dept.choice is a Dept
```

> **Unofficial and independent.** jev4s is not affiliated with, endorsed by, or part of
> TypeSafe AI.

**Status: design finished, no code yet.** Nothing is published. The work plan is in
[`docs/tasks/`](docs/tasks/README.md), and every design decision, with the options that were
rejected, is in [`docs/adr/`](docs/adr/README.md).

## Two modules

jev4s has one module for each Scala version, and each one is written in the style of its own
version ([ADR-0009](docs/adr/0009-two-native-modules-no-shared-code.md)):

| | Scala 3.7+ | Scala 2.13 |
|---|---|---|
| Artifact | `jev4s_3` | `jev4s_2.13` |
| Answers | named tuples: `r.dept` | typed keys: `r.get(dept)` |
| Choice options from an `enum` | `derives JevChoice` | written by hand |

Both modules use the same coordinates, so sbt picks the right one:
`"io.github.maxtrezzi" %% "jev4s" % "<version>"`.

## Thanks

These community clients came first and shaped the design:
[scala-jev-sdk](https://github.com/ticofab/scala-jev-sdk) and
[zio-typesafe-ai](https://github.com/jamesward/zio-typesafe-ai).

## License

[Apache License 2.0](LICENSE).
