# T3: Collections and meal plans migrated — migration complete — Implementation Plan

**Date:** 2026-08-27

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/backend/standards/module-structure.md` — the facade-per-module rule, the `dto`/`exception`
  package layout, and the exception-handler shape both rewritten handlers must land in
  (`@RestControllerAdvice`, package-private method, bare `ProblemDetail` from
  `forStatusAndDetail(...)` + `setTitle(...)`).
- `docs/backend/standards/java-patterns.md` — records for DTOs, derived query methods over `@Query`
  where a derived name will do (`findByIdInOrderByCreatedAtAsc`), and the
  package-private-unless-crossing-a-boundary rule that decides which resource-key constants can be
  named from another module.
- `docs/backend/standards/integration-tests.md` — `@SpringBootTest(RANDOM_PORT)` +
  `TestcontainersConfiguration` + `TestSecurityConfiguration` + `RestClient`, the `shouldXxxWhenYyy`
  naming, and the rule that a suite seeds and reads through the module's own business surface.
- `docs/backend/modules/recipes/{module.md,api.md,db.md}` and
  `docs/backend/modules/planning/{module.md,api.md,db.md}` — what this task's changes make stale.
- `docs/backend/modules/permissions/{module.md,api.md,db.md}` — the facade contract, the invite
  handshake, the refusal rules, and the per-module copy note `V22__`/`V23__` extend.
- `docs/backend/modules/limits/{module.md,db.md}` and `docs/backend/modules/shopping-lists/db.md` —
  the recompute's per-resource sources, and the two `docs/tasks/` references T1 left behind that this
  task's docs pass removes.
- `docs/project/local-development.md` — `./recipai.sh start-backend`, the dev-profile
  `Bearer alice` → `alice@local.test` bypass, and the `backend/http/` suite conventions.

**Design & ADRs**

- `plans/T3-task-design.md` — the whole document; **Interfaces and method signatures**, **Data flow**
  and **Pseudo-code** are assumed here rather than re-derived.
- `plans/T2-task-design.md` and `plans/T2-implementation-plan.md` — the composition rule this task
  keeps, and the two deferrals it collects: the collection access check and the surviving
  `RecipesCollectionPermission` joins.
- `plans/T1-task-design.md` — the facade contract, the two-table model, the per-task copy, the
  `/permissions` rename and the shared public types this task finishes rolling out.
- `tasks.md` > T3 — Scope, Out of scope, and the five-step **How to verify** that gates the task.
- `tasks.md` > Cross-task notes — why the drop closes the accepted legacy-permission delete gap, and
  why the migration mechanism is not re-decided here.
- `docs/ADRs/0007-shared-permissions-module.md` — the boundary the four de-joined queries restore in
  full, and why composition stays with `recipes`.
- `docs/ADRs/0008-invite-label-snapshot.md` — why the collection and plan names are supplied by the
  inviting module and stored opaquely.

**Code to mirror**

- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — the migrated
  service, method for method: `findAll` off `accessibleResources` with the empty-map short-circuit,
  `share…(ShareRequest, …)` calling `invite` with the resource's name as the label, `unshare…`
  handing both guards to `revoke`, `getPermissions` delegating to the facade, `deleteById` calling
  `resourceDeleted` before the repository delete and `limitsFacade.release` last.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListController.java` — the
  `ShareRequest` / `UnshareRequest` signatures and `GET /{id}/permissions` returning
  `List<PermissionDto>`.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListRepository.java` —
  `findByIdInOrderByCreatedAtAsc(Collection<UUID>)`, the derived query both list repositories adopt.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java` — T2's migrated service:
  `resolveAccess`, the `grantOwner` on create, and the `getPermissions` shape.
- `backend/src/main/java/xyz/stasiak/recipai/permissions/PermissionsFacade.java` — the exact method
  set available: `roleOf`, `requireEditor`, `requireOwner`, `accessibleResources`, `ownerEmail`,
  `grantOwner`, `invite`, `revoke`, `getPermissions`, `resourceDeleted`.
- `backend/src/main/java/xyz/stasiak/recipai/permissions/PermissionsExceptionHandler.java` — the
  handler shape the two rewritten handlers copy, and the 403 body both modules inherit.
- `backend/src/main/resources/db/migration/V21__recipe_permission_to_resource_permission.sql` — the
  one-statement copy `V22__` and `V23__` mirror; bare table names (Flyway's `default-schema: recipai`
  supplies the schema).
- `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the `RECIPE` and
  `SHOPPING_LIST` slices are exactly what the `RECIPES_COLLECTION` and `MEAL_PLAN` slices become,
  including the FLOW-exclusion `COALESCE(override, default) IS DISTINCT FROM 'FLOW'` pattern that
  must survive the repointing.
- `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java` and
  `.../recipes/RecipeIntegrationTest.java` — the handshake helpers to copy (`getPendingInvites`,
  `acceptInvite`, `findPendingInviteId`, `acceptPending…Invite`) and the rewritten sharing
  assertions.
- `backend/http/recipes.http` and `backend/http/shopping-lists.http` — the migrated `.http` shape:
  `GET …/{id}/permissions`, `"role": "EDITOR"` in the share body, and the pointer to
  `backend/http/invites.http` for answering the invite.

## File inventory

**Migrations**

- **CREATE** `backend/src/main/resources/db/migration/V22__recipes_collection_permission_to_resource_permission.sql` — copies `recipes_collection_permission` in under `'RECIPES_COLLECTION'`.
- **CREATE** `backend/src/main/resources/db/migration/V23__meal_plan_permission_to_resource_permission.sql` — copies `meal_plan_permissions` in under `'MEAL_PLAN'`.
- **CREATE** `backend/src/main/resources/db/migration/V24__drop_legacy_permission_tables.sql` — drops all four legacy permission tables.
- **MODIFY** `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the `RECIPES_COLLECTION` and `MEAL_PLAN` slices read `resource_permission`.

**Collections — modified**

- **MODIFY** `…/recipes/collections/RecipesCollectionService.java` — `PermissionsFacade` replaces the permission repository; the key constant becomes `public`; gains `accessibleCollectionIds`.
- **MODIFY** `…/recipes/collections/RecipesCollectionRepository.java` — `findAllByUserEmail` becomes the derived `findByIdInOrderByCreatedAtAsc`.
- **MODIFY** `…/recipes/collections/RecipesCollectionController.java` — shared request records, `/permissions` path, `List<PermissionDto>`.
- **MODIFY** `…/recipes/collections/RecipesCollectionsExceptionHandler.java` — one branch left, in the standard's handler shape.

**Collections — deleted**

- **DELETE** `…/recipes/collections/RecipesCollectionPermission.java` — superseded by `ResourcePermission`.
- **DELETE** `…/recipes/collections/RecipesCollectionPermissionId.java` — superseded by `ResourcePermissionId`.
- **DELETE** `…/recipes/collections/RecipesCollectionPermissionRepository.java` — no reader left.
- **DELETE** `…/recipes/collections/UserRole.java` — superseded by `ResourceRole`.
- **DELETE** `…/recipes/collections/dto/SharedUserDto.java` — superseded by `PermissionDto`.
- **DELETE** `…/recipes/collections/dto/ShareRecipesCollectionRequest.java` — superseded by `ShareRequest`.
- **DELETE** `…/recipes/collections/dto/UnshareRecipesCollectionRequest.java` — superseded by `UnshareRequest`.
- **DELETE** `…/recipes/collections/exception/RecipesCollectionAccessDeniedException.java` — superseded by `ResourceAccessDeniedException`.

**Recipes — modified**

- **MODIFY** `…/recipes/RecipeService.java` — `resolveAccess`' collection half becomes a `roleOf` call; both list methods resolve collection ids; two catch blocks retype; gains `accessibleRecipeIds`.
- **MODIFY** `…/recipes/RecipeRepository.java` — both list queries lose the `RecipesCollectionPermission` join and take `collectionIds`.
- **MODIFY** `…/recipes/RecipeFacade.java` — gains the two accessible-id reads `planning`'s calendar needs (see **Risks**).

**Planning — modified**

- **MODIFY** `…/planning/MealPlanService.java` — `PermissionsFacade` replaces the permission repository across every guard, share, unshare and delete path.
- **MODIFY** `…/planning/MealPlanRepository.java` — `findAllByUserEmail` becomes the derived `findByIdInOrderByCreatedAtAsc`.
- **MODIFY** `…/planning/MealPlanEntryRepository.java` — both queries drop their permission joins; the calendar's access `CASE` becomes three id tests.
- **MODIFY** `…/planning/MealPlanCalendarService.java` — gains `PermissionsFacade` and resolves the three id sets.
- **MODIFY** `…/planning/MealPlanController.java` — shared request records, `/permissions` path, `List<PermissionDto>`.
- **MODIFY** `…/planning/dto/MealPlanDto.java` — `role` becomes `ResourceRole`.
- **MODIFY** `…/planning/PlanningExceptionHandler.java` — the access-denied branch goes; four stay.

**Planning — deleted**

- **DELETE** `…/planning/MealPlanPermission.java` — superseded by `ResourcePermission`.
- **DELETE** `…/planning/MealPlanPermissionId.java` — superseded by `ResourcePermissionId`.
- **DELETE** `…/planning/MealPlanPermissionRepository.java` — no reader left.
- **DELETE** `…/planning/UserRole.java` — superseded by `ResourceRole`.
- **DELETE** `…/planning/dto/SharedUserDto.java` — superseded by `PermissionDto`.
- **DELETE** `…/planning/dto/ShareMealPlanRequest.java` — superseded by `ShareRequest`.
- **DELETE** `…/planning/dto/UnshareMealPlanRequest.java` — superseded by `UnshareRequest`.
- **DELETE** `…/planning/exception/MealPlanAccessDeniedException.java` — superseded by `ResourceAccessDeniedException`.

**Tests**

- **MODIFY** `…/test/…/recipes/collections/RecipesCollectionIntegrationTest.java` — helpers move to the shared types and the `/permissions` path; sharing tests assert the handshake; the 403 body assertion retargets.
- **MODIFY** `…/test/…/planning/MealPlanIntegrationTest.java` — same mechanical move, plus the three calendar cases and the default-FLOW recompute case.
- **MODIFY** `…/test/…/recipes/RecipeIntegrationTest.java` — the collection-share helper moves to `ShareRequest` + accept; the collection-derived cases gain an accept step; two new `collectionIds` cases.

**HTTP suite**

- **MODIFY** `backend/http/collections.http` — the renamed path, the `role` field, and the invite pointer.
- **MODIFY** `backend/http/meal-plans.http` — the same, plus the `role` field in the plan DTO's shape.

**Docs** (named by `task-design.md` > Modified and by `tasks.md` > Cross-task notes; produced by the `docs-updating` step at the end of the task)

- **MODIFY** `docs/backend/modules/recipes/{module.md,api.md,db.md}` — collection permissions moved out, share is an invite, `users` renamed to `permissions`, the two dropped tables gone from `db.md`.
- **MODIFY** `docs/backend/modules/planning/{module.md,api.md,db.md}` — same, for meal plans.
- **MODIFY** `docs/backend/modules/permissions/db.md` — `V22__`/`V23__` join the per-module copy note; `V24__` records the drop.
- **MODIFY** `docs/backend/modules/limits/{module.md,db.md}` — every recompute slice reads `resource_permission`; the `docs/tasks/` reference goes.
- **MODIFY** `docs/backend/modules/shopping-lists/db.md` — `shopping_list_permission` is gone, not merely unread; the `docs/tasks/` reference goes.
- **MODIFY** `docs/INDEX.md` — the recipes and planning `db.md` lines drop the two removed tables.

## Step-by-step plan

1. **Copy both legacy tables into `resource_permission`** — add
   `V22__recipes_collection_permission_to_resource_permission.sql` and
   `V23__meal_plan_permission_to_resource_permission.sql` with exactly the two statements in
   `task-design.md` > Schema. Bare table names; no new tables and no indexes — `V20__` built both.
   Neither source table has a `created_at` column, so nothing is lost by not naming one: the
   destination's `DEFAULT now()` fills it, as it did for shopping lists and recipes. Leave both
   tables in place, unread and unwritten from step 2 on. **Before writing these files, run the
   one-OWNER-per-resource check from `task-design.md` > Assumptions to verify against production** —
   `uq_resource_permission_owner` aborts the migration otherwise.
   - Files: `backend/src/main/resources/db/migration/V22__recipes_collection_permission_to_resource_permission.sql`,
     `backend/src/main/resources/db/migration/V23__meal_plan_permission_to_resource_permission.sql`
   - Verify: `cd backend && ./mvnw test -Dtest=RecipAiApplicationTests` — Flyway applies V22 and V23
     against a fresh Testcontainers Postgres and the context boots.

2. **Migrate `recipes.collections` onto `permissions`, de-join `recipes`' two list queries, repoint
   the `RECIPES_COLLECTION` recompute slice, delete the superseded collections types, and bring the
   three affected suites over — one commit.** These cannot be sequenced apart: deleting the
   `RecipesCollectionPermission` entity breaks `RecipeRepository`'s two queries and
   `MealPlanEntryRepository.findCalendarEntries` at context load, and once
   `RecipesCollectionService` writes `resource_permission` the recompute's `RECIPES_COLLECTION` slice
   reads a table that no longer receives rows, failing that suite's recompute cases. Work in this
   order:

   a. **`RecipesCollectionService`** — drop `RecipesCollectionPermissionRepository` for
      `PermissionsFacade`; make `RECIPES_COLLECTION_RESOURCE` `public`. Then, mirroring
      `ShoppingListService`:
      - `findAll` — `accessibleCollectionIds(userEmail)` (2e), return `List.of()` when empty,
        otherwise `findByIdInOrderByCreatedAtAsc(ids)`.
      - `findById` — load the collection (404 if absent), then
        `permissionsFacade.requireEditor(...)`. Stays `public`: it is still how `recipes` gets a
        collection's **name**.
      - `create` — `permissionsFacade.grantOwner(...)` replaces the hand-built
        `RecipesCollectionPermission`.
      - `updateById` — load (404), `requireEditor`, save.
      - `deleteById` — `existsById` (404), `requireOwner`,
        `permissionsFacade.resourceDeleted(RECIPES_COLLECTION_RESOURCE, id)` in place of
        `deleteAllByRecipesCollectionId`, then `recipesCollectionRepository.deleteById(id)`, then
        `limitsFacade.release(...)` — unchanged and still last.
      - `shareRecipesCollection(ShareRequest request, UUID collectionId, String requesterEmail)` —
        load the collection with `findById` on the repository (404 if absent; the name is needed for
        the label, so `existsById` no longer suffices), `requireEditor(...)`, then
        `permissionsFacade.invite(RECIPES_COLLECTION_RESOURCE, collectionId, request.email(), request.role(), collection.getName(), requesterEmail)`.
        The already-shared no-op goes; `permissions` refuses with 409 `ALREADY_HAS_ACCESS`.
      - `unshareRecipesCollection` — exactly the design's pseudo-code: `existsById` (404),
        `requireEditor`, **read `roleOf(...).isPresent()` into a local before** calling
        `permissionsFacade.revoke(...)`, and publish `RecipesCollectionUnshared` only if that local
        was true. Both unshare guards (never an `OWNER`, never yourself) go — `revoke` holds them and
        throws before returning, so the event cannot fire for a refused unshare.
      - `getSharedUsers` → `getPermissions(UUID collectionId, String userEmail)` returning
        `List<PermissionDto>`: `existsById` (404), `requireEditor`, then
        `permissionsFacade.getPermissions(RECIPES_COLLECTION_RESOURCE, collectionId)`.
      Every path loads or `existsById`-checks the collection before asking about access, so 404 still
      wins over 403.
   b. **`RecipesCollectionRepository`** — delete the `@Query` and `findAllByUserEmail`; declare
      `List<RecipesCollection> findByIdInOrderByCreatedAtAsc(Collection<UUID> ids);`, mirroring
      `ShoppingListRepository`. Import `java.util.Collection`; the `@Query` and `List` imports may go.
   c. **`RecipesCollectionController`** — `ShareRequest` / `UnshareRequest` in the two signatures,
      `getSharedUsers` → `getPermissions` on `@GetMapping("/{id}/permissions")` returning
      `List<PermissionDto>`. Both `share` and `unshare` already return 204 — no status change. No
      `SecurityConfig` change: `/collections/**` already covers the renamed path.
   d. **`RecipesCollectionsExceptionHandler`** — `@RestControllerAdvice` with one package-private
      `ProblemDetail handleRecipesCollectionNotFound(...)`; the access-denied branch goes.
   e. **`RecipeFacade`** — the `recipes` module's single public boundary gains the two reads
      `planning`'s calendar needs, so no resource key leaves the module (see **Risks**).
      `RECIPE_RESOURCE` stays package-private on `RecipeService`, unchanged.
      ```java
      // Direct RECIPE permissions only: collection-derived access is composed by the caller,
      // which is why this is not the set RecipeFacade.getRecipes filters on.
      public Set<UUID> getDirectlyAccessibleRecipeIds(String userEmail);
      public Set<UUID> getAccessibleCollectionIds(String userEmail);
      ```
      Per `module-structure.md` > Facade Pattern the facade delegates single-service calls straight
      through, so add the two sources alongside it:
      - **`RecipeService`** — package-private
        `Set<UUID> accessibleRecipeIds(String userEmail)` returning
        `permissionsFacade.accessibleResources(RECIPE_RESOURCE, userEmail).keySet()`. `findAll` and
        `findAllUnassigned` keep using the full `accessibleResources` **map** — `findAllUnassigned`
        short-circuits on its emptiness — so this method is an addition, not a refactor of them.
      - **`RecipesCollectionService`** — `public Set<UUID> accessibleCollectionIds(String userEmail)`
        over `RECIPES_COLLECTION_RESOURCE`; `findAll` (2a) and `RecipeService`'s two list methods
        (2f) both read it instead of calling `accessibleResources` themselves.
      `RecipeFacade` injects `RecipesCollectionService` for the second delegate — intra-module, and
      no cycle: `RecipeService` already depends on it and nothing depends on `RecipeFacade` inside
      `recipes`. Leave `RecipeFacade.getRecipes` alone: it filters on `recipeService.findAll`, the
      **composed** set, which is not what either new method returns.
   f. **`RecipeService`** — `resolveAccess`' collection branch becomes
      `permissionsFacade.roleOf(RecipesCollectionService.RECIPES_COLLECTION_RESOURCE, recipe.getRecipesCollectionId(), userEmail).isPresent()`
      per the design's pseudo-code; the `try`/`catch` and the
      `RecipesCollectionNotFoundException` import go. The two catch blocks in `findById` and
      `updateById` that suppress the collection **name** stay, retyped to
      `catch (ResourceAccessDeniedException _)`. `findAll` and `findAllUnassigned` each resolve
      `recipesCollectionService.accessibleCollectionIds(userEmail)` (2e) alongside the recipe ids
      and pass both; `findAll` keeps **no** short-circuit, `findAllUnassigned` keeps its
      short-circuit on the **recipe** map only. Drop the
      `RecipesCollectionAccessDeniedException` import.
   g. **`RecipeRepository`** — both queries exactly as `task-design.md` > Interfaces sketches them:
      `findAllByUserEmail(recipeIds, collectionIds)` with no join and no `DISTINCT`, and
      `findAllUnassignedByUserEmail(recipeIds, collectionIds)` with `NOT IN :collectionIds`. Both
      lose `:email`.
   h. **`MealPlanEntryRepository`** — in `findCalendarEntries` only, replace the
      `RecipesCollectionPermission` `EXISTS` with `r.recipesCollectionId IN :collectionIds` and add
      the `@Param("collectionIds") Collection<UUID> collectionIds` argument. The
      `MealPlanPermission` join and the `ResourcePermission` `EXISTS` stay until step 3.
   i. **`MealPlanCalendarService`** — inject `RecipeFacade` and pass
      `recipeFacade.getAccessibleCollectionIds(userEmail)` through to the query. The plan
      intersection and recipe id set arrive in step 3.
   j. **Delete** the eight superseded `recipes.collections` types listed in the inventory.
   k. **`R__recompute_limit_usage.sql`** — the `RECIPES_COLLECTION` `INSERT` reads
      `FROM resource_permission p WHERE p.resource_type = 'RECIPES_COLLECTION' AND p.role = 'OWNER'`,
      matching the `RECIPE` slice. The `DELETE` above it is unchanged — it keys off
      `limit_usage.subject`, not the permission table. `MEAL_PLAN` stays as it is.
   l. **Suites, mechanically** — `RecipesCollectionIntegrationTest`: `shareRecipesCollection` sends
      `new ShareRequest(email, ResourceRole.EDITOR)`, `unshareRecipesCollection` sends
      `new UnshareRequest(email)`, `getSharedUsers` becomes `getPermissions` over
      `/collections/{id}/permissions` returning `List<PermissionDto>`, every `UserRole` becomes
      `ResourceRole`, the handshake helpers plus an `acceptPendingCollectionInvite` are copied from
      `ShoppingListIntegrationTest`, and every test that shares then reads as the recipient gains an
      accept step. `shouldReturn403WhenAccessingOthersRecipesCollection`'s body assertions retarget
      to `"Access denied to RECIPES_COLLECTION with id: "` and `"Resource Access Denied"`.
      `shouldBeIdempotentWhenSharingTwice` becomes a 409 case (see **Test plan**).
      `RecipeIntegrationTest`: `shareCollection` moves to `ShareRequest` and every collection-derived
      case gains an accept for the recipient; the two inline share/unshare blocks in
      `shouldRemoveOwnedRecipesFromCollectionWhenUnshared` move to the shared records.
      `MealPlanIntegrationTest` needs no change in this step.
   - Files: `…/recipes/collections/RecipesCollectionService.java`, `RecipesCollectionRepository.java`,
     `RecipesCollectionController.java`, `RecipesCollectionsExceptionHandler.java`, the eight
     deletions, `…/recipes/RecipeFacade.java`, `RecipeService.java`, `RecipeRepository.java`,
     `…/planning/MealPlanEntryRepository.java`, `MealPlanCalendarService.java`,
     `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql`,
     `…/test/…/recipes/collections/RecipesCollectionIntegrationTest.java`,
     `…/test/…/recipes/RecipeIntegrationTest.java`
   - Verify: `cd backend && ./mvnw -q compile` after (j), then
     `cd backend && ./mvnw test -Dtest=RecipesCollectionIntegrationTest,RecipeIntegrationTest,MealPlanIntegrationTest,ShoppingListIntegrationTest,InviteIntegrationTest`,
     and finally `cd backend && ./mvnw test`.

3. **Migrate `planning` onto `permissions`, de-join its two entry queries, repoint the `MEAL_PLAN`
   recompute slice, delete the superseded planning types, and bring `MealPlanIntegrationTest` over —
   one commit.** Same coupling as step 2: deleting `MealPlanPermission` breaks `MealPlanRepository`
   and both `MealPlanEntryRepository` queries at context load, and repointing the recompute has to
   land with the service that stops writing the old table.

   a. **`MealPlanService`** — drop `MealPlanPermissionRepository` for `PermissionsFacade`:
      - `findAll` — one `accessibleResources(MEAL_PLAN_RESOURCE, userEmail)` map, `List.of()` when
        empty, then `findByIdInOrderByCreatedAtAsc(access.keySet())` mapped with
        `access.get(plan.getId())`. The per-plan lookup and its unreachable
        `MealPlanAccessDeniedException` go.
      - `create` — `permissionsFacade.grantOwner(...)`; the DTO carries `ResourceRole.OWNER`.
      - `update` — load (404), `ResourceRole role = requireEditor(...)`, save, `toDto(savedPlan, role)`.
      - `delete` — `existsById` (404), `requireOwner`,
        `permissionsFacade.resourceDeleted(MEAL_PLAN_RESOURCE, id)` in place of `deleteAllByPlanId`,
        `mealPlanRepository.deleteById(id)` (entries cascade), `limitsFacade.release(...)` last.
      - `createEntry`, `deleteEntry` — `existsById` (404) then `requireEditor`.
      - `updateEntry` — both `existsById` checks stay in their current order, then `requireEditor`
        for `planId` **and** for `request.planId()`, preserving today's two-plan guard.
      - `generateShoppingListItems` — the per-plan loop keeps `existsById` (404) and swaps the
        permission lookup for `requireEditor`, then calls
        `entryRepository.findEntriesWithRecipes(planIds, dates)`.
      - `shareMealPlan(ShareRequest request, UUID planId, String requesterEmail)` — load the plan
        with `findById` (404; the name is the label), `requireEditor`, then
        `permissionsFacade.invite(MEAL_PLAN_RESOURCE, planId, request.email(), request.role(), plan.getName(), requesterEmail)`.
        The already-shared no-op goes.
      - `unshareMealPlan` — `existsById` (404), `requireEditor`, then
        `permissionsFacade.revoke(MEAL_PLAN_RESOURCE, planId, targetEmail, requesterEmail)`. No
        `roleOf` read here: `planning` publishes no unshare event.
      - `getSharedUsers` → `getPermissions(UUID planId, String userEmail)` returning
        `List<PermissionDto>`: `existsById` (404), `requireEditor`, then
        `permissionsFacade.getPermissions(MEAL_PLAN_RESOURCE, planId)`.
      - `toDto(MealPlan, ResourceRole)` — retype the parameter.
      `handleRecipeDeleted` is untouched.
   b. **`MealPlanRepository`** — delete the `@Query` and `findAllByUserEmail`; declare
      `List<MealPlan> findByIdInOrderByCreatedAtAsc(Collection<UUID> ids);`.
   c. **`MealPlanEntryRepository`** — `findEntriesWithRecipes` drops the `MealPlanPermission` join
      and the `:email` parameter outright. `findCalendarEntries` drops the same join, drops
      `:email`, and rewrites the `ResourcePermission` `EXISTS` as `e.recipeId IN :recipeIds`, so the
      access `CASE` is exactly the three-branch form in `task-design.md` > Interfaces. Both queries'
      signatures land as sketched there. **No `permissions` or `recipes` entity is named in this
      module's JPQL afterwards** — check that before moving on; it is the ADR-0007 boundary this task
      restores.
   d. **`MealPlanCalendarService`** — complete the design's pseudo-code: inject `PermissionsFacade`
      alongside step 2's `RecipeFacade`, intersect `requestedPlanIds` with
      `accessibleResources(MEAL_PLAN_RESOURCE, userEmail).keySet()`, return `Map.of()` when the
      intersection is empty (today's join dropped an unreachable plan silently, and this preserves
      that — no 403), then take `recipeFacade.getDirectlyAccessibleRecipeIds(userEmail)` and the
      collection set from step 2 and call
      `findCalendarEntries(startDate, endDate, planIds, recipeIds, collectionIds)`. `recipeIds` and
      `collectionIds` may legitimately be empty and must **not** short-circuit — they feed `CASE`
      branches, not a `WHERE` filter. `validateDateRange` stays first, so a bad range still 400s
      before any facade call.
   e. **`MealPlanDto`** — `ResourceRole role`.
   f. **`MealPlanController`** — `ShareRequest` / `UnshareRequest` in the two signatures,
      `getSharedUsers` → `getPermissions` on `@GetMapping("/{id}/permissions")` returning
      `ResponseEntity<List<PermissionDto>>`. Both already return 204 from share and unshare. No
      `SecurityConfig` change: `/meal-plans/**` already covers the renamed path.
   g. **`PlanningExceptionHandler`** — delete the `MealPlanAccessDeniedException` branch; the other
      four stay untouched.
   h. **Delete** the eight superseded `planning` types listed in the inventory.
   i. **`R__recompute_limit_usage.sql`** — the `MEAL_PLAN` `INSERT` reads
      `FROM resource_permission p WHERE p.resource_type = 'MEAL_PLAN' AND p.role = 'OWNER'`. After
      this edit **no slice in the file reads a per-module permission table** — grep it to confirm.
   j. **`MealPlanIntegrationTest`, mechanically** — `shareMealPlan` sends
      `new ShareRequest(email, ResourceRole.EDITOR)`, `unshareMealPlan` sends
      `new UnshareRequest(email)`, `getSharedUsers` becomes `getPermissions` over
      `/meal-plans/{id}/permissions`, every `UserRole` becomes `ResourceRole`, an
      `acceptPendingMealPlanInvite` joins the existing `acceptPendingRecipeInvite`, and every test
      that shares then acts as the recipient gains an accept step —
      `shouldAllowEditorToEditSharedPlan`, `shouldAllowEditorToCreateEntries`,
      `shouldPreventEditorFromDeletingPlan`, `shouldUnshareMealPlan`, `shouldPreventUnsharingOwner`,
      `shouldAllowEditorToShareMealPlan`, and both calendar cases.
      `shouldBeIdempotentWhenSharingTwice` becomes a 409 case.
   - Files: `…/planning/MealPlanService.java`, `MealPlanRepository.java`,
     `MealPlanEntryRepository.java`, `MealPlanCalendarService.java`, `MealPlanController.java`,
     `dto/MealPlanDto.java`, `PlanningExceptionHandler.java`, the eight deletions,
     `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql`,
     `…/test/…/planning/MealPlanIntegrationTest.java`
   - Verify: `cd backend && ./mvnw -q compile` after (h), then
     `cd backend && ./mvnw test -Dtest=MealPlanIntegrationTest`, then `cd backend && ./mvnw test`.

4. **Drop the four legacy permission tables** — add `V24__drop_legacy_permission_tables.sql` with
   exactly the four `DROP TABLE` statements in `task-design.md` > Schema. Each drop takes its
   indexes and its foreign key to the resource table with it, which is what closes the
   delete-a-resource-with-legacy-rows gap `tasks.md` > Cross-task notes accepts for the T1–T3 window.
   This is the irreversible step and the last one that touches the schema; nothing has read these
   tables since step 3. `R__recompute_limit_usage.sql` needs no further edit — Flyway runs repeatable
   migrations after the versioned ones, so the body written in step 3 is what runs after `V24__`.
   - Files: `backend/src/main/resources/db/migration/V24__drop_legacy_permission_tables.sql`
   - Verify: `cd backend && ./mvnw test` — the whole chain applies from an empty database and every
     suite, including the recompute cases in all four `LimitsEnforced` blocks, stays green. Then
     grep the tree: `grep -rn "recipes_collection_permission\|meal_plan_permissions\|recipe_permission\|shopping_list_permission" backend/src/main` must return only `V4__`, `V6__`, `V7__`, `V11__`,
     `V19__`, `V22__`, `V23__` and `V24__`.

5. **New coverage** — the cases from the **Test plan** below that do not already exist: the calendar
   trio in `MealPlanIntegrationTest`, the empty-collection-set and unassigned cases in
   `RecipeIntegrationTest`, and the refusal, invite-cancel and unshare-detach cases in
   `RecipesCollectionIntegrationTest`.
   - Files: `…/test/…/planning/MealPlanIntegrationTest.java`,
     `…/test/…/recipes/RecipeIntegrationTest.java`,
     `…/test/…/recipes/collections/RecipesCollectionIntegrationTest.java`
   - Verify: `cd backend && ./mvnw test`

6. **HTTP suite** — in `backend/http/collections.http` and `backend/http/meal-plans.http`: rename the
   shared-users request to `GET {{baseUrl}}/…/{{id}}/permissions`, add `"role": "EDITOR"` to both
   share bodies, and note in a `###` comment that share creates a pending invite `bob` answers from
   `backend/http/invites.http`. The meal-plan file's create/list responses now carry
   `"role": "OWNER"` from `ResourceRole` — no request change, but drop any comment implying the old
   enum.
   - Files: `backend/http/collections.http`, `backend/http/meal-plans.http`
   - Verify: `./recipai.sh start-backend`, then run `backend/http/collections.http` and
     `backend/http/meal-plans.http` top to bottom as `alice`, answering each invite from
     `backend/http/invites.http` as `bob`; then re-run `recipes.http` and `shopping-lists.http` to
     confirm all four modules still work end to end (`tasks.md` > T3 > How to verify, step 5).
7. **Docs** — run the `docs-updating` step for the files listed in the inventory.
   - Files: `docs/backend/modules/{recipes,planning,permissions,limits,shopping-lists}/…`,
     `docs/INDEX.md`
   - Verify: no `docs/tasks/` references and no past-tense change narration remain in
     `docs/backend/modules/`; `grep -rn "docs/tasks" docs/backend/modules/` is empty.

## Test plan

**Unit tests**

_N/A — the project has no service-level unit tests for this kind of logic; both modules are exercised
through their HTTP surface, per `docs/backend/standards/integration-tests.md`._

**Integration tests**

`RecipesCollectionIntegrationTest` (`user1@example.com` owns and shares, `user2@example.com` is
invited, `user@example.com` is the third party):

- `shouldShareAndUnshareRecipesCollections` — **rewritten**: after sharing, user2 is still 403 on the
  collection and it is absent from their `GET /collections`; `/permissions` reads
  `[user1 OWNER pending=false, user2 EDITOR pending=true]`; user2 accepts; the collection appears in
  their list; user1 unshares; user2 is 403 again and `/permissions` is back to one row.
- `shouldAllowEditorsToShareAndUnshare` — **rewritten** with accepts for both recipients.
- `shouldPreventUnsharingOwner` — unchanged in intent; the 403 now comes from
  `PermissionsExceptionHandler`.
- `shouldRefuseSecondShareWhenTargetAlreadyHasAccess` — replaces
  `shouldBeIdempotentWhenSharingTwice`: share, accept, share again → 409 with
  `reason = ALREADY_HAS_ACCESS`.
- `shouldRefuseSecondShareWhileFirstInviteIsPending` — share twice without accepting → 409.
- `shouldCancelPendingInviteOnUnshare` — share, unshare before accepting, the invite is gone from
  user2's `GET /invites` and `/permissions` is back to one row.
- `shouldReturn403WhenAccessingOthersRecipesCollection` — body assertions retargeted to
  `Access denied to RECIPES_COLLECTION with id: …` / `Resource Access Denied`.
- `shouldNotDetachRecipesWhenCancellingAPendingInvite` — user2 is invited but has not accepted;
  unsharing fires no `RecipesCollectionUnshared`, so a recipe user2 owns elsewhere is untouched.
  (The `roleOf`-before-`revoke` read.)
- `shouldClearPendingInvitesWhenCollectionIsDeleted` — share, delete the collection as owner, the
  invite is gone from user2's `GET /invites`.
- `LimitsEnforced` — the five existing recompute cases stay and must pass unchanged against the
  repointed slice; `shouldLeaveRecipientBalanceUntouchedOnShareAndUnshare` gains an accept step.

`MealPlanIntegrationTest`:

- The sharing cases listed in step 3(j), each with an accept step inserted.
- `shouldRefuseSecondShareWhenTargetAlreadyHasAccess` — replaces
  `shouldBeIdempotentWhenSharingTwice`; 409 with `ALREADY_HAS_ACCESS`.
- `shouldListPlansWithRoleFromOneAccessMap` — owner sees `OWNER`, accepted invitee sees `EDITOR`, in
  `createdAt` order.
- Calendar trio (the three `hasRecipeAccess` branches, all with the plan shared and accepted):
  - `shouldGrantCalendarRecipeAccessThroughDirectRecipePermission` — user2 accepts a recipe invite;
    `hasRecipeAccess` is true. (Retitle of `shouldIndicateRecipeAccessGrantedThroughResourcePermission`.)
  - `shouldGrantCalendarRecipeAccessThroughSharedCollection` — the entry's recipe sits in a
    collection user2 has accepted; `hasRecipeAccess` is true and no recipe permission exists.
  - `shouldRefuseCalendarRecipeAccessWhenNeitherPathReaches` — neither; `hasRecipeAccess` is false
    and the request does **not** 500 with both id sets empty. (Extends
    `shouldIndicateRestrictedRecipeAccess` with an explicit assertion that user2 holds no recipe and
    no collection.)
- `shouldReturnEmptyCalendarWhenNoRequestedPlanIsAccessible` — user2 asks for user1's unshared plan;
  an empty map, not a 403 — the intersection preserving today's silent join behaviour.
- `shouldIncludeOnlyAccessiblePlansWhenSomeRequestedPlansAreNot` — a mix of one shared and one
  unshared plan returns only the shared plan's entries.
- `shouldGenerateShoppingListItemsForAnEditorWithoutTheDeJoinedFilter` — an accepted invitee
  generates from a shared plan, confirming `findEntriesWithRecipes` still returns their entries with
  the join gone.
- `LimitsEnforced` — the four existing recompute cases pass unchanged; add
  `shouldSpareSubjectWithoutOverrideWhenResourceDefaultIsFlow`, the one case the other three suites
  have and this one lacks.

`RecipeIntegrationTest`:

- Every collection-derived case gains an accept step (`shouldAccessRecipeInSharedCollection`,
  `shouldAccessRecipeDetailInSharedCollectionWithEditorRole`,
  `shouldIgnoreCollectionAssignmentChangeByEditor`,
  `shouldRemoveOwnedRecipesFromCollectionWhenUnshared`,
  `shouldListCollectionDerivedRecipeWithoutAnyDirectPermission`,
  `shouldExcludeCollectionDerivedRecipeFromUnassignedList`,
  `shouldKeepDirectRoleForRecipeAlsoReachableThroughCollection`).
- `shouldListRecipesWhenCallerHasNoAccessibleCollections` — a caller holding only direct recipe
  permissions and no collection at all: `GET /recipes` returns them and does not 500 (the empty
  `IN :collectionIds`).
- `shouldListUnassignedRecipesWhenCallerHasNoAccessibleCollections` — the same caller's
  `GET /recipes?unassigned=true` returns its unassigned recipes (the empty `NOT IN :collectionIds`
  rendering as a true predicate — the assumption `task-design.md` flags).
- `shouldExcludeRecipeInAReachableCollectionFromUnassignedList` — a caller who *does* reach the
  collection: the recipe is absent from the unassigned list, confirming `NOT IN` matches today's
  `NOT EXISTS`.
- `shouldReturnNullCollectionNameWhenUserLacksCollectionAccess` — unchanged in intent; confirms the
  retyped `catch (ResourceAccessDeniedException _)` still suppresses the name rather than 403ing.

`InviteIntegrationTest`:

_Unchanged — the suite stays on its opaque `INVITE_TEST_RESOURCE` and is a regression gate here, not
a target. The four-resource-type `GET /invites` response is covered by manual verification
(`tasks.md` > T3 > How to verify, step 2)._

**Flutter widget/integration tests**

_N/A — `tasks.md` > T3 > Out of scope puts all mobile work in T4 and T5._

**Manual verification**

- `tasks.md` > T3 > How to verify, steps 1–3 and 5, over `./recipai.sh start-backend` and the
  `backend/http/` suite: the T1 handshake against `/collections/{id}/share` and
  `/meal-plans/{id}/share`, `bob`'s four-type `GET /invites`, the unshare-detaches-recipes path, and
  all four `.http` files top to bottom.
- Step 4 (recompute parity) against a database restored from a **T2-era** dump: capture
  `SELECT resource, subject, used FROM limit_usage ORDER BY 1, 2` before the deploy, run the full
  migration chain, and diff. This is the one check the integration suite cannot make, because it
  starts from an empty schema.
- The one-OWNER-per-resource queries from `task-design.md` > Assumptions to verify, run against
  production **before** shipping `V22__`/`V23__`.

## Verification checklist

- [ ] `cd backend && ./mvnw -q compile` — no new warnings
- [ ] `cd backend && ./mvnw test` — every suite green, including all four `LimitsEnforced` recompute
      blocks
- [ ] The full migration chain applies cleanly from an empty database (`RecipAiApplicationTests`) and
      from a T2-era dump
- [ ] `grep -rn "recipes_collection_permission\|meal_plan_permissions\|recipe_permission\|shopping_list_permission" backend/src/main` returns only the migration files that create or drop them
- [ ] No `permissions`, `recipes` or `recipes.collections` entity is named in `planning`'s JPQL, and
      no `permissions` entity in `recipes`' (ADR-0007)
- [ ] `planning` reaches `recipes` only through `RecipeFacade`, and names no `'RECIPE'` /
      `'RECIPES_COLLECTION'` key of its own
- [ ] `PermissionsModuleArchitectureTest` and `LimitsModuleArchitectureTest` pass unchanged
- [ ] `tasks.md` > T3 > How to verify, all five steps, succeed end to end
- [ ] `task-design.md` > Assumptions to verify are confirmed, or the remaining ones are documented
- [ ] Logs at `INFO` are clean on the happy path — one line per share, unshare and delete
- [ ] `grep -rn "docs/tasks" docs/backend/modules/` is empty

## Risks surfaced during planning

- **Risk:** `task-design.md` > Data flow has `MealPlanCalendarService` calling `accessibleResources`
  for `RECIPE` and `RECIPES_COLLECTION` itself, which needs both keys to be reachable from
  `planning`. `RECIPE_RESOURCE` lives on the package-private `RecipeService`, and Decisions widens
  only `RECIPES_COLLECTION_RESOURCE`.
  **Why it matters:** step 3(d) does not compile as the design writes it, and exporting keys so
  another module can ask `permissions` about a resource it does not own is the shape ADR-0007 exists
  to prevent.
  **Mitigation:** step 2(e) puts `getDirectlyAccessibleRecipeIds` and `getAccessibleCollectionIds`
  on `RecipeFacade` — the `recipes` module's single public boundary per
  `module-structure.md` > Facade Pattern, already injected into `MealPlanService`. `planning` then
  names neither key, which is a stronger boundary than the design asked for: after this task the
  only `recipes` string left anywhere in `planning` is the `'RECIPE'` literal T2 put in
  `MealPlanEntryRepository`'s JPQL, and step 3(c) deletes that too.

- **Risk:** `task-design.md` > Modified lists `InviteIntegrationTest` (MODIFY) for a `GET /invites`
  response carrying all four resource types; this plan leaves the suite untouched.
  **Why it matters:** the design's file list and this plan's inventory disagree, and
  `tasks.md` > T3 > How to verify step 2 asks for exactly that response.
  **Mitigation:** deliberate, at the user's direction — the suite is built on an opaque
  `INVITE_TEST_RESOURCE` precisely to keep domain knowledge out of it, and the four-type case would
  have been the first thing to reach into all four modules. Step 6's manual `.http` run covers the
  verification step instead. Revisit if a regression ever slips through there.

- **Risk:** `task-design.md` > Modified — `planning` does not list `MealPlanController`, but the
  controller carries `ShareMealPlanRequest`, `UnshareMealPlanRequest`, `SharedUserDto` and the
  `/users` path, all of which this task removes.
  **Why it matters:** an implementer working strictly from the design's component list deletes types
  that are still referenced and hits a compile error.
  **Mitigation:** the file inventory and step 3(f) name it explicitly. No behaviour question — the
  change is the same one `RecipesCollectionController` gets.

- **Risk:** `task-design.md` > Modified says both suites gain a recompute case "following the ones
  already there for `RECIPE` and `SHOPPING_LIST`", but `RecipesCollectionIntegrationTest` already
  carries all five and `MealPlanIntegrationTest` four of the five.
  **Why it matters:** read literally it invites a duplicate case; read as written it hides that the
  real work is keeping the *existing* cases green across the repointing.
  **Mitigation:** the test plan treats the existing cases as regression gates and adds only
  `shouldSpareSubjectWithoutOverrideWhenResourceDefaultIsFlow` to `MealPlanIntegrationTest`, the one
  genuinely missing case.

- **Risk:** `docs/backend/standards/java-patterns.md` uses `MealPlanRepository.findAllByUserEmail`'s
  JPQL as its worked example of when `@Query` is warranted — the exact query step 3(b) deletes.
  **Why it matters:** a standards document would be citing a query that no longer exists, and the
  standard's point (a join no derived name can express) is precisely what this task argues against.
  **Mitigation:** out of this task's code scope, and standards changes are the user's call per
  `CLAUDE.md`. Raise it when the docs step runs and offer a surviving `@Query` — for example
  `MealPlanEntryRepository.findCalendarEntries` — as the replacement example.

- **Risk:** `MealPlanIntegrationTest.cleanup()` deletes every plan visible to all three users after
  each test, and `RecipesCollectionIntegrationTest` deletes collections likewise. Between steps 2/3
  and step 4 the legacy tables still hold rows with `ON DELETE`-less foreign keys to
  `recipes_collections` and `meal_plans`.
  **Why it matters:** a test run at the step-2 or step-3 commit would 500 on teardown for rows copied
  by `V22__`/`V23__` — the gap `tasks.md` > Cross-task notes accepts.
  **Mitigation:** none needed in the suite, which starts from an empty schema and so has no copied
  rows. It matters only if someone deploys an intermediate commit; steps 1–4 ship together.
