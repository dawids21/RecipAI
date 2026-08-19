# T3: Meal plan cap migrated onto the shared mechanism — Implementation Plan

**Date:** 2026-08-19

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/backend/standards/integration-tests.md` > *Testing a Suite Whose Module Is Capped by `limits`*
  — the class-level disable + `@Nested` enable shape, the "seed your own override" rule, and the
  "teardown deletes through the API, never through `limit_usage`" rule this task's test work follows
  verbatim.
- `docs/backend/standards/integration-tests.md` > the `LimitsFacade.currentUsage` note — the nested
  class reads standing through the facade, never with hand-written SQL against `limit_usage`.
- `docs/backend/standards/module-structure.md` — cross-module access goes through the facade
  (`LimitsFacade`), and the `@ControllerAdvice`-per-module rule that explains why deleting
  `planning`'s 409 handler is enough for `LimitsExceptionHandler` to take over.
- `docs/backend/standards/configuration-profiles.md` — the three yml files losing `recipai.meal-plan`.
- `docs/backend/modules/limits/db.md` — the seeded-defaults table and the recompute description, both
  of which this task extends.
- `docs/backend/modules/planning/api.md`, `codebase_structure.md` — the two planning docs the design
  names as deliverables.

**Design & ADRs**

- `plans/T3-task-design.md` in full — every decision below is already made there; do not re-decide.
- `plans/T2-task-design.md` > *Decisions made* — governs reserve-first/release-last ordering, the
  versioned-config / repeatable-usage migration split, and the `FLOW` guard carried into the new block.
- `plans/T2-implementation-plan.md` > *Risks surfaced during planning* — the shared-context and
  `now()`-timezone findings apply unchanged to the fourth nested class.
- `docs/ADRs/0006-shared-limits-module.md` > *Consequences* — the 409→429 consequence this task
  discharges, and the missed-release failure mode the recompute repairs.
- `HLD.md` > Feature areas > *Owner-scoped resources*, *Rejection contract*, *Mobile*.

**Code to mirror**

- `backend/src/main/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionService.java:21,26,53-55,109`
  — the closest sibling consumer: the `static final String …_RESOURCE` constant, the `LimitsFacade`
  field, `reserve` as the first statement of `create`, `release` as the last statement of `deleteById`.
- `backend/src/test/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionIntegrationTest.java:334-560`
  — the `LimitsEnforced` nested class to copy: imports, `SUBJECT`, `@BeforeEach seedOverride`,
  `@AfterEach tearDown`, `usedFor`, `seedConfigOverride`, and the test bodies.
- `backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java:1047-1330` — the fuller
  variant of the same class, including the recompute assertions.
- `backend/src/test/java/xyz/stasiak/recipai/RecomputeMigration.java` — the runner the recompute tests
  reuse; no change needed.
- `backend/src/main/resources/db/migration/V16__owner_scoped_limit_config.sql` — the shape `V17` copies.
- `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the three existing blocks;
  the fourth is a copy with `MEAL_PLAN` and `meal_plan_permissions` substituted.
- `backend/src/main/resources/db/migration/V11__meal_planning_schema.sql` — confirms the table is
  `meal_plan_permissions` (plural) with columns `email`, `plan_id`, `role`.
- `docs/backend/modules/recipes/api.md:1-30,147` — the api.md shape T2 introduced (budget preamble,
  *Refusal Response* block, `429` in the endpoint's `Errors` line) that `planning/api.md` adopts.
- `mobile/lib/features/planning/meal_plan_repository.dart:86-98` — the status ladder gaining `429`.

## File inventory

**Backend — main**

- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanService.java` — add
  `MEAL_PLAN_RESOURCE` and `LimitsFacade`, drop `MealPlanProperties`, reserve in `create`, release in
  `delete`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanPermissionRepository.java` —
  delete `countOwnedByEmail` and its `@Query`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/planning/PlanningExceptionHandler.java` —
  delete `handleMealPlanLimitExceeded` and its 409.
- **DELETE** `backend/src/main/java/xyz/stasiak/recipai/planning/exception/MealPlanLimitExceededException.java`
  — superseded by the shared refusal.
- **DELETE** `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanProperties.java` —
  `maxOwnedPlans` was its only property.
- **DELETE** `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanConfig.java` — nothing left
  to `@EnableConfigurationProperties`.

**Backend — resources**

- **CREATE** `backend/src/main/resources/db/migration/V17__meal_plan_limit_config.sql` — one default
  `MEAL_PLAN` `STOCK` row, `max_value` 2.
- **MODIFY** `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — fourth block
  rebuilding `MEAL_PLAN` usage from `meal_plan_permissions`.
- **MODIFY** `backend/src/main/resources/application.yml` — remove the `recipai.meal-plan` block.
- **MODIFY** `backend/src/main/resources/application-dev.yml` — remove the `recipai.meal-plan` block.
- **MODIFY** `backend/src/main/resources/application-prod.yml` — remove the `recipai.meal-plan` block.

**Backend — tests**

- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/planning/MealPlanIntegrationTest.java` — drop
  the `@Value` field and the `@Value`/`HttpStatus`-only imports that go stale, delete
  `shouldEnforcePlanLimit`, add the `LimitsEnforced` `@Nested` class and its imports.

**Mobile**

- **MODIFY** `mobile/lib/features/planning/meal_plan_repository.dart:93` — `409` becomes `429`.

**Docs named by the design**

- **MODIFY** `docs/backend/modules/planning/api.md` — budget preamble + *Refusal Response*, POST's
  `Errors` line 409→429, the `recipai.meal-plan.max-owned-plans` note becomes the `MEAL_PLAN` limit,
  DELETE gains the release note.
- **MODIFY** `docs/backend/modules/planning/codebase_structure.md` — remove `MealPlanConfig.java` and
  `MealPlanProperties.java`; note `MEAL_PLAN_RESOURCE` on `MealPlanService.java`.
- **MODIFY** `docs/backend/modules/limits/db.md` — fourth row in *Seeded Configuration* (naming
  `V17__meal_plan_limit_config.sql`), fourth resource/table in *Recompute*.
- **MODIFY** `docs/backend/modules/limits/codebase_structure.md` — `planning` joins the *Consumers*
  list.
- **MODIFY** `docs/project/architecture.md` — `planning` joins the `limits` consumer sentence (line
  26) and its own module description (line 27) stops calling the cap "configurable" via properties.
  See *Risks surfaced during planning* — the "per-module caps table" the design names does not exist.
- **MODIFY** `docs/mobile/modules/planning/ui.md:14,88` — "409 Conflict" / "(409)" become 429.

## Step-by-step plan

### 1. Migrations

Create `V17__meal_plan_limit_config.sql` with the single default row, copying `V16`'s formatting:

```sql
INSERT INTO limit_config (id, resource, subject, kind, max_value, period)
VALUES (gen_random_uuid(), 'MEAL_PLAN', NULL, 'STOCK', 2, NULL);
```

Append the fourth block to `R__recompute_limit_usage.sql` under a `-- MEAL_PLAN` comment, copying the
`SHOPPING_LIST` block and substituting the resource literal and `meal_plan_permissions` for
`shopping_list_permission`. Keep the `FLOW` guard on both statements verbatim. Update the file's
header comment only if it enumerates resources (it does not — it says "the owner-scoped resources").

- Files: `backend/src/main/resources/db/migration/V17__meal_plan_limit_config.sql`,
  `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql`
- Verify: `./mvnw -pl . test -Dtest=RecipAiApplicationTests` from `backend/` starts the context
  against a fresh container and applies both migrations. Then, against that database,
  `SELECT resource, kind, max_value FROM recipai.limit_config WHERE subject IS NULL;` returns four
  rows including `MEAL_PLAN | STOCK | 2`.

### 2. Wire `planning` onto `limits` and delete the module's own limit machinery

In `MealPlanService`: add `static final String MEAL_PLAN_RESOURCE = "MEAL_PLAN";` above the fields,
replace the `MealPlanProperties properties` field with `LimitsFacade limitsFacade`, make
`limitsFacade.reserve(userEmail, MEAL_PLAN_RESOURCE)` the **first** statement of `create` (before the
`log.debug`, matching `RecipesCollectionService.create`), delete the `countOwnedByEmail` comparison
and the throw, and make `limitsFacade.release(userEmail, MEAL_PLAN_RESOURCE)` the **last** statement of
`delete`, after `mealPlanRepository.deleteById(id)`. Add the `xyz.stasiak.recipai.limits.LimitsFacade`
import. Do not add or remove any `@Transactional`.

Delete `countOwnedByEmail` from `MealPlanPermissionRepository`, delete the
`handleMealPlanLimitExceeded` method from `PlanningExceptionHandler`, and delete
`MealPlanLimitExceededException`, `MealPlanProperties` and `MealPlanConfig`. The handler's
`import …planning.exception.*;` stays (five other exception types still use it).

Remove the `meal-plan:` block from all three yml files; each `recipai:` mapping retains other keys, so
no mapping is left empty.

- Files: `MealPlanService.java`, `MealPlanPermissionRepository.java`, `PlanningExceptionHandler.java`,
  `exception/MealPlanLimitExceededException.java` (deleted), `MealPlanProperties.java` (deleted),
  `MealPlanConfig.java` (deleted), `application.yml`, `application-dev.yml`, `application-prod.yml`
- Verify: `./mvnw -q compile` from `backend/` succeeds, and
  `grep -rn "meal-plan\|MealPlanProperties\|MealPlanLimitExceeded\|countOwnedByEmail" backend/src/main`
  returns only `MealPlanController`'s `@RequestMapping("/meal-plans")` and
  `SecurityConfig`'s `/meal-plans/**`.

### 3. Take the existing meal plan suite off the removed property

Delete the `@Value("${recipai.meal-plan.max-owned-plans}") private int maxOwnedPlans;` field
(`MealPlanIntegrationTest.java:36-37`), its
`org.springframework.beans.factory.annotation.Value` import, and the `shouldEnforcePlanLimit` test
(lines 340-355). Leave the class-level
`properties = "recipai.limits.enabled=false"` exactly as it is — the string must stay byte-identical
to the three T2 suites so all four share one Spring context.

- Files: `backend/src/test/java/xyz/stasiak/recipai/planning/MealPlanIntegrationTest.java`
- Verify: `./mvnw test -Dtest=MealPlanIntegrationTest` — the remaining ~30 tests pass.

### 4. Manual end-to-end check of enforcement

With the app running against a database carrying `V17`, and the `MEAL_PLAN` default at 2: two
`POST /meal-plans` succeed, the third returns 429 with a `ProblemDetail` naming `MEAL_PLAN`, `STOCK`,
`limit` 2, `used` 2 and no `Retry-After`. `UPDATE recipai.limit_config SET max_value = 3 WHERE
resource = 'MEAL_PLAN' AND subject IS NULL;` then a fourth `POST` succeeds with no restart.
`DELETE /meal-plans/{id}` drops `used` by one.

- Files: none
- Verify: the curl sequence above, plus
  `SELECT used FROM recipai.limit_usage WHERE resource = 'MEAL_PLAN' AND subject = '<email>';`
  tracking 1 → 2 → 2 (refused) → 1 (after delete).

### 5. The `LimitsEnforced` nested suite

Add the nested class at the end of `MealPlanIntegrationTest`, copied from
`RecipesCollectionIntegrationTest.LimitsEnforced` with `MEAL_PLAN` / `MealPlanService.MEAL_PLAN_RESOURCE`
/ `createMealPlan` / `deleteMealPlan` substituted. `MEAL_PLAN_RESOURCE` is package-private and the test
is in `xyz.stasiak.recipai.planning`, so it is reachable without widening visibility.

Two departures from the sibling classes, both forced by this suite:

- The nested `@AfterEach tearDown` must delete the plans over HTTP **itself**, exactly as the siblings
  do. JUnit runs the nested `@AfterEach` before the enclosing one, so the outer `cleanup()` at
  `MealPlanIntegrationTest.java:49` fires too late to make `assertThat(usedFor(SUBJECT)).isZero()` hold.
  The outer sweep then runs against an already-empty list — harmless.
- No test in this class may create a recipe. The nested context runs with limits **on** and shares one
  database with the three T2 nested classes, so a recipe there is charged against the live `RECIPE`
  cap. None of the cases below needs one.

New imports on the file: `org.junit.jupiter.api.BeforeEach`, `org.junit.jupiter.api.Nested`,
`org.springframework.beans.factory.annotation.Autowired`,
`org.springframework.jdbc.core.simple.JdbcClient`,
`org.springframework.test.context.TestPropertySource`, `xyz.stasiak.recipai.RecomputeMigration`,
`xyz.stasiak.recipai.limits.LimitUsageDetails`, `xyz.stasiak.recipai.limits.LimitsFacade`,
`javax.sql.DataSource`, `java.sql.Timestamp`, `java.time.Duration`, `java.time.Instant`,
`java.time.temporal.ChronoUnit`. `MediaType`, `ParameterizedTypeReference`, `Map`, `List`, `UUID`,
`HttpStatus` are already imported.

- Files: `backend/src/test/java/xyz/stasiak/recipai/planning/MealPlanIntegrationTest.java`
- Verify: `./mvnw test -Dtest=MealPlanIntegrationTest` — outer and nested both green. Then
  `./mvnw test` — the full suite passes and the four nested classes do not interfere.

### 6. Mobile status-code flip

`mobile/lib/features/planning/meal_plan_repository.dart:93` — `409` becomes `429`. Nothing else
changes: the thrown `Exception('Plan limit exceeded')` and `meal_plan_drawer.dart:134`'s string match
stay as they are.

- Files: `mobile/lib/features/planning/meal_plan_repository.dart`
- Verify: `dart analyze` from `mobile/` is clean, `flutter test` passes, and
  `grep -rn "409" mobile/lib/features/planning/` returns nothing.

### 7. Documentation named by the design

Rewrite `docs/backend/modules/planning/api.md`'s opening to carry the budget preamble and a
*Refusal Response* block in `recipes/api.md`'s shape (`MEAL_PLAN`, `STOCK`, limit 2), change POST's
`Errors` line from `409 Conflict (plan limit exceeded)` to `429 Too many requests (plan cap reached)`,
replace the `recipai.meal-plan.max-owned-plans` note with the `MEAL_PLAN` limit in `limit_config`, and
add the release to DELETE's note. Drop `MealPlanConfig.java` and `MealPlanProperties.java` from
`planning/codebase_structure.md` and note `MEAL_PLAN_RESOURCE` on `MealPlanService.java`. Add the
`MEAL_PLAN` row and `V17` to `limits/db.md` > *Seeded Configuration* and `meal_plan_permissions` to
*Recompute*. Add `planning` to `limits/codebase_structure.md` > *Consumers*. In
`docs/project/architecture.md`, add `planning` to the consumer list on line 26 and replace
"configurable owner plan limit" on line 27. Change both "409" references in
`docs/mobile/modules/planning/ui.md` (lines 14 and 88) to 429.

- Files: `docs/backend/modules/planning/api.md`, `docs/backend/modules/planning/codebase_structure.md`,
  `docs/backend/modules/limits/db.md`, `docs/backend/modules/limits/codebase_structure.md`,
  `docs/project/architecture.md`, `docs/mobile/modules/planning/ui.md`
- Verify: `grep -rn "409\|max-owned-plans\|MealPlanProperties\|MealPlanConfig" docs/backend docs/mobile docs/project`
  returns nothing outside `docs/tasks/`.

## Test plan

**Unit tests**

_N/A — the project has no unit-test layer for services; `limits` behaviour is covered by
`LimitsIntegrationTest` from T1 and the change here is wiring, not new logic._

**Integration tests**

`MealPlanIntegrationTest` (outer, `recipai.limits.enabled=false`) — no new cases; `shouldEnforcePlanLimit`
is removed and the remaining ~30 must still pass unchanged.

`MealPlanIntegrationTest.LimitsEnforced` (`@Nested`, `recipai.limits.enabled=true`, own
`limit_config` override of 2 for `user@example.com`):

- `shouldRefuseThirdCreateWithLimitDetails` — two plans succeed; the third returns 429 with
  `application/problem+json` and body `resource=MEAL_PLAN`, `kind=STOCK`, `limit=2`, `used=2`.
- `shouldCarryNoRetryAfterOnStockRefusal` — the same refusal has no `Retry-After` header and no
  `retryAfterSeconds` key.
- `shouldAdmitNextCreateAndDropStandingAfterDelete` — `used` reaches 2, `DELETE` drops it to 1, the
  next create succeeds and returns it to 2.
- `shouldAllowReadAndUpdateWhileOverCapButKeepCreationRefused` — with `max_value` lowered to 1 after
  two plans exist, `GET /meal-plans` still lists the plan and `PUT /meal-plans/{id}` still renames it,
  while creation stays 429.
- `shouldLeaveRecipientStandingUntouchedOnShareAndUnshare` — owner's `used` is 1; after
  `POST /{id}/share` to `user2@example.com` that recipient's `used` is 0, and still 0 after
  `POST /{id}/unshare`.
- `shouldRepairDriftToActualOwnedCountViaRecompute` — two plans, `UPDATE limit_usage SET used = 99`,
  `RecomputeMigration.run(dataSource)` restores 2.
- `shouldClearUsageForSubjectThatOwnsNothing` — a fabricated `MEAL_PLAN` usage row for
  `ghost@example.com` is gone after the recompute (`currentUsage` empty).
- `shouldSpareFlowConfiguredSubjectFromRecompute` — a subject with a `MEAL_PLAN` `FLOW` override keeps
  its `used` and `period_start` across a recompute.
- `shouldChangeNothingOnSecondRecomputeRun` — one plan; two consecutive recompute runs both yield 1.

Every assertion is scoped to `('MEAL_PLAN', <subject>)`; none counts rows in `limit_usage` globally,
because the nested contexts share one database.

**Flutter widget/integration tests**

_N/A — `mobile/test/features/planning/` does not exist; the change is a one-token status code and the
exception it throws is unchanged, so the drawer's behaviour is identical. Covered by manual
verification below._

**Manual verification**

- `tasks.md` > T3 > *How to verify*, in full: create plans until 429 (not 409); raise the limit with
  SQL and create another with no restart; in the app, attempt a create past the cap and confirm the
  snackbar still reads "Cannot create plan: You have reached the maximum number of plans" rather than
  a generic failure.
- Deploy-shaped check: against a database that already holds meal plans with no `MEAL_PLAN` usage
  rows, apply `V17` + the changed repeatable in one Flyway run and confirm `limit_usage` matches
  `SELECT email, COUNT(*) FROM recipai.meal_plan_permissions WHERE role = 'OWNER' GROUP BY email`.

## Verification checklist

- [ ] `./mvnw test` from `backend/` — all new and existing tests pass (only
      `ExtractionIntegrationTest`'s real-provider test stays `@Disabled`)
- [ ] `dart analyze` and `flutter test` from `mobile/` are clean
- [ ] `V17` and the changed repeatable apply cleanly against a fresh database, and re-running the
      repeatable by hand is a no-op
- [ ] `tasks.md` > T3 > *How to verify* succeeds end-to-end, including the in-app check
- [ ] The `MEAL_PLAN` literal is spelled identically in `MealPlanService`, `V17` and the recompute
      block — `grep -rn "MEAL_PLAN" backend/src/main` shows exactly three spellings and no fourth
- [ ] `grep -rn "meal-plan\.\|max-owned-plans\|MealPlanProperties\|MealPlanConfig\|MealPlanLimitExceeded\|countOwnedByEmail" backend/`
      returns nothing
- [ ] All four HTTP suites still carry the byte-identical `recipai.limits.enabled=false` string
- [ ] `LimitsModuleArchitectureTest` passes with no edit
- [ ] No `@Transactional` added or removed in `MealPlanService`
- [ ] `PlanningExceptionHandler` no longer references `HttpStatus.CONFLICT`, and no other planning
      handler catches broadly enough to shadow `LimitExceededException`
- [ ] The nested `tearDown` ends with `usedFor(SUBJECT)` at zero — evidence release fired
- [ ] Logs at `INFO` are clean on the happy path; refusals at `WARN`
- [ ] The design's *Assumptions to verify* are resolved or explicitly carried forward (see below)

## Risks surfaced during planning

- **Risk:** `docs/project/architecture.md` has no "per-module caps table". The design instructs that
  "the per-module caps table gains meal plans"; the file contains only a mobile-layer table, an
  integrations table and an env-var table. The actual cap prose lives in the `limits` module bullet
  (line 26, which enumerates consumers), the `planning` bullet (line 27, "configurable owner plan
  limit") and the *Usage limits* line (55).
  **Why it matters:** an implementer following the design literally would either invent a table that
  the file's structure does not call for, or skip the edit entirely and leave line 27 claiming the cap
  is a configuration property.
  **Mitigation:** edit lines 26 and 27 as step 7 describes. Line 55 already reads correctly for stock
  caps and needs no change.

- **Risk:** `MealPlanIntegrationTest` has an outer `@AfterEach cleanup()` (line 49); the three T2
  suites have no outer `@AfterEach` at all, so their nested classes were written against a blank slate.
  JUnit runs the nested `@AfterEach` first and the enclosing one second.
  **Why it matters:** a nested `tearDown` that leans on the outer sweep to delete plans would run its
  `assertThat(usedFor(SUBJECT)).isZero()` while the plans still exist, and fail on every test.
  **Mitigation:** the nested `tearDown` deletes over HTTP itself, exactly as the siblings do. Called
  out in step 5. The outer sweep afterwards is a harmless no-op.

- **Risk:** the new nested class will share one Spring context and one Postgres container with the
  three T2 nested classes — their merged configuration is byte-identical (same `@Import`, same
  `@SpringBootTest` property string, same `@TestPropertySource`), and the declaring class is not part
  of Spring's context-cache key. Confirmed identical across all four outer suites.
  **Why it matters:** the nested meal plan tests run with `RECIPE`, `RECIPES_COLLECTION` and
  `SHOPPING_LIST` caps live and against data the other nested classes may have left. A test that
  created a recipe would be charged against the `RECIPE` cap of 5, and any assertion phrased over
  `limit_usage` as a whole would answer for four resources at once.
  **Mitigation:** no recipe creation in the nested class (none of the nine cases needs one), and every
  assertion scoped to `('MEAL_PLAN', <subject>)` with the expected count derived from what the test
  itself created. This is T2's finding, restated because a fourth class makes it easier to trip.

- **Risk:** the default drops from 5 (`max-owned-plans`) to 2, and `V17` plus the recompute land in the
  same deploy.
  **Why it matters:** every existing user owning 3 or more plans is over cap the moment this ships, and
  the recompute ensures they know it — nobody starts at zero used. The design accepts this, but it is
  a user-visible regression that arrives without warning, on a resource where 5 was the shipped
  promise.
  **Mitigation:** accepted per the design. Worth naming in the PR description alongside the raise-by-SQL
  path (`UPDATE limit_config SET max_value = … WHERE resource = 'MEAL_PLAN' AND subject IS NULL`), so
  whoever handles the first complaint has the command to hand.

- **Risk:** repairing `MEAL_PLAN` drift later needs psql access or a cosmetic checksum bump — nothing
  exposes the recompute at runtime, and after this task no scheduled work edits the file again.
  **Why it matters:** T2 could assume "T3/T4 both edit this file anyway"; T4 is the last such task.
  **Mitigation:** accepted, unchanged from T2. Carry the on-call note into the PR description.

**Assumptions from `T3-task-design.md`, resolved during planning**

- *"`MealPlanService.delete` is the only path that destroys a counted unit"* — **confirmed.**
  `deleteAllByPlanId` has exactly one caller (`delete`); `unshareMealPlan` throws
  `MealPlanAccessDeniedException` before removing an `OWNER` row; `meal_plan_entries` cascades on
  `plan_id` (`V11`) and is not counted; `handleRecipeDeleted` only rewrites entries. No facade reaches
  `MealPlanService.delete` — `MealPlanController` is its only caller.
- *"The table is `meal_plan_permissions` (plural) and `role = 'OWNER'` is what `create` writes"* —
  **confirmed** in `V11__meal_planning_schema.sql` and `MealPlanService.create`.
- *"`MealPlanIntegrationTest` is the only suite that creates meal plans"* — **confirmed**; `/meal-plans`
  and `MealPlanDto` appear in no other test file.
- *"`meal_plan_drawer.dart:134` is the only consumer of `'Plan limit exceeded'`"* — **confirmed**; the
  string appears exactly twice in `mobile/`, at the throw site and that match.
- *"Nothing else references the deleted types"* — **confirmed**;
  `MealPlanLimitExceededException` is referenced only by its own file and `PlanningExceptionHandler`,
  `countOwnedByEmail` only by its declaration and `MealPlanService`, and `MealPlanConfig` by nothing.
- *"Nothing outside this repo reads `recipai.meal-plan.max-owned-plans`"* — **carried forward,
  unverifiable from the repo.** No deployment manifest in-tree sets it; Spring Boot ignores an unknown
  property, so the worst case is cosmetic.
- *"Flyway runs the changed repeatable after all versioned migrations in the same execution"* —
  **carried forward.** It is Flyway's documented ordering and matches how `V16` and the original `R__`
  landed together in T2, but this task's own first deploy is the first time it is exercised with a
  block that reads a row inserted by a versioned migration in the same run. The deploy-shaped manual
  check in *Test plan* is what proves it.
