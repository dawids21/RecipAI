---
name: task-designing
description: Produce the technical design (task-design.md) for a single task — components, interfaces and method signatures, data flows, pseudo-code, and the decisions that need making — working from whichever upstream artifact exists in a `docs/tasks/<date>-<name>/` folder: `HLD.md`, else `requirements.md`, else the user's prompt. Use this skill whenever the user wants to design the technical shape of a task, work out the components/classes/interfaces, or "figure out how to actually build" a slice before writing an implementation plan. Triggers on phrasings like "do the task design for T2 in docs/tasks/2025-11-15-shopping-lists", "design the technical approach for this task", "work out the components and interfaces for the HLD", "design this feature before I build it", "write the task-design.md". Do NOT use this for high-level approach exploration or weighing alternatives, for breaking an HLD into PR-sized tasks, or for producing the file-level, step-by-step execution plan. Handles single-task mode (no `tasks.md`; saved at `<task-dir>/task-design.md`) and multi-task mode (`tasks.md` exists; saved at `<task-dir>/plans/T<N>-task-design.md`).
disable-model-invocation: true
---

# Task Designing

Take a single task and produce its `task-design.md`: the technical shape of the
work — components, interfaces, data flows, pseudo-code, and the non-obvious
decisions. On the full track it sits between the HLD (*what approach and which
behaviors*) and the file-level implementation plan, settling *how it's
structured*; on the mid-size track it's the **only** design artifact, so it
settles the approach too. Either way it stops short of writing the
implementation.

## Inputs

Work from the highest rung of this ladder that exists. **A missing artifact is a
legitimate track choice, not an error** — it means an upstream step was skipped
deliberately, so walk up the chain instead of refusing to run:

1. `HLD.md` — the chosen approach and feature-area context (full track).
2. `requirements.md` — the *what* without the *how*. Nobody has picked an
   approach yet, so this design picks it.
3. **The user's prompt** — nothing written down at all. Capture a one-paragraph
   problem statement inline and get a yes on it before designing against it.

Stop only at the bottom: if *nothing* describes the intent — no artifact, no
prompt, no task entry — say so and ask.

Each rung you skip changes how the rest of the run goes, because the artifact
you don't have was carrying weight someone still has to carry: you read more of
the codebase (step 3), you confirm rather than invent (step 4), and you record
the gap under **Assumptions to verify** (step 6).

- `tasks.md` — **optional.** When it exists, the user must name a specific task ID
  to design.
- `research/*.md` — optional, available to link from the design.

The user provides a **task directory path** and, in multi-task mode, a **task ID**:

- `do the task design for T2 in docs/tasks/2025-11-15-shopping-lists` — multi-task, T2
- `design the technical approach for docs/tasks/2026-04-18-fix-auth-redirect` — single-task

If the user omits the task directory, stop and ask for it before anything else.

## Mode detection

Inspect the task directory:

- **Multi-task mode** — `<task-dir>/tasks.md` exists. The user must have named a
  task (T1, T2, …). The design is written to `<task-dir>/plans/T<N>-task-design.md`.
- **Single-task mode** — no `tasks.md`. The design covers the whole feature — the
  whole HLD, or the whole of `requirements.md` on the mid-size track — and is
  written to `<task-dir>/task-design.md` (in the task directory root, NOT in
  `plans/`).

If the user named a task ID but no `tasks.md` exists, stop and flag it. If
`tasks.md` exists but no task ID was given, stop and list the task IDs to choose
from.

## The depth rule: shape, not implementation

This is a *design*, so go as deep as method signatures, class names, interface
shapes, and pseudo-code for non-trivial logic — that detail is exactly what makes
the downstream implementation plan possible. But stop at the boundary of actual
implementation: **no full method bodies, no copy-paste-ready code.** Pseudo-code
captures the algorithm's shape and the tricky branches; it is not the finished
function. If you're writing code an engineer would commit verbatim, you've gone
too far.

## Workflow

### 1. Confirm inputs and mode

Detect the mode (above). In multi-task mode, find the named task in `tasks.md`; if
the ID isn't there, stop and list the ones that are. Note which of the inputs
above exist.

### 2. Read inputs

- The top rung of the ladder, in full — `HLD.md` (chosen approach, relevant
  feature area(s), listed ADRs), or `requirements.md` (requirements,
  anti-requirements, constraints, acceptance criteria, open questions). With
  neither, write the one-paragraph problem statement from the prompt and confirm
  it before going further.
- In multi-task mode, the specific task entry in `tasks.md` — **Scope**, **Out of
  scope**, **HLD references**, **How to verify**. These bound what you design.
- Any `research/*.md` relevant to this task.

### 3. Read the project

A technical design has to fit the real codebase, not a generic one. Open the
integration points the input implies, find 1–2 sibling components whose patterns
the new code should follow (naming, structure, layering), and read any ADRs the HLD
references so you don't re-decide settled questions. Note concrete module and file
paths as you go — the implementation plan will need them.

**No HLD? Read wider.** The HLD is what normally grounds a design in the real
system; without one the codebase is your only grounding — how the area is
layered, what already solves part of this, which constraints the code enforces
that nobody wrote down.

### 4. Surface the decisions and resolve them with me

A task design rests on a pile of choices. Before shaping anything, enumerate
**every decision the design depends on** — drawn from the HLD's open questions
(or, with no HLD, `requirements.md` > Open questions and the gaps you found while
reading the codebase), the gaps between the requirements and what the codebase
actually does, and the forks you hit while reading the project (which component
owns this, sync vs. async, where state lives, where the interface boundary falls,
how errors propagate, and so on).

Split them in two:

- **Style-level, low-stakes choices** — naming, file placement, obvious idiom,
  anything a reviewer wouldn't blink at. Decide these **silently** and move on;
  surfacing them just wastes attention.
- **Everything else** — any choice that changes the structure, an interface, the
  data flow, a behavior, or a trade-off. These are **not yours to make alone.**
  Anything a missing artifact would have decided — the approach with no `HLD.md`,
  a scope boundary with no `requirements.md` — is in this bucket by definition,
  however obvious it looks.

Take the substantive ones to the user **one at a time** with the `AskUserQuestion`
tool — a single question per call, never batched, so each decision gets full focus.
Give the realistic options, each with a short and honest description.

**Every one of these calls must include an explicit "I don't know" option as the
last choice — no exceptions, even when one answer looks obviously right to you.**
The user frequently hasn't formed an opinion yet; without that option they're forced
to guess or rubber-stamp your framing, which defeats the point of asking. If you
catch yourself omitting it because the choice "feels clear," that's exactly the case
where the user most needs a way to say "explain this to me first."

- If the user picks a concrete option, record it and move to the next decision.
- If the user picks **"I don't know"**, they're asking you to help them think, not
  to decide for them — and this is where the interaction changes shape. **Stop using
  `AskUserQuestion` for this decision and drop into a normal chat message.** Research
  the real options (codebase, prior art, any `research/*.md`, external docs), lay each
  one out in prose with concrete pros and cons *for this task*, end with your
  recommendation, and hand it back. Then go back and forth in normal messages for as
  long as they need — re-opening the same decision with another `AskUserQuestion`
  call just traps them in the menu they stepped out of, and picking for them defeats
  the point. Record the choice only once they've committed.

Only once **every** substantive decision is settled do you move on. The resolved
choices and their rationale become the design's **Decisions made** section.

### 5. Shape the design

With the decisions settled, work through — at the depth the task warrants:

- **Components and responsibilities** — the units involved (new and modified) and
  what each is responsible for.
- **Interfaces and method signatures** — the contracts between components: key
  methods/functions with their signatures, the data they exchange.
- **Data flow** — how data moves through the components for the main path(s).
- **Pseudo-code** — for non-trivial logic only (tricky algorithms, ordering
  constraints, error/edge handling). Skip it for anything obvious.

### 6. Flag assumptions

List the **assumptions to verify**: anything you inferred about the codebase or the
requirements rather than confirmed. Each becomes a risk downstream if it doesn't
hold.

Skipped inputs belong here too, named as such — "no `HLD.md`: the approach was
chosen in this design rather than agreed upstream" — since on the mid-size track
this is the only record of what was inferred versus agreed.

### 7. Recap, confirm, write

Give the user a short recap — the key components, the load-bearing decisions
(already agreed in step 4), and any remaining assumptions — and confirm it lands
right. Then fill [task-design-template.md](task-design-template.md), strip the
`<angle-bracket guidance>`, and write to the mode-appropriate path. Create `plans/`
if needed.

If the target file already exists, don't silently overwrite — stop, tell the user,
and ask whether to overwrite, write a `.v2` variant, or abort.

## Files

- [task-design-template.md](task-design-template.md) — the output skeleton. Copy
  its structure and fill it in.
