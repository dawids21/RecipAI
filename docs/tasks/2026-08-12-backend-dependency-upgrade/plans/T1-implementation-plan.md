# T1: Backend on Spring Boot 4, Spring AI 2, and Java 26 — Implementation Plan

**Date:** 2026-08-12

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/backend/standards/integration-tests.md` — the harness contract
  (`@Import({TestcontainersConfiguration, TestSecurityConfiguration})` +
  hand-built `RestClient` against `@LocalServerPort`) that must still hold after
  the Testcontainers 2 relocation.
- `docs/backend/standards/configuration-profiles.md` — confirms the Spring AI
  chat key belongs in `application.yml` (shared, not environment-specific);
  `application-dev.yml` / `application-prod.yml` are not touched.
- `docs/project/tech-stack.md` — the file whose version tables this task
  refreshes at the end.

**Design & ADRs**

- `plans/T1-task-design.md` > Interfaces and method signatures — the literal
  coordinate, import, accessor, YAML-key and image-tag contracts. This is the
  edit list; do not re-derive it.
- `plans/T1-task-design.md` > Data flow — the six-rung ladder that this plan's
  steps 1–7 implement one-for-one.
- `plans/T1-task-design.md` > Decisions made — eight decisions already settled
  (rung 1 targets 3.5.16, rung 2 stays on release 25, `checkout` goes to v7,
  plain `spring-boot-starter-test`, no pre-emptive `FormatMapper` config…).
  Re-opening any of these is out of scope.
- `HLD.md` > Approach > Chosen — why the 4.0.x line is skipped rather than used
  as a rung.
- `HLD.md` > Feature areas > AI extraction — the shape of the one manual
  live-provider run.
- `research.md` > Key findings — the per-library evidence behind each edit.
  Treat its version numbers as re-checkable: the task design corrects
  `actions/checkout` (v7, not v6) and the 3.5.x head (3.5.16, not 3.5.10).
- ADRs: _None._

**Code to mirror**

- `backend/src/test/java/xyz/stasiak/recipai/TestcontainersConfiguration.java` —
  the single `@Bean @ServiceConnection` container definition; rung 0 starts
  here and its shape (one bean, `DockerImageName.parse(...)`) is preserved.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeFacade.java` —
  `extractSourceUrl` / `extractServingSize` show the defensive
  `path()`-then-type-check node idiom the renamed accessors must preserve
  verbatim (`isTextual()` → `isString()`, `asText()` → `asString()`, nothing
  else).
- `backend/src/main/java/xyz/stasiak/recipai/recipes/images/ImageMetadata.java`
  — the deliberate counter-example: `com.fasterxml.jackson.annotation.JsonUnwrapped`
  stays as-is. Use it as the check that a blanket find-and-replace was not run.
- `backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java`
  — the `createRecipe(...)` helper and `RecipeData` construction to reuse for
  the temporary null-inclusion probe in step 3.

## File inventory

Committed:

- **MODIFY** `backend/pom.xml` — parent, `java.version`, `spring-ai.version`,
  starter renames, Flyway starter, Testcontainers coordinates, two out-of-BOM pins.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/Recipe.java` —
  `JsonNode` import moves to `tools.jackson.databind`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java` —
  `JsonNode` + `ObjectMapper` imports; `asText()` → `asString()` at line ~239.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeFacade.java` —
  same two imports; `isTextual()` → `isString()`, `asText()` → `asString()` at lines ~87–88.
- **MODIFY** `backend/src/main/resources/application.yml` — drop the `options:`
  level under `spring.ai.google.genai.chat` (live `model`, both commented lines).
- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/TestcontainersConfiguration.java` —
  container package relocation, drop `<?>`, pin `postgres:17.5`.
- **MODIFY** `backend/Dockerfile` — both `eclipse-temurin` stages to `26-*-alpine`.
- **MODIFY** `backend/compose.yaml` — `postgres:latest` → `postgres:17.5`.
- **MODIFY** `.github/workflows/docker-build-api.yml` — three Action majors.
- **MODIFY** `docs/project/tech-stack.md` — Java, Spring Boot, Spring AI, Lombok,
  AWS SDK versions across the prose and the Key Dependencies table.

Touched and reverted — **must be absent from `git diff main`**:

- `backend/src/test/java/xyz/stasiak/recipai/extraction/ExtractionIntegrationTest.java`
  — `@Disabled` removed for the live run (step 5), restored before the squash.
- `backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java`
  — one temporary raw-JSON null-inclusion probe (step 3), deleted after it passes.

Not committed:

- **CREATE** `<scratchpad>/modernization-note.md` — the deferred-modernization
  handoff, pasted to the author in chat. Never lands in `docs/`.

Deliberately unchanged, verified rather than assumed — do not edit:
`recipes/images/ImageMetadata.java`, `config/security/SecurityConfig.java`,
`TestSecurityConfiguration.java`, `config/s3/*`, `recipes/images/S3Service.java`,
`application-dev.yml`, `application-prod.yml`, every `db/migration/V*.sql`, and
everything under `mobile/`.

Conditional, only if an assumption fails:

- `backend/src/main/resources/application.yml` — one added line
  `spring.jpa.properties.hibernate.type.json_format_mapper` if step 3's recipe
  round-trip fails (see design > Assumptions to verify).

## Step-by-step plan

Preconditions for every step:

- Work on branch `backend-dependency-upgrade`, off `main`.
- The machine default JVM is **21.0.11**, which cannot compile `release 25` or
  `26`. JDK 26 is installed at `/usr/lib/jvm/java-26-openjdk`. Every Maven
  invocation below is written for fish as
  `env JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./mvnw …`, run from `backend/`.
  Export it once for the session instead if preferred — but never run a bare
  `./mvnw`, or a rung will fail with a release-not-supported error that has
  nothing to do with the upgrade.
- Docker daemon running (Testcontainers).
- Steps 1–7 each end on a throwaway WIP commit. **If a rung goes red for a
  reason its own edits do not explain, stop and report — do not stack the next
  rung on an unattributable failure.**

1. **Rung 0 — pin the harness database.** Replace `postgres:latest` with
   `postgres:17.5` in both container definitions, so every later red build is
   attributable to the upgrade rather than to a floating tag.
   - Files: `backend/src/test/java/xyz/stasiak/recipai/TestcontainersConfiguration.java`,
     `backend/compose.yaml`
   - Verify: `env JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./mvnw verify` is green
     on Boot 3.5.10 — this is the pre-upgrade baseline. Record the run time; it
     is the reference for "did the image build slow down" later.

2. **Rung 1 — latest 3.5.x.** Parent `3.5.10` → **3.5.16**, re-confirming
   against Maven Central that 3.5.16 is still the head of the 3.5 line. Clear
   any deprecation warnings this surfaces while Framework 6 still compiles them;
   change nothing else.
   - Files: `backend/pom.xml`
   - Verify: `env JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./mvnw verify` green,
     and `./mvnw -q dependency:tree | grep -i 'spring-boot-starter-parent\|spring-core'`
     shows the 3.5.16 line.

3. **Rung 2 — Spring Boot 4.** The heavy rung. `java.version` stays **25**.
   In `pom.xml`: parent → `4.1.0` (re-confirm no 4.1.x patch newer than 4.1.0
   exists on Central first); `spring-boot-starter-web` →
   `spring-boot-starter-webmvc`; `spring-boot-starter-oauth2-resource-server` →
   `spring-boot-starter-security-oauth2-resource-server`; **add**
   `spring-boot-starter-flyway` (the module split no longer auto-configures
   Flyway from `flyway-core` alone); `org.testcontainers:junit-jupiter` →
   `testcontainers-junit-jupiter` and `org.testcontainers:postgresql` →
   `testcontainers-postgresql`. In source: the three Jackson import moves plus
   the two accessor renames. In the harness: import
   `org.testcontainers.postgresql.PostgreSQLContainer` and drop the `<?>` — the
   class is no longer generic. Leave every BOM-managed version unpinned.
   - Files: `backend/pom.xml`, `backend/src/main/java/xyz/stasiak/recipai/recipes/Recipe.java`,
     `.../recipes/RecipeService.java`, `.../recipes/RecipeFacade.java`,
     `backend/src/test/java/xyz/stasiak/recipai/TestcontainersConfiguration.java`
   - Verify: `env JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./mvnw verify` green —
     `RecipeIntegrationTest` passing *is* the answer to the Hibernate
     `FormatMapper` question, because it round-trips `Recipe.data` through the
     `jsonb` column. If it fails on JSON serialization, add
     `spring.jpa.properties.hibernate.type.json_format_mapper: jackson` to
     `application.yml` and re-verify; that one line is the whole remedy.
   - Verify (temporary probe, then delete): the design's null-inclusion
     assumption is the one assumption nothing in the suite covers, and Boot
     issue #48343 puts collection *contents* — exactly where `Ingredient`
     lives — at risk. Add one throwaway test to `RecipeIntegrationTest` that
     `POST`s a recipe whose `RecipeData` has `sourceUrl = null` and an
     `Ingredient` with null `quantity`/`unit`/`comment`, re-fetches it with
     `.body(String.class)`, and asserts the raw body does **not** contain
     `null`. Green means `default-property-inclusion: non_null` still holds
     end-to-end and the API contract is intact. Red is a **blocker** — escalate
     rather than work around it. Delete the probe once it has answered.
   - Verify: `./mvnw -q dependency:tree` shows Framework 7.0.8, Security 7.1.0,
     Hibernate 7.4.1.Final, Jackson 3.1.4, Flyway 12.4.0, Testcontainers 2.0.5,
     Lombok 1.18.46 — and no `com.fasterxml.jackson` import remains outside
     `ImageMetadata.java` (`grep -rn "com.fasterxml.jackson" backend/src`
     returns that one annotation import and nothing else).

4. **Rung 3 — Java 26.** `java.version` → 26, both Dockerfile stages →
   `eclipse-temurin:26-jdk-alpine` / `26-jre-alpine`. This rung is the
   documented fallback's decision point: if annotation processing (Lombok +
   `spring-boot-configuration-processor` via `annotationProcessorPaths`) cannot
   be made to work, revert `java.version` to 25 and both images to
   `temurin:25-*-alpine`, record the reason in the final commit body, and
   continue — nothing else in the task changes.
   - Files: `backend/pom.xml`, `backend/Dockerfile`
   - Verify: `env JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./mvnw verify` green,
     with no annotation-processing warnings or errors in the output.
   - Verify: `docker build -t recipai-backend:upgrade-check backend/` succeeds
     and is not dramatically slower than before (watch for `-DskipTests` no
     longer covering AOT; this pom does not bind `process-aot`, so no change is
     expected — if it does bite, switch that line to `-Dmaven.test.skip=true`).
   - Verify: the built image starts and serves health —
     ```
     docker network create recipai-upgrade-check
     docker run -d --name recipai-pg --network recipai-upgrade-check \
       -e POSTGRES_DB=mydatabase -e POSTGRES_USER=myuser -e POSTGRES_PASSWORD=secret postgres:17.5
     docker run -d --name recipai-api --network recipai-upgrade-check -p 8080:8080 \
       -e SPRING_DATASOURCE_URL=jdbc:postgresql://recipai-pg:5432/mydatabase \
       -e SPRING_DATASOURCE_USERNAME=myuser -e SPRING_DATASOURCE_PASSWORD=secret \
       -e SPRING_AI_API_KEY=unused recipai-backend:upgrade-check
     curl -fsS http://localhost:8080/actuator/health   # expect {"status":"UP"}
     ```
     A healthy start also proves Flyway still runs under the module split
     (`ddl-auto: validate` would fail loudly otherwise). Tear down with
     `docker rm -f recipai-api recipai-pg; docker network rm recipai-upgrade-check`.

5. **Rung 4 — Spring AI 2.** `spring-ai.version` → `2.0.0` (re-confirm it is
   still the latest GA), and restate the chat config without the `.options.`
   segment — the live `model` key and the two commented lines below it. All four
   `org.springframework.ai:*` artifact IDs stay as they are.
   - Files: `backend/pom.xml`, `backend/src/main/resources/application.yml`
   - Verify: `env JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./mvnw verify` green.
   - Verify (live, the acceptance criterion the suite cannot cover): export a
     working `SPRING_AI_API_KEY`, remove `@Disabled` from
     `ExtractionIntegrationTest`, and run
     `env JAVA_HOME=/usr/lib/jvm/java-26-openjdk SPRING_AI_API_KEY=<key> ./mvnw test -Dtest=ExtractionIntegrationTest`.
     Both tests must return a **complete** extraction — name, non-empty
     ingredients with quantities split from comments, non-empty instructions,
     and a plausible `servingSize` — not a degraded shape from the reshaped
     `BeanOutputConverter` schema. Eyeball the returned recipe, don't just trust
     the green bar. Then **restore `@Disabled`**.
   - Verify (model binding): none beyond the above. The task design settled this
     — `GoogleGenAiChatProperties.Options.setModel()`'s write-through is treated
     as sufficient proof the property binds, and the live run judges output
     quality only. Do not add a probe, a negative control, or a temporary log
     level for this. Confirmed with the author 2026-08-12.
   - Verify: `git diff --stat -- backend/src/test` is empty before moving on.

6. **Rung 5 — out-of-BOM bumps.** `software.amazon.awssdk:s3` 2.40.7 → 2.52.0
   and `commons-io` 2.21.0 → 2.22.0. `thumbnailator` 0.4.21 is already latest
   and stays untouched. Last, so a transitive conflict here has exactly one
   candidate cause.
   - Files: `backend/pom.xml`
   - Verify: `env JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./mvnw verify` green,
     and `./mvnw -q dependency:tree | grep -i 'awssdk\|commons-io'` shows no
     duplicate or downgraded transitive.

7. **Rung 6 — CI, docs, handoff.** Bump the three Action majors, each confirmed
   against its own GitHub releases page at implementation time rather than
   against `research.md`: `actions/checkout` v5 → **v7**, `docker/metadata-action`
   v5 → **v6**, `docker/build-push-action` v6 → **v7**. Refresh `tech-stack.md`
   — Java 25 → 26 (heading and prose), Spring Boot 3.5.10 → 4.1.0 with the
   Framework note, Spring AI 1.1.2 → 2.0.0, Lombok 1.18.38 → 1.18.46, AWS SDK
   2.40.7 → 2.52.0, and the `*Last Updated*` date. PostgreSQL 17.5 already
   matches. Write the modernization note to the scratchpad.
   - Files: `.github/workflows/docker-build-api.yml`, `docs/project/tech-stack.md`,
     `<scratchpad>/modernization-note.md`
   - Verify: `grep -rn "3\.5\.10\|1\.1\.2\|Java 25\|1\.18\.38\|2\.40\.7" docs/project/tech-stack.md`
     returns nothing.

8. **Squash and final audit.** Collapse the branch to one commit — the
   requirements call for a single change, and no intermediate rung ships. If the
   Java-26 fallback was triggered at step 4, record that decision and its reason
   in the commit body.
   - Files: none
   - Verify: `git diff main --stat` — no path under `mobile/`, no path under
     `backend/src/test/` except `TestcontainersConfiguration.java`, and
     `ExtractionIntegrationTest.java` absent entirely.
   - Verify: `git log main..HEAD --oneline | wc -l` returns `1`, and the message
     follows Conventional Commits with a scope, e.g.
     `build(backend): upgrade to Spring Boot 4, Spring AI 2, and Java 26`.
   - Verify: one final clean-room
     `env JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./mvnw clean verify`.

## Test plan

No test is added or removed by this task. The existing suite is the regression
gate, and it must pass **unchanged in intent** — no assertion added, weakened,
or reordered to accommodate the upgrade.

**Unit tests**

- `ProvisioningServiceTest` — runs untouched; pure `BigDecimal` arithmetic with
  no framework surface. A failure here means the JDK or compiler changed
  behaviour, not the upgrade, and is grounds to stop and report.

**Integration tests** (all `@SpringBootTest(RANDOM_PORT)` +
`@Import({TestcontainersConfiguration, TestSecurityConfiguration})` + hand-built
`RestClient`; none uses `MockMvc`, `TestRestTemplate`, or `@MockBean`, so Boot
4's withdrawals do not reach them)

- `RecipAiApplicationTests` — context loads. First signal that the module split,
  the Flyway starter and `ddl-auto: validate` agree.
- `RecipeIntegrationTest` — the `jsonb` round-trip through `Recipe.data`, which
  is what actually answers the Hibernate `FormatMapper` question at rung 2; plus
  sharing, permissions, and collection assignment.
- `RecipesCollectionIntegrationTest` — collection CRUD, sharing, unsharing.
- `ShoppingListIntegrationTest` — list and item operations.
- `MealPlanIntegrationTest` — calendar view, entries, shopping-list generation.
  The largest suite, and the one most likely to surface a serialization
  regression; note its several `.toString()` uses are on `UUID`/`LocalDate`
  query parameters, not on response bodies, so alphabetical property sorting
  cannot reach them.
- `ExtractionIntegrationTest` — `@Disabled` in the committed tree. Enabled for
  exactly one live run at rung 4, then restored.

**Temporary probes** (written, run, deleted — never committed)

- Null-inclusion probe in `RecipeIntegrationTest` (step 3): a recipe with
  `sourceUrl = null` and an `Ingredient` carrying null `quantity`/`unit`/`comment`,
  re-fetched as a raw `String`, asserted to contain no `null`. Covers the one
  assumption that would be an API contract break — and specifically the
  collection-contents case Boot issue #48343 flags.

**Flutter widget/integration tests**

_N/A — no mobile-side work; `git diff main --stat` must show nothing under `mobile/`._

**Manual verification**

- One live Gemini extraction run per rung 4, judged on output completeness, not
  just a green bar.
- `docker build` for both stages plus the actuator health probe against the
  built image (step 4), which doubles as the Flyway-under-module-split check.
- The author's post-deploy S3 upload and mobile smoke test — explicitly outside
  this task and not a blocker on the implementer.

## Verification checklist

- [ ] Every Maven run used `JAVA_HOME=/usr/lib/jvm/java-26-openjdk`, not the
      default JDK 21.
- [ ] `env JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./mvnw clean verify` is green
      from a clean tree.
- [ ] No new compiler or annotation-processing warnings versus the rung-0
      baseline. (No formatter is configured on this module — there is no
      `spotless:check` to run.)
- [ ] Every BOM-managed version is unpinned; only `awssdk:s3`,
      `thumbnailator`, and `commons-io` carry explicit `<version>` elements.
- [ ] `grep -rn "com.fasterxml.jackson" backend/src` returns only
      `ImageMetadata.java`'s `JsonUnwrapped` annotation import.
- [ ] `application.yml` has no `options:` level under
      `spring.ai.google.genai.chat`, including the commented lines.
- [ ] The live `ExtractionIntegrationTest` run returned a complete extraction,
      and `@Disabled` is back on the class.
- [ ] `docker build` succeeds for both stages and the built image answers
      `/actuator/health` with `UP`.
- [ ] `git diff main --stat` shows nothing under `mobile/`, and nothing under
      `backend/src/test/` beyond `TestcontainersConfiguration.java`.
- [ ] `docs/project/tech-stack.md` names no version the project no longer uses.
- [ ] The branch is one commit, Conventional Commits with a scope; if the
      Java-26 fallback fired, its reason is in the commit body.
- [ ] Each assumption in `T1-task-design.md` > Assumptions to verify is resolved
      or explicitly deferred — in particular the Hibernate `FormatMapper`
      selection and the `non_null` inclusion behaviour.
- [ ] The modernization note is in the scratchpad and pasted to the author, and
      **not** committed under `docs/`.

## Risks surfaced during planning

- **Risk (RESOLVED — no action):** `tasks.md` > How to verify asks to confirm
  "the configured model is the one actually used", while `T1-task-design.md` >
  Decisions made rules out a runtime probe and treats the decompiled
  `setModel()` write-through as sufficient proof the property binds.
  **Why it matters:** a missed key rename degrades extraction silently, so it
  is worth being explicit about what evidence stands behind the criterion.
  **Resolution:** the task design is authoritative — confirmed with the author
  2026-08-12. Binding is established by the write-through; the live run at step
  5 judges output quality. No probe, negative control, or temporary logging is
  added. Recorded here so the tension is not re-opened mid-implementation.

- **Risk:** nothing in the committed suite observes response *serialization*.
  Every integration test deserializes into records via `RestClient`, so
  null-versus-absent, property naming, and inclusion behaviour are all invisible
  to it — and `Ingredient` lives inside a `List`, which is exactly the case Boot
  issue #48343 flags as not honouring `default-property-inclusion`.
  **Why it matters:** a green suite would not catch the one change that *is* an
  API contract break for the mobile client.
  **Mitigation:** the throwaway raw-`String` probe in step 3, run before the
  ladder goes any higher. If it fails, escalate — do not paper over it with a
  per-field annotation.

- **Risk:** `RecipAiApplicationTests` imports only `TestcontainersConfiguration`,
  not `TestSecurityConfiguration`, and does not use `RANDOM_PORT`. It is the
  cheapest failure signal in the suite but exercises a slightly different
  context than every other test.
  **Why it matters:** at rung 2 it is likely the *first* thing to go red, and
  its failure mode (context load) can look like a security or web-layer problem
  when the actual cause is the Flyway starter or the module split.
  **Mitigation:** read its stack trace before assuming the failure is where it
  appears to be; the actuator health probe in step 4 is the independent
  confirmation that Flyway wiring is correct.

- **Risk:** the ladder's attribution property depends on the Docker daemon and
  Maven Central being reachable at every rung, and on `postgres:17.5` being
  pulled once at rung 0.
  **Why it matters:** a network flake mid-climb produces a red build that looks
  like a migration failure.
  **Mitigation:** rung 0's green run pre-pulls the image and warms the local
  repository; if a later rung fails on resolution or container startup, re-run
  the same rung before treating it as a real failure.
