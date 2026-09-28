# ADR-0044: Documented compile errors are checked by a test

- **Status:** Accepted
- **Date:** 2026-09-25
- **Supersedes:** —
- **Amends:** [ADR-0032](0032-documentation-quotes-compiled-examples.md)

## Context

The owner wants the README to show the robustness of the Scala 3 module with an example a reader
takes in at a glance (T19): a few lines against the README's own questions that do not compile,
each with the compiler's message. Measured on 2026-09-25 on `0b462c5`, Scala 3.9.0:

| Line | The error, with `…` for what is left out |
|---|---|
| `r.priority` | `value priority is not a member of (team : …ChoiceAnswer[…Team], duplicate : …NoulAnswer, feeling : …` |
| `(r.team.choice: Feeling)` | `Found: (…ChoiceAnswer[…Team]# choice : …Team)`, then `Required: …Feeling` |
| `Probability(1.5)` | `a Probability must be between 0 and 1` |
| `client.ask(42, questions)` | ``no ToState[Int]: give one, for example `given ToState[Int] = s => ujson.Obj(...)`, or pass a String or a ujson.Value.`` |

The compiler writes every type with its package, `io.github.maxtrezzi.jev4s.`, and some messages
span several lines, so a reader-sized message is shorter than the real one.

[ADR-0032](0032-documentation-quotes-compiled-examples.md) quotes only code that compiles, from
`live/`: a line that must not compile cannot live there. This ADR amends ADR-0032 with a second
kind of checked block.

## Forces

- **Documentation that is not checked rots** — the reason for ADR-0032. A compile error is more
  fragile than a snippet: a new Scala version can reword a message the compiler owns, such as
  `Found:` or `is not a member of`, while the messages jev4s writes in `@implicitNotFound` and
  `error(...)` change only with jev4s.
- **`compileErrors` takes a literal.** It is `inline`, so a test cannot compile a line it reads
  from the README at runtime. The code has to be written in the test, and the README compared
  with it.
- **Python cannot run the compiler.** `build/check-docs.py` can compare texts, not compile them.
- **Alternatives considered.** Prose, as the tutorials say "`Probability(1.5)` does not compile":
  checked by nothing, and not an example. A comment in a `live/` file: compiled, but the comment
  is checked by nothing. mdoc's `:fail` blocks: rejected for ADR-0032's reasons, a plugin and a
  set-up per Scala version. The compiler as a test dependency, to compile the README's lines
  at runtime: a large dependency for a handful of lines.

## Decision

- A block of documented compile errors is a `scala` code block right after the line
  `<!-- compile-errors: <path of a Scala 3 test suite> -->`. Each entry starts on a line that ends
  with `// error: <message>`; the indented lines after it with no marker belong to the same entry.
  The entry's code is its lines without the comment.
- The message may be shortened: it is a list of parts separated by `…`, and each part must
  appear, in order, in the compiler's full error text after the package prefix
  `io.github.maxtrezzi.jev4s.` is removed and every run of whitespace is made one space.
- The named test suite, in the `scala3` module's tests, defines what the README's example
  defines — its types, its questions, a client over a fake transport — and has one case for each
  entry: `documented("<code>", compileErrors("<code>"))`, the code written twice. Passed through
  an `inline` method instead, the code can fail with another message: measured on 2026-09-25,
  `Score("How?")` then gives an overload error, not its `@implicitNotFound` text. The case reads
  the document, finds the entry
  with its code, and checks the message against what `compileErrors` returned. The document
  reaches the tests as a test resource, as `golden/` does. The suites and the types they define
  are in the empty package, so that the compiler names the types as the document does.
- `build/check-docs.py` checks the other direction: every entry of the block has a case in the
  suite, with the same code in both literals of the case.
- ADR-0032's rule stays for everything that compiles. A document quotes compiled examples and,
  in these blocks only, documented compile errors.

## Consequences

- A line in the README that stops failing, or fails with another message, fails the build: the
  test on a new compiler message, the docs check on an entry with no case.
- A Scala upgrade that rewords a compiler-owned message fails the test, and the README changes in
  the same commit as the upgrade.
- Each entry is written three times, once in the document and twice in its case, but a check links
  the three.
- The block shows readable messages without the package names; the parts between `…` must still
  be the compiler's own words.
- Do not "simplify" the block into prose or comments: the whole point is an example that a check
  keeps true.
