---
name: designing
description: Establish the high-level design (HLD) for a planning task — explore distinct approaches, converge on one, and break the feature into areas with key behaviors. Use this skill whenever the user wants to design or architect a feature at a high level, weigh approaches, decide "how should we build this", or points at a `docs/tasks/YYYY-MM-DD-<task>/` folder and asks for a design or HLD — working from `requirements.md` when it exists, otherwise from the prompt. Use it even when the user already has an approach in mind — the job is to make sure alternatives got a fair hearing first. Do NOT use this skill for technical/component-level design with method signatures or pseudo-code (that's task-designing), for scoping what the user wants (that's requirements-gathering), or for breaking work into PR-sized units (that's task-planning).
disable-model-invocation: true
---

# High-level design (HLD)

Take the agreed problem — `requirements.md`, or a problem statement confirmed with the user — and produce an `HLD.md`: the architecture phase the full track runs (requirements → **design** → task-planning → task-design → implementation). This step explores alternative approaches, commits to one, and breaks the feature into areas — all at the level of *approach and behavior*, never technical detail.

## Inputs and outputs

- **Input:** a task folder under `docs/tasks/YYYY-MM-DD-<task-name>/`, plus optionally `research/*.md`. Work from the highest rung of this ladder that exists — **a missing artifact is a legitimate track choice, not an error**, so walk up the chain instead of refusing to run:
  1. `requirements.md` — authoritative scope; don't re-derive it.
  2. **The user's prompt** — nothing written down. Capture a one-paragraph problem statement inline and get a yes on it *before* diverging, since approaches are only as good as the problem they answer.

  Stop only at the bottom: if nothing describes the intent — no artifact, no prompt — say so and ask. Working from the prompt also means reading more of the codebase up front (it is your only grounding) and recording the skipped rung under **Assumptions** in the HLD, so downstream steps can see that scope was inferred rather than agreed.
- **Output:** `docs/tasks/YYYY-MM-DD-<task-name>/HLD.md`, plus zero or more ADRs in `docs/ADRs/` (with an updated `docs/ADRs/INDEX.md`).

If `HLD.md` already exists, you're iterating — read it first and treat it as the working state.

## The one hard rule: stay high-level

This is a *design of the approach*, not the implementation. Keep everything at the level of strategy, structure, and behavior. **No method signatures, no pseudo-code, no specific library choices, no data model specifics (tables, columns, fields).** The moment you're naming a class method or a database column, you've dropped into the task-design step — pull back up.

The reason: committing to technical detail now, before the approach is even settled, locks in decisions that should stay open. The HLD's job is to make the shape of the solution legible and reviewable without prematurely constraining how it's built.

## Workflow

Four phases: **diverge → probe → converge → structure**. Read `requirements.md` (or your confirmed problem statement) and any `research/*.md` first so your approaches are grounded in this system, not generic. Skim the relevant existing code and `docs/ADRs/INDEX.md` — prior decisions constrain the option space.

### 1. Diverge (one turn)

Generate **3–5 genuinely distinct approaches**. Distinct means different in *strategy*, not surface details — two approaches that share a core mechanism and differ only in layout are the same approach. Axes that produce real alternatives: where the work happens (client/server/edge, sync/async, inline/background), the core data shape (normalized/document, event log/snapshot), the boundary of the change (extend/add/extract/replace), what's reused vs. built, how much now vs. later. Include the boring smallest-thing-that-works option, and at least one that challenges an unspoken assumption in the requirements.

For each approach give: **name**, one-sentence description, a 2–4 sentence **sketch** referencing concrete modules from this codebase, **key trade-offs** (honest about costs), **when it's the right choice**, and the **main risk**. Close with an **at-a-glance** table comparing them on the 2–3 dimensions that actually discriminate for *this* task (not a generic template). Ask which approaches to probe or rule out.

### 2. Probe (Socratic, multiple turns)

Ask one or two sharper questions per turn to surface what's needed to converge: force trade-off rankings, surface hidden constraints, test approaches against the ugly cases, challenge the user's stated preference. Don't repeat trade-offs already absorbed, don't add approaches unless a real gap appeared, don't drift into technical detail. Stop when the user defends a preference, says "pick one", probing stops moving them, or they choose to defer.

### 3. Converge (one turn, before writing)

Restate the approaches, state the recommended choice (or the deferred decision) with a one-paragraph rationale tied to the trade-offs that decided it, and note which alternatives were rejected and why. Deferring is a legitimate outcome — if the user can't pick yet, record what's missing and what would resolve it rather than forcing a false decision. Ask: *"Does this capture it? Anything missing before I write the HLD?"*

### 4. Structure & write

Once confirmed, break the chosen approach into **feature areas** (each affected area with its **key behaviors**), state what's **out of scope**, record any **assumptions** you had to make about scope, and list **open questions** for the downstream steps. Identify any **high-level architectural decisions** worth a persistent record and write them as ADRs (heuristic: *if you'd want to revisit why this was decided in 6 months, it's an ADR*).

Then write `HLD.md` using [hld-template.md](hld-template.md). For ADRs use [adr-template.md](adr-template.md), number sequentially from `docs/ADRs/INDEX.md`, write them `Status: proposed`, and update the INDEX. ADR Context sections must be self-contained — describe the problem directly, never link to the ephemeral HLD or requirements. Tell the user where files landed, then point at the right next step: if the HLD describes a **single cohesive unit of work**, there is nothing for `/task-planning` to split — de-escalate and go straight to `/task-designing`. Suggest `/task-planning` only when the areas are genuinely separable into PR-sized slices with their own user-visible outcomes.

## Style

- **Be even-handed.** Give each approach a fair presentation before recommending — no straw men for the options you don't like.
- **Own the recommendation.** Say why in terms of the specific trade-offs that mattered, not generic best-practice language.
- **Short beats long.** Five lean approaches and one clear recommendation beat three bloated ones and a hedge.

## Reference files

- [hld-template.md](hld-template.md) — HLD document skeleton
- [adr-template.md](adr-template.md) — ADR file skeleton
