# Migration guides for the Spring libraries used by the RecipAI backend

**Date:** 2026-08-12
**Scope:** research only — no code changed. Companion to `requirements.md` in this directory.

## Summary

The backend's target stack resolves cleanly: **Spring Boot 4.1.0** (latest GA, released 2026-06-10) with
**Spring AI 2.0.0** (latest GA, 2026-06-12) on **Java 26**. Spring Boot 4.1 officially supports Java 17–26,
and Lombok 1.18.46 — the version Boot 4.1 already manages — added JDK 26 support, so the Java 25 fallback in
the requirements is almost certainly *not* triggered.

The upgrade is not a version-number-only change. Four separate migrations land at once: Spring Boot's
**module/starter split** (Flyway auto-configuration now needs its own starter), **Jackson 2 → Jackson 3**
(package and groupId change, affecting six source files), **Testcontainers 1.x → 2.0** (artifact and package
relocation), and **Spring AI 1.1 → 2.0** (the `.options` segment disappears from configuration property keys,
which breaks `application.yml` silently). Spring Security 7 and Hibernate 7 turn out to be the *low*-risk
items for this codebase, contrary to the risk list in `requirements.md`.

## Target version matrix

Resolved from Maven Central metadata and the Spring Boot 4.1.0 BOM (`spring-boot-dependencies-4.1.0.pom`) on
2026-08-12.

| Dependency | Current | Target | Notes |
|---|---|---|---|
| `spring-boot-starter-parent` | 3.5.10 | **4.1.0** | Latest GA. 4.0 line is at 4.0.7. |
| Spring Framework | 6.2.x | 7.0.8 | Managed by the Boot BOM. |
| Spring Security | 6.5.x | 7.1.0 | Managed. |
| Hibernate ORM | 6.6.x | 7.4.1.Final | Managed. |
| Spring Data BOM | 2025.0.x | 2026.0.0 | Managed. |
| Jackson | 2.x (`com.fasterxml`) | **3.1.4 (`tools.jackson`)** | Jackson 2.21.4 also managed for compatibility. |
| Flyway | 11.x | 12.4.0 | Managed; **needs a new starter**, see below. |
| Testcontainers | 1.x | **2.0.5** | Managed; artifact + package relocation. |
| PostgreSQL JDBC driver | 42.7.x | 42.7.11 | Managed. |
| Lombok | 1.18.4x | 1.18.46 | Managed; adds JDK 26 support. |
| `spring-ai-bom` | 1.1.2 | **2.0.0** | Requires Boot 4.0/4.1 + Framework 7.0. |
| `software.amazon.awssdk:s3` | 2.40.7 | **2.52.0** | No 3.x line exists. |
| `commons-io:commons-io` | 2.21.0 | **2.22.0** | |
| `net.coobird:thumbnailator` | 0.4.21 | **0.4.21** | Already latest — no change needed. |
| Java | 25 | **26** | Boot 4.1 supports up to and including 26. |
| `actions/checkout` | v5 | v6 | Verify at implementation time (see gaps). |
| `docker/metadata-action` | v5 | v6 | Same. |
| `docker/build-push-action` | v6 | v7 | Same. |

## Key findings

### Java 26 is viable — the fallback is not needed

- Spring Boot 4.1.0 requires Java 17 minimum and supports **up to and including Java 26**; it needs Spring
  Framework 7.0.8+. (Spring Boot 4.0.x capped out at Java 25, which is why the 4.1 line matters here.)
- Lombok issue #4019 ("Unsupported class file major version 70") was closed against milestone **1.18.46**,
  and 1.18.46 is exactly the version the Boot 4.1.0 BOM manages. Since the pom uses managed Lombok (no
  explicit version), this comes for free.
- Residual risk: the original report was that command-line Maven builds worked and only IntelliJ failed. The
  acceptance test is `mvn verify` on JDK 26 with `annotationProcessorPaths` wired to Lombok +
  `spring-boot-configuration-processor`.

### Spring AI 2.0 will silently ignore the current `application.yml`

This is the highest-consequence finding, and it matches the "fails quietly" edge case in `requirements.md`.

Spring AI 2.0 decoupled options from configuration properties and **removed the artificial `.options` segment**
from property keys. The current config is:

```yaml
spring.ai.google.genai.chat.options.model: gemini-2.5-flash   # 1.1.x
spring.ai.google.genai.chat.model: gemini-2.5-flash           # 2.0.x
```

An unrecognised property is not an error, so extraction would fall back to the SDK's default model with no
log line pointing at the cause. The commented-out `thinking-level` key needs the same treatment.

Good news on artifacts: `spring-ai-starter-model-google-genai`, `spring-ai-pdf-document-reader`,
`spring-ai-spring-boot-docker-compose`, and `spring-ai-spring-boot-testcontainers` **all still exist** under
the same names in `spring-ai-bom` 2.0.0 — verified by reading the BOM directly. No artifact renames apply to
this project (the renames in the upgrade notes are for advisors, MCP, Azure/OCI modules — none used here).

The API surface used by `ExtractionService` (`ChatClient.Builder`, `PromptTemplate`, `Prompt`, `UserMessage`
builder, `Media`, `.call().entity(Class)`) is not in the removed/renamed lists. Two things to watch:

1. **`BeanOutputConverter` now delegates to `JsonSchemaGenerator`**, so the JSON schema sent to Gemini for
   `ExtractedRecipe` changes shape (OpenAPI-style `format` hints added, `@JsonProperty(required=false)`
   honoured). This is exactly the quality-degradation-without-exception path — it justifies the manual
   `ExtractionIntegrationTest` run in the acceptance criteria.
2. **Spring Boot alignment.** Spring AI 2.0.0's starter POM declares `spring-boot-starter:4.1.0` directly.
   Issue #6465 reports that 2.0.0 starters pull Boot 4.1.0-level transitives even though the docs claim 4.0.x
   support, breaking Maven upper-bound enforcement on 4.0.7. Targeting **Boot 4.1.0** sidesteps this entirely.

### Spring Boot 4's modularization changes the pom

Boot 4 split the monolithic `spring-boot-autoconfigure` jar into per-technology modules. Two consequences for
this pom:

- **Flyway auto-configuration now requires `spring-boot-starter-flyway`.** Having `flyway-core` on the
  classpath is no longer enough — migrations silently do not run on startup. Given `ddl-auto: validate`, the
  actual failure mode is a schema-validation error at boot rather than silent data loss, but the fix is to add
  the starter. (`flyway-database-postgresql` is still needed for the Postgres dialect.)
- **Starter renames.** `spring-boot-starter-web` → `spring-boot-starter-webmvc`, and
  `spring-boot-starter-oauth2-resource-server` → `spring-boot-starter-security-oauth2-resource-server`. Both
  old names are still published in the 4.1.0 BOM as deprecated aliases, so the build won't break if they're
  left alone — but the requirements ask for currency. `spring-security-test` has an equivalent
  `spring-boot-starter-security-test`.

Unchanged and still present: `spring-boot-starter-data-jpa`, `-validation`, `-actuator`, `-security`,
`-test`, `spring-boot-devtools`, `spring-boot-docker-compose`, `spring-boot-configuration-processor`,
`spring-boot-testcontainers`.

### Jackson 3 touches six files, but the API this code uses survives

Jackson 3 moved groupId and packages from `com.fasterxml.jackson` to `tools.jackson`, **except
`jackson-annotations`, which deliberately keeps `com.fasterxml.jackson.annotation`** for backward
compatibility. Applied to this codebase:

| File | Import | Action |
|---|---|---|
| `recipes/Recipe.java` | `com.fasterxml.jackson.databind.JsonNode` | → `tools.jackson.databind.JsonNode` |
| `recipes/RecipeService.java` | `JsonNode`, `ObjectMapper` | → `tools.jackson.databind.*` |
| `recipes/RecipeFacade.java` | `JsonNode`, `ObjectMapper` | → `tools.jackson.databind.*` |
| `recipes/images/ImageMetadata.java` | `com.fasterxml.jackson.annotation.JsonUnwrapped` | **no change** |

Verified against `jackson-databind` 3.1.4 by decompiling the jar:

- `tools.jackson.databind.json.JsonMapper extends tools.jackson.databind.ObjectMapper`. Spring Boot 4
  auto-configures a `JsonMapper` bean, so `RecipeService`'s `private final ObjectMapper objectMapper`
  constructor injection **keeps working** once the import is changed.
- `treeToValue(JsonNode, JavaType)`, `getTypeFactory()`, and `valueToTree(Object)` — every method
  `RecipeService` and `RecipeFacade` call — all still exist with the same signatures.
- Jackson 3 exceptions are unchecked (`tools.jackson.core.JacksonException`). Both call sites already wrap in
  `catch (Exception e)`, so there's no unreachable-catch compile error.

**Default behaviour changes that affect the JSON on the wire** (relevant to the "no API contract changes"
anti-requirement):

- `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` is now **on** by default → response field *order* changes.
  Harmless for the Flutter client (`dart:convert` is order-insensitive), but it will churn any assertion that
  compares raw JSON strings.
- `WRITE_DATES_AS_TIMESTAMPS` is now `false` by default → ISO-8601 date output. Spring Boot 3 already forced
  this, so `Instant createdAt` fields keep their current representation. **No change on the wire.**
- `FAIL_ON_UNKNOWN_PROPERTIES` — Spring Boot 3 explicitly disabled it. Under Boot 4 it should stay disabled,
  but note issue #49951: setting the migration shim `spring.jackson.use-jackson2-defaults=true` perversely
  *enables* it. Don't reach for that shim.
- `spring.jackson.default-property-inclusion: non_null` is **still a valid property** in Boot 4.1
  (`JacksonProperties` exposes it). Keep it — the mobile client relies on null fields being omitted. Caveat:
  issue #48343 reports it isn't applied to *content* inclusion (values inside collections/maps).
- `spring.jackson.read.*` / `write.*` moved under `spring.jackson.json.read.*` / `.write.*`. Not used here.

### Testcontainers 2.0 relocates the Postgres container

Boot 4.1 manages Testcontainers **2.0.5**. Verified against the published jars:

- Artifacts are now prefixed: `org.testcontainers:postgresql` → **`org.testcontainers:testcontainers-postgresql`**,
  `org.testcontainers:junit-jupiter` → **`org.testcontainers:testcontainers-junit-jupiter`**. The old
  coordinates are frozen at 1.21.4.
- Container classes moved to per-module packages: `org.testcontainers.containers.PostgreSQLContainer` →
  **`org.testcontainers.postgresql.PostgreSQLContainer`**. The 2.0.5 jar still ships the old
  `org.testcontainers.containers.PostgreSQLContainer` as a deprecated class, so this migration is soft.
- **The new class is not generic.** `public class PostgreSQLContainer extends JdbcDatabaseContainer<PostgreSQLContainer>`
  — so `TestcontainersConfiguration.java`'s `PostgreSQLContainer<?>` becomes `PostgreSQLContainer`.
- `@ServiceConnection` stays at `org.springframework.boot.testcontainers.service.connection.ServiceConnection`
  — confirmed present in `spring-boot-testcontainers` 4.1.0.
- Testcontainers 2 also drops its JUnit 4 dependency. Not used here.

### Spring Security 7 — low risk for this configuration

`AntPathRequestMatcher` and `MvcRequestMatcher` are **gone**; `PathPatternRequestMatcher` is the only
strategy. Verified by inspecting `spring-security-web`/`-config` 7.1.0: zero `MvcRequestMatcher` classes, only
`PathPatternRequestMatcher`.

Despite that, `SecurityConfig.java` should compile and behave unchanged. Verified via `javap` on
`AbstractRequestMatcherRegistry` in 7.1.0 — both `requestMatchers(String...)` and
`dispatcherTypeMatchers(DispatcherType...)` are still there, and `oauth2ResourceServer().jwt()` plus the
`spring.security.oauth2.resourceserver.jwt.issuer-uri` property are unchanged.

Two things to be aware of:

- **Trailing-slash matching is gone.** A rule for `/recipes` no longer matches `/recipes/`. Every pattern in
  this config is a `/**` prefix pattern, so it's unaffected — but any endpoint the mobile app calls with a
  trailing slash would now hit `.anyRequest().denyAll()`.
- If the app used the legacy Access API (`AccessDecisionManager`/`AccessDecisionVoter`), a new
  `spring-security-access` dependency would be needed. It doesn't.

### Hibernate 7 — also lower risk than expected

Hibernate 7.4.1 under Spring Data 2026.0.0. The general Hibernate 7 breaking changes (removal of
`Session.save/update/saveOrUpdate/delete`, `get`/`load` → `find`/`getReference`) don't apply: every repository
in this project is a plain `JpaRepository<T, ID>` interface with no `Session` usage.

The JSONB mapping is the one to verify. `Recipe.data` is a `JsonNode` annotated
`@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")`, and Hibernate serialises that through a
`FormatMapper`. Confirmed by inspecting `hibernate-core` 7.4.1.Final: it ships **both**
`org.hibernate.type.format.jackson.JacksonJsonFormatMapper` (Jackson 2) and
`org.hibernate.type.format.jackson.Jackson3JsonFormatMapper` (Jackson 3). So a `tools.jackson` `JsonNode`
field is supported — but *which* mapper Hibernate auto-selects when both Jackson lines are on the classpath
(Boot 4.1 manages both) was not determined. This is the concrete thing to check with a round-trip test.

Other Hibernate 7 notes: `hibernate-jpamodelgen` was renamed to `hibernate-processor` (not used here), and
temporal-type/column alignment got stricter. The entities use `Instant createdAt` against Flyway-managed
columns with `ddl-auto: validate`, so any mismatch surfaces loudly at startup rather than silently.

### Spring Framework 7 general notes

- The codebase is annotated with **JSpecify** instead of JSR-305; Spring's own nullness annotations are
  deprecated. Java-only projects feel almost nothing here (this bites Kotlin, where K2 turns previously
  suppressed warnings into compile errors).
- `HttpHeaders` no longer extends `MultiValueMap`. Not used directly in this backend.
- `spring-retry` was absorbed into a core Framework retry API. Not used.
- `RestClient` (used throughout the integration tests) is unchanged.

### Test infrastructure

Boot 4 stopped having `@SpringBootTest` auto-provide `MockMvc`, `WebTestClient`, and `TestRestTemplate` beans
(you now add `@AutoConfigureMockMvc` / `@AutoConfigureTestRestTemplate`), and `@MockBean`/`@SpyBean` were
replaced by `@MockitoBean`/`@MockitoSpyBean`. **Neither affects this suite** — the integration tests build a
bare `RestClient` by hand against `@LocalServerPort` and use no mock-bean annotations. That's a lucky break
that removes most of the usual test-migration cost.

## Concrete repository impact

| File | Change required |
|---|---|
| `backend/pom.xml` | Parent → 4.1.0; `java.version` → 26; `spring-ai.version` → 2.0.0; `starter-web` → `starter-webmvc`; `starter-oauth2-resource-server` → `starter-security-oauth2-resource-server`; **add `spring-boot-starter-flyway`**; testcontainers artifacts → `testcontainers-postgresql` / `testcontainers-junit-jupiter`; s3 → 2.52.0; commons-io → 2.22.0; thumbnailator unchanged |
| `src/main/resources/application.yml` | `spring.ai.google.genai.chat.options.model` → `spring.ai.google.genai.chat.model` (and the commented `thinking-level`) |
| `recipes/Recipe.java`, `RecipeService.java`, `RecipeFacade.java` | `com.fasterxml.jackson.databind.*` → `tools.jackson.databind.*` |
| `recipes/images/ImageMetadata.java` | none — `jackson-annotations` package is unchanged |
| `TestcontainersConfiguration.java` | Import → `org.testcontainers.postgresql.PostgreSQLContainer`; drop the `<?>` type argument |
| `config/security/SecurityConfig.java` | Expected to compile and behave unchanged |
| `config/s3/*`, `recipes/images/S3Service.java` | AWS SDK 2.40 → 2.52 is a minor bump within 2.x; no expected change |
| `backend/Dockerfile` | `eclipse-temurin:25-jdk-alpine` → `26-jdk-alpine`, `25-jre-alpine` → `26-jre-alpine` |
| `backend/compose.yaml` | `postgres:latest` → `postgres:17.5` |
| `.github/workflows/docker-build-api.yml` | checkout v5→v6, metadata-action v5→v6, build-push-action v6→v7 |

One incidental note on the Dockerfile: Boot 4.1 changed Maven behaviour so that **`-DskipTests` no longer
skips AOT processing** (`maven.test.skip` does). The builder stage runs `mvn package -DskipTests`. In
practice AOT processing only runs when `process-aot` is bound, which this pom does not do, so no change is
expected — but it's worth watching if the image build slows down or fails.

## Open questions / gaps

- **GitHub Action major versions** (checkout v6, metadata-action v6, build-push-action v7) come from
  secondary sources, not from the actions' own release pages. Confirm against each repository's releases
  before editing the workflow — this is the least-verified item in the report.
- **Which Hibernate `FormatMapper` wins** when both Jackson 2 and Jackson 3 are on the classpath (Boot 4.1
  manages both). Determines whether `Recipe.data` round-trips correctly without extra configuration. Needs an
  empirical check, not a doc lookup.
- **Whether Spring AI 2.0's `JsonSchemaGenerator`-based `BeanOutputConverter` degrades extraction quality**
  for `ExtractedRecipe`. Unknowable from docs — this is what the manual `ExtractionIntegrationTest` run is
  for.
- **Spring AI issue #6465 resolution.** The issue is closed but the fix version wasn't visible in the fetched
  page. Moot if targeting Boot 4.1.0, which is what Spring AI 2.0.0 actually builds against.
- **`spring-boot-starter-test-classic`** exists alongside `spring-boot-starter-test` in the 4.1 BOM; its
  purpose (which "classic" modules it restores) wasn't investigated. The plain starter still carries AssertJ,
  JUnit Jupiter, Mockito, JSONassert and Awaitility, which is everything this suite uses.
- **No Spring Boot 4.1.1** existed on Maven Central at the time of writing (2026-08-12). Re-check before
  pinning, since the requirement is "latest GA".
- `TestcontainersConfiguration` still uses the floating `postgres:latest` tag. Explicitly out of scope per
  `requirements.md`, but it means a test failure during this upgrade may have nothing to do with the upgrade.

## Sources

### Primary — official

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) — starter renames, Jackson 3 transition, test annotation changes, actuator/probe defaults.
- [Spring Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes) — removal of 4.0 deprecations, the `-DskipTests`/AOT change, new Jackson read/write auto-configuration.
- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html) — Java 17–26 range, Spring Framework 7.0.8 floor, Tomcat 11 / Servlet 6.1.
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes) — Hibernate 7.1 and Testcontainers 2.0 dependency upgrades.
- [JacksonProperties (Spring Boot 4.1.0 API)](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/jackson/autoconfigure/JacksonProperties.html) — confirms `default-property-inclusion` survives and the `json.read`/`json.write` nesting.
- [Introducing Jackson 3 support in Spring](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/) — `tools.jackson` groupId, the `jackson-annotations` exception, `JsonMapper` auto-configuration, alphabetical property sorting default.
- [Spring AI 2.0.0 GA Available Now](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/) — Boot 4.0/4.1 + Framework 7.0 baseline, options immutability, Google GenAI consolidation to a single SDK.
- [Spring AI Upgrade Notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) — the `.options` property-key removal, `BeanOutputConverter`/`JsonSchemaGenerator` change, Google GenAI package moves, module renames.
- [Migrating to Spring Security 7.0](https://docs.spring.io/spring-security/reference/7.0/migration/index.html) and its [servlet](https://docs.spring.io/spring-security/reference/7.0/migration/servlet/index.html) / [authorization](https://docs.spring.io/spring-security/reference/7.0/migration/servlet/authorization.html) sections — 6.5-first migration path, Jackson 3 for security serialization, `spring-security-access` module split.
- [Hibernate ORM 7.0 Migration Guide](https://docs.hibernate.org/orm/7.0/migration-guide/) — legacy `Session` API removal, Jakarta EE 11 alignment.
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes) and [Null-safety](https://docs.spring.io/spring-framework/reference/7.0-SNAPSHOT/core/null-safety.html) — JSpecify migration.
- [Lombok issue #4019 — Support for Java 26](https://github.com/projectlombok/lombok/issues/4019) and the [Lombok changelog](https://projectlombok.org/changelog) — closed against milestone 1.18.46, which adds JDK 26 support.
- [Spring AI issue #6465](https://github.com/spring-projects/spring-ai/issues/6465) — 2.0.0 starters align to Boot 4.1.0 despite documented 4.0.x support.
- [Modularizing Spring Boot](https://spring.io/blog/2025/10/28/modularizing-spring-boot/) — rationale for the per-technology module split that makes `spring-boot-starter-flyway` necessary.

### Primary — artifacts inspected directly

Read from `repo1.maven.org` on 2026-08-12; these are the basis for every version number and package/class
claim above:

- `spring-boot-starter-parent` / `spring-boot-dependencies` 4.1.0 POMs — managed versions and the full list of
  published starter artifact names.
- `spring-ai-bom` 2.0.0 and `spring-ai-starter-model-google-genai` 2.0.0 POMs — surviving artifact names and
  the `spring-boot-starter:4.1.0` dependency.
- `jackson-databind` 3.1.4 jar (`javap`) — `JsonMapper extends ObjectMapper`, `treeToValue`/`getTypeFactory`/
  `valueToTree` signatures, unchecked `JacksonException`.
- `hibernate-core` 7.4.1.Final jar — presence of both `JacksonJsonFormatMapper` and `Jackson3JsonFormatMapper`.
- `spring-security-config` / `spring-security-web` 7.1.0 jars (`javap`) — `dispatcherTypeMatchers` and
  `requestMatchers(String...)` still present; `MvcRequestMatcher`/`AntPathRequestMatcher` absent.
- `testcontainers-postgresql` 2.0.5 jar and `testcontainers-bom` 2.0.5 — new package, non-generic container
  class, `testcontainers-` artifact prefix.
- `spring-boot-testcontainers` 4.1.0 jar — `@ServiceConnection` package unchanged.
- `maven-metadata.xml` for `spring-boot-starter-parent`, `spring-ai-bom`, `lombok`, `awssdk:s3`, `commons-io`,
  `thumbnailator`, `testcontainers` — latest released versions.

### Secondary

- [Spring Boot Versions, EOL Dates, and Latest Releases — HeroDevs](https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026) — cross-check on the 4.1.0 release date.
- [Flyway Migrations in Spring Boot 4.x: What Changed](https://pranavkhodanpur.medium.com/flyway-migrations-in-spring-boot-4-x-what-changed-and-how-to-configure-it-correctly-dbe290fa4d47) and [Add `spring-boot-starter-flyway` — OpenRewrite](https://docs.openrewrite.org/recipes/java/spring/boot4/addspringbootstarterflyway) — confirmation that `flyway-core` alone no longer triggers auto-configuration.
- [TestContainers 2 — an upgrade that's well worth it](https://blog.doubleslash.de/en/software-technologien/coding-and-frameworks/testcontainers-2-an-upgrade-worth-it/) and [Migrate to testcontainers-java 2.x — OpenRewrite](https://docs.openrewrite.org/recipes/java/testing/testcontainers/testcontainers2migration) — artifact prefixing and package relocation.
- [Spring Boot issue #49951](https://github.com/spring-projects/spring-boot/issues/49951) — `use-jackson2-defaults` unexpectedly enabling `FAIL_ON_UNKNOWN_PROPERTIES`.
- [Spring Boot issue #48343](https://github.com/spring-projects/spring-boot/issues/48343) — `default-property-inclusion` not applied to content inclusion.
- [Jackson 3 in Spring Boot 4 — Dan Vega](https://www.danvega.dev/blog/jackson-3-spring-boot-4) and [Upgrading to Jackson 3 with Spring Boot 4 — Dimitri](https://dimitri.codes/jsonmapper/) — Jackson 3 default shifts.
- [Migration to Spring Security 7.0 — DeepWiki](https://deepwiki.com/spring-projects/spring-security/9-migration-to-spring-security-7.0) and [Spring Security 5→6→7 migration](https://ankurm.com/spring-security-5-to-6-to-7-migration-guide/) — request-matcher consolidation and trailing-slash behaviour.
- [docker/build-push-action](https://github.com/docker/build-push-action) and [docker/metadata-action](https://github.com/docker/metadata-action) — Action versions (flagged as needing confirmation).
