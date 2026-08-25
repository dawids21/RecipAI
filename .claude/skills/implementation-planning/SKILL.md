---
name: implementation-planning
description: Create a detailed, PR-ready implementation plan for a single task by investigating the codebase and filling a structured template. Use whenever the user asks to plan, write, draft, or generate an implementation plan (or 'impl plan', 'task plan') based on an existing `task-design.md` in a `docs/tasks/<date>-<name>/` directory. Triggers on phrasings like "create implementation plan for task T2 in docs/tasks/2025-11-15-shopping-lists", "plan the implementation for…", "write an impl plan for T3", "draft the implementation plan based on task-design.md". Handles both single-task mode (no `tasks.md`; plan saved at `<task-dir>/implementation-plan.md`) and multi-task mode (`tasks.md` exists; plan saved at `<task-dir>/plans/T<N>-implementation-plan.md`).
disable-model-invocation: true
---

# Implementation Planning

Produce a detailed, PR-ready implementation plan that an engineer (or another
agent) can execute top-to-bottom without further interpretation. This is the
terminal planning step on both tracks of the workflow — the full track reaches it
through `/designing` and `/task-planning`, the mid-size track through
`/task-designing` alone — and it is the last gate before code exists, where a
wrong assumption is still only a paragraph. The upstream thinking is already
done; this skill translates `task-design.md` (and, in multi-task mode, the
matching entry from `tasks.md`) into a concrete, file-level plan grounded in the
real codebase.

## Scope: code, not docs

The plan covers implementation — source, tests, config, migrations,
dependencies. Documentation is refreshed later by its own `docs-updating` step,
so read `docs/INDEX.md` and standards as *inputs* for conventions, but don't
plan updates to README, `docs/INDEX.md`, ADRs, or other prose files. The one
exception is a doc `task-design.md` explicitly names as a deliverable of this task.

## Inputs

The user provides a **task directory path** and, in multi-task mode, a **task ID**.

Examples of valid invocations:

- `create implementation plan for task T2 in docs/tasks/2025-11-15-shopping-lists` — multi-task mode, plan T2
- `plan the implementation for docs/tasks/2026-04-18-fix-auth-redirect` — single-task mode
- `write an impl plan for T1 in docs/tasks/2026-03-02-presigned-urls` — multi-task mode, plan T1

If the user omits the task directory path, stop and ask for it before doing
anything else.

## Mode detection

Detect the mode by inspecting the task directory:

- **Multi-task mode** — `<task-dir>/tasks.md` exists. The user must have named a
  specific task (T1, T2, …). The task's design lives at
  `<task-dir>/plans/T<N>-task-design.md`, and the plan is written to
  `<task-dir>/plans/T<N>-implementation-plan.md`.
- **Single-task mode** — no `tasks.md` in `<task-dir>`. The design lives at
  `<task-dir>/task-design.md` and is the basis for one plan, written to
  `<task-dir>/implementation-plan.md` (directly in the task directory, NOT in a
  `plans/` subfolder).

If the user named a task ID but no `tasks.md` exists, stop and flag the
inconsistency — either the `tasks.md` is missing, the wrong directory was
named, or the user meant single-task mode.

If `tasks.md` exists but the user didn't name a task ID, stop and list the
task IDs from `tasks.md` and ask which one to plan.

## Workflow

### 1. Confirm inputs and mode

- Determine mode (see above).
- In multi-task mode, find the named task inside `tasks.md`. If the task ID
  doesn't exist in `tasks.md`, stop and list the ones that do.
- Verify the task's `task-design.md` exists at the mode-appropriate path
  (`<task-dir>/task-design.md` in single-task mode,
  `<task-dir>/plans/T<N>-task-design.md` in multi-task mode). If it's missing,
  stop — there is nothing to plan from, and this skill refuses to guess a
  design.

### 2. Gather context

Read in roughly this order, taking notes as you go — the notes feed directly
into the template sections.

**Always**
- `docs/INDEX.md`, if it exists. Scan it for docs/standards relevant to what
  this task touches (persistence, API conventions, logging, testing,
  security, etc.).
- The task's `task-design.md` in full (single-task:
  `<task-dir>/task-design.md`; multi-task: `<task-dir>/plans/T<N>-task-design.md`).
  Pay special attention to:
  - Required reading for implementation planning
  - Components and responsibilities
  - Interfaces and method signatures
  - Data flow
  - Pseudo-code (for non-trivial logic)
  - Decisions made (so you don't re-decide them)
  - Assumptions to verify (which become risks if unresolved)
- Any ADRs linked from `task-design.md`.

**Multi-task mode only**
- The specific task entry in `tasks.md` — read **Scope**, **Out of scope**,
  **Depends on**, **HLD references**, **How to verify**, and **Risks /
  unknowns**. These constrain the plan's boundaries.
- The `Summary` and `Cross-task notes` sections of `tasks.md` — they surface
  shared prerequisites, sequencing constraints, and parallelism.
- If the task has `Depends on: T<N>`, check whether
  `<task-dir>/plans/T<N>-implementation-plan.md` already exists. If not,
  note it in the response after writing the plan; don't block.

**Project docs & standards**
- Any file referenced from `task-design.md` > "Required reading for implementation planning".
- Anything in `docs/INDEX.md` directly relevant to the task's scope.

### 3. Investigate the codebase

This is what makes the plan useful rather than a rephrased design. The
template demands concrete filepaths and patterns to mirror — those come
from reading the actual repo, not from the design document.

Investigate along these axes, to whatever depth the task requires:

- **Integration points** — for each file the task design says will change, open
  it and read enough surrounding context to understand the shape of the
  change.
- **New components** — for each new unit the task design introduces, locate 1–2
  existing siblings in the same package / folder / module whose patterns
  the new code should follow. Capture their naming, structure, and
  conventions so the plan can point to them as "code to mirror".
- **Data model changes** — read the existing migration / schema files to
  determine ordering constraints and the next identifier. Check whether
  existing models need modification and identify ripple effects.
- **Dependencies** — inspect the project's dependency manifest(s) to
  confirm what needs to be added and at which version, matching the
  project's existing version conventions.
- **Tests** — find the nearest existing test file to each new test.
  Mirror its setup, fixtures, and assertion style.
- **Configuration** — check how existing configuration is organised so
  new config keys or environment variables follow the project's naming
  and placement.

Keep a running list of filepaths as you go — these populate both
**Required reading > Code to mirror** and **File inventory**.

If the investigation surfaces something that contradicts `task-design.md` — a
pattern that's no longer current, a file that's moved, a convention that
has shifted — don't silently paper over it. Capture it under **Risks
surfaced during planning**.

### 4. Fill the template

Read [`implementation-plan-template.md`](implementation-plan-template.md) and fill every section. The
template is authoritative for structure — sections appear in the template's
order, with the template's exact heading levels.

Strip the angle-bracket guidance (`<like this>`) from the output — it's
instructional, not content.

See **Per-section guidance** below for the rules that matter most.

### 5. Write the file

- **Multi-task mode**: `<task-dir>/plans/T<N>-implementation-plan.md`. Create
  the `plans/` directory if it doesn't exist.
- **Single-task mode**: `<task-dir>/implementation-plan.md`.

If the target file already exists, do NOT silently overwrite. Stop, tell the
user, and ask whether to overwrite, create a `.v2` variant, or abort.

## Per-section guidance

### Title and metadata
- **Multi-task mode** title: `# T<N>: <Task name> — Implementation Plan`,
  where `<Task name>` matches the name used in `tasks.md`.
- **Single-task mode** title: `# <Feature name> — Implementation Plan`
  (omit the `T<N>:` prefix).
- **Date**: today's date in `YYYY-MM-DD` — not the date on `task-design.md`.

### Required reading
Three subsections: Docs & standards, Design & ADRs, Code to mirror. Pull
only entries relevant to *this task* — don't copy `task-design.md`'s full list
verbatim. Every entry needs a one-line "why" note.

If a subsection has no entries, write `_None._` under its heading — don't
delete the heading.

### File inventory
Complete list. This is the most common place for a plan to fail: people
list the obvious new files and forget the ripple effects. Think through:
- Compiler / type errors that cascade (add a required field to a shared
  type and every caller needs an update).
- Fixture / factory changes.
- Logging configuration, metric registration.
- Build and dependency manifest(s).
- Migration files, and any seed-data SQL.
- Existing test files that need to assert new behaviour.

Mark each as `**CREATE**`, `**MODIFY**`, or `**DELETE**` with a ~10-word
description.

### Step-by-step plan
Commit-sized steps. A good test: each step should leave the repo in a state
where a human would cleanly commit and push. Order steps so something
demonstrable goes green at each one:

1. Scaffolding / contracts → compiles
2. Unit logic → unit tests pass
3. Wiring / integration → integration tests pass
4. End-to-end → manual verify succeeds

Each step names:
- Files touched (filepaths, not vague descriptions).
- A concrete **Verify** command or check — a specific test invocation, a
  `curl` with the expected response, a query showing the new state.
  Spell out the command, not the intent.

Don't restate *why* — the task-design.md has that. Focus on *what* and *how*.

### Test plan
Case lists, not promises. `FooServiceTest — happy path, validation failure`
is not a case list — expand it:
- `creates entity when all required fields present`
- `returns 400 when email is malformed`
- `is idempotent when the same request is retried`

For levels that don't apply, write `_N/A — <one-line reason>_`. Don't
delete the heading.

### Verification checklist
Tailor per task. Drop items that don't apply (no migration? drop that
line). Always include at minimum:
- Lint / formatter passes.
- All tests pass.
- `tasks.md` > "How to verify" for this task succeeds (multi-task mode),
  or the task design's user-visible outcome is demonstrably achieved (single-task mode).
- Task design's Assumptions to verify are resolved or explicitly deferred.

### Risks surfaced during planning
Only things that came up **while writing this plan** — not a restatement
of `tasks.md` > "Risks / unknowns". Examples:
- The existing pattern the task design assumes has been refactored since the
  task design was written.
- Two integration points conflict and one will need to be rewritten.
- A migration can't be safely rolled back and needs a forward-only path.

If nothing new came up:
- Multi-task mode: `_No additional risks beyond those in tasks.md._`
- Single-task mode: `_No risks surfaced during planning._`

## Edge cases

- **`task-design.md` missing** — stop. The skill refuses to generate a plan
  without a design.
- **Task ID not in `tasks.md`** — stop and list the available IDs.
- **Target plan file already exists** — stop and ask before overwriting.
- **Unresolved `Assumptions to verify` in `task-design.md`** — proceed, but
  capture each unresolved assumption in Risks surfaced during planning
  and mention them in the reply.
- **Task has `Depends on: T<N>` but no `T<N>-implementation-plan.md`** —
  proceed, but mention in the reply so the user can plan the
  prerequisites or reorder.
- **Codebase investigation contradicts task-design.md** — capture in Risks
  surfaced during planning and flag in the reply; don't silently adjust
  the plan to match the codebase, since the task design may still be right and
  the code may be what needs to change.

## Example filepaths

For a request *"create implementation plan for task T2 in docs/tasks/2025-11-15-shopping-lists"*:

- Read: `docs/tasks/2025-11-15-shopping-lists/tasks.md` (find T2)
- Read: `docs/tasks/2025-11-15-shopping-lists/plans/T2-task-design.md`
- Write: `docs/tasks/2025-11-15-shopping-lists/plans/T2-implementation-plan.md`

For a request *"plan the implementation for docs/tasks/2026-04-18-fix-auth-redirect"*:

- Read: `docs/tasks/2026-04-18-fix-auth-redirect/tasks.md` → doesn't exist → single-task mode
- Read: `docs/tasks/2026-04-18-fix-auth-redirect/task-design.md`
- Write: `docs/tasks/2026-04-18-fix-auth-redirect/implementation-plan.md`
