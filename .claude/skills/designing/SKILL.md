---
name: designing
description: Produce or finalize a design artifact for a planning task in a spec-driven workflow (requirements → brainstorming → design → tasks). Use this skill whenever the user wants to design, architect, or technically plan a feature or refactor; whenever they point at a `docs/tasks/YYYY-MM-DD-<task>/` folder and ask for a design; whenever they mention "design doc", "design phase", "spec out", "figure out how to build X", or want to resolve questions left open by requirements or brainstorming. Also use when the user wants to capture architectural decisions as ADRs alongside a design, or when they want to finalize an existing draft design after review.
disable-model-invocation: true
---

# Design

Produce a **design** artifact: the third stage in a four-stage planning workflow (requirements → brainstorming → design → tasks). The design commits to the approach chosen in brainstorming and specifies the technical shape of the change, resolving any questions left open upstream.

## Inputs and outputs

- **Inputs:** a path to a task folder under `docs/tasks/YYYY-MM-DD-<task-name>/`. The folder may contain:
  - `requirements.md` — authoritative scope (what we want, anti-requirements, constraints, acceptance criteria, edge cases, integration points). **Required.**
  - `brainstorming.md` — the approaches that were considered and the chosen one, with rationale. **Expected if brainstorming was run**; if absent, the design step picks the approach implicitly, which should be flagged in **Assumptions to verify**.
- **Output:**
  - `docs/tasks/YYYY-MM-DD-<task-name>/design.md`
  - Zero or more new files in `docs/ADRs/`, plus an updated `docs/ADRs/INDEX.md`.

## Modes

This skill handles both phases of a design's life:

- **Draft** — first pass. Uses the **Assumptions to verify** section to flag every guess for human review. New ADRs are created with `Status: proposed`.
- **Finalize** — after review. Apply user feedback, clear the **Assumptions to verify** section, flip new ADRs from `proposed` to `accepted`, set design `Status: final`.

If a `design.md` already exists in the task folder, you are iterating — read it before doing anything else and use its current contents as the working state.

## Hard rules

1. **Requirements is authoritative for scope.** Never re-derive requirements, acceptance criteria, constraints, edge cases, or anti-requirements from scratch. `requirements.md` sits in the same folder; that proximity is the reference.
2. **Brainstorming is authoritative for approach.** If `brainstorming.md` exists and names a chosen approach, design that approach. Do not silently substitute a different one. If you have strong reason to deviate, surface it to the user before writing — don't just override.
3. **Transform, don't duplicate.** Turn the requirements' "integration points" into a concrete list of changes per touchpoint, but do not restate them verbatim.
4. **Do not produce a "requirements" or "spec" mirror** of the upstream docs.
4. **Be technology-specific.** Commit to concrete frameworks, libraries, patterns. "We'll use a queue" is not a design — "We'll use a `BlockingQueue<UploadJob>` in `ImageUploadService` consumed by a single worker thread" is.
5. **No extensive implementation code in the design.** Show *shape*, not implementation. Acceptable: method signatures, type sketches (class + fields + key methods), schema snippets, endpoint definitions, sequence outlines. **Not** acceptable: full method bodies, multi-screen code blocks, anything line-by-line. Implementation belongs in the tasks/coding phase.
7. **Propose, then mark for revision.** Make the best technical decision you can. Where you guessed or extrapolated, log it in **Assumptions to verify** so the human can confirm or push back.
8. **ADRs are self-contained and persistent.** They outlive the task. Their Context section must describe the problem directly — do not link to requirements, brainstorming, or design files (which are ephemeral, scoped to the task).

## Workflow

### Step 1 — Read inputs

- Locate the task folder. If the user gave a folder, expect `requirements.md` and (usually) `brainstorming.md` inside it. If they gave a specific file, use that but still look for its siblings.
- Read `requirements.md` fully. Pay particular attention to:
  - **Open questions** — you must resolve every one.
  - **Integration points** — you must transform each into concrete changes.
  - **Anti-requirements** — you must not violate them.
  - **Constraints & assumptions** — these bound your design.
- Read `brainstorming.md` fully if present. Pay particular attention to:
  - **Recommendation** — the chosen approach is the one you are designing. If the recommendation was deferred, stop and ask the user which approach to design (or whether to defer design too).
  - **Questions for design** — you must resolve every one.
  - **Approaches considered** (runners-up) — use these to understand what tradeoffs were consciously accepted, so you don't inadvertently re-litigate them.
- If `brainstorming.md` is absent, proceed but note in **Assumptions to verify** that the approach was chosen implicitly by the design step without a surveyed alternatives pass.
- If `design.md` already exists in the same folder, read it. You're iterating — its current state is your starting point.

### Step 2 — Read the project documentation

**This step is non-negotiable.** A design written without reading the codebase's existing decisions tends to invent abstractions that don't fit and miss patterns the team already uses.

- **Project docs:** start at `docs/INDEX.md`. It describes what each documentation file covers. Read the files indexed there that are relevant to this task. Don't read everything — use the index to choose.
- **Prior decisions:** read `docs/ADRs/INDEX.md`. It summarizes every ADR. Open and read only the ADRs whose summaries are relevant to this task. You must respect prior ADRs or explicitly supersede them.
- **Touched files:** the specific files mentioned in the requirements' "Integration points" — open and skim them.
- **Missing documentation:** if a documentation file you'd expect (e.g., `docs/INDEX.md`, an architecture doc covering an area you're about to design into) doesn't exist, note this in the design file's **Assumptions to verify** section. Do not substitute by guessing from the source tree.

### Step 3 — Identify ADR-worthy decisions

Walk through the open questions from `requirements.md`, the "Questions for design" from `brainstorming.md`, plus any major decisions implied by the chosen approach. For each, classify:

- **Trivial / obvious** — resolve inline in the design doc's "Resolved questions" section.
- **Non-obvious** — has real tradeoffs, plausible alternatives a reasonable engineer might pick, or long-term implications. Write a separate ADR file.

Heuristic: *if you'd want to revisit why this was decided in 6 months, it's an ADR.*

### Step 4 — Draft (or revise) the design doc

- Path: `docs/tasks/YYYY-MM-DD-<task-name>/design.md`.
- Use [design-template.md](design-template.md) as the structure. Sections that don't apply (e.g., no data model changes for a pure refactor) should remain with a brief note like `_No data model changes._` — the empty section signals you considered it.
- Apply the no-extensive-code rule from the hard rules.
- Fill the **Required reading for implementation** section with links to the project documentation files (and prior ADRs) that downstream phases — tasks planning and implementation — most need to read. Don't list new ADRs from this task here; those go in the metadata at the top.
- During iteration: revise the existing content rather than rewriting from scratch. Preserve sections the user has already endorsed.

### Step 5 — Write or update ADRs

- Number sequentially. Find the highest number in `docs/ADRs/INDEX.md` and increment. If the folder doesn't exist yet, create it and start at `0001`.
- Use [adr-template.md](adr-template.md).
- The Context section must be self-contained — describe the situation and forces directly, no links back to brainstorming or design.
- **Update `docs/ADRs/INDEX.md`** with an entry for each new ADR (number, title, one-line summary).
- During finalize: change `Status: proposed` to `Status: accepted` for ADRs the user has confirmed.

### Step 6 — Flag assumptions

Anything decided by inference rather than confirmed information goes in **Assumptions to verify**. Each entry states:

- **What** the assumption is
- **Why it matters** (what changes if it's wrong)
- **How to verify** (what to read, who to ask, what to test)

Be generous — better to over-flag than to bury a guess.

During finalize, this section should be empty (or removed entirely).

### Step 7 — Hand off

**If drafting**, show the user:
1. Path to the design doc
2. Paths to any new ADR files
3. Short summary of what's in **Assumptions to verify**

Then stop. The user reviews and pushes back. Iterate from Step 1 with the existing design as input.

**If finalizing**, show the user:
1. Path to the design doc, now `Status: final`
2. ADR status changes (which moved from `proposed` to `accepted`)
3. Confirmation that **Assumptions to verify** is empty
4. Confirmation that `docs/ADRs/INDEX.md` is up to date

## What goes in the design (and what doesn't)

| Belongs in design                                     | Belongs elsewhere                            |
|-------------------------------------------------------|----------------------------------------------|
| Module/component boundaries, responsibilities         | Requirements (→ requirements.md)             |
| Data model changes, migrations                        | Acceptance criteria (→ requirements.md)      |
| API/method signatures, error types, event payloads    | Constraints (→ requirements.md)              |
| Sequence diagrams, state machines                     | Alternative approaches (→ brainstorming.md)  |
| Concrete framework/library choices                    | Discrete implementable units (→ tasks)       |
| Resolution of upstream open questions                 | PR-sized work items (→ tasks)                |
| Per-integration-point list of concrete changes        | Test scenarios (→ tasks, paired with impl)   |
|                                                       | Full method bodies (→ implementation)        |

## Reference files

- [design-template.md](design-template.md) — design document skeleton
- [adr-template.md](adr-template.md) — ADR file skeleton
