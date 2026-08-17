# Backend dependency upgrade — High-level design

**Date:** 2026-08-12
**ADRs:** None

## Summary

Move the backend to the latest GA Spring Boot 4 line, Spring AI 2, and Java 26,
along with the dependencies pinned outside the Spring BOM, the container base
images, and the CI actions. The upgrade is performed as a **stepping-stone
ladder** — four intermediate stops, each ending on a green build — and squashed
into the single change the requirements call for.

## Approach

### Chosen: stepping-stone ladder, squashed

The upgrade lands four independent migrations at once: Spring Boot's module and
starter split, Jackson 2 → 3, Testcontainers 1 → 2, and Spring AI 1 → 2. Applied
in one pass, any failure has four plausible causes. The ladder separates them by
climbing one risk axis at a time, verifying at each stop, so a red build always
has a single candidate cause.

| Stop | Lands | Isolates |
|---|---|---|
| 1 | Latest 3.5.x patch | Deprecations, while the current framework still compiles them |
| 2 | Spring Boot 4.x on Java 25 | Module split, Jackson 3, Testcontainers 2, Hibernate 7, Security 7 |
| 3 | Java 26 | Lombok and annotation processing on a new JDK |
| 4 | Spring AI 2.x | Extraction behaviour and its configuration surface |

The intermediate Boot 4.0.x line is deliberately skipped rather than used as a
rung: it caps below Java 26, and Spring AI 2 aligns its transitives to the 4.1
line, so a stop there would surface failures that do not exist at the
destination.

Stops are a working order, not a delivery shape. Intermediate states are never
committed as such — the branch history is collapsed to one change before it
ships, satisfying the single-change requirement.

**What this gives up.** Three extra verification cycles, and some throwaway
effort at stop 1 clearing deprecations the later stops would have forced anyway.
Both were judged cheap because the integration suite runs fast.

**Where the ladder still concentrates risk.** Stop 2 remains the heavy hop —
four migrations arrive together because they all ride the Boot BOM and cannot be
separated without hand-managing versions, which would cost more than it buys.
Stop 4 carries the only failure mode the automated suite cannot see, and is
covered by a manual verification run instead.

### Rejected alternatives

- **One-shot big bang** — edit everything to the target and chase failures. Its
  only real advantage is speed, which a fast test suite erases; it trades that
  for debugging four simultaneous migrations with no attribution.
- **OpenRewrite-driven migration** — published recipes cover the mechanical
  bulk, but that bulk is a handful of files here. Tool setup plus reviewing
  generated hunks against the "no voluntary modernization" anti-requirement
  costs more than making the edits by hand.
- **Risk-first spike** — resolve the unknowns in throwaway scratch projects
  first. Subsumed by the ladder: cheap in-place cycles answer each unknown at
  the hop that introduces it, in the real environment rather than a proxy.
- **Contract-guarded snapshots** — capture and diff API responses across the
  upgrade to catch serializer-level drift the suite is blind to. Rejected in
  favour of a manual mobile smoke test after deploy, which the author accepted
  as sufficient coverage of the API contract.

## Feature areas

### Dependency baseline

**Key behaviors.**
- The build resolves against the target Spring Boot major, with the Spring AI
  BOM aligned to the version that actually builds against it.
- Starters follow the post-split naming, and any auto-configuration that the
  module split no longer activates implicitly gets its starter added explicitly.
- Dependencies pinned outside the BOM move to their latest GA; where a library
  is already current, it stays untouched rather than being churned.
- Anything the BOM manages is left unpinned, so the parent stays the single
  source of versions.

### Language level and toolchain

**Key behaviors.**
- The compiler target advances one release, and the annotation-processing path
  (Lombok plus the configuration processor) is exercised on the new JDK as the
  acceptance signal for that stop.
- This stop is the decision point for the documented fallback: if annotation
  processing cannot be made to work, the target reverts one release and the
  reason is recorded, without disturbing any other stop.

### Forced source changes

**Key behaviors.**
- Source changes are made only where the upgrade removes, renames, or relocates
  something the code depends on — principally serialization package moves.
- Behaviour at each touched call site is preserved; equivalents are substituted
  rather than idioms modernised.
- JSON persisted as a document column continues to round-trip unchanged. Which
  serialization stack the persistence layer selects is verified empirically, and
  configured explicitly only if the default proves wrong.

### Test infrastructure

**Key behaviors.**
- The container-based test harness is migrated to the relocated artifacts and
  types, and the full integration suite passes unchanged in intent — no
  assertions are added, removed, or weakened to accommodate the upgrade.
- The database image used by the test harness is pinned to the same explicit
  version as local development, so a red build during the ladder is always
  attributable to the upgrade and never to a floating image. *(Scope addition
  beyond `requirements.md`, agreed during design.)*
- Serializer default changes may reorder fields in responses; any assertion that
  breaks purely on ordering is corrected to compare content, not text.

### AI extraction

**Key behaviors.**
- Extraction configuration is restated in the key shape the new major expects.
  This is the highest-consequence item: unrecognised keys are ignored rather
  than rejected, so a missed rename degrades output silently instead of failing.
- The extraction path is verified once against the live provider by temporarily
  enabling the normally-disabled integration test, confirming that output is
  still valid and complete.
- That test is returned to its disabled state, leaving the final diff with no
  change to its status.

### Deployment and CI

**Key behaviors.**
- Both container build stages move to base images matching the final language
  level, whichever the fallback decision settles on.
- Local development pins its database image explicitly instead of tracking a
  floating tag.
- The image-build workflow's actions move to their current majors, each
  confirmed against its own release page rather than secondary sources.

### Documentation and handoff

**Key behaviors.**
- The tech-stack document names no version the project no longer uses.
- A throwaway note lists the modernization the upgrade unlocks but that was
  deliberately not taken, handed to the author directly rather than committed.

## Out of scope

- **Adopting the new framework's idioms.** Deferred to the handoff note.
- **Closing the coverage gaps** on image upload and extraction. The extraction
  check here is a one-off manual run, not new automated coverage.
- **Extending the image-pinning convention** beyond the two database references
  this task touches.

## Open questions

- Whether a newer patch exists on the target Boot line by implementation time —
  the requirement is latest GA, so this is re-checked before pinning rather than
  taken from research.
- Which serialization stack the persistence layer selects for document columns
  when both majors are on the classpath. Empirical; answered at stop 2.
- Current major versions of the three CI actions, confirmed against their own
  release pages.
- Whether the container build's test-skipping flag still has the same effect
  under the new build behaviour. No change expected; watch for a slower or
  failing image build.
