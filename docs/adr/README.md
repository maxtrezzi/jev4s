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
| [0006](0006-scala-3-7-rather-than-3-3-lts.md) | Scala 3.7 rather than 3.3 LTS | Superseded by ADR-0017 |
| [0007](0007-name-coordinates-and-package.md) | Name, coordinates and package | Accepted — publishing under the name amended by ADR-0033 |
| [0008](0008-full-coverage-and-mutation-testing.md) | Full coverage and mutation testing | Accepted — mutation threshold amended by ADR-0016; CI schedule amended by ADR-0018; mutation exclusions widened by ADR-0020 |
| [0009](0009-two-native-modules-no-shared-code.md) | Two native modules, Scala 3 and Scala 2.13, with no shared code | Accepted |
| [0010](0010-reply-metadata-through-onreply.md) | Reply metadata through `onReply` | Superseded by ADR-0025 |
| [0011](0011-record-decisions-as-adrs.md) | Record decisions as ADRs, discussions outside the repository | Accepted |
| [0012](0012-track-work-items-in-docs-tasks.md) | Track work items in `docs/tasks/` | Accepted |
| [0013](0013-one-branch-per-task-work-lands-on-dev.md) | One branch per task; work lands on `dev`, `main` carries releases | Accepted |
| [0014](0014-agent-guidance-lives-in-agents-md.md) | Agent guidance lives in `AGENTS.md` | Accepted |
| [0015](0015-user-facing-prose-for-a-non-native-reader.md) | User-facing prose is written for a non-native reader | Accepted |
| [0016](0016-undetected-mutants-are-checked-from-the-report.md) | Undetected mutants are checked from the report, not by Stryker's threshold | Accepted |
| [0017](0017-scala-3-9-lts.md) | Compile the Scala 3 module with Scala 3.9 LTS | Accepted |
| [0018](0018-mutation-testing-runs-on-every-pull-request.md) | Mutation testing runs in CI on every pull request | Accepted |
| [0019](0019-probability-literals-and-questions-compared-by-value.md) | Probability literals checked by the compiler, and questions compared by value | Accepted |
| [0020](0020-code-stryker4s-cannot-mutate-is-mutated-by-hand.md) | Code Stryker4s cannot mutate is excluded, and its mutants are applied by hand | Accepted — static mutants widened by ADR-0024 |
| [0021](0021-what-the-codec-keeps-from-a-jev-reply.md) | What the codec keeps from a Jev reply | Accepted — instructions and criteria amended by ADR-0031; shape of ScoreAnswer amended by ADR-0034; required fields amended by ADR-0035 |
| [0022](0022-the-scala-3-client-api.md) | The Scala 3 client API, with compile-time checks and no inline code | Accepted — client construction amended by ADR-0024; callback amended by ADR-0025 |
| [0023](0023-the-scala-2-13-client-api.md) | The Scala 2.13 client API: typed keys over the Scala 3 client's pieces | Accepted — client construction amended by ADR-0024; callback amended by ADR-0025; question match amended by ADR-0028 |
| [0024](0024-http-retries-and-configuration.md) | HTTP over java.net.http, the official SDKs' retries, and configuration from the environment | Accepted — 400 mapping amended by ADR-0026; API key and base URL amended by ADR-0027; server error message amended by ADR-0029; retry budget amended by ADR-0030 |
| [0025](0025-client-events-through-onevent.md) | Client events through `onEvent`, replacing `onReply` | Accepted — content of Replied amended by ADR-0035 |
| [0026](0026-http-400-is-a-rejected-request.md) | HTTP 400 is a rejected request, and other statuses carry a readable message | Accepted — raw body length amended by ADR-0029 |
| [0027](0027-the-api-key-is-a-type-and-travels-over-tls.md) | The API key is a type of its own, and travels only over TLS | Accepted |
| [0028](0028-a-key-matches-a-question-of-the-same-classes.md) | In 2.13, a key reads an answer only for a question of the same classes | Accepted |
| [0029](0029-every-http-error-carries-a-short-message.md) | Every HTTP error carries a short, readable message | Accepted |
| [0030](0030-a-call-retries-for-at-most-maxelapsed.md) | A call retries for at most `maxElapsed` | Accepted |
| [0031](0031-instructions-and-criteria-are-ujson-values.md) | Instructions and criteria are `ujson.Value`s | Accepted |
| [0032](0032-documentation-quotes-compiled-examples.md) | The README and the guides quote compiled examples | Accepted |
| [0033](0033-publish-as-jev4s-and-rename-on-request.md) | Publish as jev4s, after asking TypeSafe AI and without waiting for an answer | Accepted |
| [0034](0034-a-score-typed-by-the-callers-enum.md) | A Score typed by the caller's enum | Accepted |
| [0035](0035-a-reply-without-input-tokens-still-answers.md) | A reply without input tokens still answers | Accepted |
| [0036](0036-the-2-13-module-compiles-with-spark-4s-scala.md) | The 2.13 module compiles with the Scala of the oldest Spark 4 | Proposed |
| [0037](0037-the-2-13-client-answers-with-a-typed-tuple.md) | The 2.13 client answers with a typed tuple | Proposed |
| [0038](0038-the-caller-may-pass-its-own-http-client.md) | The caller may pass its own HTTP client | Proposed |
| [0039](0039-the-library-does-not-limit-the-request-rate.md) | The library does not limit the request rate | Proposed |
