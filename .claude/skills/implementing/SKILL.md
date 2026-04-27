---
name: implementing
description: Execute an implementation plan produced by the implementation-planning skill, top to bottom — read the plan, perform each step's edits, run that step's `Verify` command, then run the final Verification checklist. Use whenever the user asks to "implement", "execute", "carry out", "run", "do", or "follow" an implementation plan in `docs/tasks/<date>-<name>/implementation-plan.md` or `docs/tasks/<date>-<name>/plans/T<N>-implementation-plan.md`. Trigger on phrasings like "implement the plan in docs/tasks/...", "execute T2's implementation plan", "run the impl plan for the shopping-lists task", "do the implementation-plan.md for...", "carry out the plan in docs/tasks/2026-04-26-android-share-target". Do NOT use for ad-hoc coding, drafting plans (that's implementation-planning), or rewriting plans — this skill assumes the plan already exists and is the source of truth.
disable-model-invocation: true
---

# Implementation

Execute an existing implementation plan end-to-end. The upstream skills
(requirements → brainstorming → design → tasks → implementation-planning)
have already done the thinking. Your job is to faithfully carry the plan
out, verify each step before moving on, and clearly surface anything the
user must verify by hand.

The plan is the source of truth. If you find yourself wanting to deviate —
add a step, skip a verification, change a filepath — stop and surface the
discrepancy to the user instead of silently improvising. Plans get written
because someone wanted execution to be predictable; freelancing defeats
that.

## Inputs

The user provides a **task directory path** and, in multi-task mode, a
**task ID**.

Examples of valid invocations:

- `implement the plan in docs/tasks/2026-04-18-fix-auth-redirect` — single-task mode
- `execute T2 in docs/tasks/2025-11-15-shopping-lists` — multi-task mode
- `run the implementation plan for T1 in docs/tasks/2026-03-02-presigned-urls` — multi-task mode

If the user omits the task directory, stop and ask before doing anything
else. Do not guess from recent git history or open editor state.

## Mode detection

Inspect the task directory to find the plan file:

- **Single-task mode** — `<task-dir>/implementation-plan.md` exists.
- **Multi-task mode** — `<task-dir>/plans/T<N>-implementation-plan.md`
  exists for the named task ID.

If the user named a task ID but only a single-task plan exists (or vice
versa), stop and clarify — this usually means the user is referencing the
wrong task directory or the plan hasn't been generated yet.

If the matching plan file does not exist, stop. This skill refuses to
implement from `design.md` or `tasks.md` directly — those are upstream
artefacts and missing the file-level detail this skill depends on. Suggest
running implementation-planning first.

## Workflow

### 1. Confirm inputs and load the plan

- Resolve the plan path using mode detection above.
- Read the plan in full. Do not skim — every section matters for
  execution.
- Read `<task-dir>/design.md` for context the plan assumes (interface
  contracts, data model intent, resolved questions). The plan is
  file-level; design.md tells you why the shape is what it is.
- In multi-task mode, read the relevant task entry in
  `<task-dir>/tasks.md` for **Scope**, **Out of scope**, **Depends on**,
  and **How to verify**. If the task lists `Depends on: T<M>` and the
  prerequisite plan exists but its work isn't merged into the current
  branch, surface that to the user before starting.

### 2. Read the plan's Required reading

The plan's **Required reading** section names docs, ADRs, and existing
source files to study before changing code. Read each one. This is not
optional — the planner included those references because the patterns in
them constrain the implementation. Skipping them is the most common cause
of plans being implemented "almost right" but inconsistent with the
codebase.

Also read `docs/INDEX.md` and any standards files in `docs/backend/standards/`
or `docs/mobile/standards/` that the plan touches (per `CLAUDE.md`).

### 3. Internalise the file inventory

Skim every file in the plan's **File inventory** that is marked
**MODIFY** or **DELETE** so you know what you're walking into. Note any
file in the inventory that doesn't exist (for **MODIFY**/**DELETE**) or
already exists (for **CREATE**) — both are signals that the codebase has
moved since the plan was written. Surface the discrepancy before
starting.

### 4. Plan a tracked execution

Use the task-tracking tool (TaskCreate) to add one task per
**Step-by-step plan** entry, in the plan's order. Tracking each step
explicitly is what keeps the implementer from drifting — you mark a step
done only after its `Verify` passes, which forces the discipline.

### 5. Execute steps in order

For each step, in the order listed in the plan:

1. Make exactly the edits the step describes, touching only the files
   the step lists under **Files**. If a needed change isn't in the
   step's file list but is clearly required (e.g. a compile error in a
   caller you can't avoid), make the smallest necessary change and note
   it for the final summary — don't expand scope silently.
2. Run the step's **Verify** command exactly as written. If the plan
   says `./mvnw test -Dtest=FooTest`, run that, not a broader suite.
   Capture the output.
3. **If verification fails, stop.** Do not move on to the next step.
   Diagnose the failure:
   - If it's a mistake in your edits, fix it and re-run the verify.
   - If the plan's verify command is wrong (e.g. references a renamed
     test, an obsolete URL, a wrong port), surface that to the user
     before "fixing" it — the plan may need updating, not the code.
   - If the failure reveals the plan itself is wrong (a step's design
     conflicts with reality), stop and report. Don't silently improvise
     a different design.
4. Mark the step's task complete and move to the next.

If a step has no explicit `Verify` line, derive a reasonable one from
context (the nearest test, a `dart analyze` / `./mvnw compile`, etc.) and
note in the final summary that you did so. The planner aiming for
green-at-each-step is the spirit of the workflow even when a specific
line was omitted.

### 6. Run the Verification checklist

After all steps are done, walk the plan's **Verification checklist**
top-to-bottom. For each item:

- **Automatable items** (lint, formatter, full test suite, build,
  migrations) — run the command. If the checklist names the command
  (e.g. `./mvnw spotless:check`), use that exact command. Otherwise
  use the project's standard command for the language/framework as
  documented in `docs/INDEX.md` and the standards files. Capture
  pass/fail.
- **Items the plan can't fully automate** (e.g. "tasks.md > How to
  verify succeeds end-to-end", "design assumptions confirmed", "logs at
  INFO are clean on the happy path") — do as much as possible
  programmatically (check a log line via `grep`, hit an endpoint with
  `curl`), and clearly mark anything that genuinely needs the user.

If any automatable check fails, stop and report — do not pretend the
checklist passed. The checklist is the pre-merge gate; a failing item
means the work isn't done.

### 7. Surface manual verification to the user

At the very end, present a clear, dedicated section to the user covering
everything they need to do by hand. This is the most important part of
the report — the user is depending on you to call out what *you* could
not verify so they don't merge a half-tested change.

Pull manual verification from three places in the plan:

1. **Test plan > Manual verification** — copy each item verbatim.
2. **Verification checklist** items that genuinely need a human (visual
   UI checks, cross-device behaviour, real third-party services,
   subjective judgments like "logs are clean").
3. **`tasks.md` > How to verify** (multi-task mode) — anything that
   describes user-visible outcomes the agent can't observe.

Format that section as an actionable checklist the user can tick
through, not a wall of prose. See **Output format** below.

## Output format

After execution, reply with this structure (in this order):

```
## Implementation summary

<2–4 sentences: what was done, which plan was followed, what state the
branch is in.>

## Steps completed

- [x] Step 1: <name> — verified via `<command>` ✅
- [x] Step 2: <name> — verified via `<command>` ✅
...

## Verification checklist

- [x] Lint / formatter — `./mvnw spotless:check` passed
- [x] Tests — `./mvnw test` passed (47 passed, 0 failed)
- [ ] (manual) <item the user must do>
...

## Manual verification required

The following items from the plan need you to verify by hand — I could
not do them programmatically:

- [ ] <item> — <where it came from in the plan, e.g. "Test plan >
      Manual verification">
- [ ] <item> — <source>
...

## Deviations from the plan

<Anything you had to do differently from what the plan said, with the
reason. If nothing: "_None._">

## Risks / follow-ups

<Anything you noticed during implementation that the user should
know about — drift between plan and reality, scope creep you suppressed,
TODOs you left for them. If nothing: "_None._">
```

If you stopped partway through (a verify failed and you couldn't resolve
it without user input), still produce this report — just up to the point
you got to, with a clear "Stopped at step N because: <reason>" at the
top.

## Edge cases

- **Plan file missing** — stop. Suggest running implementation-planning
  first; do not work from `design.md` directly.
- **Plan references a file that no longer exists** — stop on first
  occurrence and report. The plan is stale; the user needs to decide
  whether to refresh it.
- **A `Verify` command references infrastructure that isn't running**
  (Postgres, Testcontainers, a dev server) — try to bring it up using
  the project's standard command (e.g. `docker compose up -d` if the
  repo uses one). If you can't, surface it as a manual verification
  item rather than skipping silently.
- **Plan step depends on credentials or external services you don't
  have** — implement the code, then list the verify step under
  **Manual verification required** with the exact command for the user
  to run.
- **Two consecutive steps that the plan ordered separately can be
  trivially combined** — still execute them as separate commits/edits.
  The plan's ordering is intentional; collapsing it loses the
  green-at-each-step property.
- **Plan's step is ambiguous** — stop and ask. Better one round-trip
  than guessing wrong and unwinding.

## Why each rule exists

- *Run the verify after every step* — catches regressions when they're
  one step's worth of context, not five steps of tangled changes.
- *Don't deviate silently* — the plan represents decisions made with
  more context than you have during execution; deviations need to be
  surfaced so the user can confirm or reject them.
- *Surface manual verification explicitly* — the most expensive failure
  mode is the user assuming you tested something you couldn't, then
  shipping it. A dedicated section makes that impossible.
