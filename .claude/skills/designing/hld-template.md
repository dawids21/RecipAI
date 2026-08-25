# <Task name> — High-level design

**Date:** <YYYY-MM-DD>
**ADRs:** <ADR files written for this task, e.g., docs/ADRs/0007-image-storage-backend.md. "None" if there are no new ADRs.>

## Summary

<One or two sentences: what we're building (from requirements) and the chosen
approach in a nutshell.>

## Approach

### Chosen

<The selected approach, named, with a short paragraph on how it works at the
level of shape and strategy — not implementation. Reference concrete modules
where it helps. State what this choice gives up relative to the runners-up.>

### Rejected alternatives

<Each alternative considered, with one sentence on why it lost. This is the
section a future reader revisits to understand why the obvious-looking option
wasn't taken — make it useful.>

- **<Approach name>** — <why rejected>
- **<Approach name>** — <why rejected>

<If the recommendation was deferred instead of chosen, replace "Chosen" with a
**Deferred** note: which approaches are still live, what information is missing,
and what would resolve it (a spike, a benchmark, a conversation, a data check).>

## Feature areas

<Break the chosen approach into the areas it affects. For each area, name it and
list its key behaviors at the level of what happens, not how it's coded. No
method signatures, no data model specifics.>

### <Area name>

**Key behaviors.**
- <What this area does, observably>
- <...>

### <Area name>

<Same structure. Add areas as needed.>

## Out of scope

<What came up while designing but is deliberately deferred. A design-stage
extension of the requirements' anti-requirements, not a duplication of them.
If none, delete this section.>

## Assumptions

<Anything inferred rather than agreed — most often scope, when there was no
`requirements.md` to work from. Name the skipped input as such so downstream
steps know what was never confirmed. If none, delete this section.

- **Assumption:** <what you're assuming> — **why it matters:** <what breaks if
  it's wrong>>

## Open questions

<Questions the downstream steps (task-planning, task-design) need to resolve.
Different from a deferred recommendation, which blocks the approach itself.
If none, delete this section.>

- ...
