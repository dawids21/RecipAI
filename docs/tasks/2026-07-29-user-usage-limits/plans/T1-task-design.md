# T1: Limits module foundation and the AI extraction budget — Task Design

**Date:** 2026-08-17

## Summary

A new `xyz.stasiak.recipai.limits` module stores limit configuration and usage records in two tables,
resolves override-then-default per check with no caching, and performs check-and-reserve as a single
conditional upsert whose affected-row count is the answer. Extraction becomes its first consumer:
both endpoints gain identity, reserve budget immediately before the Gemini call, and refusals surface
as a shared HTTP 429.

## Components and responsibilities

### New — `backend/src/main/java/xyz/stasiak/recipai/limits/`

- **`LimitsFacade`** (CREATE) — the module's only public type reachable from other modules. Takes an
  opaque subject and an opaque resource key, delegates, and lets the refusal propagate.
- **`LimitService`** (CREATE) — resolves configuration, derives the period cutoff, invokes the reserve
  statement, and turns a zero-row result into a refusal carrying the subject's standing.
- **`LimitConfig`** (CREATE) — entity over `limit_config`; a row is either the default for a resource
  (`subject IS NULL`) or one subject's override.
- **`LimitConfigRepository`** (CREATE) — the override-then-default resolution query.
- **`LimitUsage`** / **`LimitUsageId`** (CREATE) — entity over `limit_usage`, keyed by
  `(resource, subject)`. Read only to report a standing on refusal; never written through JPA.
- **`LimitUsageRepository`** (CREATE) — owns the native conditional upsert that *is* the indivisible
  reserve.
- **`LimitKind`** (CREATE, public enum `STOCK` / `FLOW`) — public because it rides on the refusal.
- **`LimitPeriod`** (CREATE, package-private enum `DAY` / `WEEK` / `MONTH`) — owns all period
  arithmetic: the cutoff for a given instant and the next period start for a given start.
- **`LimitExceededException`** (CREATE, public) — the shared refusal, carrying resource, kind, limit,
  used and an optional retry-after.
- **`LimitConfigurationMissingException`** (CREATE, public) — no configuration resolved at all.
- **`LimitsExceptionHandler`** (CREATE) — `@RestControllerAdvice` mapping the two exceptions to 429
  and 500. The one place the refusal contract is expressed.
- **`LimitsProperties`** / **`LimitsModuleConfig`** (CREATE) — bind and enable `recipai.limits.*`,
  which carries the `enabled` kill-switch the facade checks before delegating.
- The `Clock` the service reads time from is supplied by `config.time.TimeConfig`, outside this
  module, so later consumers share one clock bean.

### New — migration

- **`V15__limits_schema.sql`** (CREATE, `backend/src/main/resources/db/migration/`) — both tables,
  their constraints, and the seeded `EXTRACTION` default row.

### Modified — `backend/src/main/java/xyz/stasiak/recipai/extraction/`

- **`ExtractionController`** (MODIFY) — gains a `Jwt` parameter on both endpoints and passes the email
  claim down. Keeps its MIME validation, which now throws a typed exception.
- **`ExtractionService`** (MODIFY) — gains a `LimitsFacade` dependency and owns the `"EXTRACTION"`
  resource key. Reserves before constructing the prompt, so no path reaches `ChatClient` unreserved.
- **`UnsupportedImageTypeException`**, **`ExtractionFailedException`** (CREATE) — replace the bare
  `IllegalArgumentException` / `IllegalStateException` thrown today.
- **`ExtractionExceptionHandler`** (CREATE) — the module's first exception handling.

### Modified — build and tests

- **`pom.xml`** (MODIFY) — adds `com.tngtech.archunit:archunit-junit5` at test scope.
- **`LimitsModuleArchitectureTest`** (CREATE) — holds `limits` free of domain knowledge.
- **`LimitsIntegrationTest`** (CREATE) — the module's own semantics against a real Postgres:
  override-then-default, stock vs. flow, lazy restart, "N ever", concurrent reserve at the cap,
  missing configuration.
- **`TestAiConfiguration`** (CREATE) — a `@Primary` mock `ChatClient`, mirroring how
  `TestSecurityConfiguration` mocks `JwtDecoder`.
- **`ExtractionIntegrationTest`** (MODIFY) — the 429 asserted over HTTP with the AI call mocked. The
  class-level `@Disabled` is lifted and the limit cases are added here; only the one test that calls
  Gemini for real keeps a method-level `@Disabled` with a reason.

## Interfaces and method signatures

### Crossing the module boundary

```java
public class LimitsFacade {
    public void reserve(String subject, String resource);   // throws LimitExceededException
}

public class LimitExceededException extends RuntimeException {
    public String resource();
    public LimitKind kind();
    public int limit();
    public int used();
    public Long retryAfterSeconds();   // null for STOCK and for a FLOW cap with no period
}

public class LimitConfigurationMissingException extends RuntimeException {
    public String resource();
}

public enum LimitKind { STOCK, FLOW }
```

`reserve` returns nothing: a caller either proceeds or is interrupted by the refusal. Nothing else in
`limits` is visible outside the package, and no type in the signature carries domain meaning — the
resource key is a plain string the *calling* module owns.

### Internal to `limits`

```java
class LimitService {
    void reserve(String subject, String resource);   // @Transactional
}

enum LimitPeriod {
    DAY, WEEK, MONTH;
    Instant cutoffFrom(Instant now);            // now minus one period
    Instant nextStart(Instant periodStart);     // periodStart plus one period
}

interface LimitConfigRepository extends JpaRepository<LimitConfig, UUID> {
    // subject row wins, default row (subject IS NULL) is the fallback
    Optional<LimitConfig> resolve(String resource, String subject);
}

interface LimitUsageRepository extends JpaRepository<LimitUsage, LimitUsageId> {
    int reserve(String resource, String subject, Instant now, Instant cutoff, int max);   // 1 = granted, 0 = refused
}
```

`LimitPeriod` is the only place calendar arithmetic lives: `DAY` and `WEEK` are fixed `Duration`s,
`MONTH` uses `Period` against a `ZonedDateTime` at UTC.

### Extraction

```java
class ExtractionController {
    ExtractedRecipe extractFromText(@Valid @RequestBody ExtractTextRequest request, Jwt jwt);
    ExtractedRecipe extractFromImage(@RequestParam("file") MultipartFile file, Jwt jwt);
}

class ExtractionService {
    static final String EXTRACTION_RESOURCE = "EXTRACTION";
    ExtractedRecipe extractFromText(String text, String userEmail);
    ExtractedRecipe extractFromImage(Media imageMedia, String userEmail);
}
```

### Schema

```sql
CREATE TABLE limit_config
(
    id         UUID PRIMARY KEY,
    resource   VARCHAR(64)  NOT NULL,
    subject    VARCHAR(255),                      -- NULL = the default for this resource
    kind       VARCHAR(16)  NOT NULL CHECK (kind IN ('STOCK', 'FLOW')),
    max_value  INTEGER      NOT NULL CHECK (max_value >= 0),
    period     VARCHAR(16)           CHECK (period IN ('DAY', 'WEEK', 'MONTH')),
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_limit_config_resource_subject UNIQUE NULLS NOT DISTINCT (resource, subject),
    CONSTRAINT ck_limit_config_stock_has_no_period CHECK (kind <> 'STOCK' OR period IS NULL)
);

CREATE TABLE limit_usage
(
    resource     VARCHAR(64)  NOT NULL,
    subject      VARCHAR(255) NOT NULL,
    used         INTEGER      NOT NULL,
    period_start TIMESTAMP    NOT NULL,
    PRIMARY KEY (resource, subject)
);

INSERT INTO limit_config (id, resource, subject, kind, max_value, period)
VALUES (gen_random_uuid(), 'EXTRACTION', NULL, 'FLOW', 2, NULL);
```

`resource` carries no `CHECK` constraint: enumerating the valid resource keys in the schema would put
domain knowledge back into the module the ArchUnit rule exists to keep clean.

### Refusal contract

```
HTTP/1.1 429 Too Many Requests
Retry-After: 51840
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Limit Exceeded",
  "status": 429,
  "detail": "Limit for EXTRACTION reached (2 of 2 used)",
  "resource": "EXTRACTION",
  "kind": "FLOW",
  "limit": 2,
  "used": 2,
  "retryAfterSeconds": 51840
}
```

`retryAfterSeconds` and the `Retry-After` header are both absent for a stock cap and for a flow cap
with no period — by construction there is no time at which the answer changes.
`LimitConfigurationMissingException` maps to 500 with title `Limit Configuration Missing`.

## Data flow

Text extraction, the granted path:

1. `ExtractionController.extractFromText` reads `jwt.getClaimAsString("email")` and calls
   `ExtractionService.extractFromText(text, userEmail)`.
2. The service calls `LimitsFacade.reserve(userEmail, EXTRACTION_RESOURCE)` **before** touching
   `ChatClient`.
3. `LimitService.reserve` resolves configuration for `(EXTRACTION, userEmail)` — one query, no cache,
   so an operator's `UPDATE` is in force on the very next request.
4. It derives `cutoff` from the resolved period and `Clock`-supplied `now`, then runs the conditional
   upsert. One affected row means granted; the service returns and, having no ambient transaction,
   the reservation commits here.
5. The service builds the prompt and calls Gemini. Whatever happens next — a provider error, a garbage
   response, an abandoned client — nothing refunds the unit.

The refused path diverges at step 4: zero affected rows means the row exists and is at its cap, so the
service reads it for the standing, computes `retryAfterSeconds` when a period applies, and throws
`LimitExceededException`. `LimitsExceptionHandler` renders the 429 and `ChatClient` is never reached.

Image extraction is identical except that the controller validates the MIME type first, so an
unsupported upload is refused with 400 without consuming budget — no AI call was going to happen.

## Pseudo-code

The reserve statement, which is the whole concurrency guarantee:

```sql
INSERT INTO limit_usage (resource, subject, used, period_start)
VALUES (:resource, :subject, 1, :now)
ON CONFLICT (resource, subject) DO UPDATE SET
    used         = CASE WHEN limit_usage.period_start <= :cutoff THEN 1     ELSE limit_usage.used + 1        END,
    period_start = CASE WHEN limit_usage.period_start <= :cutoff THEN :now  ELSE limit_usage.period_start    END
WHERE limit_usage.period_start <= :cutoff     -- period elapsed: restart, regardless of used
   OR limit_usage.used < :max                 -- or still under the cap
```

Postgres takes a row lock for the duration of the statement, so two concurrent reserves for one
subject are serialised and the second sees the first's increment. Zero affected rows is a refusal, not
an error.

```
reserve(subject, resource):
    config = configRepo.resolve(resource, subject)
             or throw LimitConfigurationMissingException(resource)   # logged at ERROR

    now    = clock.instant()
    cutoff = config.period == null ? Instant.EPOCH : config.period.cutoffFrom(now)
             # EPOCH can never be >= a period_start, so STOCK and "N ever" never restart

    if usageRepo.reserve(resource, subject, now, cutoff, config.maxValue) == 1:
        return                                     # granted

    usage = usageRepo.findById(resource, subject)  # guaranteed present: absent would have inserted
    retryAfter = null
    if config.kind == FLOW and config.period != null:
        retryAfter = max(1, secondsBetween(now, config.period.nextStart(usage.periodStart)))
    log.warn(...)
    throw LimitExceededException(resource, config.kind, config.maxValue, usage.used, retryAfter)
```

Resolution, ordering the override ahead of the default:

```
SELECT c FROM LimitConfig c
 WHERE c.resource = :resource AND (c.subject = :subject OR c.subject IS NULL)
 ORDER BY c.subject NULLS LAST
 LIMIT 1
```

## Decisions made

- **Subject key is the email claim** — every controller and permission table in the codebase already
  keys on it, and T2's recompute can then count straight off `recipe_permission.email` with no bridge
  table. Settles `HLD.md` > Open questions > *Identity key*.
- **The resource key is an opaque `String`, not an enum** — an enum listing `EXTRACTION`, `RECIPE`,
  … would be domain knowledge inside the module ADR-0006 requires to have none, and would make the
  ArchUnit rule guard a boundary the code had already crossed. Each calling module owns its own key
  constant; `ExtractionService.EXTRACTION_RESOURCE` is the first.
- **One configuration table with a nullable subject** — `subject IS NULL` is the default row, so an
  operator sees every limit in one table, which is the entire operating model given there is no admin
  surface. Resolution is a single query.
- **Period is a named enum (`DAY` / `WEEK` / `MONTH` / `NULL`)** — the table is the operator
  interface, so `'DAY'` beats `86400`, and `CHECK` constraints make nonsense values unrepresentable.
  `NULL` expresses "N ever".
- **Check-and-reserve is one conditional upsert** — the lazy restart folds into the same statement as
  `CASE` expressions, the first-ever request needs no special case, and no transaction or explicit
  lock is required. Settles `HLD.md` > Open questions > *Concurrency mechanism*.
- **The no-period cutoff is `Instant.EPOCH`, not `NULL`** — same semantics (`period_start <= EPOCH` is
  never true) while keeping the `WHERE` clause in two-valued logic and sparing Postgres from having to
  infer the type of a null parameter.
- **The reservation joins the caller's transaction** (default `REQUIRED`) — extraction has no ambient
  transaction, so its unit is committed before the AI call and never refunded, exactly as required;
  from T2 on, a create that rolls back rolls its reservation back too rather than manufacturing the
  drift ADR-0006 names as the design's principal cost. This softens ADR-0006's "still costs the user a
  unit" consequence note, which was written as an acceptance rather than a requirement.
- **Refusals are `ProblemDetail` with extension properties plus `Retry-After`** — `ProblemDetail` is
  the house convention in four of five modules; relative seconds are immune to device clock skew and
  their absence states "no retry time" without inventing one. The handler returns
  `ResponseEntity<ProblemDetail>` to set the header, a small departure from the bare-`ProblemDetail`
  handlers elsewhere.
- **The 429 is owned by a single `@RestControllerAdvice` in `limits`** — the exception is shared, so
  one handler keeps the contract identical across all five future consumers instead of five copies.
- **Missing configuration fails closed as a 500** — a deleted default row must not silently uncap paid
  AI calls, and a 429 would tell the user they hit a limit they never had. Logged at ERROR.
- **Time comes from an injected `Clock`** — otherwise the lazy restart is only testable by waiting a
  day.
- **`limit_usage` is written only through the native statement** — JPA writes would reintroduce the
  read-modify-write race the upsert exists to eliminate. The entity exists purely to read a standing.
- **Extraction's exceptions live flat in the module package**, matching its current flat layout rather
  than the `exception/` subpackage used by `planning` and `shoppinglists`.
- **Unsupported image types now return 400 rather than 500** — a side effect of giving the module a
  real handler; today the bare `IllegalArgumentException` is unhandled. The null-extraction path stays
  500, now with a `ProblemDetail` body.
- **The seeded extraction default is `FLOW`, 2, no period — "2 ever."** Runtime-editable by design, so
  the seed is not load-bearing.
- **The AI call is mocked for the limit tests** — `ExtractionIntegrationTest` was `@Disabled` at class
  level because it bills Gemini, so a `@Primary` mock `ChatClient` (the pattern
  `TestSecurityConfiguration` already uses for `JwtDecoder`) keeps the HTTP-level 429 assertion runnable
  in CI, and the `@Disabled` shrinks to the single real-provider test.
- **`recipai.limits.enabled` is a kill-switch, not a rollout flag** — the facade short-circuits when it
  is false, so limits can be turned off without touching data. It is `false` in `application-dev.yml`,
  which means local development is uncapped and every test that exercises a limit must opt back in with
  `properties = "recipai.limits.enabled=true"`.
- **The `Clock` bean lives in `config.time`, not in `limits`** — it is shared infrastructure rather
  than a limits concern, and every later time-dependent service reads the same bean.

## Assumptions to verify

- **Assumption:** Hibernate accepts `LIMIT` and `NULLS LAST` in the HQL resolution query.
  **If wrong:** return a `List<LimitConfig>` from the same ordered query and take the first element, or
  pass `Pageable.ofSize(1)`. No design change.
- **Assumption:** nothing wraps `ExtractionService` in a transaction, so the reservation commits before
  the AI call. Confirmed by reading the module today — it has no `@Transactional` and no facade.
  **If wrong:** a failed extraction would be refunded, breaking the requirement that every attempt
  counts; the reserve would then need `REQUIRES_NEW` for this call site.
- **Assumption:** `spring.jpa.hibernate.ddl-auto: validate` is satisfied by mapping `kind` and `period`
  as `@Enumerated(EnumType.STRING)` against `VARCHAR`, and `Instant` against `TIMESTAMP` as the rest of
  the schema already does.
  **If wrong:** the application fails fast at startup, so this surfaces immediately.
- **Assumption:** `Retry-After` survives any reverse proxy in front of the application.
  **If wrong:** the same value is still in the response body, which is what the mobile client will
  actually read in T5.
- **Assumption:** the mobile client tolerates an unrecognised 429 from `/extract/*` by showing a
  generic error until T5 lands.
  **If wrong:** T5's shared 429 handling has to be pulled forward. `tasks.md` > Cross-task notes
  already accepts the generic error in the interim.
- **Assumption:** two extractions "ever" is an acceptable production default at rollout.
  **If wrong:** it is one `UPDATE` against `limit_config`, with no redeploy — the feature's own premise.

## Required reading for implementation planning

- `docs/ADRs/0006-shared-limits-module.md` — the decision this task implements; *Consequences* governs
  the architecture test and the drift obligation.
- `HLD.md` > Feature areas > *Limits module (new)*, *Extraction*, *Rejection contract* — the behaviors
  in scope.
- `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanService.java:50-68` — the count-then-check
  this design replaces, and the create-path shape T2/T3 will migrate.
- `backend/src/main/java/xyz/stasiak/recipai/planning/PlanningExceptionHandler.java` — the
  `ProblemDetail` handler pattern the shared handler mirrors.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeFacade.java` — the public-facade-over-
  package-private-internals pattern `LimitsFacade` follows.
- `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanPermissionId.java` and
  `shoppinglists/ShoppingListItem.java` — composite-id and entity conventions for `LimitUsage`.
- `backend/src/main/resources/db/migration/V11__meal_planning_schema.sql` — migration style and
  `TIMESTAMP` usage.
- `backend/src/test/java/xyz/stasiak/recipai/TestSecurityConfiguration.java` — the `@Primary` mock-bean
  test configuration `TestAiConfiguration` copies.
- `docs/backend/standards/module-structure.md`, `java-patterns.md`, `integration-tests.md` — facade,
  exception-handler, entity, visibility and test conventions.
