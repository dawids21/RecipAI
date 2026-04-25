---
name: documentation-syncing
description: Update the project's living documentation (project docs, module/feature docs, standards, INDEX.md) to reflect what was just implemented on the current branch. Use whenever the user asks to update, sync, or refresh documentation after finishing implementation work — phrasings like "update docs after implementation", "sync documentation", "update project docs from branch changes", "document what I just implemented", "refresh the docs for this feature", "the implementation is done, update the docs". Always triggered by an explicit task directory under `docs/tasks/<date>-<name>/`. Produces a doc-update plan first, waits for the user to approve, then edits. Does NOT modify the task's design.md / tasks.md / implementation-plan.md (those are historical records of the work, not living docs).
disable-model-invocation: true
---

# Documentation Sync

Update the project's living documentation after a task from the
design → tasks → plan → implementation workflow has been implemented on the
current branch. The task plan files are historical and stay untouched; this
skill updates the docs that describe how the system *is now*.

The skill runs in two phases:

1. **Plan** — analyse the branch, read `docs/INDEX.md`, and produce a short
   doc-update plan listing every doc file that needs to change and why. Stop
   and wait for the user to approve.
2. **Apply** — once approved, edit the listed doc files and surface
   suggestions for any new standards that emerged from recurring patterns.

Two phases exist because docs are read by humans and written carefully —
silently rewriting wording is worse than proposing changes the user can
redirect.

## Inputs

The user provides a **task directory path**, e.g.
`docs/tasks/2026-04-18-shopping-list-sharing`.

If the user omits the task directory, stop and ask for it. Don't guess from
recently-modified directories — the skill needs an explicit anchor so the
"why these changes" story is clear.

## Workflow

### 1. Confirm inputs and gather branch context

- Verify `<task-dir>` exists and contains at least `design.md`. If it
  doesn't, stop and ask the user to confirm the path.
- Determine the diff range. Use the **three-dot** form so commits that
  landed on `main` *after* this branch diverged are excluded:

  ```bash
  git diff main...HEAD          # changes on this branch only
  git log main..HEAD --oneline  # commits unique to this branch
  ```

  Three-dot diff (`main...HEAD`) is shorthand for
  `git diff $(git merge-base main HEAD) HEAD` — it compares against the
  branch point, not the current tip of `main`. This is important in this
  repo because the user sometimes commits directly to `main`; those commits
  must NOT pollute the analysis.

  If `HEAD` *is* `main` (the user committed directly to main), fall back to
  diffing the commits that touch the task directory or were authored since
  the task directory's creation date — and surface this in the reply so
  the user knows the inference happened.

- List changed files with `git diff main...HEAD --name-status` and read
  enough of each diff to understand the user-visible / structural impact.
  You don't need to read every line — focus on:
  - **New files** (likely new components, endpoints, screens, tables).
  - **Renamed / moved files** (likely affect codebase_structure.md).
  - **Modified files** that change public API: controller signatures,
    route definitions, JPA entities, migration files, exported widgets,
    DI registrations, theme constants.
  - Anything under `mobile/` or `backend/` that doesn't look like a pure
    refactor.

### 2. Read the documentation index

Read `docs/INDEX.md` in full. It is the authoritative map of what
documentation exists. Build a mental model of:

- Which **project-level** docs (`docs/project/*.md`) exist and what each
  describes.
- Which **module docs** exist for backend (`docs/backend/modules/<m>/`) and
  mobile (`docs/mobile/modules/<m>/`), and the file split within each
  (`codebase_structure.md`, `api.md`, `db.md`, `ui.md`).
- Which **standards** exist (`docs/backend/standards/*.md`,
  `docs/mobile/standards/*.md`).
- Whether `docs/ADRs/` is relevant — open `docs/ADRs/INDEX.md` if any
  branch change looks like an architecturally significant decision.

### 3. Map branch changes onto doc files

For each meaningful change from step 1, ask: *which doc(s) does this make
stale?* Use these heuristics — but they are heuristics, not exhaustive:

| Branch change | Likely doc impact |
|---|---|
| New / removed / renamed source files | `codebase_structure.md` of the affected backend or mobile module |
| New / changed REST endpoint, request/response shape | backend module `api.md` |
| New table, column, index, migration | backend module `db.md` |
| New screen, widget, navigation route, user flow | mobile module `ui.md` |
| New module entirely | New module dir under `docs/backend/modules/` or `docs/mobile/modules/`, plus an entry in `docs/INDEX.md`, plus a module description in `docs/project/architecture.md` |
| New external integration, hosting / infra change, dependency added | `docs/project/tech-stack.md` and possibly `docs/project/architecture.md` |
| Change to MVP scope or product surface | `docs/project/prd.md` |
| Significant architectural decision (new pattern, library choice, structural shift) | New ADR under `docs/ADRs/`, indexed in `docs/ADRs/INDEX.md` |
| Recurring new code pattern (e.g. same fix or convention applied across many files) | Suggest a new or updated standard — do NOT write it without user approval |

INDEX.md itself needs updating whenever a *new* module dir, standards
file, or ADR appears, or when a module's file split changes.

### 4. Produce the doc-update plan

Output the plan to the user as a single message — do not write any files
yet. Structure it like this:

```
# Documentation update plan

**Task:** <task-dir path>
**Branch:** <current branch>
**Diff range:** main...HEAD (<N> commits, <M> files changed)

## Files to update

### docs/<path>.md — <MODIFY | CREATE>
- What's stale: <one line>
- Proposed change: <2–4 lines, concrete>

### docs/<path>.md — ...
...

## INDEX.md updates
- <e.g. "Add entry for new mobile module `notifications`"> OR `_None._`

## Standards suggestions (for your approval, NOT auto-applied)
- <pattern observed, where, why it might warrant a standard> OR `_None._`

## Out of scope (intentionally not touching)
- The task's own design.md / tasks.md / implementation-plan.md (historical)
- <anything else you considered and rejected, with one-line reason>
```

Then stop and ask: *"Apply this plan as-is, or want to adjust before I
edit?"*

### 5. Apply (only after user approval)

When the user approves (with or without adjustments), edit each listed
file. Guidelines:

- **Match the existing voice.** Read the surrounding section before
  editing — these docs are terse, declarative, and avoid filler. If
  `api.md` describes endpoints as one-line entries with a request/response
  example, match that. Don't introduce a new format mid-document.
- **Edit, don't rewrite.** Use targeted Edit calls. Don't regenerate a
  whole file just to add one endpoint — you'll churn unrelated wording.
- **Update INDEX.md last.** That way you've seen the final shape of every
  module dir before describing it.
- **Don't touch the task plan files** under `<task-dir>/`. They are the
  historical record of how the work was planned; updating them after the
  fact destroys that record.
- **Standards suggestions stay as suggestions.** If the user approved
  "suggest a standard about X", surface it again in the final reply with
  proposed wording — but do not create the standards file unless the user
  explicitly says to.

### 6. Final reply

Summarise:
- Files edited (one line each).
- Standards suggestions (repeated, with proposed wording if asked).
- Anything you noticed but didn't update (e.g. a pre-existing doc gap
  that's outside the scope of this branch's changes).

## Edge cases

- **Task directory missing or has no design.md** — stop and ask. The
  skill needs to anchor "what was implemented" against an intent.
- **Branch has no diff vs `main`** — stop and report. Either the user
  forgot to checkout the right branch, or the work is already merged
  and the docs should already reflect it.
- **HEAD is on `main`** — see step 1 fallback. Surface the inference
  explicitly so the user can correct course.
- **Diff is enormous** (hundreds of files, multiple unrelated features)
  — stop and ask the user to confirm scope before producing a sprawling
  plan. They may have meant to point at a narrower range.
- **A doc file the heuristic suggests doesn't exist yet** — propose
  creating it in the plan, and call out that it's a new file (not a
  modification) so the user can decide whether the structure is right.
- **Branch changes contradict an existing standard** — flag it in the
  plan under a separate "Standards conflicts" heading. Don't silently
  update the standard; the user needs to decide whether the code or the
  standard is wrong.

## What this skill does NOT do

- It does not update `<task-dir>/design.md`, `tasks.md`, or any
  implementation plan. Those are historical.
- It does not auto-create new standards files. It only suggests them.
- It does not run tests, lint, or verify the implementation actually
  works — that's the implementation step's job, already complete by the
  time this skill runs.
- It does not write release notes, changelogs, or PR descriptions.
