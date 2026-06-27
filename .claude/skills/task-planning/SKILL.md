---
name: task-planning
description: Break a completed high-level design (HLD.md) into an ordered list of vertical-slice implementation tasks, producing a tasks.md that feeds the downstream task-designing and implementation-planning steps. Use this skill whenever the user has an HLD and wants to break it down into implementable chunks, work items, vertical slices, or a task list. Trigger for phrases like "break this HLD into tasks", "what tasks does this design need", "task breakdown for the design", "plan the implementation tasks", "split this into work items", or when an HLD.md is being handed off for execution. Do NOT use for ad-hoc TODO lists, sprint planning across many features, or project-level roadmaps — this skill specifically translates one HLD into its implementation tasks.
disable-model-invocation: true
---

# Task Planning

Translates a completed `HLD.md` (high-level design) into a `tasks.md` — an ordered list of vertical-slice implementation tasks, each delivering a user-visible outcome. The output feeds the downstream task-designing and implementation-planning steps.

## Pipeline position

This skill is the optional task-splitting step that follows high-level design:

1. Requirements gathering → `requirements.md`
2. High-level design (`/designing`) → `HLD.md`
3. **Task planning (this skill, optional)** → `tasks.md`
4. Task designing (per-task technical design) → `task-design.md`
5. Implementation planning → per-task implementation plan
6. Implementation → code

This step is optional: run it when the HLD covers more than one cohesive unit of work. When the HLD is a single slice, skip straight to task-designing.

## Inputs

- `HLD.md` — **required.** Must follow the structure of the HLD template.

## Pre-flight check

Before generating tasks, verify the HLD is present and populated. Flag and ask whether to proceed if any of these are a problem:

- **Approach > Chosen** — the approach must be settled. If the HLD instead carries a **Deferred** note (the decision is still open, awaiting a spike/benchmark/conversation), you cannot reliably break it into tasks. Surface this and stop.
- **Feature areas** — present, with **key behaviors** listed per area. This is the backbone of the task breakdown; if it's missing, empty, or still placeholder text, the breakdown will be unreliable.
- **Out of scope** and **Open questions** — read these for context if present. They are legitimately absent when there's nothing to record (the HLD template deletes them when empty), so don't treat their absence as a gap — but unresolved open questions that block a task are worth flagging.

Surface any gap and let the user decide whether to proceed anyway, patch the HLD first, or abort.

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

Keep tone and formatting aligned with `HLD.md` — this is a sibling artifact in the same pipeline, not a separate document family.

### File-level content

- **Metadata:** date, status (draft / final)
- **Summary:** ordered list of all tasks (ID + one-line name) so the reader sees the full shape before diving in. Order by dependency and value delivery, not alphabetically or by size.
- **Cross-task notes** (optional): parallelism opportunities, shared prerequisites, sequencing constraints, feature-flag coordination

### Per-task content

Each task entry includes:

- **ID and name** (e.g., `T1: S3 image upload`) — stable identifier used for cross-references
- **User-visible outcome** — one sentence naming what someone can do after this task ships that they couldn't before. This is the anchor for the whole task.
- **Scope** — terse bullets of what's included. Reference HLD feature areas by name; do not restate HLD content.
- **Out of scope** — what is explicitly *not* in this task and where it lives instead ("covered in T3", "deferred"). Primary guardrail against scope creep during implementation planning.
- **Depends on** — task IDs of prerequisites, or "none"
- **HLD references** — pointers into `HLD.md` sections (feature areas, and listed ADRs) the downstream task-designing step should read most carefully
- **How to verify** — concrete, observable verification: a curl command, a UI interaction, a manual test flow. Must confirm the user-visible outcome *at the slice boundary*, not internal implementation details.
- **Risks / unknowns** (optional) — task-specific gotchas worth flagging to the implementation planner

### What tasks MUST NOT contain

- Step-by-step implementation instructions or file-by-file change lists — that's the implementation-planning step's job
- Restated design decisions or their rationale — those live in `HLD.md` and ADRs
- Acceptance criteria written as unit-test assertions or method signatures — keep acceptance observable at the slice boundary

## Files

- [tasks-template.md](tasks-template.md) — starting template for the output. Copy its structure and fill in.
