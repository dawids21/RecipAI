# T2: Owner-scoped caps for recipes, collections and shopping lists — Implementation Plan

**Date:** 2026-08-19

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/backend/standards/module-structure.md` — the facade rule (`release` goes on `LimitsFacade`,
  not on `LimitService` reached directly), the `log.warn`-for-business-violations convention, and
  `jwt.getClaimAsString("email")` as the identity source the three controllers already use.
- `docs/backend/standards/integration-tests.md` — `@SpringBootTest(RANDOM_PORT)` +
  `@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})`, `RestClient` over
  MockMvc, AssertJ, `shouldXxxWhenYyy` naming, and — load-bearing here — **read state back through
  `LimitsFacade.currentUsage`, never with a `SELECT` against `limit_usage`**. Seeding fixtures with
  `JdbcClient` is allowed.
- `docs/backend/standards/java-patterns.md` — package-private-unless-crossing-a-boundary; the three
  resource-key constants are `static final String`, package-private, on the owning service.
- `docs/backend/modules/limits/codebase_structure.md`, `db.md` — the two files this task updates, and
  the current statement of reserve/resolution behaviour that `release` must sit alongside.

**Design & ADRs**

- `plans/T2-task-design.md` > *Pseudo-code*, *Interfaces and method signatures*, *Data flow* — the
  literal contracts; the release statement and the recompute blocks are reproduced below.
- `plans/T2-task-design.md` > *Decisions made* — settled; do not re-open (recompute as a repeatable
  migration, `V16` versioned for config, full rebuild per resource, `FLOW` excluded, `release` never
  throws, reserve first / release last, existing suites run with limits off).
- `plans/T1-task-design.md` > *Decisions made* — the transaction (`REQUIRED`), clock, kill-switch and
  refusal-contract behaviour `release` matches.
- `docs/ADRs/0006-shared-limits-module.md` > *Consequences* — the drift obligation this task
  discharges and the opaque-subject rule the recompute must not break.
- `HLD.md` > Feature areas > *Owner-scoped resources*, *Limits module (new)*, *Rollout*.

**Code to mirror**

- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitService.java:22-51` — how `reserve` resolves
  configuration and derives the cutoff; `release` resolves identically and then diverges.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitUsageRepository.java:12-24` — the
  `@Modifying` + `@Query(nativeQuery = true)` + `{h-schema}` convention the decrement copies.
  `{h-schema}` is not optional — see T1's plan, *Risks* > *Native SQL and the `recipai` schema*.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsFacade.java:25-32` — the kill-switch guard
  and `log.debug` shape `release` repeats.
- `backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractionService.java:20,26` — the
  resource-constant + reserve-as-first-statement call-site pattern the three consumers copy.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java:92-128,182-204` — the create
  and delete paths; note `deleteAllImages` runs last and swallows S3 failures.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionService.java:49-62,84-103`
  and `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java:33-46,140-160`
  — the other two create/delete pairs, both already `@Transactional`.
- `backend/src/main/resources/db/migration/V15__limits_schema.sql:23-24` — the `EXTRACTION` seed row
  `V16` mirrors: unqualified table name, `gen_random_uuid()`, `created_at` left to its default.
- `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsIntegrationTest.java:37-41,363-407` — the
  `TEST_LIMIT_*` isolation convention, `JdbcClient` config/usage seeding helpers and the targeted
  `@AfterEach`.
- `backend/src/test/java/xyz/stasiak/recipai/planning/MealPlanIntegrationTest.java:50-65` — teardown
  that deletes over HTTP across all three tokens and swallows the EDITOR-cannot-delete 403.

## File inventory

**Limits module** — `backend/src/main/java/xyz/stasiak/recipai/limits/`

- **MODIFY** `LimitsFacade.java` — public `release(subject, resource)` behind the kill-switch
- **MODIFY** `LimitService.java` — `@Transactional void release`, `FLOW` no-op, never throws
- **MODIFY** `LimitUsageRepository.java` — native `GREATEST(used - 1, 0)` decrement

**Migrations** — `backend/src/main/resources/db/migration/`

- **CREATE** `V16__owner_scoped_limit_config.sql` — three default `STOCK` config rows (5 / 2 / 2)
- **CREATE** `R__recompute_limit_usage.sql` — repeatable rebuild of usage from the permission tables

**Consumers**

- **MODIFY** `recipes/RecipeService.java` — `RECIPE_RESOURCE`, `LimitsFacade`, reserve in both `save`
  overloads, release in `deleteById`
- **MODIFY** `recipes/collections/RecipesCollectionService.java` — `RECIPES_COLLECTION_RESOURCE`,
  reserve in `create`, release in `deleteById`
- **MODIFY** `shoppinglists/ShoppingListService.java` — `SHOPPING_LIST_RESOURCE`, reserve in
  `create`, release in `deleteById`

**Tests** — `backend/src/test/java/xyz/stasiak/recipai/`

- **CREATE** `RecomputeMigration.java` — public helper that executes `R__recompute_limit_usage.sql`
- **MODIFY** `limits/LimitsIntegrationTest.java` — release semantics against `TEST_LIMIT_*` resources
- **MODIFY** `recipes/RecipeIntegrationTest.java` — class-level `recipai.limits.enabled=false`, plus
  a `@Nested LimitsEnforced` class carrying the RECIPE enforcement and recompute cases
- **MODIFY** `recipes/collections/RecipesCollectionIntegrationTest.java` — same, for
  `RECIPES_COLLECTION`
- **MODIFY** `shoppinglists/ShoppingListIntegrationTest.java` — same, for `SHOPPING_LIST`
- **MODIFY** `planning/MealPlanIntegrationTest.java` — class-level `recipai.limits.enabled=false`
  **only**; no nested class (meal plans are T3). It creates recipes over HTTP and shares a Spring
  context with the three suites above — see *Risks* > *`MealPlanIntegrationTest` is a fourth consumer
  of the recipe cap*.

**Documentation** (named as deliverables by `T2-task-design.md` > *Modified — documentation*; the
wider docs refresh remains the separate `docs-updating` step)

- **MODIFY** `docs/backend/modules/limits/codebase_structure.md` — release behaviour, the three new
  consumers, the recompute migration
- **MODIFY** `docs/backend/modules/limits/db.md` — the three seeded defaults and the recompute

No change to `backend/pom.xml`, to `LimitsModuleArchitectureTest` (no new public *type*, only a
method on an existing public facade), to any of the three exception handlers, or to `mobile/`.

## Step-by-step plan

### 1. Migrations

Add `V16__owner_scoped_limit_config.sql`, exactly as `T2-task-design.md` > *Configuration seed*
specifies — unqualified table name, `gen_random_uuid()`, `created_at` left to its default:

```sql
INSERT INTO limit_config (id, resource, subject, kind, max_value, period)
VALUES (gen_random_uuid(), 'RECIPE',             NULL, 'STOCK', 5, NULL),
       (gen_random_uuid(), 'RECIPES_COLLECTION', NULL, 'STOCK', 2, NULL),
       (gen_random_uuid(), 'SHOPPING_LIST',      NULL, 'STOCK', 2, NULL);
```

Add `R__recompute_limit_usage.sql` — three blocks, one per resource, differing only in the table, the
ownership column and the literal. Keep every statement plain and `;`-terminated (no functions, no
dollar-quoting) so `ScriptUtils` can split it in step 5:

```sql
-- RECIPE
DELETE FROM limit_usage u
 WHERE u.resource = 'RECIPE'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject = u.subject),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW';

INSERT INTO limit_usage (resource, subject, used, period_start)
SELECT 'RECIPE', p.email, COUNT(*), now()
  FROM recipe_permission p
 WHERE p.role = 'OWNER'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'RECIPE' AND c.subject = p.email),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'RECIPE' AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW'
 GROUP BY p.email
    ON CONFLICT (resource, subject) DO NOTHING;
```

*As implemented:* the `FLOW` guard resolves the subject's **effective** kind — its own override if one
exists, otherwise the resource default — the same order `LimitConfigRepository.resolve` uses, instead of
the `NOT EXISTS ... AND c.kind = 'FLOW'` this plan first specified. That version spared a subject holding
a `FLOW` override but would have flattened every subject inheriting a `FLOW` default into a stock count,
which the step's own test list (a subject-level `FLOW` override shadowing a `STOCK` default, and the
reverse) requires it not to do.

Repeat for `'RECIPES_COLLECTION'` over `recipes_collection_permission` and `'SHOPPING_LIST'` over
`shopping_list_permission`. All three permission tables use `email` and `role` — verified against
`V1`/`V6`, `V7` and `V4`.

- Files: `backend/src/main/resources/db/migration/V16__owner_scoped_limit_config.sql`,
  `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql`
- Verify (from `backend/`): `./mvnw test -Dtest=RecipAiApplicationTests` — Flyway applies `V16` and
  then the repeatable against a fresh Testcontainers Postgres and the context loads.

### 2. `release` in the limits module

`LimitUsageRepository` gains the decrement. `{h-schema}` qualification, `@Modifying`, `int` return
used only for diagnostics:

```java
@Modifying
@Query(value = """
        UPDATE {h-schema}limit_usage
           SET used = GREATEST(used - 1, 0)
         WHERE resource = :resource AND subject = :subject
        """, nativeQuery = true)
int release(@Param("resource") String resource, @Param("subject") String subject);
```

`LimitService.release` is `@Transactional`
(`org.springframework.transaction.annotation.Transactional`, matching `reserve`) and follows the
design's pseudo-code: resolve configuration; on absence `log.error` and **return** — no throw; on
`LimitKind.FLOW` return; otherwise call the decrement. Zero rows affected is normal (a release with
no prior reserve), so do not log it above `debug`.

`LimitsFacade.release` mirrors `reserve` exactly — kill-switch guard with the same `log.debug`
wording, then delegate.

- Files: `limits/LimitUsageRepository.java`, `limits/LimitService.java`, `limits/LimitsFacade.java`,
  `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsIntegrationTest.java`
- Verify: `./mvnw test -Dtest=LimitsIntegrationTest` — the release cases in the *Test plan* pass,
  including the floor-at-zero and concurrent-release cases.

### 3. Wire the three consumers, and take the pre-existing suites off limits

Each service gains a package-private `static final String <X>_RESOURCE` constant, a `LimitsFacade`
field on the existing `@RequiredArgsConstructor`, a `reserve` as the **first statement** of the
create method (before collection validation, before any write) and a `release` as the **last
statement** of the delete method (after the delete has succeeded, still inside the transaction):

- `RecipeService` — `RECIPE_RESOURCE = "RECIPE"`. Put the reserve in the *two-argument*
  `save(request, images, userEmail)` only; the single-argument overload delegates to it, so
  reserving in both would double-charge every JSON create. Release goes after
  `recipeImagesService.deleteAllImages(id)` in `deleteById`.
- `RecipesCollectionService` — `RECIPES_COLLECTION_RESOURCE = "RECIPES_COLLECTION"`; reserve at the
  head of `create`, release at the tail of `deleteById`.
- `ShoppingListService` — `SHOPPING_LIST_RESOURCE = "SHOPPING_LIST"`; reserve at the head of
  `create`, release at the tail of `deleteById`. `createItem` / `deleteItem` are untouched — T4.

In the same commit, add `properties = "recipai.limits.enabled=false"` to the class-level
`@SpringBootTest` of **all four** pre-existing HTTP suites. Use the identical property string in all
four so they keep sharing one Spring context and one Postgres container.

- Files: `recipes/RecipeService.java`, `recipes/collections/RecipesCollectionService.java`,
  `shoppinglists/ShoppingListService.java`, and the four test classes
  `recipes/RecipeIntegrationTest.java`, `recipes/collections/RecipesCollectionIntegrationTest.java`,
  `shoppinglists/ShoppingListIntegrationTest.java`, `planning/MealPlanIntegrationTest.java`
- Verify: `./mvnw test -Dtest='RecipeIntegrationTest,RecipesCollectionIntegrationTest,ShoppingListIntegrationTest,MealPlanIntegrationTest'`
  — all 93 existing tests still pass, unchanged.

### 4. Manual end-to-end check of enforcement

Before writing the nested suites, run `tasks.md` > T2 > *How to verify* by hand against a locally
running app with `recipai.limits.enabled=true` (the `dev` profile turns limits off, so override it).
This catches a mis-wired call site as a plain 429/200 result rather than as an opaque test failure.

- Files: none
- Verify: `UPDATE recipai.limit_config SET max_value = 3 WHERE resource = 'RECIPE' AND subject IS NULL;`
  then three `curl -X POST .../recipes` succeed and the fourth returns `429` with
  `Content-Type: application/problem+json`; `GET` and `PUT` on an existing recipe still return 200;
  one `DELETE` then admits the next create.

### 5. Recompute helper and the recipes nested suite

`RecomputeMigration` (public, package `xyz.stasiak.recipai`, so all three suites can reach it) takes
a `DataSource`, borrows a connection, sets the schema, runs the script and resets — the `RESET` must
execute on the same connection *before* it returns to HikariCP, which does not reset session state:

```java
public static void run(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection()) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO recipai");
            try {
                ScriptUtils.executeSqlScript(connection,
                        new ClassPathResource("db/migration/R__recompute_limit_usage.sql"));
            } finally {
                statement.execute("RESET search_path");
            }
        }
    } catch (SQLException e) {
        throw new IllegalStateException("Failed to run the recompute migration", e);
    }
}
```

Add `@Nested @TestPropertySource(properties = "recipai.limits.enabled=true") class LimitsEnforced` to
`RecipeIntegrationTest`. It autowires `LimitsFacade`, `JdbcClient` and `DataSource`; the enclosing
instance's `restClient()`, `createRecipe(...)`, `getRecipe(...)` and `deleteRecipe(...)` helpers are
used as-is.

Conventions the nested class must follow:

- **Seed a subject override, never edit the shipped default.** `INSERT INTO limit_config` with
  `subject = 'user@example.com'` (or the token under test) so the cases are immune to an operator
  changing the production number. The subject is fixed by the JWT, so "unique" means *subject-scoped*
  here, not a random string.
- **Assert standings through `limitsFacade.currentUsage(subject, resource)`**, per the
  integration-test standard. `JdbcClient` is for seeding only.
- **Teardown** deletes created rows over HTTP for all three tokens (wrapping the EDITOR 403 in a
  `try`/`catch`, as `MealPlanIntegrationTest` does), then deletes the seeded `limit_config` rows and
  only those `limit_usage` rows fabricated for synthetic subjects. **No blanket
  `DELETE FROM limit_usage`** — a zero standing after teardown is the evidence that release fired.
  Finish teardown by asserting the subject's standing is zero, so a missed release is attributed to
  the test that broke it.
- **Scope every recompute assertion to its own resource and subject.** Do not count rows across the
  whole table — see *Risks* > *The three nested classes probably share one context*.

- Files: `backend/src/test/java/xyz/stasiak/recipai/RecomputeMigration.java`,
  `backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java`
- Verify: `./mvnw test -Dtest='RecipeIntegrationTest'` — both the outer suite and
  `RecipeIntegrationTest$LimitsEnforced` pass. (In fish, quote the `$`.)

### 6. Collections and shopping-list nested suites

Mirror step 5 into `RecipesCollectionIntegrationTest` and `ShoppingListIntegrationTest`, changing the
resource key, the creation/deletion helpers and the permission table the recompute rebuilds from.
Keep the nested class name, annotations and property string byte-identical to the recipes one so all
three share a context.

- Files: `recipes/collections/RecipesCollectionIntegrationTest.java`,
  `shoppinglists/ShoppingListIntegrationTest.java`
- Verify: `./mvnw test` — the whole backend suite is green.

### 7. Documentation named by the design

Update `docs/backend/modules/limits/codebase_structure.md` (*Behaviour* gains release: resolves
configuration, no-ops for `FLOW`, floors at zero, never throws; *Consumers* gains the three modules
with their resource keys; the "**No release** — T1 ships reserve only" line goes) and
`docs/backend/modules/limits/db.md` (*Seeded Configuration* gains the three `V16` rows; a new short
section describes `R__recompute_limit_usage.sql` as the rollout seed and the drift repair, and that
`FLOW`-configured subjects are excluded from it).

- Files: `docs/backend/modules/limits/codebase_structure.md`, `docs/backend/modules/limits/db.md`
- Verify: both files describe the behaviour the code now has, and neither repeats what the other
  states.

## Test plan

**Unit tests**

_N/A — every behaviour added here is a SQL statement or a transaction boundary, neither of which is
observable without a real Postgres. `LimitPeriodTest` is unaffected._

**Integration tests**

- `LimitsIntegrationTest` — release semantics against synthetic `TEST_LIMIT_*` resources, reusing the
  existing `seedConfig` / `seedUsage` / `@AfterEach` helpers
  - decrements `used` by one for a `STOCK`-configured subject
  - leaves `used` unchanged for a `FLOW`-configured subject
  - leaves `used` unchanged when a subject-level `FLOW` override shadows a `STOCK` default
    (configuration resolution, not just the default row, decides)
  - floors at zero: releasing twice from `used = 1` leaves zero, never `-1`
  - creates no row when releasing with no usage row present — `currentUsage` stays empty
  - returns silently when no configuration resolves at all, and throws nothing
  - a reserve refused at the cap succeeds after one release
  - concurrency: 8 threads releasing simultaneously from `used = 1` leave `used` at exactly zero

- `RecipeIntegrationTest$LimitsEnforced` (`@Nested`, `recipai.limits.enabled=true`, `RECIPE`
  overridden to 2 for the test subject)
  - refuses the third create with 429, `application/problem+json`, and a body carrying
    `resource=RECIPE`, `kind=STOCK`, `limit=2`, `used=2`
  - that 429 carries **no** `Retry-After` header and no `retryAfterSeconds` key (stock refusal)
  - `GET` and `PUT` on an existing recipe still return 200 after the cap is lowered to 1 by SQL, and
    creation stays refused
  - deleting one recipe admits the next create, and the standing drops by exactly one
  - a create that fails after the reservation — `recipesCollectionId` pointing at a collection the
    user cannot see — rolls the reservation back: the standing is unchanged after the 404/403
  - sharing a recipe with `AUTH_TOKEN_USER_2` leaves user 2's `RECIPE` standing untouched, and
    unsharing releases nothing
  - the recompute repairs drift: fabricate `used = 99` by SQL, run `RecomputeMigration.run`, and the
    standing equals the number of recipes the subject actually owns
  - the recompute clears a subject that owns nothing: fabricate a usage row for
    `ghost@example.com`, run it, and that row is gone
  - the recompute spares a `FLOW`-configured subject: seed a `FLOW` config plus a usage row for
    `flow-subject@example.com`, run it, and both `used` and `period_start` are unchanged
  - running the recompute a second time changes nothing for the subject under test

- `RecipesCollectionIntegrationTest$LimitsEnforced` — the same nine cases against
  `RECIPES_COLLECTION`, `recipes_collection_permission` and the `/collections` endpoints, minus the
  rollback case (collection creation has no post-reserve validation step that can fail)

- `ShoppingListIntegrationTest$LimitsEnforced` — the same nine cases against `SHOPPING_LIST`,
  `shopping_list_permission` and the `/shopping-lists` endpoints, minus the rollback case. Also
  asserts that adding and deleting **items** moves no `SHOPPING_LIST` standing — the boundary T4 will
  push against.

- `RecipeIntegrationTest`, `RecipesCollectionIntegrationTest`, `ShoppingListIntegrationTest`,
  `MealPlanIntegrationTest` (outer classes) — unchanged behaviour, now pinned with
  `recipai.limits.enabled=false`. These are a regression gate, not new coverage.

**Flutter widget/integration tests**

_N/A — T2 is backend-only; the client-side refusal messaging is T5._

**Manual verification**

- `tasks.md` > T2 > *How to verify*, in full, with curl (step 4 above covers the first half; finish
  with the recompute against a database seeded with pre-existing recipes, run twice).
- After deploying, insert the developer's own overrides by hand — the design deliberately keeps them
  out of `V16`:
  `INSERT INTO recipai.limit_config (id, resource, subject, kind, max_value, period) VALUES (gen_random_uuid(), 'RECIPE', '<dev-email>', 'STOCK', 500, NULL);`
  and the same for the other two resources. Until that runs, the developer's own account is capped.
- Confirm on the deployed database that `R__recompute_limit_usage.sql` ran (`SELECT * FROM
  recipai.flyway_schema_history WHERE version IS NULL;`) and that `limit_usage` counts match the
  permission tables for a spot-checked user.

## Verification checklist

- [ ] `./mvnw test` — all new and existing tests pass (only `ExtractionIntegrationTest`'s
      real-provider test stays `@Disabled`)
- [ ] `V16` and the repeatable apply cleanly against a fresh database, and re-running the repeatable
      by hand is a no-op
- [ ] `tasks.md` > T2 > *How to verify* succeeds end-to-end with curl
- [ ] The stock 429 body carries no `retryAfterSeconds` key and the response no `Retry-After` header
- [ ] `limit_usage` is still never written through JPA — grep the module for
      `limitUsageRepository.save` and `.delete`
- [ ] `release` throws nothing on any path — missing configuration, absent usage row, `FLOW` subject
- [ ] The single-argument `RecipeService.save` overload does not reserve a second unit
- [ ] All four pre-existing HTTP suites carry the *identical* `recipai.limits.enabled=false` string
      (context-cache sharing depends on it)
- [ ] `LimitsModuleArchitectureTest` still passes with no edit
- [ ] No `@Transactional` was added or removed in the three consumer services
- [ ] Logs at `INFO` are clean on the happy path; refusals at `WARN`, missing configuration at `ERROR`
- [ ] The design's *Assumptions to verify* are resolved or explicitly carried forward (see below)

## Risks surfaced during planning

- **Risk:** `MealPlanIntegrationTest` is a fourth consumer of the recipe cap, and the design's file
  inventory omits it. It creates recipes over HTTP at ten call sites — four as `user1@example.com`,
  three as `user@example.com` — and its `@AfterEach` deletes only meal plans, so those recipes
  accumulate. Worse, it shares a Spring context (and therefore a Postgres container) with the three
  suites in scope, because all four declare identical `@SpringBootTest` / `@Import` annotations.
  **Why it matters:** with limits left on, user 1 lands 4 recipes against a cap of 5 — passing today
  by a one-recipe margin, and failing the moment anyone adds an eleventh creation. If the property is
  added to only three of the four suites, the context cache splits: `MealPlanIntegrationTest` gets
  its own container *and* keeps limits enabled.
  **Mitigation:** add the identical `properties = "recipai.limits.enabled=false"` to all four suites
  in step 3. No nested class there — the meal plan cap is T3.

- **Risk:** the class-level disable is *required*, not a convenience, and the design's framing
  understates it. `T2-task-design.md` > *Assumptions to verify* measures "maxima of 3 recipes, 2
  lists, 2 collections **in a single method**" against caps of 5/2/2 — but none of the three suites
  has any teardown at all, and they share one database. The real totals are 33 recipes and 14
  collections in `RecipeIntegrationTest` and 46 lists in `ShoppingListIntegrationTest`.
  **Why it matters:** anyone reading that assumption might conclude the disable is optional headroom
  and remove it. It is not: the class totals exceed every cap by an order of magnitude.
  **Mitigation:** stated here and in the checklist. The per-method figure is only relevant to a
  future world where the suites gain teardown.

- **Risk:** the three `@Nested` classes probably share one Spring context and one container, not one
  each. Spring's context cache key is the merged configuration — declaring class is not part of it —
  and all three nested classes will have byte-identical merged configuration (same `@SpringBootTest`,
  same `@Import` inherited from their enclosing classes, same property overrides). The design's
  measurement of "its own context and a fresh Postgres container… roughly 4.6s per nested class" was
  taken with only one nested class in existence.
  **Why it matters:** cheaper than budgeted, but the three nested classes are then **not** isolated
  from one another. A recompute assertion phrased as "`limit_usage` now has N rows" or "the table is
  empty" would be answering for all three resources at once and would fail unpredictably on test
  ordering.
  **Mitigation:** scope every recompute assertion to its own `(resource, subject)` pair, and derive
  the expected count from what the test itself created rather than hardcoding it. Do **not** force
  isolation with a per-class dummy property — that buys three containers for no behavioural gain.

- **Risk:** the design names the recompute test helper twice, inconsistently — `RecomputeMigration`
  under *Modified — tests*, `RecomputeMigrationTest` under *Decisions made*.
  **Why it matters:** an implementer could create a standalone test class, which is precisely the
  shape the design argues against (it would have to hardcode `'RECIPE'` and seed with raw `INSERT`s).
  **Mitigation:** one class, `RecomputeMigration`, a runner with no `@Test` methods. Every assertion
  lives in the owning module's nested class. Treat `RecomputeMigrationTest` as a stale name.

- **Risk:** `now()` in the recompute writes a `timestamp` in the database server's zone, while the
  application writes `Instant`s converted through the JVM's default zone. The two can differ.
  **Why it matters:** `period_start` is inert for `STOCK` rows — `LimitService` derives
  `cutoff = Instant.EPOCH` when `period` is null, so the value is never compared. It becomes live
  only if an operator later switches that subject to `FLOW`, where it could mis-anchor one window.
  **Mitigation:** keep plain `now()`, matching `V15`'s `created_at DEFAULT now()`. This is the cost
  `HLD.md` already accepts: "gives up correctness-by-construction when a subject is switched between
  stock and flow — corrected by hand". Clearing the subject's usage row is part of that hand
  correction.

- **Risk:** a repeatable migration re-runs only when its checksum changes, and nothing exposes the
  recompute at runtime.
  **Why it matters:** repairing drift on an unchanged file is not a redeploy — it needs psql access
  to run the script by hand, or a cosmetic edit to bump the checksum.
  **Mitigation:** accepted; it is the operating model the feature is built around, and T3/T4 both
  edit this file anyway. Worth a line in the PR description so the on-call path is written down.

**Assumptions from `T2-task-design.md`, resolved during planning**

- *"The audit of destroying paths is complete"* — **confirmed.** `recipes.recipes_collection_id`
  carries `ON DELETE SET NULL` (`V8`), so deleting a collection never deletes recipes;
  `RecipeService.handleRecipesCollectionUnshared` only nulls the collection id;
  `MealPlanService` is the only listener for `RecipeDeleted` and removes entries, not plans; the only
  callers of the three `create`/`save`/`deleteById` methods are the three controllers — no facade
  reaches them, and `provisioning` is pure computation.
- *"`role = 'OWNER'` is what the creation path writes"* — **confirmed** in all three services, and
  all three permission tables spell the columns `email` and `role` (`V1`/`V6`, `V7`, `V4`).
- *"Shopping-list deletion cascades its items"* — **confirmed**, `fk_shopping_list_items_list_id … ON
  DELETE CASCADE` (`V3`). Carried forward to T4 as flagged.
- *"None of the three exception handlers shadows the 429"* — **confirmed**; all twenty
  `@ExceptionHandler` methods in the codebase are bound to a specific exception type, and none to
  `Exception` or `RuntimeException`.

Still open and carried forward: whether `ScriptUtils` splits the repeatable correctly and whether the
`search_path` dance is needed (both fail loudly on the first run of step 5, with the fallbacks the
design already names), and whether the resource-key literals stay in step between Java and SQL —
enforced for `R__` by the nested tests, but **not** for `V16`, where a typo would surface only as a
500 from `LimitConfigurationMissingException` on first use.
