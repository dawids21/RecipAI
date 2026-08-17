# Backend dependency upgrade

**Date:** 2026-08-12
**Type:** refactor

## Summary

Upgrade the backend to the latest GA major versions of all its dependencies —
Spring Boot 4 (Spring Framework 7) foremost — and move the Java target from 25
to 26, with matching updates to the Docker images, CI workflow, and tech-stack
documentation.

## Context

Routine currency hygiene. There is no incident, CVE, or blocked feature driving
this — the goal is to stay on current versions rather than accumulate drift.

The backend currently runs Spring Boot 3.5.10 (Spring Framework 6.2.x), Spring
AI 1.1.2, and Java 25. The target is Spring Framework 7 via Spring Boot 4, not
patch-level bumps within the 3.5 line. Work is happening on the
`backend-dependency-upgrade` branch.

## Requirements

- The backend builds and runs on **Spring Boot 4** (Spring Framework 7), taking
  the latest GA release of that major line.
- **Spring AI** moves to its latest GA major, in whatever version is compatible
  with Spring Boot 4.
- The three dependencies pinned outside the Spring BOM move to their latest GA
  majors: `software.amazon.awssdk:s3`, `net.coobird:thumbnailator`,
  `commons-io:commons-io`.
- The Java target moves from **25 to 26** (`<java.version>` in `pom.xml`), and
  both Docker stages move to the matching `eclipse-temurin:26-*-alpine` images.
- `compose.yaml` pins Postgres to **`postgres:17.5`**, replacing the floating
  `postgres:latest` tag.
- The GitHub Actions in `.github/workflows/docker-build-api.yml`
  (`actions/checkout`, `docker/metadata-action`, `docker/build-push-action`) move
  to their latest major versions.
- `docs/project/tech-stack.md` is updated so every version it names matches what
  the project actually uses after the upgrade.
- Source changes in `src/main` are made **only where the upgrade forces them** —
  removed or renamed APIs, changed configuration property names, packages that
  moved.
- The upgrade ships as a **single change**, not a staged sequence.
- As the final step, a throwaway note lists modernization opportunities the
  upgrade has unlocked but which were deliberately not taken. This is a scratch
  note for the author to read, not a document that lands in `docs/`.

## Anti-requirements

- **No API contract changes.** The Flutter app in `mobile/` talks to this
  backend and is not being touched; every endpoint must keep its existing paths,
  request shapes, and response shapes.
- **No voluntary code modernization.** Adopting new Spring 7 idioms, dropping
  Lombok in favour of records, or replacing hand-rolled code with newly-native
  framework features are all out of scope. They belong in the throwaway note, to
  be picked up as separate work if wanted.
- **No new test coverage as a deliverable.** The S3 image-upload path and the
  Gemini extraction path stay untested by the automated suite; this task does not
  fix that.
- **No mobile-side work** of any kind.
- **No infrastructure or deployment changes** beyond the base-image tags and the
  Action versions.

## Constraints & assumptions

**Priority order when things conflict — this is the decision rule for the whole
task:**

1. **Spring Boot 4 landing is non-negotiable.** If a library has no release
   compatible with Spring Boot 4, hold that library back at the newest version
   that works rather than holding Spring Boot back.
2. **Java 26 is best-effort.** If any library has no Java 26-compatible release,
   fall back to Java 25 (and `eclipse-temurin:25-*-alpine`) while still landing
   Spring Boot 4. Lombok is the likely candidate — it hooks into compiler
   internals and historically lags new JDK releases.

**Assumptions:**

- Java 26 is a non-LTS release with a roughly six-month support window. Moving
  off LTS is a deliberate choice, accepted with the understanding that another
  bump will be due.
- The `eclipse-temurin:26-jdk-alpine` and `26-jre-alpine` images are available
  and usable on the deploy target — confirmed by the author.
- The backend is deployed to a self-hosted VPS from a GHCR image built by
  GitHub Actions. Rollback is redeploying the previous image, so no in-code
  compatibility shim or feature flag is needed.
- PostgreSQL 17.5 is the version already documented in `tech-stack.md`; pinning
  `compose.yaml` to it aligns local development with the documented target
  rather than introducing a new version.

## Acceptance criteria

- [ ] `mvn verify` passes — the full Testcontainers-backed integration suite
      (recipes, collections, shopping lists, planning, provisioning, security)
      is green.
- [ ] `ExtractionIntegrationTest` is temporarily un-`@Disabled` and run once
      against the real Gemini API, confirming the Spring AI upgrade produces
      valid extraction output.
- [ ] That test is re-`@Disabled` afterwards — the final diff leaves it in its
      original disabled state.
- [ ] Spring Boot is on the latest GA 4.x release.
- [ ] Spring AI, `awssdk:s3`, `thumbnailator`, and `commons-io` are on their
      latest compatible GA versions.
- [ ] `<java.version>` is 26 and both Dockerfile stages use `eclipse-temurin:26`
      images — or, if the Java 26 fallback was triggered, both are on 25 and the
      reason is recorded.
- [ ] `compose.yaml` uses `postgres:17.5`.
- [ ] The three GitHub Actions in `docker-build-api.yml` are on their latest
      major versions.
- [ ] `docs/project/tech-stack.md` names no stale versions.
- [ ] The modernization note has been produced and handed to the author.
- [ ] Manual check, performed by the author outside the implementation task: S3
      recipe-image upload works end-to-end against the upgraded backend. Not a
      blocker on the implementer.

## Edge cases

None of the following were investigated during scoping — they are the surfaces
flagged as most likely to break.

- **Spring AI / Google Genai across a major.** The highest-risk item, because it
  fails *quietly*: a prompt or response-mapping change degrades extraction
  quality rather than throwing. This is the specific reason the disabled
  extraction test must be run manually rather than trusted to stay working.
- **Hibernate 7 under Spring Data JPA.** Dialect and schema-generation behaviour
  against Flyway-managed tables; entity mapping defaults that may have shifted.
- **Spring Security / OAuth2 resource server.** Configuration DSL and property
  churn is common across Spring Security majors; the JWT resource-server setup
  and `TestSecurityConfiguration` are both exposed.
- **JSON serialization changes.** Any shift in default serializer behaviour
  (naming, null handling, date formats) would change response shapes the mobile
  client parses — a silent break in the API contract that the backend suite
  would not catch.
- **Lombok and Java 26 annotation processing.** Lombok is wired into
  `maven-compiler-plugin`'s `annotationProcessorPaths` alongside
  `spring-boot-configuration-processor`; either could fail on a new JDK.
- **`postgres:latest` in `TestcontainersConfiguration`.** Still floating after
  this task (only `compose.yaml` is being pinned), so the test suite pulls
  whatever is newest and could fail for reasons unrelated to the upgrade.
- **Transitive version conflicts.** A pinned third-party dependency may drag in
  a transitive that the Spring Boot 4 BOM overrides, or vice versa.

## Integration points

| File | Change |
|---|---|
| `backend/pom.xml` | Parent version, `<java.version>`, `spring-ai.version`, the three explicit dependency versions |
| `backend/Dockerfile` | Both `eclipse-temurin` base image tags (builder and runtime stages) |
| `backend/compose.yaml` | Postgres image tag → `17.5` |
| `backend/src/test/java/xyz/stasiak/recipai/extraction/ExtractionIntegrationTest.java` | Temporarily enabled for one manual run, then restored |
| `.github/workflows/docker-build-api.yml` | Three Action versions |
| `docs/project/tech-stack.md` | Java, Spring Boot, Spring AI, and any other named versions |
| `backend/src/main/**` | Only where the upgrade forces a change |

Areas of `src/main` most likely to need forced changes, based on the risk list
above: `config/security/`, `config/s3/`, `extraction/ExtractionConfig.java`,
`extraction/ExtractionService.java`, `recipes/images/S3Service.java`, and JPA
entity/repository definitions across the modules.

## Open questions

- **The actual latest GA version numbers** for Spring Boot 4.x, Spring AI,
  `awssdk:s3`, `thumbnailator`, `commons-io`, and the GitHub Actions were
  deliberately not pinned during scoping. The implementer resolves them at
  implementation time.
- **Does a Java 26-compatible Lombok release exist?** Determines whether the
  Java 25 fallback is triggered.
- **Which Spring AI version supports Spring Boot 4?** May force holding Spring
  AI back below its latest GA.
