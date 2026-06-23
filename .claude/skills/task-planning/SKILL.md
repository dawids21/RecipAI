---
name: task-planning
description: Break a completed software design document into an ordered list of vertical-slice implementation tasks, producing a tasks.md that pairs with the design to feed a downstream implementation-planning step. Use this skill whenever the user has a design document and wants to break it down into implementable chunks, work items, vertical slices, or a task list. Trigger for phrases like "break this design into tasks", "what tasks does this design need", "task breakdown for the design", "plan the implementation tasks", "turn this design into work items", or when a design.md is being handed off for execution. Do NOT use for ad-hoc TODO lists, sprint planning across many features, or project-level roadmaps — this skill specifically translates one design into its implementation tasks.
disable-model-invocation: true
---

# Task Planning

Translates a completed `design.md` into a `tasks.md` — an ordered list of vertical-slice implementation tasks, each delivering a user-visible outcome. The output pairs with the design to feed the downstream implementation-planning step.

## Pipeline position

This skill is step 4 of a 5-step workflow:

1. Requirements gathering (Socratic) → `requirements.md`
2. Brainstorming (solution alternatives) → `brainstorming.md`
3. Design → `design.md`
4. **Task planning (this skill)** → `tasks.md`
5. Implementation planning → per-task implementation plan

## Inputs

- `design.md` — **required.** Must follow the structure of the design template.

## Pre-flight check

Before generating tasks, verify the design document is present and populated. Flag and ask whether to proceed if any of these sections are missing, empty, or contain unresolved placeholder text:

- **Module & component boundaries**
- **Interface contracts**
- **Flows & state**
- **Assumptions to verify** (outstanding assumptions are fine; the section being entirely absent is not)

Gaps in these areas produce unreliable task breakdowns. Surface the gap and let the user decide whether to proceed anyway, patch the design first, or abort.

## Core principle: vertical slices with user-visible outcomes

Every task must deliver a testable piece of software that **someone** can interact with and form an opinion about. Name that "someone" explicitly — it varies by project:

- **UI-inclusive feature** → the end user of the app
- **Backend-only API** → a frontend developer or API consumer (curl / Postman)
- **Library / SDK** → the developer integrating it

The common thread: after the task ships, someone can exercise the new behaviour and give feedback on it.

If a proposed task has no clear user-visible outcome, either fold it into an adjacent task or surface the issue to the user. **Do not invent an outcome to make a task look legitimate.**

## Infrastructure is never a standalone task

Pure infrastructure work — new buckets, migrations, dependency additions, config changes — must always be folded into a user-facing task. The first task in a sequence is often chunkier because it carries the infra; that's expected and correct.

If a proposed task reads "Set up X" with no observable outcome, it's wrong. Merge it into the first task that actually uses X.

## Granularity

Target: one task ≈ one implementation session an agent can complete and a human can review in one sitting, delivering something demonstrable.

Heuristics:

- ~4+ hours of agent work or a ~500+ line PR → split it
- Two consecutive tasks could be done together with no meaningful feedback moment between them → merge them

**Err toward fewer, more meaningful tasks over many granular ones.**

## Dependencies

Don't impose rigid backend-then-frontend splits or other artificial sequencing. Capture *real* dependencies only — task B genuinely cannot start until task A is merged, a shared migration must land first, etc. Record these in each task's `Depends on` field and surface sequencing constraints in the file-level cross-task notes.

## The single-task case

Trivial work may yield exactly one task. Tolerate this gracefully. **Do not pad, do not invent splits, do not manufacture dependencies.** A single-task `tasks.md` is a legitimate output.

## Output structure

Start from [tasks-template.md](tasks-template.md) and fill it in. Place the completed file at:

```
docs/tasks/YYYY-MM-DD-<task-name>/tasks.md
```

Keep tone and formatting aligned with `design.md` — this is a sibling artifact in the same pipeline, not a separate document family.

### File-level content

- **Metadata:** date, status (draft / final)
- **Summary:** ordered list of all tasks (ID + one-line name) so the reader sees the full shape before diving in. Order by dependency and value delivery, not alphabetically or by size.
- **Cross-task notes** (optional): parallelism opportunities, shared prerequisites, sequencing constraints, feature-flag coordination

### Per-task content

Each task entry includes:

- **ID and name** (e.g., `T1: S3 image upload`) — stable identifier used for cross-references
- **User-visible outcome** — one sentence naming what someone can do after this task ships that they couldn't before. This is the anchor for the whole task.
- **Scope** — terse bullets of what's included. Reference design sections by name; do not restate design content.
- **Out of scope** — what is explicitly *not* in this task and where it lives instead ("covered in T3", "deferred"). Primary guardrail against scope creep during implementation planning.
- **Depends on** — task IDs of prerequisites, or "none"
- **Design references** — pointers into `design.md` sections (and listed ADRs) the implementation planner should read most carefully
- **How to verify** — concrete, observable verification: a curl command, a UI interaction, a manual test flow. Must confirm the user-visible outcome *at the slice boundary*, not internal implementation details.
- **Risks / unknowns** (optional) — task-specific gotchas worth flagging to the implementation planner

### What tasks MUST NOT contain

- Step-by-step implementation instructions or file-by-file change lists — that's the implementation-planning step's job
- Restated design decisions or their rationale — those live in `design.md` and ADRs
- Acceptance criteria written as unit-test assertions or method signatures — keep acceptance observable at the slice boundary

## Files

- [tasks-template.md](tasks-template.md) — starting template for the output. Copy its structure and fill in.
