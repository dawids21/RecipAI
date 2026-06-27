# <Task ID / Feature name> — Task Design

**Date:** <YYYY-MM-DD>
**Status:** draft

## Summary

<One or two sentences: what this task builds and the technical shape of the
solution in a nutshell. Assumes the reader has the HLD for the "why".>

## Components and responsibilities

<The units involved — new and modified — and what each is responsible for. Keep
each to its responsibility, not its implementation. Reference concrete modules /
packages from this codebase.

- **`NewComponent`** (CREATE, `path/to/area/`) — <what it owns>
- **`ExistingComponent`** (MODIFY, `path/to/file`) — <what changes about it>>

## Interfaces and method signatures

<The contracts between components: the key methods/functions with their
signatures and the data they exchange. This is the backbone the implementation
plan builds on — be precise about names and types, vague about bodies.

```
class ImageUploader:
    def upload(file: UploadFile, owner_id: UUID) -> ImageRef
    def presign_get(ref: ImageRef, ttl: Duration) -> Url
```

Include only the interfaces that matter — don't transcribe every getter.>

## Data flow

<How data moves through the components for the main path(s). A short numbered
walkthrough or a small diagram. Cover the primary flow and any branch that
isn't obvious.

1. Controller receives the multipart upload, validates size/type.
2. `ImageUploader.upload` streams to object storage, returns an `ImageRef`.
3. The ref is persisted against the owning record; a presigned GET URL is
   returned to the caller.>

## Pseudo-code

<For non-trivial logic only — tricky algorithms, ordering constraints, error and
edge handling. Capture the shape and the branches, not a finished function. Skip
this section (or write "_None — logic is straightforward._") when nothing here
needs it.

```
on upload(file, owner):
    if file.size > MAX: reject(413)
    ref = store.put(stream(file))          # may throw StorageError
    try:
        record.attach(ref); commit()
    except:
        store.delete(ref)                   # don't orphan the blob
        raise
    return presign_get(ref, DEFAULT_TTL)
```>

## Decisions made

<The non-obvious technical choices settled here, each with a one-line reason, so
the implementer doesn't reopen them. These are smaller than HLD ADRs — they're
within-the-task calls.

- **<Decision>** — <why>
- **<Decision>** — <why>

If a decision is big enough that you'd want to revisit *why* in 6 months, it
probably belongs in an ADR from the HLD step, not here.>

## Assumptions to verify

<Anything inferred rather than confirmed — about the codebase, the data, or the
requirements. Each becomes a risk for the implementation planner if it doesn't
hold. Be honest; this is the section that prevents nasty surprises.

- **Assumption:** <what you're assuming>
  **If wrong:** <what breaks / what changes>

If none: "_No outstanding assumptions._">

## Required reading for implementation planning

<The specific files, docs, and ADRs the implementation-planning step should read
first — the subset that matters for *this* task, each with a one-line reason.

- `path/to/SiblingComponent` — pattern to mirror for <X>
- `docs/ADRs/NNNN-<slug>.md` — the decision governing <Y>
- `HLD.md` > Feature areas > <Area name> — context for <Z>>
