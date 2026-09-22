# ADR-0032: The README and the guides quote compiled examples

- **Status:** Accepted
- **Date:** 2026-09-22
- **Supersedes:** —
- **Amends:** —

## Context

The owner asked for user documentation: two examples at the top of the README, one for each
Scala version, and three guides — the concepts of Jev, shared by both versions, and one tutorial
for each version, each starting from the simplest call. Together they hold about fifty pieces of
Scala code.

Until now the README's code was checked by hand: M6 compiled its snippets once, in a temporary
project. Nothing kept them compiling after that, and the API changed twice since (ADR-0027,
ADR-0031).

## Forces

- **Code in documentation rots silently.** A renamed method or a new parameter type breaks a
  snippet, and no build notices. Fifty snippets make that certain rather than possible.
- **mdoc** compiles the Markdown itself and is the usual answer in Scala. It adds an sbt plugin
  and a generated copy of every document, it would need one set-up per Scala version, and its
  output is not formatted by scalafmt. It was not chosen.
- **Examples that compile somewhere, with the documentation copying them**, need only a check
  that the copy is exact. The live projects are already compiled by CI for both versions
  (M5, M6), and M6 settled that they hold the examples.
- The examples call the paid API when they run, so they must stay outside the root build, as the
  live tests do.

## Decision

- The examples of the README and of `docs/guide/` are programs in `live/scala3` and
  `live/scala213`: the README's in the default package, the tutorials' in the package `guide`.
  CI compiles them with the live tests, and formats them with scalafmt.
- A region of an example is marked with `// snippet: <name>` and `// end: <name>`. A document
  quotes it with the line `<!-- snippet: <path>#<name> -->` right before a code block.
- `build/check-docs.py` fails when a quoted block differs from its region, without the region's
  common indent, or when the region or the file does not exist, or the file is not tracked.
  `build/check-docs.py --write-snippets` copies every region into its block.
- `sbt scala3Live/run` and `sbt scala213Live/run` keep starting the example of M5; the others
  start with `runMain`.
- JSON in the concepts guide is copied from `golden/`, so every reply shown there is real.

## Consequences

- A change of API that breaks a documented example fails the build of the live project, and a
  change to an example that the documents do not follow fails the docs check.
- The documents show only code that compiles as a whole program, with its imports and
  definitions around it; a line that does not compile, such as a compile error to show, is
  written as text, not as a quoted block.
- Output printed by an example is not checked. A document that shows output copies it from a
  real run and says so.
- Anyone editing a guide edits the example first, then runs `--write-snippets`. Editing the code
  block directly is overwritten by the next `--write-snippets` and fails the check until then.
