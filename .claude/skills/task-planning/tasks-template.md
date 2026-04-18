# <Feature / design name> — Tasks

**Date:** <YYYY-MM-DD>
**Status:** draft

## Summary

<Ordered list of all tasks — ID and one-line name only. Gives the reader the
full shape before diving in. Order by dependency and value delivery, not
alphabetically or by size.

- **T1:** <one-line name>
- **T2:** <one-line name>
- **T3:** <one-line name>

For a trivial design, a single entry is a legitimate output — do not pad.>

## Cross-task notes

<Optional. Things that span tasks and are worth flagging upfront: parallelism
opportunities, shared prerequisites landing in T1, feature-flag coordination,
a migration that must be reversible before TN ships, sequencing constraints
that aren't obvious from individual `Depends on` fields.

If nothing spans tasks: "_No cross-task concerns._">

---

## T1: <task name>

**User-visible outcome**

<One sentence. What can someone do after this task ships that they couldn't
before? Name the "someone" if it isn't obvious from the project — end user,
API consumer with curl, integrating developer, etc.>

**Scope**

<Terse bullets of what's included. Reference design sections by name rather
than restating their content.

- <bullet>
- <bullet>>

**Out of scope**

<Explicit list of what is *not* in this task and where it lives instead.
Primary guardrail against scope creep at implementation-planning time.

- <thing> — covered in T<N>
- <thing> — deferred
- <thing> — lives in design.md > <section> but not built in this task>

**Depends on:** <task IDs, or "none">

**Design references**

<Pointers into `design.md` sections (and listed ADRs) the implementation
planner should read most carefully for this task.

- `design.md` > <Section name>
- `docs/ADRs/NNNN-<slug>.md`>

**How to verify**

<A concrete, observable check that confirms the user-visible outcome at the
slice boundary — not internal implementation details. Examples:

- `curl -X POST .../api/images -F file=@test.jpg` returns 201 with a
  presigned GET URL in the response body
- From the recipe detail screen, tapping "Add photo", selecting an image,
  and returning shows the thumbnail on the recipe
- `npm install <pkg> && node -e "require('<pkg>').foo()"` prints "ok">

**Risks / unknowns**

<Optional. Task-specific gotchas worth flagging to the implementation
planner. Delete this section if none.>

---

## T2: <task name>

<Same structure as T1.>

---

<Continue for each task. For a single-task file, remove everything after T1
including the separator.>
