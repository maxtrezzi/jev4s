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

## Status board

| Item | Title | Status |
|---|---|---|
| [M1](milestones.md#m1--base-two-modules-and-the-model) | Base: two modules and the model | Done |
| [M2](milestones.md#m2--json-codec) | JSON (`Codec`) | Not started |
| [M3](milestones.md#m3--scala-3-api) | Scala 3 API | Not started |
| [M4](milestones.md#m4--scala-213-api) | Scala 2.13 API | Not started |
| [M5](milestones.md#m5--real-http) | Real HTTP | Not started |
| [M6](milestones.md#m6--quality-and-documentation) | Quality and documentation | Not started |
| [M7](milestones.md#m7--publishing) | Publishing | Not started |
| [M8](milestones.md#m8--optional) | Optional | Not started |
| [D1](open-decisions.md#d1--scala-3-target-version) | Scala 3 target version | Needs decision |
| [D2](open-decisions.md#d2--the-name-jev-in-the-library-name) | The name "Jev" in the library name | Needs decision |
| [D3](open-decisions.md#d3--when-mutation-testing-runs-in-ci) | When mutation testing runs in CI | Needs decision |

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
