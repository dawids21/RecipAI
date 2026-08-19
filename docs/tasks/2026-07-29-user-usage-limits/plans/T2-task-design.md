# T2: Owner-scoped caps for recipes, collections and shopping lists — Task Design

**Date:** 2026-08-19

## Summary

`recipes`, `recipes.collections` and `shoppinglists` become consumers of the `limits` module built in
T1: each reserves one unit keyed by the owning user's email before creating, and releases one on
deletion. `limits` gains a single new facade method, `release`, which resolves configuration exactly
as `reserve` does so that a subject configured `FLOW` never gets budget back. Configuration defaults
arrive in a versioned migration; usage is rebuilt from the permission tables by a repeatable
migration that is both the rollout seed and the drift repair.

## Components and responsibilities

### Modified — `backend/src/main/java/xyz/stasiak/recipai/limits/`

- **`LimitsFacade`** (MODIFY) — gains `release(String subject, String resource)`, guarded by the same
  `recipai.limits.enabled` kill-switch as `reserve`. It is the only new cross-module surface; no new
  public type is introduced, so `LimitsModuleArchitectureTest` needs no change.
- **`LimitService`** (MODIFY) — gains `release`: resolves configuration, no-ops for a `FLOW`-configured
  subject, and otherwise decrements with a floor at zero. It never throws — a delete must not be
  blocked or turned into a 500 by the limits module.
- **`LimitUsageRepository`** (MODIFY) — gains the native decrement statement. `limit_usage` continues
  to be written only through native SQL, never through JPA.

### New — migrations, `backend/src/main/resources/db/migration/`

- **`V16__owner_scoped_limit_config.sql`** (CREATE) — seeds the three default `limit_config` rows.
  Versioned and therefore one-shot: an operator's later `UPDATE` must never be overwritten.
- **`R__recompute_limit_usage.sql`** (CREATE) — the recompute. Rebuilds `limit_usage` for the three
  resources from `recipe_permission`, `recipes_collection_permission` and `shopping_list_permission`.
  Repeatable, so T3 and T4 extend this one file and the checksum change re-seeds their resource while
  harmlessly re-asserting the others.

### Modified — consumers

- **`RecipeService`** (MODIFY, `recipes/RecipeService.java`) — owns `RECIPE_RESOURCE`, takes a
  `LimitsFacade`, reserves at the head of `save` and releases at the tail of `deleteById`.
- **`RecipesCollectionService`** (MODIFY, `recipes/collections/RecipesCollectionService.java`) — owns
  `RECIPES_COLLECTION_RESOURCE`, reserves in `create`, releases in `deleteById`.
- **`ShoppingListService`** (MODIFY, `shoppinglists/ShoppingListService.java`) — owns
  `SHOPPING_LIST_RESOURCE`, reserves in `create`, releases in `deleteById`. Item paths are untouched;
  they are T4.

None of the three exception handlers change: `LimitExceededException` is unhandled by them and falls
through to `LimitsExceptionHandler`, which none of them shadow with a catch-all.

### Modified — tests

- **`RecipeIntegrationTest`**, **`RecipesCollectionIntegrationTest`**, **`ShoppingListIntegrationTest`**
  (MODIFY) — each gains `properties = "recipai.limits.enabled=false"` at class level, leaving its
  existing tests untouched, plus a `@Nested @TestPropertySource(properties = "recipai.limits.enabled=true")`
  inner class carrying that resource's enforcement *and* recompute tests: refusal at the cap, release on
  delete, read and edit still working while over cap, sharing not charging the recipient, and the
  recompute repairing drift, clearing a zero-owner subject, sparing a `FLOW`-configured subject and
  changing nothing on a second run.
- **`LimitsIntegrationTest`** (MODIFY) — release semantics against synthetic `TEST_LIMIT_*` resources:
  `FLOW` no-op, floor at zero, absent usage row, missing configuration.
- **`RecomputeMigration`** (CREATE, `backend/src/test/java/xyz/stasiak/recipai/`) — a test helper that
  executes `R__recompute_limit_usage.sql` on demand, since Flyway has already run by the time a test
  starts. It is only the runner; every assertion about the recompute lives in the module suite that owns
  the resource.

The recompute is deliberately *not* tested in a standalone class under `limits`. A test there could not
reference `RecipeService.RECIPE_RESOURCE` — a package-private class in another package — so it would
hardcode `'RECIPE'`, the very literal it exists to guard, and seeding its fixtures with raw `INSERT`s
would never touch the Java creation path. Placed inside the owning module's suite, rows are created over
HTTP (recording usage under the Java constant) and the recompute must reproduce that same number (using
the SQL literal), so a mismatch between the two fails.

### Modified — documentation

- **`docs/backend/modules/limits/codebase_structure.md`** (MODIFY) — the release behaviour and the
  three new consumers.
- **`docs/backend/modules/limits/db.md`** (MODIFY) — the three seeded default rows and the recompute.

*As implemented, the docs pass reached further than these two files:* the 429 and the release-on-delete
went into `docs/backend/modules/recipes/api.md` and `shopping-lists/api.md` (following the
`extraction/api.md` precedent from T1), the three services' resource keys into the two module
`codebase_structure.md` file trees, the consumer list and the per-module caps into
`docs/project/architecture.md`, and the limits/integration-test entries in `docs/INDEX.md` were
refreshed. `docs/backend/standards/integration-tests.md` also gained the limits-off-at-class-level /
`@Nested`-on shape as a standard, since three suites now use it.

## Interfaces and method signatures

### Crossing the module boundary

```java
public class LimitsFacade {
    public void reserve(String subject, String resource);   // existing — throws LimitExceededException
    public void release(String subject, String resource);   // new — never throws
    public Optional<LimitUsageDetails> currentUsage(String subject, String resource);   // existing
}
```

`release` returns nothing and throws nothing. A caller deletes and tells `limits`; whether that
actually decrements anything is the module's business, not the caller's.

### Internal to `limits`

```java
class LimitService {
    @Transactional void release(String subject, String resource);
}

interface LimitUsageRepository extends JpaRepository<LimitUsage, LimitUsageId> {
    @Modifying
    int release(String resource, String subject);   // native; rows affected is diagnostic only
}
```

### Resource keys

```java
class RecipeService              { static final String RECIPE_RESOURCE = "RECIPE"; }
class RecipesCollectionService   { static final String RECIPES_COLLECTION_RESOURCE = "RECIPES_COLLECTION"; }
class ShoppingListService        { static final String SHOPPING_LIST_RESOURCE = "SHOPPING_LIST"; }
```

Each calling module owns its own key, as `ExtractionService.EXTRACTION_RESOURCE` established in T1.
The same three literals appear in the two migrations; that duplication is unavoidable given the
module owns no enum of resource keys, and is called out under *Assumptions to verify*.

### Configuration seed

```sql
-- V16__owner_scoped_limit_config.sql
INSERT INTO limit_config (id, resource, subject, kind, max_value, period)
VALUES (gen_random_uuid(), 'RECIPE',             NULL, 'STOCK', 5, NULL),
       (gen_random_uuid(), 'RECIPES_COLLECTION', NULL, 'STOCK', 2, NULL),
       (gen_random_uuid(), 'SHOPPING_LIST',      NULL, 'STOCK', 2, NULL);
```

## Data flow

**Creation, granted.** Identical in all three modules; recipes shown.

1. `RecipeController.createRecipe` extracts `jwt.getClaimAsString("email")` and calls
   `RecipeService.save`, which is already `@Transactional`.
2. `save` calls `limitsFacade.reserve(userEmail, RECIPE_RESOURCE)` **first**, before validating the
   collection and before any write.
3. `LimitService.reserve` joins the caller's transaction (`REQUIRED`, unchanged from T1), resolves
   configuration with no cache, and runs the conditional upsert.
4. The recipe row, the `OWNER` permission row and any images are written. If any of that throws —
   including an S3 failure inside `recipeImagesService.uploadImages` — the transaction rolls back and
   takes the reservation with it, so a failed create costs the user nothing.

**Creation, refused.** Step 3's upsert affects zero rows, `LimitService` reads the standing and throws
`LimitExceededException`; `LimitsExceptionHandler` renders the shared 429. `kind` is `STOCK`, so the
body carries no `retryAfterSeconds` and no `Retry-After` header — correctly, since no amount of
waiting resolves a stock refusal. Nothing was written.

**Deletion.** `RecipeService.deleteById` already requires `OWNER`. After the permission rows and the
recipe are deleted, it calls `limitsFacade.release(userEmail, RECIPE_RESOURCE)`, still inside the
transaction. `deleteAllImages` swallows `S3StorageException` and cannot fail the delete, so the
release is always reached.

**Sharing and unsharing.** Untouched. A recipient is written an `EDITOR` row and never reserved
against; unsharing removes an `EDITOR` row and releases nothing. Only the owner's records move, which
is the behaviour the HLD requires and the audit below confirms.

**Rollout.** `V16` seeds the configuration defaults; `R__recompute_limit_usage.sql` then runs in the
same Flyway execution and rebuilds `limit_usage` from the permission tables, so no subject starts at
zero used. Both run inside Flyway's per-migration transaction. Overrides for the developer's own
account are inserted by hand afterwards — see *Decisions made*.

## Pseudo-code

The release path, where the stock/flow distinction lives:

```
release(subject, resource):
    if not limitsProperties.enabled: return          # kill-switch, as reserve

    config = configRepo.resolve(resource, subject)
    if config is absent:
        log.error("No limit configuration for resource {}", resource)
        return                                       # never block a delete

    if config.kind == FLOW: return                   # flow is consumed, never returned

    usageRepo.release(resource, subject)              # 0 rows affected is normal, not an error
```

```sql
UPDATE limit_usage
   SET used = GREATEST(used - 1, 0)
 WHERE resource = :resource AND subject = :subject
```

`GREATEST` makes the floor a property of the statement rather than of a read-modify-write, so
concurrent deletes cannot drive `used` negative. No row is created if none exists: a release with no
prior reserve is a no-op, not an insert of `-1`.

The recompute, one block per resource; the other two differ only in the table, the column and the
literal:

```sql
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

*As implemented:* the guard resolves the subject's **effective** kind the way `LimitConfigRepository`
does — its own override if one exists, otherwise the resource default (`subject IS NULL`) — rather than
only looking for a subject-level `FLOW` row. The design's original `NOT EXISTS ... AND c.kind = 'FLOW'`
spared a subject with a `FLOW` override but would have overwritten every subject that inherits a `FLOW`
default with a stock count. `IS DISTINCT FROM` makes a subject with no configuration at all (`NULL`
kind) count as stock, which is what a default-less resource is.

The guard appears in both statements. In the `DELETE` it protects a `FLOW`-configured subject's window
from being wiped; in the `INSERT` it stops that subject's surviving row from being counted as stock.
`ON CONFLICT DO NOTHING` is belt-and-braces — after the guarded `DELETE` there should be nothing left
to conflict with.

## Decisions made

- **The recompute is SQL in a repeatable migration, not a Java facade method.** Settles
  `HLD.md` > Open questions > *Recompute trigger* as "only as a repeatable migration". It is the
  smallest thing that works and needs no new cross-module API. The cost is that the ownership
  predicate is now stated twice — once in SQL, once implicitly by what the creation path writes — and
  that ADR-0006's "the module exposes a re-runnable recompute" is satisfied by a migration the module
  ships rather than by a method on its facade. `RecomputeMigrationTest` exists to hold the two
  statements of the predicate together.
- **Repeatable (`R__`) rather than a one-shot `V__` for the usage seed.** Re-running is safe by
  construction for stock resources, since the statement derives `used` from `COUNT(*)`; and T3 and T4
  each extend the same file, so their seeding comes free with the checksum change instead of
  accumulating a seed migration per resource. Repair means bumping the file, or executing it by hand.
- **Configuration defaults stay in a versioned `V16`.** Config seeding is genuinely one-shot: a
  repeatable would overwrite an operator's `UPDATE` on the next deploy, destroying the entire
  operating model.
- **The recompute is a full rebuild per resource, not an upsert over current owners.** A missed release
  leaves a subject permanently poorer — ADR-0006 names that as the design's principal cost — and only a
  rebuild that also deletes stale rows repairs it. After it runs, `limit_usage` for a resource *is* the
  permission table.
- **`FLOW`-configured subjects are excluded from the recompute.** The requirements allow any resource
  to be flow-configured per subject, and for such a subject `used` means "consumed this period" and
  `period_start` anchors a window — neither survives being overwritten with a stock count.
- **`release` resolves configuration and no-ops for `FLOW`.** The same per-subject freedom means the
  caller cannot know whether a delete should refund; only `limits` can. The cost is one extra query per
  delete, which is negligible against the delete itself.
- **`release` never throws.** Missing configuration logs at ERROR and returns. Failing a delete because
  the limits module is misconfigured would be strictly worse than under-counting, and unlike `reserve`
  there is no cost being guarded — nothing is consumed by giving up on a release.
- **Defaults are 5 recipes, 2 collections, 2 shopping lists, all `STOCK`.** Deliberately strict for
  launch. The known consequence is that existing testers land over the cap on day one; per the
  requirements that is a normal state — read and edit stay intact, only creation blocks.
- **The developer's override is manual post-deploy SQL, not seeded in `V16`.** Keeps a personal email
  out of a migration that runs in CI and every local database, and exercises the hand-edited-database
  operating model the feature is built around. The accepted cost is a window between deploy and that
  `INSERT` where the developer's own account is capped.
- **Reserve goes first in the create path, before validation and before any write.** It keeps one
  uniform ordering rule across every consumer, and since the reservation shares the caller's
  transaction, work that fails later releases it automatically.
- **Release goes last in the delete path, after the delete has succeeded.** Same transaction, so a
  failed delete does not hand back budget.
- **Existing suites run with limits off; limit tests live in a `@Nested` class that turns them on.**
  Verified empirically against this project (Spring Boot 4.1.0): `@TestPropertySource` on a `@Nested`
  class overrides the enclosing `@SpringBootTest` properties, the nested class gets its own context and
  a fresh Postgres container, and — the reason this shape is worth it — the enclosing instance's
  injected fields are wired from the *nested* context, so the outer suite's `restClient()`,
  `createRecipe()` and friends work unchanged inside it. Cost is roughly 4.6s per nested class. What it
  gives up: with limits off in the outer suites, a delete path that forgets to release is not caught
  ambiently by the other 83 tests, so each nested class asserts release explicitly instead.
- **Limit tests seed their own `limit_config` override for a unique subject** rather than relying on
  the shipped defaults, so a test never breaks when an operator changes a production number.
- **Nested-class teardown deletes through the API and touches `limit_usage` only for fabricated rows.**
  Deleting over HTTP exercises the real release path and leaves usage at zero as evidence it fired; a
  blanket `DELETE FROM limit_usage` in teardown would erase exactly that evidence and hide a missed
  release — the one bug these tests are the last line of defence against, since the outer suites run
  with limits off. `limit_config` still needs direct cleanup, because the per-test override collides
  with `UNIQUE NULLS NOT DISTINCT (resource, subject)` and because one test lowers the cap mid-run; so
  do usage rows fabricated for a subject that owns nothing and has no API presence. Teardown runs across
  all three test tokens, matching `MealPlanIntegrationTest`, since the sharing test creates as another
  user. Worth considering on top: ending teardown by asserting the subject's usage is zero, which
  attributes the failure to the test that broke release rather than to the next one.

## Assumptions to verify

- **Assumption:** the audit of destroying paths is complete — for these three resources exactly one
  path destroys a counted unit each (`RecipeService.deleteById`, `RecipesCollectionService.deleteById`,
  `ShoppingListService.deleteById`), all three requiring `OWNER`. Read during design: collection
  deletion sets `recipes.recipes_collection_id` to `NULL` rather than deleting recipes;
  `handleRecipesCollectionUnshared` only nulls the collection id on the owner's recipes; the
  `RecipeDeleted` event is consumed only by `planning`, which removes meal plan entries, not plans;
  unsharing removes `EDITOR` rows, which never consumed anything.
  **If wrong:** a missed release leaves the owner permanently poorer until the recompute is re-run.
  This is the failure mode `tasks.md` > Cross-task notes flags as the design's principal cost.
- **Assumption:** `role = 'OWNER'` in each of the three permission tables is exactly what the creation
  path writes, so the recompute's predicate and the creation check agree. Confirmed by reading all
  three `create`/`save` methods.
  **If wrong:** the seed and the live count diverge silently — the risk `tasks.md` names first for T2.
- **Assumption:** shopping-list deletion cascades its items, which will orphan `SHOPPING_LIST_ITEM`
  usage rows once T4 introduces them.
  **If wrong / when T4 lands:** T4 must release or delete per-list item usage inside
  `ShoppingListService.deleteById`. Out of scope here, flagged for T4.
- **Assumption:** Flyway's `default-schema: recipai` means the migrations can use unqualified table
  names, as `V15` already does, and the repeatable runs after all versioned migrations in the same
  execution.
  **If wrong:** the rebuild would run against the wrong schema or before `V16`, and startup fails loudly.
- **Assumption:** the three resource-key literals stay in step between the Java constants and the two
  migrations. Nothing in the compiler enforces this — there is no shared enum, by ADR-0006's design —
  but the nested recompute tests do: they create over HTTP under the Java constant and assert the
  recompute reproduces the count using the SQL literal.
  **If wrong:** caught by those tests. `V16` is not covered the same way, so a typo there still yields a
  default nobody resolves, surfacing as a 500 from `LimitConfigurationMissingException` on first use.
- **Assumption:** `ScriptUtils.executeSqlScript` splits `R__recompute_limit_usage.sql` correctly. The
  file holds only plain statements — no functions, no dollar-quoted bodies — so `;` separation should
  hold.
  **If wrong:** the helper splits the statements itself, or the assertions move behind a Flyway
  `migrate` against a throwaway datasource.
- **Assumption:** a pooled connection needs `SET search_path TO recipai` before the script runs, because
  the schema reaches Hibernate and Flyway through their own settings, not through the connection.
  HikariCP does not reset session state on return, hence the matching `RESET` in a `finally`.
  **If wrong:** the script resolves against `public` and fails on its first statement — an obvious
  failure rather than a silent one.
- **Assumption:** `spring.profiles.active: prod` applies in tests and nothing activates `dev`, so
  `recipai.limits.enabled` is `true` by default in every suite. Confirmed by running the probe: the
  outer class had to set it to `false` explicitly to see `false`.
  **If wrong:** the three suites would not need the class-level property at all.
- **Assumption:** no test method in the three suites creates more than the default cap in a single
  method — measured maxima are 3 recipes, 2 lists, 2 collections against caps of 5/2/2.
  **If wrong:** only relevant if the class-level disable is ever removed; lists and collections have
  zero headroom under 2/2.

## Required reading for implementation planning

- `plans/T1-task-design.md` — the module this task extends; its *Decisions made* govern the
  transaction, clock, kill-switch and refusal-contract behaviour that `release` must match.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitService.java` — the shape `release` mirrors,
  including how configuration is resolved and how the kill-switch is applied at the facade.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitUsageRepository.java` — the native-statement
  convention `release` follows; `limit_usage` is never written through JPA.
- `backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractionService.java` — the first consumer,
  and the call-site pattern the three new consumers copy.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java:86-125,177-199` — the create and
  delete paths being modified, including the S3 deletion that cannot fail the delete.
- `backend/src/main/resources/db/migration/V15__limits_schema.sql` — migration style, unqualified
  table names and the `EXTRACTION` seed row `V16` mirrors.
- `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsIntegrationTest.java` — the `JdbcClient`
  config-seeding and `TEST_LIMIT_*` isolation pattern the new limit tests reuse.
- `backend/src/test/java/xyz/stasiak/recipai/planning/MealPlanIntegrationTest.java` — how a suite
  already copes with a capped resource, and the `@Value`-read-the-cap convention worth not repeating
  here now that tests seed their own overrides.
- `docs/ADRs/0006-shared-limits-module.md` > *Consequences* — the drift obligation this task discharges.
- `HLD.md` > Feature areas > *Owner-scoped resources*, *Limits module (new)*, *Rollout* — the behaviours
  in scope.
- `docs/backend/standards/module-structure.md`, `integration-tests.md` — facade, logging and test
  conventions.
