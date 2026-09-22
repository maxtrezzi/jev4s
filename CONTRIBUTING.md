# Contributing

Thank you for looking. jev4s is a small library with strong opinions, so the most useful thing
to do before you write code is **open an issue**. A short conversation costs ten minutes; a
large pull request that does not fit the design costs both of us much more.

## Before you propose a feature

Read [`docs/adr/`](docs/adr/README.md). It holds every design decision, with the options that
were rejected and why. A change that goes against one of them is still welcome to discuss, but
the discussion starts from the reasons already written there.

## Pull requests

- **Branch from `dev` and open your pull request against `dev`.** `main` holds released
  versions only ([ADR-0013](docs/adr/0013-one-branch-per-task-work-lands-on-dev.md)).
- One branch per change.
- The build stays green, and CI must pass before a merge.
- **Format your code** with `sbt scalafmtAll scalafmtSbt` before you commit. CI fails on code
  that is not formatted.
- **Both modules get the change.** jev4s has a Scala 3 module and a Scala 2.13 module, with no
  shared code ([ADR-0009](docs/adr/0009-two-native-modules-no-shared-code.md)). The Scala 3
  module goes first; the 2.13 module follows in its own style.
- **Tests must cover every statement and branch, and survive mutation testing**
  ([ADR-0008](docs/adr/0008-full-coverage-and-mutation-testing.md)). If a mutant survives
  and no test can kill it, explain why in
  [`docs/testing/equivalent-mutants.md`](docs/testing/equivalent-mutants.md).
- Stryker4s cannot mutate `inline` code. If you change an `inline` method, apply its mutants by
  hand and update the table in the same file
  ([ADR-0020](docs/adr/0020-code-stryker4s-cannot-mutate-is-mutated-by-hand.md)).
- A test that cannot fail is worse than no test. If a test guards against a specific fault,
  break the code and check that the test catches it.
- If your change settles a design question, it needs an ADR. Copy
  [`docs/adr/0000-template.md`](docs/adr/0000-template.md), take the next free number after
  the ones already on `dev`, and add a row to the index. Two open pull requests can take the
  same number; renumber yours if the other one merges first.
- Tests that call the real Jev API cost money. They are not part of the normal build.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE), the same terms that cover the project.
