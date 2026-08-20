# <Task ID / Feature name> — Review Findings

**Date:** <YYYY-MM-DD>

## Context for the fixing agent

<You are writing to an agent that has never seen this task and won't have the
review conversation. Give it, in a short paragraph: what the task was meant to
deliver, which artifacts define it (paths to `requirements.md`, `HLD.md`,
`tasks.md`, `task-design.md` — and which of them don't exist), and what state the
working tree is in. It should be able to start from this file alone.>

## What was reviewed

<The scope of the diff: the files touched, roughly what changed in each. A short
list, not a re-listing of the diff.

- `path/to/file.ext` (NEW) — <what it adds>
- `path/to/other.ext` (MODIFIED) — <what changed>>

## Findings

<Group by severity. Drop any severity heading that has no findings — don't leave
empty sections. Number findings within each group so they're easy to refer to.

Each finding needs four things: where it is, what's wrong, why that matters, and
what a fix should achieve. Describe the problem, not the patch — the fixing agent
should be free to find a better answer than the one you'd have written.>

### Blocking

<Ships a bug, breaks a stated requirement, or introduces a security or
data-integrity problem. Must be fixed before this is committed.

**B1. <Short title>**
- **Where:** `path/to/file.ext:120-134`
- **What:** <the defect, concretely — what the code does>
- **Why it matters:** <the consequence: the input that breaks it, the requirement
  it violates, the data it corrupts>
- **Fix should achieve:** <the property that must hold afterwards>>

### Should-fix

<Real problems a reviewer would push back on: missing tests, inconsistent
patterns, unhandled edge cases, undocumented drift. Same four fields.

**S1. <Short title>**
- **Where:** `path/to/file.ext:42`
- **What:** <...>
- **Why it matters:** <...>
- **Fix should achieve:** <...>>

### Nits

<Genuine but discretionary — style, phrasing, minor simplification. One line each
is fine; the fixing agent decides whether they're worth the churn.

**N1.** `path/to/file.ext:88` — <the nit, and the suggestion>>

## Conformance check

<How the diff measured against the upstream artifacts. Be specific about what
you could and couldn't verify.

- **Acceptance criteria** — which are met (point at the code), which aren't.
- **Out of scope** — anything in the diff the artifacts didn't ask for.
- **Design divergence** — where the implementation differs from
  `task-design.md`, and whether that looks deliberate.
- **Not checkable** — criteria you couldn't verify, and why.

If an artifact is absent, say so here rather than silently skipping it.>

## Documentation check

<Docs drift this change introduced, scoped to what the project's `CLAUDE.md`
defines as documentation. For each in-scope doc: in sync, or specifically what is
stale/missing/incorrect.

If `CLAUDE.md` defines no documentation scope, say that — it's a gap in the
review, not a clean result.>

## Not reviewed

<Anything you couldn't cover, so nobody mistakes silence for approval: tests you
couldn't run, generated files skipped, areas needing domain knowledge you don't
have. If nothing: "_Full diff reviewed._">
