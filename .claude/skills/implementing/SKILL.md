---
name: implementing
description: Execute a finished implementation plan and write the actual code — read the plan top-to-bottom, do the required reading, work the steps in order, and verify each one. Use whenever the user wants to build, implement, or "do" a task that already has an `implementation-plan.md` in a `docs/tasks/<date>-<name>/` directory. Triggers on phrasings like "implement task T2 in docs/tasks/2025-11-15-shopping-lists", "execute the implementation plan for docs/tasks/2026-04-18-fix-auth-redirect", "build out T3 from the plan", "carry out the impl plan". Do NOT use this to create or revise a plan (that's `/implementation-planning`) or to design the technical shape (that's `/task-designing`). Handles single-task mode (no `tasks.md`; reads `<task-dir>/implementation-plan.md`) and multi-task mode (`tasks.md` exists; reads `<task-dir>/plans/T<N>-implementation-plan.md`).
disable-model-invocation: true
---

# Implementing

Execute a completed implementation plan: read it end-to-end, then write the real
code, step by step. This is the final build step in the
requirements → designing → task-planning → task-designing →
implementation-planning → **implementing** workflow. Every decision has already
been made upstream — your job is to carry out the plan faithfully, not to
re-plan or re-design it on the fly. The plan is a contract: follow it, and when
reality contradicts it, stop and say so rather than quietly improvising.

## Inputs

The user provides a **task directory path** and, in multi-task mode, a **task ID**.

- `implement task T2 in docs/tasks/2025-11-15-shopping-lists` — multi-task, T2
- `execute the implementation plan for docs/tasks/2026-04-18-fix-auth-redirect` — single-task

If the user omits the task directory path, stop and ask for it before anything else.

## Mode detection

Inspect the task directory:

- **Multi-task mode** — `<task-dir>/tasks.md` exists. The user must have named a
  task (T1, T2, …). The plan lives at `<task-dir>/plans/T<N>-implementation-plan.md`.
- **Single-task mode** — no `tasks.md`. The plan lives at
  `<task-dir>/implementation-plan.md` (task directory root, NOT in `plans/`).

If the user named a task ID but no `tasks.md` exists, stop and flag the
inconsistency. If `tasks.md` exists but no task ID was given, stop and list the
task IDs to choose from.

If the plan file is missing at the mode-appropriate path, stop — there's nothing
to execute, and this skill refuses to implement from a design or a guess.

## Workflow

### 1. Confirm inputs and mode

Detect the mode and locate the plan file (see above). In multi-task mode, confirm
the named task exists in `tasks.md`; if not, stop and list the ones that do.

### 2. Read the whole plan first

Read the entire `implementation-plan.md` before touching any code. The steps are
ordered so that each builds on the last and something goes green at every stage;
you can't execute step 3 sensibly without knowing where steps 4–6 are headed.
Skim-then-code leads to rework when a later step revisits a file you already
touched.

### 3. Do the required reading

Open every file listed under **Required reading** — the docs and standards, the
design sections and ADRs, and the existing source files marked as "code to
mirror". This is what keeps the new code consistent with the codebase instead of
generically correct. The plan points at these for a reason; don't skip them to
save time.

### 4. Work the steps in order

Execute the **Step-by-step plan** top to bottom. For each step:

- Make the changes it describes, touching the files it names.
- Run its **Verify** check exactly as written — the specific test invocation,
  `curl`, compile, or query the step spells out. Confirm it actually passes.
- Only then move to the next step. A red verify is a stop signal, not something
  to push past and "come back to" — the ordering exists precisely so problems
  surface at the step that caused them.

Keep a running note of which steps passed, so the final report is accurate.

### 5. Run the verification checklist

After the last step, work through the plan's **Verification checklist** in full —
lint/formatter, the whole test suite, the task's "How to verify", and any
task-specific gates. These catch cross-cutting breakage that per-step checks
miss (a green unit test says nothing about whether the formatter is happy or an
unrelated suite still passes).

### 6. Report

Give the user an honest completion summary:

- **Steps completed** — which steps ran and verified green.
- **Verification checklist** — each item's result; call out anything that
  failed or you couldn't run.
- **Deviations** — anywhere you departed from the plan, and why.
- **Manual follow-up** — anything the plan flagged for a human, or that you hit
  and couldn't resolve.

Don't paper over failures. If three steps passed and the fourth's tests are red,
say that plainly with the output — a faithful "here's where it broke" is far
more useful than a falsely green "done".

## When the plan doesn't match reality

The plan was written against the codebase as it was; the codebase may have moved.
If a step can't be executed as written — a file has been renamed or deleted, the
pattern it says to mirror has been refactored away, an integration point now
conflicts, a dependency version is gone — **stop and surface it to the user.**
Show what the plan expected, what you actually found, and the options. Don't
silently adapt: the plan may still be right and the code may be what needs
fixing, or the divergence may mean the task needs re-planning. That's the user's
call, not a guess to bury inside the diff.

Small, obvious mismatches (an import path that's off by one, a method that
gained a parameter you can clearly supply) you can adjust in stride — just record
them under **Deviations** in the report. The line is whether a reviewer would be
surprised: if the change is mechanical and unambiguous, proceed and note it; if
it changes behavior, structure, or intent, stop and ask.
