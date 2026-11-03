# ADR-0054: `dev` records each release commit of `main` with an ours-merge

- **Status:** Accepted
- **Date:** 2026-09-28
- **Supersedes:** —
- **Amends:** ADR-0013, ADR-0051

## Context

[ADR-0013](0013-one-branch-per-task-work-lands-on-dev.md) releases by squashing `dev` onto
`main`, and forbids merging `main` into `dev` or rebasing `dev` onto it. Its consequences expected
that `dev` and `main` would then hold the same tree with unrelated histories. It also asked that,
once the repository is public, a pull request into `dev` or `main` pass the CI checks and be
current with its base branch.

The first release, `0.1.0` on 2026-09-28, squashed `dev` onto `main` as `dd3dcec`. Measured on the
same day, after `dev` moved to `0.1.1-SNAPSHOT`:

- `dev` does not contain `dd3dcec`; the merge base of `dev` and `main` is still `0e94844`, the
  bootstrap commit of ADR-0013.
- `git merge-tree` of `main` and `dev` reports five conflicts (`AGENTS.md`, `CHANGELOG.md`,
  `build.sbt`, `docs/tasks/README.md`, `docs/tasks/milestones.md`): from the old base, both
  sides changed the same lines, `main` to the release and `dev` past it. Every later change to a
  file makes the list longer.
- The ruleset on `dev` and `main` requires a pull request to be current with its base
  (`strict_required_status_checks_policy`). `dev` is behind `main` by `dd3dcec` and, under
  ADR-0013, can never catch up.

So the next release pull request, from `dev` to `main`, would be both conflicting and blocked.

## Forces

- **`main` must stay one commit per release**, each with its tag, whose tree is what Maven Central
  has (ADR-0013). That is what makes `git log main` a list of releases.
- **`dev`'s tree must not change** because of a release: every line on `dev` came through a
  reviewed pull request.
- **Merging with a merge commit instead of squashing** gives `main` the whole history of `dev`,
  and still leaves on `main` a merge commit that `dev` lacks: the next pull request is behind
  again.
- **Dropping the "current with the base" rule** on `main` does not remove the conflicts.
- **An ours-merge** (`git merge -s ours origin/main`) makes `main`'s release commit an ancestor of
  `dev` and keeps `dev`'s tree exactly as it is. Measured on 2026-09-28 with a merge commit built by
  hand from `dev`'s tree and the parents `dev` and `main`: its tree is `dev`'s, `main` is its
  ancestor, and `git merge-tree` of `main` and it finishes with no conflict, with `dev`'s tree as
  the result.

## Decision

- **After each release, `dev` records the release commit of `main` with an ours-merge**: on a
  branch from `dev`, `git merge -s ours origin/main`, whose tree is the tree of `dev`. Its pull
  request into `dev` is merged with a **merge commit, never a squash**: a squash keeps the tree and
  drops the parent that records `main`.
- This is the only merge of `main` into `dev`. Any other merge of `main` into `dev`, and any
  rebase of `dev` onto `main`, stays forbidden (ADR-0013).
- A release stays four steps (ADR-0051), and the last one changes: a pull request on `dev` that
  sets the version and the CHANGELOG; a squashed pull request from `dev` to `main` whose subject
  is the version; the tag on that commit; and a pull request on `dev` that moves to the next
  `-SNAPSHOT` **and holds the ours-merge of the release commit**, merged with a merge commit.
- For `0.1.0`, whose move to `0.1.1-SNAPSHOT` was already squashed (#27), the ours-merge of
  `dd3dcec` has a pull request of its own, T31's.

## Consequences

- The release pull request from `dev` to `main` is current with `main` and has no conflicts; the
  squash puts `dev`'s tree on `main` unchanged.
- `main` stays one squashed commit per release. `dev`'s history gains one merge commit per
  release, whose second parent is the release on `main`.
- `git log main..dev` again lists what the next release would contain: `main`'s commits are all in
  `dev`.
- Do not squash the pull request that holds the ours-merge "to keep `dev` linear", and do not
  replace `-s ours` with a normal merge "to be safe": the first loses the parent, the second
  brings the conflicts into `dev`.
