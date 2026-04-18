# <Task name> — Design

**Date:** <YYYY-MM-DD>
**Status:** draft
**ADRs:** <list of ADR files written for this task, e.g., docs/ADRs/0007-image-storage-backend.md. None if there are no new ADRs.>

## Overview

<2–3 sentences. The technical shape of the solution. Not a restatement of the
problem — an answer to it.>

## Required reading for implementation

<Project documentation files and prior ADRs that downstream phases (tasks
planning and implementation) most need to read to do this work correctly.
Do not list new ADRs from this task — those are in the metadata above.

- `docs/architecture/<file>.md` — <one-line reason it's relevant>
- `docs/ADRs/NNNN-<slug>.md` — <one-line reason it's relevant>

If none: "_No additional project documentation is required beyond this design._">

## Approach

<The high-level structure: what changes, what stays, the dominant pattern
(e.g., "event-driven via outbox table", "synchronous with optimistic locking",
"pull-based polling with exponential backoff"). Keep alternatives discussion
in ADRs unless trivial.>

## Module & component boundaries

<New files, classes, packages, services. For each: name, location,
responsibility. Note which existing modules are extended vs. which are new.>

## Data model changes

<New tables/entities, new columns, relationships, indexes. Migration strategy
(new migration file, backfill plan if needed). Use schema-style snippets where
they help — keep them brief, not full migration scripts. If none:
"_No data model changes._">

## Interface contracts

<API endpoints (method, path, request/response shape, status codes, error
responses). Service-layer method signatures for non-trivial APIs. Event
payloads for async work. Be explicit about error modes and return types.
Keep to signatures and shapes — no method bodies. If none: "_No new interfaces._">

## Flows & state

<Numbered steps or sequence descriptions for non-trivial interactions
(multi-service flows, async work, retries, race conditions). State machines
for entities with non-trivial lifecycles. If none: "_No non-trivial flows;
behaviour is direct request/response._">

## Integration changes

<Walk through each integration point from the brainstorming and state
concretely what changes there. Format suggestion:

**`path/to/File.java`** — <what changes and why, in 1–3 sentences>

Do not restate the integration point — describe the change.>

## Resolved questions

<For each open question from brainstorming, the resolution.

- **Q:** <restate question briefly>
  **A:** <decision>. <One-line rationale, OR link to ADR if non-obvious:
  see [ADR-NNNN](../../ADRs/NNNN-slug.md).>

If the brainstorming had no open questions: "_No open questions from brainstorming._">

## Assumptions to verify

<Things this design assumes without confirmation. Each item is for human
review before implementation. Cleared and removed when the design is finalized.

- **Assumption:** <what>
  **Why it matters:** <consequence if wrong>
  **How to verify:** <what to read, ask, or test>

If none: "_No outstanding assumptions._">

## Out of scope (design-level)

<Anything that came up while designing but is being deferred. This is a
design-stage extension of brainstorming's anti-requirements, not a
duplication of them. If none, delete this section.>
