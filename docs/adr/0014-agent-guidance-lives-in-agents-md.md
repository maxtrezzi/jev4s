# ADR-0014: Agent guidance lives in `AGENTS.md`

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

Coding agents work in this repository, and they need the rules and the load-bearing
constraints before they touch anything.

## Forces

- Guidance that is local and untracked drifts without anyone seeing it; tracked guidance
  changes through review.
- Two copies of the same rule drift apart.
- Claude Code loads a file named `CLAUDE.md`; the name `AGENTS.md` describes the job rather
  than one tool.

## Decision

- The guidance is **[`AGENTS.md`](../../AGENTS.md)**, tracked.
- `CLAUDE.md` exists only as a pointer to it, and holds no rule of its own.
- `AGENTS.md` is kept current **in the same commit** as the work it describes. A stale
  instruction there is a defect: the next session will follow it.

## Consequences

- One source for the rules, readable by any tool and any person.
- The file is public when the repository is, so it holds nothing that must stay private.
