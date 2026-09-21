# Architecture Decision Records

One file per decision, numbered in order, never deleted. A decision that turns out wrong is
not edited away: a new ADR supersedes it, and both stay in the history.

## Format

`NNNN-kebab-case-title.md`, starting at `0001`. Copy [`0000-template.md`](0000-template.md).

The body follows **Context → Forces → Decision → Consequences**: what was being decided and
why then, what made it a real choice, what was chosen, and what the project now lives with.
Consequences include the costs. An ADR that lists no downside was not a decision.

The header carries `Supersedes` (this ADR replaces one wholesale) and `Amends` (this ADR
narrows or widens part of one). Both default to `—`.

## Status values

| Status | Meaning |
|---|---|
| `Proposed` | Written up, not yet agreed |
| `Accepted` | In force; the code must follow it |
| `Accepted — <aspect> amended by ADR-NNNN` | Still in force, but a later ADR changed part of it; the later ADR wins where they differ |
| `Superseded by ADR-NNNN` | Replaced wholesale; kept for the reasoning |

`amended` may be `widened` or `narrowed` where that is more precise. The row below and the
ADR's own `Status` line must match **exactly**, and a pointer must name an ADR number.
[`build/check-docs.py`](../../build/check-docs.py) checks both, and checks that an amendment
or a replacement is recorded at both ends.

Superseding or amending an ADR edits only the old file's `Status` line. Its body stays as it
was, including the parts the newer ADR overrode: they are what makes the change readable.

**The body is also closed to additions.** A later measurement, a finding, or a note that the
Jev API has changed does not get appended, however clearly dated. It goes to
[`../tasks/`](../tasks/README.md), which exists to record what was found.

## When to write one

Whenever a discussion settles something that constrains future code: a dependency taken on,
an API shape fixed, a scope boundary drawn, a mechanism chosen over a real alternative. Not
for reversible implementation details, and not for what the code already states plainly.

Discussions and half-formed thinking do not belong here. They are kept outside this
repository, and an ADR is their rewritten result, written to be published
([ADR-0011](0011-record-decisions-as-adrs.md)). Work items do not belong here either: they
live in [`../tasks/`](../tasks/README.md) ([ADR-0012](0012-track-work-items-in-docs-tasks.md)).

**An ADR number is only safe once it is on `dev`.** Two branches that each take "the next
free number" are both right and still collide. Renumber before anything cites the number,
and after a renumber search the whole tree, source included, for the old `ADR-NNNN`.

## Index

| # | Title | Status |
|---|---|---|
| [0001](0001-a-study-and-portfolio-project.md) | Jev4s is a study and portfolio project | Accepted |
| [0002](0002-direct-style-no-effect-system.md) | Direct style, no effect system | Accepted |
| [0003](0003-cross-build-scala-2-13-and-3-from-m1.md) | Cross-build Scala 2.13 and Scala 3, from M1 | Superseded by ADR-0009 |
| [0004](0004-two-apis-typed-keys-and-named-tuples.md) | Two APIs: typed keys and named tuples | Superseded by ADR-0009 |
| [0005](0005-minimal-dependencies-ujson-and-the-jdk.md) | Minimal dependencies: ujson and the JDK | Accepted |
| [0006](0006-scala-3-7-rather-than-3-3-lts.md) | Scala 3.7 rather than 3.3 LTS | Accepted |
| [0007](0007-name-coordinates-and-package.md) | Name, coordinates and package | Accepted |
| [0008](0008-full-coverage-and-mutation-testing.md) | Full coverage and mutation testing | Accepted |
| [0009](0009-two-native-modules-no-shared-code.md) | Two native modules, Scala 3 and Scala 2.13, with no shared code | Accepted |
| [0010](0010-reply-metadata-through-onreply.md) | Reply metadata through `onReply` | Accepted |
| [0011](0011-record-decisions-as-adrs.md) | Record decisions as ADRs, discussions outside the repository | Accepted |
| [0012](0012-track-work-items-in-docs-tasks.md) | Track work items in `docs/tasks/` | Accepted |
| [0013](0013-one-branch-per-task-work-lands-on-dev.md) | One branch per task; work lands on `dev`, `main` carries releases | Accepted |
| [0014](0014-agent-guidance-lives-in-agents-md.md) | Agent guidance lives in `AGENTS.md` | Accepted |
| [0015](0015-user-facing-prose-for-a-non-native-reader.md) | User-facing prose is written for a non-native reader | Accepted |
