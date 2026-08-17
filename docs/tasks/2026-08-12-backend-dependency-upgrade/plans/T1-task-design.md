# T1: Backend on Spring Boot 4, Spring AI 2, and Java 26 — Task Design

**Date:** 2026-08-12

## Summary

A single squashed change moving `backend/` from Boot 3.5.10 / Spring AI 1.1.2 /
Java 25 to Boot 4.1.0 / Spring AI 2.0.0 / Java 26, reached by climbing six
verified rungs whose intermediate states are never committed. The source
footprint is small and entirely forced: three files change Jackson imports (two
of them also rename two deprecated node accessors), one test file follows
Testcontainers' package relocation, and one YAML key loses its `.options.`
segment. Everything else is coordinates, image tags, and Action versions.

## Components and responsibilities

### Build and dependency surface

- **`backend/pom.xml`** (MODIFY) — the whole version contract: parent, language
  level, Spring AI BOM, post-split starter names, the Flyway starter the module
  split now requires, relocated Testcontainers artifacts, and the three
  out-of-BOM pins. Everything the parent manages stays unpinned.

### Forced source changes

- **`recipes/Recipe.java`** (MODIFY) — `JsonNode` import only. The
  `@JdbcTypeCode(SqlTypes.JSON)` / `columnDefinition = "jsonb"` mapping is
  untouched; the field type changes package, not shape.
- **`recipes/RecipeService.java`** (MODIFY) — `JsonNode` + `ObjectMapper`
  imports, and `asText()` → `asString()` in `convertToRecipeData`.
- **`recipes/RecipeFacade.java`** (MODIFY) — same imports, plus `isTextual()` →
  `isString()` and `asText()` → `asString()` in `extractSourceUrl`.
- **`src/main/resources/application.yml`** (MODIFY) — the Spring AI chat key
  shape (live key and the commented `thinking-level`).

### Unchanged by design — verified, not assumed

- **`recipes/images/ImageMetadata.java`** — `jackson-annotations` deliberately
  keeps `com.fasterxml.jackson.annotation`. No import change.
- **`config/security/SecurityConfig.java`** — uses only `requestMatchers(String...)`,
  `dispatcherTypeMatchers(DispatcherType...)`, and `oauth2ResourceServer().jwt()`,
  all present in Security 7.1.0. Every pattern is a `/**` prefix, so the
  trailing-slash matching removal cannot bite.
- **`TestSecurityConfiguration.java`** — mocks `JwtDecoder` directly; uses no
  `@MockBean`/`@SpyBean`, so the `@MockitoBean` rename does not apply.
- **`config/s3/*`, `recipes/images/S3Service.java`** — AWS SDK 2.40.7 → 2.52.0 is
  a minor bump inside 2.x.
- **All `*IntegrationTest` classes** — they build a bare `RestClient` against
  `@LocalServerPort`, so Boot 4's withdrawal of auto-provided `MockMvc` /
  `TestRestTemplate` beans is a non-event, and none of them asserts on raw JSON
  text, so Jackson 3's alphabetical property sorting has nothing to break.

### Test harness

- **`TestcontainersConfiguration.java`** (MODIFY) — container package
  relocation, loss of the type parameter, and the image pin. Owns the
  "attributable red build" property for the whole climb.
- **`extraction/ExtractionIntegrationTest.java`** (TOUCH AND REVERT) —
  `@Disabled` removed for one live run, restored before the squash. Must be
  absent from the final diff.

### Deployment and CI

- **`backend/Dockerfile`** (MODIFY) — both `eclipse-temurin` stages, following
  whichever language level stop 3 settles on.
- **`backend/compose.yaml`** (MODIFY) — Postgres pin.
- **`.github/workflows/docker-build-api.yml`** (MODIFY) — three Action majors.

### Documentation and handoff

- **`docs/project/tech-stack.md`** (MODIFY) — Java, Spring Boot, Spring AI,
  Lombok, AWS SDK, commons-io versions; PostgreSQL 17.5 already matches.
- **Modernization note** (CREATE, **not committed**) — written to the session
  scratchpad and handed to the author in chat.

## Interfaces and method signatures

### `pom.xml` coordinate contract

```
org.springframework.boot:spring-boot-starter-parent   3.5.10 → 3.5.16 → 4.1.0
<java.version>                                        25 → 26          (stop 3)
<spring-ai.version>                                   1.1.2 → 2.0.0    (stop 4)

RENAMED (post-split naming)
  spring-boot-starter-web                     → spring-boot-starter-webmvc
  spring-boot-starter-oauth2-resource-server  → spring-boot-starter-security-oauth2-resource-server
  org.testcontainers:junit-jupiter            → org.testcontainers:testcontainers-junit-jupiter
  org.testcontainers:postgresql               → org.testcontainers:testcontainers-postgresql

ADDED (module split no longer auto-configures Flyway from flyway-core alone)
  org.springframework.boot:spring-boot-starter-flyway

VERSION-BUMPED (out of BOM)
  software.amazon.awssdk:s3      2.40.7 → 2.52.0
  commons-io:commons-io          2.21.0 → 2.22.0
  net.coobird:thumbnailator      0.4.21 → unchanged (already latest)

UNCHANGED COORDINATES (all verified to resolve at 4.1.0 / 2.0.0)
  spring-boot-starter-{data-jpa,validation,actuator,security,test}
  spring-boot-{devtools,docker-compose,testcontainers,configuration-processor}
  org.springframework.security:spring-security-test        (7.1.0, managed)
  org.flywaydb:{flyway-core,flyway-database-postgresql}
  org.springframework.ai:{spring-ai-pdf-document-reader,
                          spring-ai-starter-model-google-genai,
                          spring-ai-spring-boot-docker-compose,
                          spring-ai-spring-boot-testcontainers}
```

Versions the 4.1.0 BOM then supplies, none of which get pinned here: Framework
7.0.8, Security 7.1.0, Hibernate 7.4.1.Final, Jackson 3.1.4, Flyway 12.4.0,
Testcontainers 2.0.5, Lombok 1.18.46.

### Jackson 3 call-site contract

```
tools.jackson.databind.JsonNode          (was com.fasterxml.jackson.databind.JsonNode)
tools.jackson.databind.ObjectMapper      (was com.fasterxml.jackson.databind.ObjectMapper)

node.isTextual() → node.isString()       # @Deprecated since 3.0, exact alias
node.asText()    → node.asString()       # @Deprecated since 3.0, exact alias

UNCHANGED — same signatures in 3.1.4:
  objectMapper.treeToValue(JsonNode, JavaType)
  objectMapper.getTypeFactory().constructCollectionType(Class, Class)
  objectMapper.valueToTree(Object)
  node.has(String) / node.get(String) / node.path(String) / node.isNull() / node.asInt()
```

Injection is unaffected: Boot 4.1 auto-configures a `JsonMapper`, and
`tools.jackson.databind.json.JsonMapper extends tools.jackson.databind.ObjectMapper`,
so the `private final ObjectMapper objectMapper` fields keep resolving.

### Testcontainers 2 harness contract

```
- import org.testcontainers.containers.PostgreSQLContainer;
+ import org.testcontainers.postgresql.PostgreSQLContainer;
  import org.testcontainers.utility.DockerImageName;      // unchanged package

- PostgreSQLContainer<?> postgresContainer()
+ PostgreSQLContainer   postgresContainer()               // class is no longer generic
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.5"))
```

`@ServiceConnection` keeps its
`org.springframework.boot.testcontainers.service.connection` package.

### Configuration keys

```yaml
spring.ai.google.genai.chat.model: gemini-2.5-flash        # was chat.options.model
# spring.ai.google.genai.chat.thinking-level: low          # was chat.options.thinking-level
spring.ai.google.genai.api-key: ${SPRING_AI_API_KEY}       # unchanged
spring.jackson.default-property-inclusion: non_null        # unchanged, still valid in 4.1
```

### Deploy and CI versions

```
Dockerfile   eclipse-temurin:25-jdk-alpine → 26-jdk-alpine   (builder)
             eclipse-temurin:25-jre-alpine → 26-jre-alpine   (runtime)
compose.yaml postgres:latest → postgres:17.5
workflow     actions/checkout          v5 → v7   (v7.0.1 is current — research said v6)
             docker/metadata-action    v5 → v6   (v6.2.0)
             docker/build-push-action  v6 → v7   (v7.3.0)
```

## Data flow

The ladder, as working order. Each rung ends green before the next begins;
nothing between rung 0 and the squash is a commit that ships.

0. **Pin the harness.** `TestcontainersConfiguration` → `postgres:17.5`,
   `compose.yaml` → `postgres:17.5`. Verify `./mvnw verify`. From here a red
   build is attributable to the upgrade and never to a floating image.
1. **Latest 3.5.x.** Parent → **3.5.16**. Clear whatever deprecations that
   surfaces while Framework 6 still compiles them. Verify.
2. **Boot 4.** Parent → 4.1.0, starter renames, add `spring-boot-starter-flyway`,
   Testcontainers artifacts and package, Jackson imports and the two accessor
   renames. `java.version` stays **25**. Verify — this is the heavy rung, and
   the one where the Hibernate format-mapper question is answered by the
   existing recipe round-trip tests.
3. **Java 26.** `java.version` → 26, both Dockerfile stages → `temurin:26-*-alpine`.
   Verify `./mvnw verify` plus `docker build` and an actuator health probe on
   the built image. This is the documented fallback's decision point.
4. **Spring AI 2.** `spring-ai.version` → 2.0.0, `application.yml` key shape.
   Verify the suite, then un-`@Disabled` `ExtractionIntegrationTest` for one
   live Gemini run, then restore `@Disabled`.
5. **Out-of-BOM bumps.** `awssdk:s3` → 2.52.0, `commons-io` → 2.22.0. Verify.
   Last because a transitive conflict here has exactly one candidate cause once
   the Spring line is already settled.
6. **Wrap up.** Workflow Action majors, `tech-stack.md`, the scratchpad
   modernization note. Squash the branch to one commit.

At runtime the only data path that changes shape is `Recipe.data`: a
`tools.jackson.databind.JsonNode` written by `RecipeService.convertToJsonNode`,
persisted by Hibernate through a `FormatMapper` into a `jsonb` column, read back
and destructured by `convertToRecipeData` / `RecipeFacade`. The wire format is
unchanged except for field *order* in responses, which Jackson 3 now sorts
alphabetically — invisible to `dart:convert`.

## Pseudo-code

The climb, with its attribution rule and the one branch that matters:

```
JAVA_HOME := jdk-26                      # default JVM here is 21; release 25/26 needs 26

for rung in [0..5]:
    apply(rung.edits)
    result = mvn verify
    if result is red:
        if failure is explained by rung.edits:
            fix within this rung, re-verify        # never carry a red build upward
        else:
            stop and report                        # attribution broke; do not stack rungs
    commit(wip)                                    # throwaway; squashed later

# rung 3 only — the documented fallback
if rung3 fails on annotation processing (Lombok / configuration-processor):
    java.version := 25
    Dockerfile   := temurin:25-*-alpine
    record reason in the final commit body
    continue to rung 4                             # nothing else in the task changes

# rung 4 only — the live check the suite cannot perform
remove @Disabled from ExtractionIntegrationTest
run both tests against the real Gemini API
assert output is complete, not a degraded shape    # BeanOutputConverter now
                                                   # builds its schema via
                                                   # JsonSchemaGenerator
restore @Disabled                                  # must be absent from final diff

squash branch → one commit
assert git diff main --stat touches nothing under mobile/
```

## Decisions made

- **Four-stop ladder kept, with a pre-flight rung 0 and a trailing rung 5** —
  the HLD's four stops stand as written; rung 0 (image pinning) is the HLD's own
  "pin before climbing" instruction given its own checkpoint, and rung 5 carries
  the out-of-BOM bumps the HLD's table does not place. Both are checkpoints, not
  new scope.
- **Rung 2 targets release 25 on JDK 26** — no JDK 25 is installed and the
  project already compiles under JDK 26, so the rung isolates the Boot 4 hop
  while the Java-26 annotation-processing path is exercised regardless. Rung 3
  therefore flips the bytecode target and the base images, not the toolchain.
- **No runtime probe of the Gemini model** — the decompiled
  `GoogleGenAiChatProperties.Options.setModel()` write-through is treated as
  sufficient proof the property binds; the live run judges output quality only.
- **`asText()` / `isTextual()` renamed to `asString()` / `isString()`** — exact
  aliases, zero behaviour change, and it keeps the build warning-free on a major
  that deprecates the old names.
- **`spring-security-test` coordinate kept** — it is a Spring Security artifact,
  unaffected by the Boot module split, and still managed at 7.1.0. The
  `spring-boot-starter-security-test` equivalent buys nothing here.
- **Plain `spring-boot-starter-test`, not `-test-classic`** — the plain starter
  still carries AssertJ, JUnit Jupiter and Mockito, which is all this suite
  touches.
- **No pre-emptive Hibernate `FormatMapper` configuration** — per the HLD, the
  default is verified empirically at rung 2 and configured explicitly only if it
  proves wrong.
- **The Java-26 fallback reason, if triggered, goes in the commit body** — the
  handoff note is throwaway and `docs/` should not carry a decision about a
  version the project then isn't running.
- **`actions/checkout` goes to v7, not v6** — v7.0.1 is the current release;
  `research.md` was wrong on this one.
- **Rung 1 targets 3.5.16, not 3.5.10** — 3.5.10 is not the head of the 3.5
  line, so the rung is a real six-patch hop rather than a no-op.

## Assumptions to verify

- **Assumption:** Hibernate 7.4.1 selects `Jackson3JsonFormatMapper` for
  `Recipe.data` when both Jackson lines are on the classpath.
  **If wrong:** `jsonb` round-tripping fails loudly in the recipe integration
  tests at rung 2; the fix is one property,
  `spring.jpa.properties.hibernate.type.json_format_mapper`, and the rung's
  scope grows by that line.
- **Assumption:** no test asserts on raw JSON text, so alphabetical property
  sorting breaks nothing (grep for raw-body assertions across `src/test`
  returned nothing).
  **If wrong:** the affected assertions are rewritten to compare content —
  never to pin ordering.
- **Assumption:** `spring.jackson.default-property-inclusion: non_null` still
  suppresses nulls on the wire under Boot 4.1.
  **If wrong:** the mobile client starts seeing null-valued fields it currently
  never receives — an API contract change, and a blocker. Boot issue #48343
  notes it is not applied to *content* inclusion inside collections/maps.
- **Assumption:** `mvn package -DskipTests` in the Dockerfile builder still
  behaves as today (this pom does not bind `process-aot`).
  **If wrong:** the image build slows down or fails; switch that line to
  `-Dmaven.test.skip=true`.
- **Assumption:** 4.1.0 and 2.0.0 are still the latest GA at implementation
  time (both confirmed 2026-08-12; no 4.1.1 on Central).
  **If wrong:** re-pin to the newer patch — the requirement is latest GA, not
  the number in this document.
- **Assumption:** a working Gemini API key is available for the one live
  extraction run.
  **If wrong:** the highest-consequence acceptance criterion cannot be met and
  the task cannot be called done; escalate rather than skip it.
- **Assumption:** the implementer builds with `JAVA_HOME` pointing at JDK 26
  throughout. The machine default is JDK 21, which cannot compile `release 25`
  or `26`.
  **If wrong:** rungs fail at compile with a release-not-supported error that
  has nothing to do with the upgrade.
- **Assumption:** `spring-boot-devtools` and `spring-boot-docker-compose`
  (both `runtime`/`optional`) behave unchanged at 4.1.0 — the artifacts resolve,
  but neither is exercised by `mvn verify`.
  **If wrong:** it surfaces in local development, not in CI.

## Required reading for implementation planning

- `docs/tasks/2026-08-12-backend-dependency-upgrade/research.md` — the per-library
  migration detail behind every edit above. Treat its version numbers as
  re-checkable: `actions/checkout` (v7, not v6), the latest 3.5.x patch (3.5.16,
  not 3.5.10), and the Spring AI `.options` claim (the old key still binds) are
  all corrected here.
- `HLD.md` > Approach > Chosen — the ladder and why the 4.0.x line is skipped.
- `HLD.md` > Feature areas > AI extraction — the shape of the one manual
  verification run.
- `docs/backend/standards/integration-tests.md` — the harness pattern that
  `TestcontainersConfiguration` and every `*IntegrationTest` must still satisfy
  after the Testcontainers 2 relocation.
- `docs/backend/standards/configuration-profiles.md` — which of the three YAML
  files the Spring AI key change belongs in (`application.yml`; the key is not
  environment-specific).
- `backend/pom.xml`, `backend/Dockerfile`, `backend/compose.yaml`,
  `.github/workflows/docker-build-api.yml` — the four files carrying most of the
  diff.
- `backend/src/test/java/xyz/stasiak/recipai/TestcontainersConfiguration.java` —
  rung 0 starts here.
- ADRs: none apply.
