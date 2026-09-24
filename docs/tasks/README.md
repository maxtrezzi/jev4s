# Work items

What to do next, and what is done. Why the work is shaped this way lives in
[`../adr/`](../adr/README.md): tasks and ADRs answer different questions and should not
restate each other ([ADR-0012](../adr/0012-track-work-items-in-docs-tasks.md)).

| Question | Answer lives in |
|---|---|
| What do I do next? Is it done? | here |
| Why is it built this way? What did we reject? | [`../adr/`](../adr/README.md) |

A task that settles a design question closes by writing an ADR and linking to it. A task that
only gets work done closes by being marked `Done`, with what it found.

## Files

- [`milestones.md`](milestones.md) — M1–M8. What ships, in what order.
- [`open-decisions.md`](open-decisions.md) — items waiting on the owner, not on work.
- [`tasks.md`](tasks.md) — work items between milestones (T1…).

## Status board

| Item | Title | Status |
|---|---|---|
| [M1](milestones.md#m1--base-two-modules-and-the-model) | Base: two modules and the model | Done |
| [M2](milestones.md#m2--json-codec) | JSON (`Codec`) | Done |
| [M3](milestones.md#m3--scala-3-api) | Scala 3 API | Done |
| [M4](milestones.md#m4--scala-213-api) | Scala 2.13 API | Done |
| [M5](milestones.md#m5--real-http) | Real HTTP | Done |
| [M6](milestones.md#m6--quality-and-documentation) | Quality and documentation | Done |
| [M7](milestones.md#m7--publishing) | Publishing | Not started |
| [M8](milestones.md#m8--optional) | Optional | Not started |
| [T1](tasks.md#t1--scala-3-model-polish-before-m2) | Scala 3 model polish before M2 | Done |
| [T2](tasks.md#t2--structured-instructions-and-criteria) | Structured instructions and criteria | Done |
| [T3](tasks.md#t3--newer-major-versions-of-the-github-actions) | Newer major versions of the GitHub actions | Done |
| [T4](tasks.md#t4--fixes-from-the-review-of-the-integrated-branch) | Fixes from the review of the integrated branch | Done |
| [T5](tasks.md#t5--a-time-limit-on-the-retries-of-a-call) | A time limit on the retries of a call | Done |
| [T6](tasks.md#t6--user-guides) | User guides | Done |
| [T7](tasks.md#t7--a-score-typed-by-the-callers-enum) | A Score typed by the caller's enum | Done |
| [T8](tasks.md#t8--a-reply-without-input-tokens-still-answers) | A reply without input tokens still answers | Done |
| [T9](tasks.md#t9--the-213-module-on-scala-21316) | The 2.13 module on Scala 2.13.16 | Not started |
| [T10](tasks.md#t10--typed-tuples-from-the-213-client) | Typed tuples from the 2.13 client | Blocked |
| [T11](tasks.md#t11--the-callers-own-http-client) | The caller's own HTTP client | Not started |
| [T12](tasks.md#t12--scaladoc-fixes) | Scaladoc fixes | Done |
| [T13](tasks.md#t13--a-guide-section-on-gateways) | A guide section on gateways | Blocked |
| [T14](tasks.md#t14--a-guide-chapter-on-many-requests) | A guide chapter on many requests | Blocked |
| [T15](tasks.md#t15--every-header-of-a-real-reply) | Every header of a real reply | Needs decision |
| [D1](open-decisions.md#d1--scala-3-target-version) | Scala 3 target version | Done |
| [D2](open-decisions.md#d2--the-name-jev-in-the-library-name) | The name "Jev" in the library name | Done |
| [D3](open-decisions.md#d3--when-mutation-testing-runs-in-ci) | When mutation testing runs in CI | Done |

## Conventions

**Identifiers never change.** `M3` and `D1` are cited from `AGENTS.md` and from the ADRs.
Items are retired in place rather than renumbered, and new work takes the next free number.

**Status values**

| Status | Meaning |
|---|---|
| `Not started` | Ready to pick up |
| `In progress` | Someone is on it |
| `Blocked` | Waiting on another item; names which one |
| `Needs decision` | Waiting on the owner, not on work |
| `Done` | Finished, with its outcome recorded in the entry |

**Closing an item** means recording what was *found*, not just ticking a box. A finding that
contradicts an accepted ADR triggers a new ADR that amends or supersedes it.

**Every item gets its own branch**, cut from `dev` and merged back into it, named after the
item: `milestone/m1-base`, `decision/d1-scala-3-target`
([ADR-0013](../adr/0013-one-branch-per-task-work-lands-on-dev.md)). The branch carries the
work, the status update here, and any ADR the item produces.

**Scala 3 first.** In each milestone the Scala 3 module settles the design and the 2.13 module
follows ([ADR-0009](../adr/0009-two-native-modules-no-shared-code.md)).
