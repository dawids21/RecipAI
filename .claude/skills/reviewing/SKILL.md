---
name: reviewing
description: Review the uncommitted changes for a task from three angles — code quality and correctness, conformance to the upstream artifacts (`requirements.md`, `HLD.md`, `tasks.md`, `task-design.md`), and documentation drift — then write findings to a review file and ask whether to commit. Use whenever the user wants their working-tree changes checked before committing, especially for a `docs/tasks/<date>-<name>/` task. Triggers on phrasings like "review T2 in docs/tasks/2025-11-15-shopping-lists", "check my changes before I commit", "does this diff actually match the requirements", "review the uncommitted work for docs/tasks/2026-04-18-fix-auth-redirect", "is this ready to commit". Do NOT use this to fix the findings (that's `/implementing` against the review file, in a fresh session), to write code, or to update docs (that's `/docs-updating`). Handles single-task mode (no `tasks.md`; writes `<task-dir>/review.md`) and multi-task mode (`tasks.md` exists; writes `<task-dir>/plans/T<N>-review.md`).
disable-model-invocation: true
---

# Reviewing

Gate the uncommitted changes before they enter history. This is the last step on
both tracks of the workflow — whether the work came through the full chain or the
mid-size track's shorter one, it ends here. Everything upstream said what
*should* happen; this step checks what *did*.

You review from three angles, because each catches a different class of problem:
the code can be well-written and still build the wrong thing, and it can be both
correct and correctly-scoped while leaving the docs describing a system that no
longer exists.

## The rule: you review, you don't fix

**Never fix what you find.** By the time you've read the diff closely enough to
review it, you've absorbed its assumptions — and an agent that repairs its own
findings tends to patch symptoms it already rationalized. Findings go into a
review file written for a *fresh* agent with no history here, which the user
feeds to `/implementing` in a new session. That separation is the entire point of
this step; collapsing it wastes the review.

The one thing you do change is the commit, and only when the user says yes.

## Inputs

The user provides a **task directory path** and, in multi-task mode, a **task ID**:

- `review T2 in docs/tasks/2025-11-15-shopping-lists` — multi-task, T2
- `review the uncommitted work for docs/tasks/2026-04-18-fix-auth-redirect` — single-task

If the user omits the task directory, ask for it. If they say there isn't one —
the change was made outside the workflow — say that you'll do the code-review and
docs passes only, and skip the conformance pass rather than inventing criteria to
check against.

Upstream artifacts (`requirements.md`, `HLD.md`, `tasks.md`, `task-design.md`)
are each optional. Steps get skipped legitimately; a missing artifact is a gap in
what you can check, not a finding in itself.

## Mode detection

- **Multi-task mode** — `<task-dir>/tasks.md` exists. The user must have named a
  task (T1, T2, …). Findings go to `<task-dir>/plans/T<N>-review.md`.
- **Single-task mode** — no `tasks.md`. Findings go to `<task-dir>/review.md`
  (task directory root, NOT in `plans/`).

If the user named a task ID but no `tasks.md` exists, stop and flag it. If
`tasks.md` exists but no task ID was given, stop and list the task IDs.

## Workflow

### 1. Collect the diff

Get the complete picture of what's uncommitted:

- `git status --porcelain` — the full inventory, including untracked files.
- `git diff` and `git diff --staged` — modified content, staged and unstaged.
- Read each **untracked** file directly. `git diff` doesn't show them, and new
  files are usually where the substance of a task lives — missing them is the
  most common way a review comes back falsely clean.

If the working tree is clean, stop and say so. There's nothing to gate.

For anything you'll review closely, open the whole file rather than judging from
diff hunks. A hunk can look fine and still be wrong in the context of the
function that surrounds it.

### 2. Read the upstream artifacts

Read the ones that exist: `requirements.md` (acceptance criteria, anti-requirements,
edge cases), `HLD.md` (chosen approach, out-of-scope), the specific task entry in
`tasks.md` (scope, out of scope, how to verify), and `task-design.md` /
`plans/T<N>-task-design.md` (components, interfaces, decisions made).

These are what the diff is answerable to. Note which are absent — the report
should say what you couldn't check.

### 3. Code review pass

Review the changes as an engineer who knows this codebase would, looking for:

- **Correctness** — does it do what it claims? Off-by-ones, inverted conditions,
  wrong defaults, race conditions, resource leaks.
- **Edge cases and error handling** — empty, null, huge, concurrent, failing
  dependency. Are errors swallowed, or surfaced with enough context to act on?
- **Codebase consistency** — naming, structure, layering, and idiom against the
  sibling code it sits next to. Read those siblings; "consistent" is a claim about
  this repo, not about general good practice.
- **Standards conformance** — open the standards that cover what this change
  touches (the project's `CLAUDE.md` says where they live) and check the change
  against them rule by rule. Sibling code can be older than the standard, so a
  pattern copied from a neighbour is not evidence it conforms.
- **Duplication** — logic reimplemented that already exists somewhere here.
- **Tests** — do they exist, do they test behavior rather than implementation, do
  they cover the edge cases the requirements named?
- **Security and data handling** — where input crosses a trust boundary, where
  secrets or user data flow.

### 4. Conformance pass

Now stop reading the diff as code and read it against the artifacts. Three
questions:

- **Satisfied?** Walk the acceptance criteria and the task's "how to verify" one
  by one and point at the code that fulfills each. A criterion you can't point at
  is a finding.
- **Out of scope?** Anything in the diff the artifacts didn't ask for. Extra
  refactors, speculative abstractions, features nobody requested — including
  anything the artifacts explicitly listed as out of scope or as an
  anti-requirement.
- **Diverged?** Where the implementation took a different shape from
  `task-design.md` — different components, different interfaces, a decision
  re-decided. Divergence isn't automatically wrong; the design may have been
  wrong. But it must be deliberate and visible, not silent.

### 5. Docs pass

Check whether documentation kept up, scoped exactly as `/docs-updating` scopes it:
read the project's `CLAUDE.md` to find what *this* project counts as
documentation, and check only those files. If `CLAUDE.md` names none, note that
you couldn't determine the scope rather than guessing from filenames.

You're looking for drift this change introduced: renamed commands, changed
defaults, new surface area that goes unmentioned, removed behavior still
documented as present.

Also check how the docs are *framed*, not just whether they're accurate: a doc
that narrates the change ("was X before", "no longer", "as of this task") is a
finding even when every fact in it is true, unless the project says otherwise.

### 6. Report to the user

Summarize in the conversation, grouped by severity, before writing anything:

- **Blocking** — ships a bug, breaks a requirement, or introduces a security or
  data-integrity problem. Must be fixed before commit.
- **Should-fix** — real problems that a reviewer would push back on: missing
  tests, inconsistent patterns, unhandled edge cases, undocumented drift.
- **Nit** — genuine but discretionary. Style, phrasing, minor simplification.

Be honest about severity. Inflating nits to blocking makes the whole review easy
to dismiss; burying a real bug among nits makes it easy to miss. If the review is
clean, say so plainly — a clean review is a real outcome, not a sign you didn't
look hard enough. Also state what you *couldn't* check and why (artifact absent,
tests not runnable, docs scope undefined).

### 7. Write the findings file

**Only if there are findings.** A clean review writes no file.

Fill [review-template.md](review-template.md), strip the `<angle-bracket
guidance>`, and write to the mode-appropriate path (create `plans/` if needed).

Write it for an agent that has never seen this task and won't have this
conversation. That agent needs the task context restated, each finding located at
`path:line`, an explanation of *why* it's a problem, and what a fix should
achieve — but not the code to write. Prescribing the patch turns a review into a
dictation and robs the fixing agent of the chance to find a better answer.

If the file already exists, don't silently overwrite — ask whether to overwrite,
write a `.v2` variant, or abort.

### 8. Ask about committing

Ask **every time**, clean or not. On a clean review it's the natural next step; on
a review with findings the user may still choose to commit — deferring known
findings is a legitimate call, and it's theirs to make, not yours to block.

- **Yes** — stage the changes and commit with a message derived from the task
  (its ID and user-visible outcome), following the repo's existing commit
  conventions. Check recent `git log` rather than assuming a format.
- **No** — stop. Leave the working tree exactly as it is.

Never commit without an explicit yes, and never commit as a side effect of
anything above.

## Files

- [review-template.md](review-template.md) — the findings-file skeleton. Copy its
  structure and fill it in.
