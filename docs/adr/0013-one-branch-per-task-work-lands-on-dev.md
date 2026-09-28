# ADR-0013: One branch per task; work lands on `dev`, `main` carries releases

- **Status:** Accepted — merging `main` into `dev` amended by ADR-0054
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

Work needs a branching model before the first milestone starts. The repository is private for
now, and it will publish artifacts to Maven Central, where a published version can never be
changed or deleted.

## Forces

- Committing straight to the default branch mixes unfinished work with finished work, and
  leaves no pull request to run the checks against.
- If `main` is where work lands, nothing shows which commits a release contains.
- On a private repository under GitHub's free plan, **branch protection is not available**:
  the API answers "Upgrade to GitHub Pro or make this repository public to enable this
  feature" (checked on 2026-09-21). Nothing enforces a rule until the repository is public.
- Even with protection, an administrator can bypass it unless `enforce_admins` is on, and
  turning that on also imposes a review that a single maintainer cannot give.

## Decision

- **`dev` is the default branch. `main` carries releases only**: one commit per released
  version, each with its tag, so its tree is always what Maven Central has, and
  `git log main..dev` lists what the next release would contain.
- **Every task gets its own branch, cut from `dev`**, named after the work item:
  `milestone/m1-base`, `task/<slug>`, `decision/d1-<slug>`, or `docs/<slug>` for work with no
  identifier. The branch carries the work, its status in `docs/tasks/`, and any ADR it
  produces.
- **Every pull request targets `dev`.** Nothing is committed directly to `dev` or `main`.
- A release is a pull request from `dev` to `main`, squashed, whose subject is the version.
  Never merge `main` into `dev`, never branch from `main`, never rebase `dev` onto it.
- The bootstrap commits that created this structure are the one exception: they went to
  `main` before `dev` existed.
- When the repository becomes public, `dev` and `main` get protection: a pull request is
  required, force-pushing and deletion are blocked, and the CI checks must pass and be current
  with the branch. No approving review is required.

## Consequences

- `main` is always the released state; `dev` is always the integrated state.
- Until the repository is public, the rule holds by discipline alone.
- After a squashed release, `dev` and `main` hold the same tree but unrelated histories.
  That is expected, and is why `main` is never merged back.
