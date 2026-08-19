# T3: Meal plan cap migrated onto the shared mechanism — Task Design

**Date:** 2026-08-19

## Summary

`planning` becomes the fourth consumer of the `limits` module, in exactly the shape T2 established for
recipes, collections and shopping lists: `create` reserves one `MEAL_PLAN` unit keyed by the owner's
email before writing, `delete` releases one. The module's own limit machinery —
`MealPlanProperties.maxOwnedPlans`, `MealPlanLimitExceededException` and the 409 that handler produced
— is deleted rather than adapted, so the cap lives in `limit_config` and the refusal is the shared 429.
`limits` gains no new surface at all; the only additions are a versioned config seed, a fourth block in
the repeatable recompute, and a one-line status-code swap in the mobile planning repository.

## Components and responsibilities

### Modified — `backend/src/main/java/xyz/stasiak/recipai/planning/`

- **`MealPlanService`** (MODIFY) — owns `MEAL_PLAN_RESOURCE`, takes a `LimitsFacade` in place of
  `MealPlanProperties`. `create` reserves at the head, replacing the `countOwnedByEmail` comparison;
  `delete` releases at the tail. No other method changes.
- **`MealPlanPermissionRepository`** (MODIFY) — `countOwnedByEmail` is deleted along with its only
  caller. The recompute counts owners in SQL and never goes through this repository.
- **`PlanningExceptionHandler`** (MODIFY) — the `MealPlanLimitExceededException` handler and its
  `HttpStatus.CONFLICT` go. Nothing replaces them in this module: `LimitExceededException` falls
  through to `LimitsExceptionHandler`, which this advice does not shadow.
- **`exception/MealPlanLimitExceededException`** (DELETE) — superseded by the shared refusal.
- **`MealPlanProperties`** (DELETE) — `maxOwnedPlans` was its only property.
- **`MealPlanConfig`** (DELETE) — its only job was `@EnableConfigurationProperties(MealPlanProperties.class)`,
  so it has nothing left to enable.

### Modified — application configuration

- **`application.yml`**, **`application-prod.yml`**, **`application-dev.yml`** (MODIFY) — the whole
  `recipai.meal-plan` block is removed. Dev raised the cap to 50; dev already runs with
  `recipai.limits.enabled: false`, so plans stay effectively uncapped there with no replacement key.

### New and modified — migrations, `backend/src/main/resources/db/migration/`

- **`V17__meal_plan_limit_config.sql`** (CREATE) — one default `limit_config` row. Versioned and
  therefore one-shot, so an operator's later `UPDATE` is never overwritten — the same reasoning that
  put the T2 defaults in `V16`.
- **`R__recompute_limit_usage.sql`** (MODIFY) — a fourth block, rebuilding `MEAL_PLAN` usage from
  `meal_plan_permissions`. The checksum change makes Flyway re-run the whole file on deploy, which
  seeds `MEAL_PLAN` and re-asserts the other three resources — harmless by construction, and the point
  at which any drift they accumulated since T2 is also repaired. This is the extension path T2's
  design anticipated.

### Modified — tests

- **`MealPlanIntegrationTest`** (MODIFY) — the `@Value("${recipai.meal-plan.max-owned-plans}")` field
  goes with the property. The class already carries `properties = "recipai.limits.enabled=false"`, so
  the 30-odd existing tests are untouched. `shouldEnforcePlanLimit` moves into a
  `@Nested @TestPropertySource(properties = "recipai.limits.enabled=true") class LimitsEnforced`, per
  `docs/backend/standards/integration-tests.md` > *Testing a Suite Whose Module Is Capped by `limits`*,
  and is joined there by: refusal carrying the 429 details, no `Retry-After` on a stock refusal,
  release admitting the next create, read and edit surviving while over cap, sharing leaving the
  recipient's standing untouched, and the recompute repairing drift and changing nothing on a second
  run. The nested class seeds its own `limit_config` override for a unique subject rather than relying
  on the shipped default of 2, and reuses `RecomputeMigration.run(dataSource)`.

### Modified — mobile

- **`mobile/lib/features/planning/meal_plan_repository.dart:93`** (MODIFY) — `409` becomes `429`. The
  thrown `Exception('Plan limit exceeded')` is unchanged, so the string match in
  `meal_plan_drawer.dart:134` and the message it shows keep working untouched. No other Dart file
  changes; the shared 429 handling that replaces this string matching is T5.

### Modified — documentation

- **`docs/backend/modules/planning/api.md`** — the create endpoint's 409 becomes 429, the note about
  `recipai.meal-plan.max-owned-plans` becomes the `MEAL_PLAN` limit, and delete gains the release,
  following the `recipes/api.md` shape T2 introduced.
- **`docs/backend/modules/planning/codebase_structure.md`** — `MealPlanProperties.java` and
  `MealPlanConfig.java` leave the tree, `MEAL_PLAN_RESOURCE` is noted on `MealPlanService`.
- **`docs/backend/modules/limits/db.md`**, **`codebase_structure.md`** — the fourth seeded default and
  the fourth recompute block.
- **`docs/project/architecture.md`** — `planning` joins the consumer list; the per-module caps table
  gains meal plans.
- **`docs/mobile/modules/planning/ui.md:14,88`** — the two "409 Conflict" references become 429.

## Interfaces and method signatures

### `limits` — unchanged

No new method, no new public type. `LimitsModuleArchitectureTest` needs no change, and `planning`
gains a dependency on `limits` exactly as the three T2 consumers did.

```java
public class LimitsFacade {
    public void reserve(String subject, String resource);   // throws LimitExceededException
    public void release(String subject, String resource);   // never throws
}
```

### `planning`

```java
class MealPlanService {
    static final String MEAL_PLAN_RESOURCE = "MEAL_PLAN";

    private final LimitsFacade limitsFacade;                 // replaces MealPlanProperties

    @Transactional MealPlanDto create(CreateMealPlanRequest request, String userEmail);
    @Transactional void delete(UUID id, String userEmail);
}
```

`jakarta.transaction.Transactional` here and `org.springframework...Transactional` inside
`LimitService` share one transaction manager with `REQUIRED` semantics, so the reservation joins the
caller's transaction — the same pairing all three T2 consumers already rely on.

### Configuration seed

```sql
-- V17__meal_plan_limit_config.sql
INSERT INTO limit_config (id, resource, subject, kind, max_value, period)
VALUES (gen_random_uuid(), 'MEAL_PLAN', NULL, 'STOCK', 2, NULL);
```

### Mobile

```dart
} else if (response.statusCode == 429) {
  throw Exception('Plan limit exceeded');
}
```

## Data flow

**Creation, granted.**

1. `MealPlanController.createMealPlan` extracts `jwt.getClaimAsString("email")` and calls
   `MealPlanService.create`, already `@Transactional`.
2. `create` calls `limitsFacade.reserve(userEmail, MEAL_PLAN_RESOURCE)` **first**, before the plan or
   the permission row is built — the uniform ordering T2 fixed for every consumer.
3. `LimitService.reserve` joins the caller's transaction, resolves configuration with no cache
   (subject override, else the `MEAL_PLAN` default), and runs the conditional upsert.
4. The `meal_plans` row and the `OWNER` `meal_plan_permissions` row are written. Anything that throws
   after step 3 rolls the reservation back with the transaction.

**Creation, refused.** The upsert affects zero rows, `LimitService` reads the standing and throws
`LimitExceededException`, and `LimitsExceptionHandler` renders the shared 429 with `resource`,
`kind`, `limit` and `used`. `kind` is `STOCK`, so there is no `retryAfterSeconds` property and no
`Retry-After` header. Nothing was written. The mobile repository maps the status to the same exception
it maps 409 to today, and the drawer's message is unchanged.

**Deletion.** `MealPlanService.delete` already requires `OWNER`. After
`permissionRepository.deleteAllByPlanId(id)` and `mealPlanRepository.deleteById(id)`, it calls
`limitsFacade.release(userEmail, MEAL_PLAN_RESOURCE)`, still inside the transaction, so a failed
delete hands back no budget. `meal_plan_entries` cascades on the FK and holds nothing counted.

**Sharing and unsharing.** Untouched, and nothing to change: a recipient gets an `EDITOR` row and is
never reserved against, unsharing removes an `EDITOR` row, and `unshareMealPlan` already refuses to
remove an `OWNER` row at all — so the one permission row that represents a consumed unit cannot be
destroyed by that path.

**Rollout.** `V17` seeds the default; the modified `R__recompute_limit_usage.sql` runs after it in the
same Flyway execution and rebuilds `MEAL_PLAN` usage from `meal_plan_permissions`, so no user starts
at zero used. With the default at 2, users who own 3 or more plans land over cap on deploy: per the
requirements that is a normal state — every plan stays readable and editable, only creation blocks.

## Pseudo-code

The create path, before and after:

```
# before
ownedCount = permissionRepository.countOwnedByEmail(userEmail)
if ownedCount >= properties.maxOwnedPlans():
    throw MealPlanLimitExceededException(properties.maxOwnedPlans())     # -> 409

# after
limitsFacade.reserve(userEmail, MEAL_PLAN_RESOURCE)                      # -> 429 on refusal
```

The recompute block, mirroring the three T2 blocks. Note the table is `meal_plan_permissions` —
plural, unlike `recipe_permission`, `recipes_collection_permission` and `shopping_list_permission`:

```sql
DELETE FROM limit_usage u
 WHERE u.resource = 'MEAL_PLAN'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject = u.subject),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW';

INSERT INTO limit_usage (resource, subject, used, period_start)
SELECT 'MEAL_PLAN', p.email, COUNT(*), now()
  FROM meal_plan_permissions p
 WHERE p.role = 'OWNER'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'MEAL_PLAN' AND c.subject = p.email),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'MEAL_PLAN' AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW'
 GROUP BY p.email
    ON CONFLICT (resource, subject) DO NOTHING;
```

The `FLOW` guard is carried over verbatim from T2 rather than simplified away: a subject configured as
flow for meal plans has a window in `period_start` that a stock count would destroy.

## Decisions made

- **The `MEAL_PLAN` default is 2, not the 5 that `max-owned-plans` carries today.** Aligns with T2's
  deliberately strict launch defaults (5 recipes, 2 collections, 2 lists) rather than preserving the
  old number. The known consequence is that existing users owning 3 to 5 plans are over cap the moment
  this deploys — accepted, because over-limit is a normal state that blocks only creation, and because
  the whole point of moving the cap into the database is that it is now one `UPDATE` to raise.
- **The mobile change is the status code and nothing else.** `429` replaces `409`; the exception
  message and the drawer's string match stay as they are. Parsing the ProblemDetail body for `limit`
  and `used` would build a planning-local version of exactly what T5 is scoped to build once and share,
  and would be thrown away.
- **No 409/429 dual mapping in the client.** Backend and client ship together — `tasks.md` names a
  backend-only deploy as this task's stated risk — so a compatibility branch would guard a window that
  is not supposed to exist, and T5 would delete it.
- **`MealPlanProperties`, `MealPlanConfig` and the three yml blocks are deleted outright.**
  `maxOwnedPlans` was the record's only property and enabling it was the config class's only job;
  keeping either as an empty shell would leave a second, dead place to look for the plan cap.
- **Dev gets no replacement for `max-owned-plans: 50`.** Dev runs with `recipai.limits.enabled: false`,
  so plans are uncapped there either way, and a dev-only override row would have to live in a migration
  that also runs in CI and prod.
- **`countOwnedByEmail` is deleted rather than left for the recompute.** The recompute is SQL in a
  migration and never touches the repository; an unused query that looks like the ownership predicate
  invites a future caller to re-derive the count outside `limits`.
- **`MealPlanLimitExceededException` is deleted, not retained as a subclass of the shared one.** The
  scope calls it superseded; keeping it would give `planning` a second exception type that renders the
  same 429 and a handler that has to stay in step with `LimitsExceptionHandler`.
- **Config seed in a versioned `V17`, usage seed by extending the repeatable `R__`.** Exactly T2's
  split and reasoning: config seeding is one-shot or it overwrites an operator's edit, while the usage
  rebuild is safe to re-run and extending the one file re-asserts the earlier resources for free.
- **Reserve first in create, release last in delete.** The uniform ordering rule across all consumers,
  which the shared transaction makes safe in both directions.
- **The existing suite keeps limits off at class level; the limit tests live in a `@Nested` class that
  turns them on.** The pattern T2 proved and wrote into `docs/backend/standards/integration-tests.md`;
  following it keeps the ~30 existing meal plan tests unaffected by a default of 2.
- **The migrated limit test seeds its own override instead of reading the shipped default.** As in T2 —
  a test must not break when an operator changes a production number, and the old `@Value` field is
  going away with the property anyway.

## Assumptions to verify

- **Assumption:** `MealPlanService.delete` is the only path that destroys a counted meal plan unit.
  Read during design: `unshareMealPlan` throws rather than remove an `OWNER` row, `deleteAllByPlanId`
  runs only inside `delete`, `meal_plan_entries` cascades on the plan FK but is not counted, and
  `handleRecipeDeleted` only rewrites entries into placeholders. No event in the codebase deletes a plan.
  **If wrong:** a missed release leaves the owner permanently poorer until the recompute re-runs — the
  failure mode ADR-0006 names as the design's principal cost.
- **Assumption:** the permission table is `meal_plan_permissions` (plural), unlike the three tables the
  existing recompute blocks read, and `role = 'OWNER'` is exactly what `create` writes.
  **If wrong:** the migration fails loudly on a bad table name, but a wrong predicate would diverge
  silently from the live count.
- **Assumption:** Flyway re-runs a repeatable migration whose checksum changed, after all versioned
  migrations in the same execution, so `V17` is in place before the new block reads `limit_config`.
  Consistent with how `V16` and the original `R__` landed together in T2.
  **If wrong:** `MEAL_PLAN` usage is not seeded and every existing owner starts at zero used.
- **Assumption:** the `MEAL_PLAN` literal stays in step across `MealPlanService`, `V17` and the
  recompute block. Nothing in the compiler enforces it — there is no shared enum, by ADR-0006's design.
  **If wrong:** the nested recompute test catches a mismatch between the Java constant and the recompute
  literal, since it creates over HTTP and asserts the recompute reproduces the count. `V17` is not
  covered that way, so a typo there surfaces as a 500 from `LimitConfigurationMissingException` on the
  first create.
- **Assumption:** `MealPlanIntegrationTest` is the only test suite that creates meal plans — confirmed
  by grep, no other suite references `/meal-plans` or `MealPlanDto`.
  **If wrong:** another suite starts failing at the default of 2 and needs the class-level disable.
- **Assumption:** `meal_plan_drawer.dart:134` is the only consumer of the `'Plan limit exceeded'`
  message, so leaving the message alone leaves the user-visible behaviour identical.
  **If wrong:** some other screen shows a generic error for plan refusals until T5.
- **Assumption:** nothing outside this repo reads `recipai.meal-plan.max-owned-plans` — no deployment
  manifest or env override sets it.
  **If wrong:** an unknown property in a deployed configuration, which Spring Boot ignores rather than
  failing on, so this is cosmetic.

## Required reading for implementation planning

- `plans/T2-task-design.md` — the pattern this task repeats; its *Decisions made* govern reserve/release
  ordering, the migration split, the `FLOW` guard and the nested-test shape.
- `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanService.java:50-70,89-107` — the create
  check being replaced and the delete path gaining the release.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionService.java` — the
  closest sibling consumer: same shape, same size, same permission-table ownership model.
- `backend/src/main/java/xyz/stasiak/recipai/planning/PlanningExceptionHandler.java` — the advice losing
  its 409 handler; confirm nothing else in it catches broadly enough to swallow `LimitExceededException`.
- `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the file being extended, and
  the block to copy.
- `backend/src/main/resources/db/migration/V11__meal_planning_schema.sql` — the `meal_plan_permissions`
  table the new block reads, and the entries cascade.
- `backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java:1047-1330` — the
  `LimitsEnforced` nested class to mirror, including teardown and the recompute assertions.
- `backend/src/test/java/xyz/stasiak/recipai/RecomputeMigration.java` — the helper the migrated tests
  reuse.
- `docs/backend/standards/integration-tests.md` > *Testing a Suite Whose Module Is Capped by `limits`* —
  the standard T2 wrote for exactly this migration.
- `mobile/lib/features/planning/meal_plan_repository.dart:86-98` and `meal_plan_drawer.dart:126-142` —
  the status mapping and its single consumer.
- `docs/ADRs/0006-shared-limits-module.md` — the 409→429 consequence this task discharges.
- `HLD.md` > Feature areas > *Owner-scoped resources*, *Rejection contract*, *Mobile* — the behaviours
  in scope.
