# T1: Limits module foundation and the AI extraction budget — Implementation Plan

**Date:** 2026-08-17

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/backend/standards/module-structure.md` — the public-facade rule `LimitsFacade` follows, the
  one-`@ControllerAdvice`-per-module rule, `jwt.getClaimAsString("email")` as the identity source, and
  the `log.warn` convention for business-rule violations.
- `docs/backend/standards/java-patterns.md` — records for DTOs, entity structure, and the
  package-private-unless-crossing-a-boundary visibility rule the ArchUnit test enforces.
- `docs/backend/standards/integration-tests.md` — `@SpringBootTest(RANDOM_PORT)` +
  `@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})`, `RestClient` over
  MockMvc, `@AfterEach` cleanup, AssertJ, `shouldXxxWhenYyy` naming.
- `docs/backend/standards/configuration-profiles.md` — no new config keys are added, but confirms
  `prod` is the default active profile, which matters for how the new tests boot.

**Design & ADRs**

- `plans/T1-task-design.md` > *Interfaces and method signatures*, *Schema*, *Pseudo-code* — the
  literal contracts to implement; the reserve statement is reproduced verbatim below.
- `plans/T1-task-design.md` > *Decisions made* — settled; do not re-open (email as subject, opaque
  string resource key, `Instant.EPOCH` as the no-period cutoff, `REQUIRED` propagation).
- `HLD.md` > Feature areas > *Limits module (new)*, *Extraction*, *Rejection contract*.
- `docs/ADRs/0006-shared-limits-module.md` > *Decision*, *Consequences* — the domain-freedom
  obligation the architecture test holds, and the reserve-before-the-work ordering rule.

**Code to mirror**

- `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeFacade.java` — public facade over
  package-private internals; `@Service @RequiredArgsConstructor @Slf4j`.
- `backend/src/main/java/xyz/stasiak/recipai/planning/PlanningExceptionHandler.java` — the
  `ProblemDetail.forStatusAndDetail(...)` + `setTitle(...)` handler shape.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/images/exception/RecipeImagesExceptionHandler.java`
  — `@RestControllerAdvice` + `@Slf4j` with logging inside the handler (the closer of the two
  handlers to what `LimitsExceptionHandler` needs).
- `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanPermissionId.java` — `@Embeddable`
  record composite id; `LimitUsageId` copies it exactly.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipePermission.java` — `@EmbeddedId` entity
  with `@Enumerated(EnumType.STRING)` and explicit `equals`/`hashCode`.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListItem.java` — entity field
  and `@Column` conventions.
- `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanService.java:50-68` — the
  count-then-check this task's mechanism replaces (migrated in T3, untouched here).
- `backend/src/main/resources/db/migration/V11__meal_planning_schema.sql` — migration style:
  unqualified table names, `TIMESTAMP`, inline `CHECK (x IN (...))`, indexes last.
- `backend/src/test/java/xyz/stasiak/recipai/TestSecurityConfiguration.java` — the `@TestConfiguration`
  + `@Bean @Primary` + `Mockito.mock` shape `TestAiConfiguration` copies, and the three test tokens.
- `backend/src/test/java/xyz/stasiak/recipai/planning/MealPlanIntegrationTest.java:341-354` — the
  existing limit-enforcement test, closest sibling for the extraction limit cases.
- `backend/pom.xml:142-156` — third-party dependencies carry an inline `<version>`; ArchUnit follows.

## File inventory

**Limits module** — `backend/src/main/java/xyz/stasiak/recipai/limits/`

- **CREATE** `LimitsFacade.java` — public `reserve(subject, resource)`, the module's only entry point
- **CREATE** `LimitService.java` — resolves config, derives cutoff, calls reserve, throws the refusal
- **CREATE** `LimitConfig.java` — entity over `limit_config`, nullable subject
- **CREATE** `LimitConfigRepository.java` — override-then-default resolution query
- **CREATE** `LimitUsage.java` — read-only entity over `limit_usage`, used for the standing
- **CREATE** `LimitUsageId.java` — `@Embeddable` record `(resource, subject)`
- **CREATE** `LimitUsageRepository.java` — the native conditional upsert, `@Modifying`
- **CREATE** `LimitKind.java` — public enum `STOCK` / `FLOW`
- **CREATE** `LimitPeriod.java` — package-private enum, owns all period arithmetic
- **CREATE** `LimitExceededException.java` — public refusal carrying the standing
- **CREATE** `LimitConfigurationMissingException.java` — public, fails closed
- **CREATE** `LimitsExceptionHandler.java` — `@RestControllerAdvice`, 429 and 500
- **CREATE** `LimitsProperties.java` / `LimitsModuleConfig.java` — bind and enable `recipai.limits.*`
- **CREATE** `config/time/TimeConfig.java` — supplies the shared `Clock` bean (outside the module)

**Migration**

- **CREATE** `backend/src/main/resources/db/migration/V15__limits_schema.sql` — both tables,
  constraints, seeded `EXTRACTION` default

**Extraction** — `backend/src/main/java/xyz/stasiak/recipai/extraction/`

- **MODIFY** `ExtractionController.java` — `Jwt` parameter on both endpoints, typed MIME exception
- **MODIFY** `ExtractionService.java` — `LimitsFacade` dependency, resource constant, reserve first
- **CREATE** `UnsupportedImageTypeException.java` — replaces the bare `IllegalArgumentException`
- **CREATE** `ExtractionFailedException.java` — replaces the bare `IllegalStateException`
- **CREATE** `ExtractionExceptionHandler.java` — the module's first exception handling

**Build**

- **MODIFY** `backend/pom.xml` — adds `com.tngtech.archunit:archunit-junit5:1.5.0` at test scope

**Tests** — `backend/src/test/java/xyz/stasiak/recipai/`

- **CREATE** `limits/LimitPeriodTest.java` — unit test for period arithmetic (same package, the enum
  is package-private)
- **CREATE** `limits/LimitsIntegrationTest.java` — the module's semantics against real Postgres
- **CREATE** `limits/LimitsModuleArchitectureTest.java` — holds `limits` free of domain knowledge
- **CREATE** `TestAiConfiguration.java` — `@Primary` mock `ChatClient`
- **MODIFY** `extraction/ExtractionIntegrationTest.java` — the 429 asserted over HTTP; the class-level
  `@Disabled` is lifted and only the real-provider test keeps a method-level one

No other module changes: `LimitsFacade` is new, nothing else compiles against it yet.

## Step-by-step plan

### 1. Migration and build dependency

Add `V15__limits_schema.sql` exactly as specified in `T1-task-design.md` > *Schema* — two tables, the
two `CHECK` constraints, the `UNIQUE NULLS NOT DISTINCT` constraint, and the seeded `EXTRACTION`
default (`FLOW`, `max_value` 2, `period` NULL). Write table names unqualified, as every other
migration does; Flyway's `default-schema: recipai` places them. Add the ArchUnit dependency to
`pom.xml` next to the other explicitly-versioned third-party entries.

- Files: `backend/src/main/resources/db/migration/V15__limits_schema.sql`, `backend/pom.xml`
- Verify: `./mvnw test -Dtest=RecipAiApplicationTests` — Flyway applies V15 against a fresh
  Testcontainers Postgres and the context loads.

### 2. Limits persistence and value types

Create `LimitKind`, `LimitPeriod`, `LimitConfig`, `LimitConfigRepository`, `LimitUsage`,
`LimitUsageId`, `LimitUsageRepository`, both exceptions, `LimitsProperties` and `LimitsModuleConfig`,
plus `config/time/TimeConfig` for the shared `Clock`. Everything except
`LimitKind`, `LimitExceededException` and `LimitConfigurationMissingException` is package-private.

`LimitPeriod` holds all calendar arithmetic and nothing else:

```java
enum LimitPeriod {
    DAY, WEEK, MONTH;

    Instant cutoffFrom(Instant now);          // DAY/WEEK: fixed Duration. MONTH: Period at UTC.
    Instant nextStart(Instant periodStart);
}
```

`LimitUsageRepository.reserve` is the native statement from `T1-task-design.md` > *Pseudo-code*,
annotated `@Modifying` and returning `int`. **Qualify the table with Hibernate's `{h-schema}`
placeholder** — see *Risks* > *Native SQL and the `recipai` schema*:

```java
@Modifying
@Query(value = """
        INSERT INTO {h-schema}limit_usage (resource, subject, used, period_start)
        VALUES (:resource, :subject, 1, :now)
        ON CONFLICT (resource, subject) DO UPDATE SET
            used         = CASE WHEN limit_usage.period_start <= :cutoff THEN 1    ELSE limit_usage.used + 1     END,
            period_start = CASE WHEN limit_usage.period_start <= :cutoff THEN :now ELSE limit_usage.period_start END
        WHERE limit_usage.period_start <= :cutoff
           OR limit_usage.used < :max
        """, nativeQuery = true)
int reserve(@Param("resource") String resource, @Param("subject") String subject,
            @Param("now") Instant now, @Param("cutoff") Instant cutoff, @Param("max") int max);
```

`LimitConfigRepository.resolve` is the HQL from the design, ordering the override ahead of the
default. Map `kind` and `period` with `@Enumerated(EnumType.STRING)`; `period` is nullable. Write
`LimitPeriodTest` alongside.

- Files: all of `backend/src/main/java/xyz/stasiak/recipai/limits/` except `LimitService`,
  `LimitsFacade` and `LimitsExceptionHandler`; `backend/src/test/java/xyz/stasiak/recipai/limits/LimitPeriodTest.java`
- Verify: `./mvnw test -Dtest=LimitPeriodTest` passes, and
  `./mvnw test -Dtest=RecipAiApplicationTests` still passes — `ddl-auto: validate` now checks the two
  new entities against V15, so a mapping/column mismatch fails here.

### 3. Reserve logic, facade and refusal contract

Implement `LimitService.reserve` per the design's pseudo-code: resolve or throw
`LimitConfigurationMissingException` (logged at ERROR), take `now` from the injected `Clock`, derive
`cutoff` (`Instant.EPOCH` when `period` is null), call the upsert, and on a zero-row result read
`LimitUsage` for the standing, compute `retryAfterSeconds` only for `FLOW` with a period, `log.warn`
and throw `LimitExceededException`. Annotate the method `@Transactional`
(`org.springframework.transaction.annotation.Transactional` — see *Risks*). `LimitsFacade` is a thin
public delegate.

`LimitsExceptionHandler` maps `LimitExceededException` to 429 and
`LimitConfigurationMissingException` to 500. It returns `ResponseEntity<ProblemDetail>` so it can set
`Retry-After`. **Set the `retryAfterSeconds` extension property only when it is non-null** — omit the
`setProperty` call entirely rather than passing null.

- Files: `limits/LimitService.java`, `limits/LimitsFacade.java`, `limits/LimitsExceptionHandler.java`,
  `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsIntegrationTest.java`
- Verify: `./mvnw test -Dtest=LimitsIntegrationTest` — all cases in the *Test plan* below pass,
  including the concurrent-reserve case.

### 4. Extraction identity, reservation and exception handling

`ExtractionController` gains a `Jwt jwt` parameter on both endpoints, reads
`jwt.getClaimAsString("email")` and passes it down; its MIME check throws
`UnsupportedImageTypeException`. `ExtractionService` gains the `LimitsFacade` dependency and
`static final String EXTRACTION_RESOURCE = "EXTRACTION"`, and calls
`limitsFacade.reserve(userEmail, EXTRACTION_RESOURCE)` as the **first statement** of both methods —
before the prompt is constructed, so no path reaches `ChatClient` unreserved. Both null-result checks
throw `ExtractionFailedException`. `ExtractionExceptionHandler` (`@RestControllerAdvice`, flat in the
module package) maps `UnsupportedImageTypeException` → 400 and `ExtractionFailedException` → 500,
both as `ProblemDetail`.

Do not add `@Transactional` anywhere in `extraction`: the reservation must commit before the AI call.

- Files: `extraction/ExtractionController.java`, `extraction/ExtractionService.java`,
  `extraction/UnsupportedImageTypeException.java`, `extraction/ExtractionFailedException.java`,
  `extraction/ExtractionExceptionHandler.java`
- Verify: `./mvnw -q compile` succeeds and `./mvnw test -Dtest=RecipAiApplicationTests` still passes.

### 5. HTTP-level tests with the AI call mocked

`TestAiConfiguration` supplies a `@Bean @Primary ChatClient` built with
`Mockito.mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS)` — a plain mock returns null from
`prompt(...)` and NPEs. Expose the mock so tests can restub it per case (a plain field/getter on the
configuration is enough). `ExtractionIntegrationTest` imports it alongside
`TestcontainersConfiguration` and `TestSecurityConfiguration`, and pins
`spring.ai.google.genai.api-key` and `recipai.limits.enabled=true` via
`@SpringBootTest(properties = ...)` — the `dev` profile turns limits off, so every limit test must opt
back in explicitly.

- Files: `backend/src/test/java/xyz/stasiak/recipai/TestAiConfiguration.java`,
  `backend/src/test/java/xyz/stasiak/recipai/extraction/ExtractionIntegrationTest.java`
- Verify: `./mvnw test -Dtest=ExtractionIntegrationTest` — the 429 body, the absent
  `Retry-After`, the runtime limit change and the no-refund-on-failure cases all pass.

### 6. Architecture test

`LimitsModuleArchitectureTest` uses `@AnalyzeClasses(packages = "xyz.stasiak.recipai")` with two
`@ArchTest` rules (both listed under *Test plan*).

- Files: `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsModuleArchitectureTest.java`
- Verify: `./mvnw test -Dtest=LimitsModuleArchitectureTest` passes, and deliberately adding an import
  of `xyz.stasiak.recipai.recipes.Recipe` to `LimitService` makes it fail.

## Test plan

**Unit tests**

- `LimitPeriodTest`
  - `cutoffFrom` subtracts exactly 24 hours for `DAY`
  - `cutoffFrom` subtracts exactly 7 days for `WEEK`
  - `cutoffFrom` subtracts a calendar month for `MONTH`, not 30 days (2026-03-31 → 2026-02-28)
  - `nextStart` adds exactly 24 hours for `DAY` and 7 days for `WEEK`
  - `nextStart` adds a calendar month for `MONTH`, clamping the day (2026-01-31 → 2026-02-28)
  - `MONTH` arithmetic is performed at UTC and is unaffected by the JVM default zone
  - `cutoffFrom(now)` is always strictly before `now`, and `nextStart(t)` always strictly after `t`

**Integration tests**

- `LimitsIntegrationTest` (`@SpringBootTest` + `TestcontainersConfiguration`, `LimitsFacade`
  autowired, `JdbcClient` used to seed `limit_config` / `limit_usage` rows directly and to assert
  stored state; `@AfterEach` deletes the test rows from both tables)
  - grants and inserts a usage row of 1 on a subject's first reserve
  - increments `used` on each subsequent grant while under the limit
  - refuses once `used` equals the configured maximum, and `used` does not advance past it
  - a subject override wins over the resource default, both when it is lower and when it is higher
  - a subject with no override falls back to the resource default
  - raising `max_value` with SQL after a refusal admits the next reserve with no restart
  - a `STOCK` configuration never restarts: a usage row at the cap with a year-old `period_start`
    still refuses
  - a `FLOW` configuration with no period ("N ever") never restarts under the same seeding
  - a `FLOW` `DAY` configuration restarts lazily: a usage row at the cap with `period_start` two days
    old is granted, and afterwards `used` is 1 and `period_start` has advanced to now
  - a `FLOW` `DAY` configuration does not restart when `period_start` is inside the window
  - the refusal carries the resource, kind, configured limit and current `used`
  - a `FLOW`-with-period refusal carries a positive `retryAfterSeconds`; a `STOCK` refusal and a
    no-period `FLOW` refusal carry null
  - throws `LimitConfigurationMissingException` when no default and no override exist
  - two subjects on the same resource are independent
  - one subject on two resources is independent
  - **concurrency:** 16 threads reserving simultaneously for one subject configured at 5 yield
    exactly 5 grants and 11 refusals, and the stored `used` is exactly 5

- `ExtractionIntegrationTest` (`@SpringBootTest(RANDOM_PORT, properties =
  {"spring.ai.google.genai.api-key=test-key", "recipai.limits.enabled=true"})`,
  `@Import({TestcontainersConfiguration,
  TestSecurityConfiguration, TestAiConfiguration})`, `RestClient`, `@AfterEach` clears `limit_usage`
  and restores the seeded `EXTRACTION` config)
  - `POST /extract/text` returns 200 and the mocked recipe while budget remains
  - the third call at the seeded limit of 2 returns 429 with `Content-Type:
    application/problem+json` and a body carrying `resource=EXTRACTION`, `kind=FLOW`, `limit=2`,
    `used=2`
  - that 429 carries no `Retry-After` header and no `retryAfterSeconds` key in the body
  - raising `limit_config.max_value` to 5 with SQL mid-test admits the next call with no restart
  - a `ChatClient` that throws still consumes budget: the next call is refused, and `used` advanced
  - a `ChatClient` returning null yields 500 `Extraction Failed` and still consumed the unit
  - `POST /extract/image` with `text/plain` returns 400 `Unsupported Image Type` and leaves `used`
    unchanged
  - `POST /extract/image` with a JPEG from `recipe_sources/` consumes one unit and returns 200
  - `AUTH_TOKEN_USER_1` and `AUTH_TOKEN_USER_2` have independent budgets
  - a request with no `Authorization` header is still 401 (identity plumbing did not widen access)

- `LimitsModuleArchitectureTest` (ArchUnit)
  - no class in `..limits..` depends on any class in `xyz.stasiak.recipai..` outside `..limits..`
  - the only public types in `..limits..` are `LimitsFacade`, `LimitExceededException`,
    `LimitConfigurationMissingException` and `LimitKind`

**Flutter widget/integration tests**

_N/A — T1 is backend-only; all mobile work is T3 and T5._

**Manual verification**

- The full `tasks.md` > T1 > *How to verify* walkthrough against a locally running app: two
  successful `curl -X POST .../extract/text`, a third returning 429, an `UPDATE limit_config SET
  max_value = 5` making the next call succeed with no restart, and an extraction whose AI call fails
  still advancing the standing.
- Confirm the deployed Postgres major version is 15 or newer (`SELECT version()`), for
  `UNIQUE NULLS NOT DISTINCT`.

## Verification checklist

- [ ] `./mvnw test` — all new and existing tests pass (only `ExtractionIntegrationTest`'s
      real-provider test stays `@Disabled`)
- [ ] `./mvnw -q compile` produces no new warnings
- [ ] V15 applies cleanly against a fresh database, and `ddl-auto: validate` accepts both new entities
- [ ] The native reserve statement resolves the `recipai` schema in **both** the Testcontainers
      environment and a `currentSchema`-less local database
- [ ] `tasks.md` > T1 > *How to verify* succeeds end-to-end with curl
- [ ] The 429 body matches `T1-task-design.md` > *Refusal contract* field for field, and
      `retryAfterSeconds` is **absent**, not null, for the seeded configuration
- [ ] Deliberately breaking the ArchUnit rule fails the build
- [ ] `limit_usage` is never written through JPA — grep the module for `LimitUsageRepository.save`
- [ ] No `@Transactional` was added anywhere in `extraction`
- [ ] Logs at `INFO` are clean on the happy path; refusals appear at `WARN`, missing configuration at
      `ERROR`
- [ ] The design's *Assumptions to verify* are resolved or explicitly carried forward (see below)

## Risks surfaced during planning

- **Risk:** Native SQL and the `recipai` schema. `spring.jpa.properties.hibernate.default_schema:
  recipai` qualifies *mapped entities only*; a native query resolves unqualified table names through
  the connection's `search_path`. `TestcontainersConfiguration` uses `@ServiceConnection`, which
  produces a JDBC URL with no `currentSchema`, so `search_path` is `"$user", public` and
  `limit_usage` would not be found. This is the codebase's **first** native query — nothing proves
  the setup works today.
  **Why it matters:** the reserve statement is the entire concurrency guarantee; if it cannot even
  resolve its table, step 3 blocks and the failure looks like a Flyway or mapping problem.
  **Mitigation:** use Hibernate's `{h-schema}` placeholder as shown in step 2, and treat the first
  green run of `LimitsIntegrationTest` as the proof. If the placeholder does not expand as expected,
  the fallback is appending `currentSchema=recipai` to the datasource URL in every environment —
  which is a wider change and should be raised before taking it.

- **Risk:** `UNIQUE NULLS NOT DISTINCT` requires Postgres 15+. Testcontainers pins 17.5, but the
  production version is not pinned anywhere in the repo — `SPRING_DATASOURCE_URL` is injected at
  deploy time.
  **Why it matters:** V15 would fail on an older production instance after passing every test.
  **Mitigation:** confirm the deployed major version before merging (in the manual checklist). The
  fallback is a pair of partial unique indexes (`WHERE subject IS NULL` / `WHERE subject IS NOT
  NULL`), which is behaviourally equivalent but does not back an `ON CONFLICT (resource, subject)`
  inference — the upsert would then need `ON CONFLICT ON CONSTRAINT` against `limit_usage`'s primary
  key only, which is unaffected. `limit_usage`'s own key is a plain composite primary key, so the
  reserve statement itself is safe either way.

- **Risk:** the new integration tests inherit an undocumented environment dependency. There is no
  `src/test/resources` configuration and no CI job running backend tests; `prod` is the default
  active profile, and `application.yml` resolves `spring.ai.google.genai.api-key` from
  `${SPRING_AI_API_KEY}`. A `@Primary` mock `ChatClient` does **not** remove that requirement,
  because `ExtractionConfig.chatClient(ChatClient.Builder)` is unconditional and still forces the
  Google GenAI autoconfiguration to resolve the key.
  **Why it matters:** `ExtractionIntegrationTest` is the task's headline verification and must
  be runnable by anyone, not only on a machine with the AI key exported.
  **Mitigation:** `@SpringBootTest(properties = "spring.ai.google.genai.api-key=test-key")` on the
  new test — the inline property source shadows the unresolvable placeholder. Do **not** add
  `src/test/resources/application.yml`; it would shadow the main file's JPA and Flyway configuration
  wholesale.

- **Risk:** `ChatClient` is a fluent chain (`prompt(...).call().entity(...)`), so a plain Mockito
  mock returns null at the first hop.
  **Why it matters:** a silent NPE inside the service is easily mistaken for a limits bug.
  **Mitigation:** `Mockito.RETURNS_DEEP_STUBS`, and assert the granted path returns the stubbed
  recipe before writing any refusal assertions.

- **Risk:** `spring.jackson.default-property-inclusion: non_null` interacts with `ProblemDetail`
  extension properties. Calling `setProperty("retryAfterSeconds", null)` may still emit the key.
  **Why it matters:** the design's contract says the field is *absent*, and T5's client will branch
  on absence.
  **Mitigation:** guard the `setProperty` call; assert absence (not null) in the HTTP test.

- **Risk:** transaction annotation divergence. Four existing services import
  `jakarta.transaction.Transactional`, which has no `propagation` attribute. The design anticipates a
  possible `REQUIRES_NEW` for this call site, and T2 depends on `REQUIRED` semantics being explicit.
  **Why it matters:** switching later means touching the annotation import as well as the attribute.
  **Mitigation:** use `org.springframework.transaction.annotation.Transactional` on `LimitService`
  (already used in `RecipePermissionRepository`, so it is not novel to the codebase) and note the
  deliberate divergence in the PR description.

**Assumptions from `T1-task-design.md`, resolved during planning**

- *"Nothing wraps `ExtractionService` in a transaction"* — **confirmed.** `ExtractionService` and
  `ExtractionController` carry no `@Transactional`, the service is package-private, and
  `ExtractionController` is the only other class in the package. No `REQUIRES_NEW` is needed.
- *"The mobile client tolerates an unrecognised 429 by showing a generic error"* — **confirmed.**
  `mobile/lib/features/extraction/extraction_repository.dart:35,72` treats every non-200 identically
  and throws a generic message; 400, 429 and 500 are indistinguishable to it today.

Still open and carried forward: the HQL `LIMIT` / `NULLS LAST` assumption (fails loudly at first
query, fallback documented in the design), and the `Retry-After`-through-a-proxy assumption (the
value is duplicated in the body regardless).
