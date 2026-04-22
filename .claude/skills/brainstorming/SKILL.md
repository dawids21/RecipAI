---
name: brainstorming
description: Generate and compare multiple distinct implementation approaches for a scoped task, surface trade-offs, and help the user converge on one approach to hand off to the designing step. Use this skill whenever the user has requirements in hand and is asking how to build something — phrases like "brainstorm approaches", "what are the options", "how should we build this", "compare approaches", "what are the ways we could do this", "explore options", or when they point at a `docs/tasks/YYYY-MM-DD-<task>/` folder with a `requirements.md` and ask for solution options. Use it even when the user seems to already have one approach in mind — the skill's job is to make sure alternatives got considered before committing. Do NOT use this skill when the user is still scoping *what* they want (that's requirements-gathering), when one approach is already chosen and they want it detailed (that's designing), or when they want the work broken into PR-sized units (that's task-planning).
disable-model-invocation: true
---

# Solution brainstorming

Generate 3–5 genuinely distinct implementation approaches for a scoped task, surface the trade-offs between them, and help the user converge on one (or explicitly defer the decision). This is the **divergent** step in a spec-driven workflow:

```
requirements-gathering  →  brainstorming  →  designing  →  task-planning
    (what & why)          (how — options)    (how — chosen)   (work units)
```

The output is a `brainstorming.md` file that the designing step will read to pick an approach and commit to it.

## Inputs and outputs

- **Input:** a task folder under `docs/tasks/YYYY-MM-DD-<task-name>/`. The user may give the folder alone (assume `requirements.md` inside it), a specific requirements file, or just describe the task in chat. If there's no requirements.md yet, say so and suggest running requirements-gathering first — don't silently fill the gap by inventing requirements.
- **Output:** `docs/tasks/YYYY-MM-DD-<task-name>/brainstorming.md` containing the approaches, trade-offs, and a recommended (or deferred) choice.

## Why this step exists

Most engineering mistakes happen before a line of code is written — a reasonable approach gets picked too early and its alternatives never get a fair hearing. A good brainstorming step forces the alternatives into view *before* committing. The point is not to be exhaustive; the point is to make sure the chosen approach won against real competitors rather than by default.

If the user already has one approach in mind, don't just rubber-stamp it. Generate alternatives anyway — they may confirm the user's choice (good, now it's been stress-tested) or reveal a better option (even better).

## What counts as a "distinct approach"

Distinct approaches differ in **strategy**, not in surface details. Two approaches that share the same core mechanism and differ only in library choice or file layout are the same approach.

Axes that typically produce real alternatives:
- **Where the work happens** — client vs. server vs. edge, sync vs. async, inline vs. background job, build-time vs. runtime.
- **What the core data structure is** — normalized table vs. document, event log vs. snapshot, push vs. pull.
- **What the boundary of the change is** — extend existing module, add new module, extract shared primitive, replace wholesale.
- **What's reused vs. built** — use an existing framework feature, pull in a library, build from scratch.
- **How much is done now vs. later** — minimal viable version, full version, phased rollout.

Include the "boring" approach (the smallest thing that could work) and at least one approach that challenges an unspoken assumption in the requirements. If every option you've generated assumes X, generate one that doesn't assume X.

## Workflow

The skill has four phases: **load**, **diverge**, **probe**, **converge**. Do not skip phases.

### Phase 1: Load (before the first response)

- Read `requirements.md` if present. It's authoritative — don't re-derive or restate requirements.
- Skim the project's existing code/docs relevant to the task. Specifically:
  - `docs/INDEX.md` if it exists, then the indexed files relevant to the task area.
  - `docs/ADRs/INDEX.md` if it exists — prior decisions constrain the option space. An approach that violates an accepted ADR is only valid if you also propose superseding the ADR, and you should flag that explicitly.
  - The files the requirements.md mentions under "Integration points".
- If requirements.md is missing, tell the user and suggest requirements-gathering. Offer to proceed with the task description alone, but mark the output as being based on an informal scope.

The goal of the load phase is not to produce a design — it's to know enough about the existing system that your generated approaches are grounded, not generic.

### Phase 2: Diverge (one turn)

Generate **3–5 distinct approaches** and present them to the user in a single response. For each approach, give:

- **Name** — short, memorable (e.g., "Server-side rendering", "Client-side cache with SWR", "Background job queue").
- **One-sentence description** — what the approach is, in plain language.
- **Sketch** — 2–4 sentences on how it would actually work in this codebase. Reference concrete modules/files from the load phase when you can. Stay at the *shape* level, not implementation.
- **Key trade-offs** — 2–4 bullets. Honest about costs, not just benefits.
- **When it's the right choice** — the conditions under which this is clearly the best option.
- **Main risk** — one sentence on the thing most likely to go wrong with this approach.

At the end of the diverge response, include a short **At a glance** table comparing the approaches on the 2–3 dimensions that matter most for *this* task (e.g., complexity, latency, migration cost, operational burden). Don't use a generic template of dimensions — pick the ones that actually discriminate between these options for this task.

Close the turn by asking the user which approaches they want to probe further, or if any are already non-starters.

### Phase 3: Probe (multiple turns, Socratic)

Based on the user's reaction, ask **one or two sharper questions per turn** to surface the information needed to converge. Good probes:

- Force explicit trade-off rankings ("Approach A is simpler but higher latency. What's your latency budget?")
- Surface hidden constraints ("Approach C needs a background worker — does the current deploy setup support that?")
- Test approaches against the ugly cases ("How does Approach B behave if the upstream service is down for an hour?")
- Challenge assumptions in the user's preference ("You're leaning toward B — what would have to be true about C for it to be better?")

Avoid:
- Repeating trade-offs the user has already absorbed.
- Adding new approaches at this stage unless the probe phase revealed a genuine gap. If you do add one, say so explicitly and rerun the at-a-glance table.
- Drifting into design-level detail (exact class structures, error types, schema fields). That's the designing step. Stop when your next question would be about the interior of an approach rather than the choice between approaches.

End the probe phase when **any** of these is true:

- The user has stated a preference and defended it under probing.
- The user says "pick one", "write it up", "good enough", or similar.
- Two or three probe rounds have not moved the user's preference — further probing is unlikely to help.
- The user explicitly wants to defer the choice (see "Deferring the decision" below).

### Phase 4: Converge (one turn, before writing the file)

Summarize the current state:
1. Restate the approaches considered.
2. State the recommended choice (or the deferred decision), with a one-paragraph rationale referencing the trade-offs that decided it.
3. List any follow-up questions that are now the designing step's job to resolve.
4. Propose the filename: `docs/tasks/<YYYY-MM-DD>-<task-name>/brainstorming.md`. Reuse the task-name and date from the existing folder — don't invent a new one.

Ask: **"Does this capture it? Anything missing, wrong, or worth adding before I write the doc?"**

Only after the user confirms do you write the file.

## Deferring the decision

Sometimes the right outcome of brainstorming is "we don't know enough to pick yet." That's a legitimate result — force it into a false decision and the designing step will inherit a shaky foundation.

If the user wants to defer, the brainstorming.md should still capture all the approaches and trade-offs — what's deferred is only the recommendation. Record clearly what information is needed to decide, and where it might come from (a spike, a benchmark, a conversation with a specific person, a check of production data).

## Writing the file

After the user confirms the recap:

1. Read the template at [brainstorming-template.md](brainstorming-template.md).
2. Write `brainstorming.md` into the existing task folder. Do not create a new dated folder — the folder already exists from requirements-gathering.
3. Fill each approach into the template. Keep the same approach names used in the conversation so the user can map the doc back to what they remember.
4. In **Recommendation**, either state the chosen approach with rationale, or state "Deferred" with the decision criteria and what's needed to resolve.
5. If the brainstorming surfaced questions that belong in the designing step (e.g., "if we go with A, we still need to decide X"), put them in **Questions for design**. These are different from deferred brainstorming questions — they're questions that only matter once the approach is chosen.

Tell the user where the file was written and suggest the designing step as the natural next move.

## Style guidance

- **Be concrete.** "Use a queue" is not an approach. "Use an in-memory `BlockingQueue<UploadJob>` in the existing `ImageService`, consumed by a single worker thread" is — or more accurately, it's the *sketch* for an approach named something like "Inline background worker."
- **Be even-handed.** Don't stack the deck by writing a glowing description of your favorite approach and a straw-man version of the others. Each approach deserves a fair presentation before the user picks.
- **Own the recommendation.** When you do recommend, say why in terms of the specific trade-offs that mattered for this task — not generic best-practice language.
- **Short is better than long.** A brainstorming doc with five lean approaches and one clear recommendation is more useful than one with three bloated approaches and a hedged conclusion.
- **Don't design inside brainstorming.** The moment you're describing error types, exact method signatures, or database column names, you've slipped into the designing step. Pull back up.

## Example opening

> User: I've got the requirements for the recipe share feature in docs/tasks/2026-04-22-share-recipe/. Can we brainstorm approaches?
>
> Agent: [reads requirements.md and skims relevant modules] Here are four approaches worth considering — the first is the smallest thing that could work, the others explore real alternatives:
>
> **1. Signed URL with snapshot**
> Generate a one-time signed URL that points to a static JSON snapshot of the recipe stored in S3. Read-only, immutable, no DB changes.
> - Trade-offs: simplest to build; recipient always sees the recipe as it was when shared; no way to revoke or update after the fact.
> - Right choice when: sharing is read-only and snapshot semantics are acceptable.
> - Main risk: if the requirements shift toward "link reflects current recipe", this has to be rebuilt.
>
> **2. Share token table**
> [...]
>
> **3. Public recipe flag on the Recipe model**
> [...]
>
> **4. Dedicated sharing service**
> [...]
>
> **At a glance:**
> | Approach | Implementation cost | Revocable | Reflects edits | Auth coupling |
> | ...      | ...                 | ...       | ...            | ...           |
>
> Which of these look plausible to you, and are any of them clearly off the table?

## Reference files

- [brainstorming-template.md](brainstorming-template.md) — brainstorming document skeleton
