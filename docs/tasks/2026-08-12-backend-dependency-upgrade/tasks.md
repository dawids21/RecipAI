# Backend dependency upgrade — Tasks

**Date:** 2026-08-12

## Summary

- **T1:** Backend on Spring Boot 4, Spring AI 2, and Java 26

This design produces one task. The HLD's four-stop ladder is a working order
inside that task, not four deliverables — see cross-task notes.

## Cross-task notes

The ladder in `HLD.md` > Approach (3.5.x patch → Boot 4 on Java 25 → Java 26 →
Spring AI 2) is a **sequencing device for the implementer**, not a delivery
shape. Each stop ends on a green build so a failure has one candidate cause, but
intermediate states are never committed: the branch collapses to a single change
before it ships, per the single-change requirement. The implementation-planning
step should carry the ladder into its step ordering and its checkpoints.

The stops are also not independently shippable. Spring AI 2 requires Boot 4 and
Framework 7 on the classpath, so stop 4 cannot precede stop 2, and stopping short
of stop 4 leaves extraction on a configuration surface the new major has changed.

---

## T1: Backend on Spring Boot 4, Spring AI 2, and Java 26

**User-visible outcome**

The mobile app — and any API consumer with curl — sees every existing endpoint
behave identically, including AI recipe extraction, while the backend runs on
Spring Boot 4 / Spring AI 2 / Java 26 with all out-of-BOM dependencies, container
base images, and CI actions on current versions.

**Scope**

- `HLD.md` > Feature areas > Dependency baseline — parent bump, Spring AI BOM
  alignment, post-split starter naming, and the three externally-pinned
  dependencies; BOM-managed versions left unpinned.
- `HLD.md` > Feature areas > Language level and toolchain — compiler target and
  the Lombok + configuration-processor annotation path on the new JDK, including
  the documented Java 25 fallback decision.
- `HLD.md` > Feature areas > Forced source changes — serialization package moves
  and any other removed/renamed API, behaviour preserved at each call site.
- `HLD.md` > Feature areas > Test infrastructure — Testcontainers relocation,
  pinning the harness database image, and ordering-only assertion fixes.
- `HLD.md` > Feature areas > AI extraction — configuration restated in the new
  key shape, verified by one live provider run, test returned to disabled.
- `HLD.md` > Feature areas > Deployment and CI — both container stages, the
  `compose.yaml` database pin, and the three workflow actions.
- `HLD.md` > Feature areas > Documentation and handoff — tech-stack versions
  refreshed; throwaway modernization note handed to the author.

**Out of scope**

- Adopting Spring 7 / Boot 4 idioms, dropping Lombok for records, or replacing
  hand-rolled code with newly-native framework features — deferred to the
  throwaway handoff note.
- Any change to endpoint paths, request shapes, or response shapes — hard
  anti-requirement; the mobile client is not being touched.
- New automated coverage for the S3 image-upload or extraction paths — the
  extraction check here is a one-off manual run, not a test that lands.
- Any mobile-side work.
- Infrastructure or deployment changes beyond the base-image tags and Action
  versions.
- Pinning container images anywhere beyond the two database references this task
  touches.
- The author's end-to-end S3 upload check after deploy — performed outside this
  task and explicitly not a blocker on the implementer.
- Committing the modernization note into `docs/` — it is scratch, handed over
  directly.

**Depends on:** none

**HLD references**

- `HLD.md` > Approach > Chosen — the ladder, and specifically why the 4.0.x line
  is skipped rather than used as a rung.
- `HLD.md` > Feature areas — all seven areas; Dependency baseline and AI
  extraction carry the most consequence.
- `HLD.md` > Open questions — four items the task-designing step must resolve
  empirically rather than inherit: the latest Boot patch at implementation time,
  which serialization stack the persistence layer picks for document columns,
  the current CI action majors, and the container build's test-skipping flag.
- `research.md` — resolved version matrix and per-library migration notes;
  treat its version numbers as re-checkable, not settled.
- ADRs: none.

**How to verify**

- `cd backend && ./mvnw verify` is green — the full Testcontainers-backed
  integration suite (recipes, collections, shopping lists, planning,
  provisioning, security) passes with no assertion added, removed, or weakened.
- `ExtractionIntegrationTest` un-`@Disabled` once and run against the live
  Gemini API returns a valid, complete extraction — not a degraded result from a
  silently-ignored model property. Confirm the configured model is the one
  actually used, then restore the `@Disabled` annotation so it is absent from
  the final diff.
- `docker build` succeeds for both stages on the new base images, and the
  built image starts and serves the actuator health endpoint.
- `git diff main --stat` shows no change under `mobile/`, and the tech-stack doc
  names no version the project no longer uses.

**Risks / unknowns**

- **Spring AI 2 config keys fail silently.** Unrecognised properties are ignored,
  not rejected, so a missed rename degrades extraction output instead of
  breaking the build. This is why the live run is a verification step and not
  optional — and why "the test passed" is insufficient without confirming the
  intended model was used.
- **Java 26 is a decision point, not a given.** If annotation processing cannot
  be made to work, the target reverts one release and the reason is recorded;
  the container base images must follow whichever way that lands. Nothing else
  in the task changes.
- **Jackson field reordering is expected and benign** at the API contract level,
  but will break any assertion comparing response text. Fix those by comparing
  content — do not pin ordering to make an assertion pass.
- **The test harness database image floats today.** Pin it before climbing the
  ladder, or a red build mid-upgrade may have nothing to do with the upgrade.
- **CI action majors must be confirmed against their own release pages**, not
  secondary sources or the research file.
