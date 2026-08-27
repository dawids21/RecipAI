# T3: Collections and meal plans migrated — migration complete — Task Design

**Date:** 2026-08-27

## Summary

`recipes_collection_permission` and `meal_plan_permissions` are copied into `resource_permission`
under the `RECIPES_COLLECTION` and `MEAL_PLAN` keys, and both `RecipesCollectionService` and
`MealPlanService` drop their permission repositories for `PermissionsFacade`. Both share endpoints
create invites labelled with the resource name; both delete paths report the deletion. The four
queries that still answer access with a permission join lose it: every one of them now takes the
accessible ids, resolved in Java through `accessibleResources`, as an `IN` parameter — including the
`ResourcePermission` `EXISTS` T2 put in the calendar query, so all four land in one shape. The
recompute then reads ownership entirely from `resource_permission`, and the four legacy
`*_permission` tables are dropped last.

## Components and responsibilities

### Modified — `recipes.collections`

- **`RecipesCollectionService`** (MODIFY, `recipes/collections/RecipesCollectionService.java`) —
  drops `RecipesCollectionPermissionRepository` for `PermissionsFacade`. `findAll` resolves ids
  through the facade; `findById` and `updateById` guard with `requireEditor`; `create` calls
  `grantOwner`; `deleteById` guards with `requireOwner` and reports the deletion;
  `shareRecipesCollection` creates an invite with the collection name as the label;
  `unshareRecipesCollection` asks `roleOf` before `revoke` so `RecipesCollectionUnshared` still fires
  only on a real unshare; `getSharedUsers` becomes `getPermissions`. Its
  `RECIPES_COLLECTION_RESOURCE` constant becomes `public` — `RecipeService` names the key now.
- **`RecipesCollectionRepository`** (MODIFY) — `findAllByUserEmail` is replaced by the derived
  `findByIdInOrderByCreatedAtAsc(Collection<UUID>)`, mirroring `ShoppingListRepository`.
- **`RecipesCollectionController`** (MODIFY) — `ShareRequest` / `UnshareRequest` in the signatures;
  `getSharedUsers` renamed to `getPermissions`, returning `List<PermissionDto>` on the renamed path
  `GET /collections/{id}/permissions`.
- **`RecipesCollectionsExceptionHandler`** (MODIFY) — loses its `RecipesCollectionAccessDeniedException`
  branch; the `RecipesCollectionNotFoundException` branch stays. 403s come from
  `PermissionsExceptionHandler`.
- **DELETE** — `RecipesCollectionPermission`, `RecipesCollectionPermissionId`,
  `RecipesCollectionPermissionRepository`, `collections/UserRole`, `collections/dto/SharedUserDto`,
  `ShareRecipesCollectionRequest`, `UnshareRecipesCollectionRequest`,
  `RecipesCollectionAccessDeniedException`.

### Modified — `recipes`

- **`RecipeService`** (MODIFY, `recipes/RecipeService.java`) — `resolveAccess`' collection fallback
  becomes `permissionsFacade.roleOf(RECIPES_COLLECTION_RESOURCE, …)`, replacing the
  `RecipesCollectionService.findById`-and-catch that T2 left in place. `findAll` and
  `findAllUnassigned` resolve the accessible **collection** ids alongside the recipe ids and pass
  both to the repository. The two `catch (RecipesCollectionAccessDeniedException _)` blocks that
  suppress the collection name in `findById` and `updateById` catch `ResourceAccessDeniedException`
  instead — those call sites keep using `RecipesCollectionService.findById`, which is what supplies
  the name.
- **`RecipeRepository`** (MODIFY) — `findAllByUserEmail` and `findAllUnassignedByUserEmail` lose the
  `RecipesCollectionPermission` join T2 deliberately left behind and gain a `collectionIds`
  parameter. `findAllByUserEmail` loses its join entirely, and with it the `DISTINCT`.

### Modified — `planning`

- **`MealPlanService`** (MODIFY, `planning/MealPlanService.java`) — drops
  `MealPlanPermissionRepository` for `PermissionsFacade`. `findAll` reads roles from one
  `accessibleResources` map instead of a lookup per plan; `create` calls `grantOwner`; `update`,
  `createEntry`, `updateEntry` (both plans), `deleteEntry` and `generateShoppingListItems` guard with
  `requireEditor`; `delete` guards with `requireOwner` and reports the deletion; `shareMealPlan`
  creates an invite with the plan name as the label; `unshareMealPlan` hands both guards to `revoke`;
  `getSharedUsers` becomes `getPermissions`.
- **`MealPlanRepository`** (MODIFY) — `findAllByUserEmail` is replaced by
  `findByIdInOrderByCreatedAtAsc(Collection<UUID>)`.
- **`MealPlanEntryRepository`** (MODIFY) — `findEntriesWithRecipes` drops its `MealPlanPermission`
  join outright; `findCalendarEntries` drops the same join and rewrites both `hasRecipeAccess`
  branches — the `ResourcePermission` `EXISTS` T2 added and the surviving
  `RecipesCollectionPermission` one — as `IN` tests over id sets.
- **`MealPlanCalendarService`** (MODIFY) — gains `PermissionsFacade`. It intersects the requested
  plan ids with the caller's accessible plans and resolves the recipe and collection id sets before
  calling the query.
- **`MealPlanDto`** (MODIFY) — its `role` field becomes `ResourceRole`.
- **`PlanningExceptionHandler`** (MODIFY) — loses its `MealPlanAccessDeniedException` branch; the
  other four stay.
- **DELETE** — `MealPlanPermission`, `MealPlanPermissionId`, `MealPlanPermissionRepository`,
  `planning/UserRole`, `planning/dto/SharedUserDto`, `ShareMealPlanRequest`,
  `UnshareMealPlanRequest`, `MealPlanAccessDeniedException`.

### Modified — migrations and tests

- **`V22__recipes_collection_permission_to_resource_permission.sql`** (CREATE) — copies
  `recipes_collection_permission` under the `RECIPES_COLLECTION` key.
- **`V23__meal_plan_permission_to_resource_permission.sql`** (CREATE) — copies
  `meal_plan_permissions` under the `MEAL_PLAN` key.
- **`V24__drop_legacy_permission_tables.sql`** (CREATE) — drops all four legacy tables. The last
  thing to run, and the irreversible step.
- **`R__recompute_limit_usage.sql`** (MODIFY) — the `RECIPES_COLLECTION` and `MEAL_PLAN` slices read
  `resource_permission` filtered by their type key. No slice reads a per-module permission table
  afterwards. Flyway runs repeatable migrations after the versioned ones, so this rewritten body is
  what runs after `V24__`, never the old one against dropped tables.
- **`RecipesCollectionIntegrationTest`** (MODIFY) — its sharing tests assert the handshake; a
  recompute case follows the ones already there for `RECIPE` and `SHOPPING_LIST`; the unshare test
  keeps asserting that the unshared user's recipes are detached.
- **`MealPlanIntegrationTest`** (MODIFY) — sharing tests assert the handshake; calendar cases cover
  a recipe reached directly, a recipe reached through a shared collection, and one reached by
  neither; a recompute case for `MEAL_PLAN`.
- **`RecipeIntegrationTest`** (MODIFY) — the collection-derived cases are re-pointed at the new
  composition; the case where the caller's only access is a shared collection now covers the
  `collectionIds` parameter as well.
- **`InviteIntegrationTest`** (MODIFY) — one `GET /invites` response carrying invites of all four
  resource types, each with its label and sender.
- **`backend/http/collections.http`**, **`backend/http/meal-plans.http`** (MODIFY) — the renamed
  path and the `role` field.
- **Docs** — `docs/backend/modules/{recipes,planning,permissions,limits,shopping-lists}/` and the two
  `docs/INDEX.md` `db.md` lines. `limits/db.md` and `shopping-lists/db.md` also carry the two
  `docs/tasks/` references T1 left behind, which this task's docs pass removes.

## Interfaces and method signatures

### `RecipesCollectionService` — after migration

```java
public class RecipesCollectionService {

    // Public now: RecipeService.resolveAccess names this key when it asks for the
    // collection half of the composition.
    public static final String RECIPES_COLLECTION_RESOURCE = "RECIPES_COLLECTION";

    List<RecipesCollectionListDto> findAll(String userEmail);
    public RecipesCollectionListDto findById(UUID collectionId, String userEmail);
    RecipesCollectionListDto create(CreateRecipesCollectionRequest request, String userEmail);
    RecipesCollectionListDto updateById(UUID id, UpdateRecipesCollectionRequest request, String userEmail);
    void deleteById(UUID id, String userEmail);

    void shareRecipesCollection(ShareRequest request, UUID collectionId, String requesterEmail);
    void unshareRecipesCollection(String targetEmail, UUID collectionId, String requesterEmail);
    List<PermissionDto> getPermissions(UUID collectionId, String userEmail);
}
```

`findById` stays public and stays the way `recipes` gets a collection's **name**; it is no longer the
way `recipes` gets a yes/no on **access**.

### `MealPlanService` — the signatures that change

```java
List<MealPlanDto> findAll(String userEmail);          // roles from one accessibleResources map
void shareMealPlan(ShareRequest request, UUID planId, String requesterEmail);
void unshareMealPlan(String targetEmail, UUID planId, String requesterEmail);
List<PermissionDto> getPermissions(UUID planId, String userEmail);
```

### The four de-joined queries

```java
// RecipeRepository — no join, and no DISTINCT: a recipe matches at most one row now.
@Query("""
        SELECT r FROM Recipe r
        WHERE r.id IN :recipeIds
           OR r.recipesCollectionId IN :collectionIds
        ORDER BY r.createdAt
        """)
List<Recipe> findAllByUserEmail(@Param("recipeIds") Collection<UUID> recipeIds,
                                @Param("collectionIds") Collection<UUID> collectionIds);

@Query("""
        SELECT r FROM Recipe r
        WHERE r.id IN :recipeIds
        AND (r.recipesCollectionId IS NULL
             OR r.recipesCollectionId NOT IN :collectionIds)
        ORDER BY r.createdAt
        """)
List<Recipe> findAllUnassignedByUserEmail(@Param("recipeIds") Collection<UUID> recipeIds,
                                          @Param("collectionIds") Collection<UUID> collectionIds);
```

Both lose `:email` — nothing in either query is keyed on it any more.

```java
// MealPlanEntryRepository — the permission join is gone; planIds is already access-filtered.
@Query("""
        SELECT e FROM MealPlanEntry e
        WHERE e.planId IN :planIds
        AND e.date IN :dates
        AND e.recipeId IS NOT NULL
        """)
List<MealPlanEntry> findEntriesWithRecipes(@Param("planIds") List<UUID> planIds,
                                           @Param("dates") List<LocalDate> dates);

List<MealPlanCalendarEntryProjection> findCalendarEntries(@Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate,
                                                          @Param("planIds") Collection<UUID> planIds,
                                                          @Param("recipeIds") Collection<UUID> recipeIds,
                                                          @Param("collectionIds") Collection<UUID> collectionIds);
```

`findCalendarEntries`' access `CASE` becomes three id tests and no subqueries:

```
CASE
    WHEN e.recipeId IS NULL THEN true
    WHEN e.recipeId IN :recipeIds THEN true
    WHEN r.recipesCollectionId IS NOT NULL
         AND r.recipesCollectionId IN :collectionIds THEN true
    ELSE false
END AS hasRecipeAccess
```

Its `INNER JOIN MealPlanPermission` disappears; `WHERE e.planId IN :planIds` carries the plan-level
access filter, because the caller passes only plans it has resolved as accessible.

### HTTP surface

| Method | Path | Body / result |
|---|---|---|
| `POST` | `/collections/{id}/share` | `{"email":…, "role":"EDITOR"}` → 204, creates an invite |
| `POST` | `/collections/{id}/unshare` | `{"email":…}` → 204, revokes a permission **or** cancels a pending invite |
| `GET` | `/collections/{id}/permissions` | `[{email, role, pending}]` — replaces `GET /collections/{id}/users` |
| `POST` | `/meal-plans/{id}/share` | `{"email":…, "role":"EDITOR"}` → 204, creates an invite |
| `POST` | `/meal-plans/{id}/unshare` | `{"email":…}` → 204 |
| `GET` | `/meal-plans/{id}/permissions` | `[{email, role, pending}]` — replaces `GET /meal-plans/{id}/users` |

All four already return 204 from `share` and `unshare`, so no status changes here — recipes was the
only outlier and T2 moved it.

### Schema

```sql
-- V22__
INSERT INTO resource_permission (email, resource_type, resource_id, role)
SELECT email, 'RECIPES_COLLECTION', recipes_collection_id, role FROM recipes_collection_permission;

-- V23__
INSERT INTO resource_permission (email, resource_type, resource_id, role)
SELECT email, 'MEAL_PLAN', plan_id, role FROM meal_plan_permissions;

-- V24__
DROP TABLE recipes_collection_permission;
DROP TABLE meal_plan_permissions;
DROP TABLE recipe_permission;
DROP TABLE shopping_list_permission;
```

No new tables and no index changes — `V20__` built both tables and all their indexes. Dropping each
table takes its indexes and its foreign key to the resource table with it, which is what closes the
delete-a-resource-with-legacy-rows gap `tasks.md` accepts for the T1–T3 window.

## Data flow

### Sharing a collection or a meal plan (invite creation)

1. The controller passes `ShareRequest(email, role)` down.
2. The service loads the resource — no longer just `existsById`, because it needs the name for the
   label — 404 if absent.
3. `permissionsFacade.requireEditor(<TYPE>, id, requesterEmail)` — 403 if the caller cannot reach it.
   Editors may still share onward, unchanged.
4. `permissionsFacade.invite(<TYPE>, id, targetEmail, role, resource.getName(), requesterEmail)`.
   No permission row is written, so the resource stays absent from the invitee's list until accept.

The pre-existing "already shared → silent no-op" branch in both services disappears: the refusal
rules in `permissions` turn that case into a 409, which is what the requirements ask for.

### Unsharing a collection

1. `requireEditor` for the requester.
2. `roleOf(RECIPES_COLLECTION, id, targetEmail)` — remembered as a boolean before anything is
   removed.
3. `permissionsFacade.revoke(...)` — removes the granted permission, or cancels a pending invite, or
   does nothing. Both guards (never an `OWNER`, never yourself) live inside it.
4. `RecipesCollectionUnshared` is published **only if step 2 found a permission**, so cancelling an
   invite does not trigger the detach pass.
5. `RecipeService.handleRecipesCollectionUnshared` runs `BEFORE_COMMIT`, as today, and detaches the
   recipes that user owns from the collection. It reads `RECIPE` ownership through the facade, which
   never depended on the collection permission, so the listener is order-independent now.

### Listing recipes

1. `accessibleResources(RECIPE, email)` → the direct half.
2. `accessibleResources(RECIPES_COLLECTION, email)` → the collection-derived half. This is the second
   facade call `recipes` makes for itself; it replaces the join that used to do the same work in SQL.
3. `findAllByUserEmail(recipeIds, collectionIds)` — an `OR` over two `IN` lists.
4. Neither set short-circuits `findAll`: a user with no direct recipe permission may still reach
   recipes through a shared collection, and vice versa. `findAllUnassigned` still short-circuits on an
   empty **recipe** set — an unassigned recipe is by definition not reached through a collection — but
   not on an empty collection set, where every directly-permitted recipe is unassigned-visible.

### Calendar view

1. `MealPlanCalendarService` validates the date range, as today.
2. `accessibleResources(MEAL_PLAN, email)` — the requested `planIds` are intersected with the key set.
   An empty intersection returns an empty map without querying, preserving today's behaviour where
   the permission join silently excluded a plan the caller could not reach.
3. `accessibleResources(RECIPE, email)` and `accessibleResources(RECIPES_COLLECTION, email)` supply
   the two id sets behind `hasRecipeAccess`.
4. `findCalendarEntries(start, end, accessiblePlanIds, recipeIds, collectionIds)`.

Three facade calls per calendar load, against one query that used to carry two correlated subqueries
and a join. The sets are bounded by the caller's quotas.

### Deleting a collection or a meal plan

1. `requireOwner(<TYPE>, id, userEmail)` — the owner-only guard, now one call.
2. `permissionsFacade.resourceDeleted(<TYPE>, id)` replaces the module's `deleteAllBy…`, so pending
   invites go with the permissions.
3. The repository delete (meal-plan entries cascade; recipes' `recipes_collection_id` is
   `ON DELETE SET NULL`).
4. `limitsFacade.release(userEmail, <TYPE>)` — unchanged, and still last.

## Pseudo-code

### `RecipesCollectionService.unshareRecipesCollection` — the event, minus the invite case

```
@Transactional
unshareRecipesCollection(targetEmail, collectionId, requesterEmail):
    if not collectionRepository.existsById(collectionId):
        throw RecipesCollectionNotFound
    permissionsFacade.requireEditor(RECIPES_COLLECTION, collectionId, requesterEmail)

    # Read before the write: revoke() cannot tell us afterwards whether it removed a
    # permission or cancelled an invite, and only the first should detach recipes.
    hadPermission = permissionsFacade.roleOf(RECIPES_COLLECTION, collectionId, targetEmail).isPresent()

    permissionsFacade.revoke(RECIPES_COLLECTION, collectionId, targetEmail, requesterEmail)

    if hadPermission:
        eventPublisher.publishEvent(new RecipesCollectionUnshared(collectionId, targetEmail))
```

`revoke` throws before returning when the target is an `OWNER` or is the requester, so the event
cannot fire for a refused unshare.

### `RecipeService.resolveAccess` — both halves from the facade

```
resolveAccess(userEmail, recipe):
    direct = permissionsFacade.roleOf(RECIPE_RESOURCE, recipe.id, userEmail)
    if direct present:
        return direct                      # OWNER stays OWNER; a direct EDITOR stays EDITOR

    if recipe.recipesCollectionId != null:
        if permissionsFacade.roleOf(RECIPES_COLLECTION_RESOURCE,
                                    recipe.recipesCollectionId, userEmail).isPresent():
            log.debug("{} reaches recipe {} via collection {}", ...)
            return EDITOR                  # synthetic, never materialised

    throw ResourceAccessDenied(RECIPE, recipe.id)
```

The composition rule is unchanged from T2 — a direct row wins outright, a reachable collection yields
a synthetic `EDITOR`, neither is a refusal. What changes is that the second answer is a role lookup
rather than a caught exception, so a genuine fault in `recipes.collections` can no longer be read as
a refusal, and no collection row is loaded to answer a yes/no.

### `MealPlanCalendarService.getCalendarView` — resolving the three id sets

```
getCalendarView(userEmail, startDate, endDate, requestedPlanIds):
    validateDateRange(startDate, endDate)

    accessiblePlans = permissionsFacade.accessibleResources(MEAL_PLAN_RESOURCE, userEmail).keySet()
    planIds = requestedPlanIds ∩ accessiblePlans     # today's join did this silently
    if planIds is empty:
        return {}                                     # no query; an empty IN is not a filter here

    recipeIds     = permissionsFacade.accessibleResources(RECIPE_RESOURCE, userEmail).keySet()
    collectionIds = permissionsFacade.accessibleResources(RECIPES_COLLECTION_RESOURCE, userEmail).keySet()

    projections = entryRepository.findCalendarEntries(startDate, endDate, planIds,
                                                      recipeIds, collectionIds)
    ... group by date, unchanged
```

`recipeIds` and `collectionIds` may legitimately be empty — a caller can hold a meal plan and no
recipes — and the `CASE` must then answer `false` rather than fail. `planIds` is the only set whose
emptiness is a short-circuit, because it is a `WHERE` filter rather than a `CASE` branch.

### `MealPlanService.findAll` — one map instead of a lookup per plan

```
findAll(userEmail):
    access = permissionsFacade.accessibleResources(MEAL_PLAN_RESOURCE, userEmail)
    if access is empty:
        return []
    return mealPlanRepository.findByIdInOrderByCreatedAtAsc(access.keySet())
             .map(plan -> toDto(plan, access.get(plan.id)))
```

Today's per-plan `findById` on the permission table — an N+1 the join made invisible — collapses into
the map `accessibleResources` already returns. The `MealPlanAccessDeniedException` it threw for a
plan with no permission row was unreachable and goes away with the exception class.

## Decisions made

- **Every de-joined query takes accessible ids resolved in Java** — `recipes`' two list queries,
  `findCalendarEntries` and `findEntriesWithRecipes` all move to `IN` parameters fed by
  `accessibleResources`, and **T2's `ResourcePermission` `EXISTS` in the calendar query is rewritten
  the same way** rather than left as a second idiom. No `permissions` entity is named in another
  module's JPQL when this task is done, which is the boundary ADR-0007 draws. The cost is two or three
  facade calls per read path where there used to be one query; the sets are quota-bounded, as T1's
  shopping-list `IN` already is.
- **`RecipeService.resolveAccess` asks `PermissionsFacade` for the collection role** instead of
  calling `RecipesCollectionService.findById` and catching. T2 chose the catch only to avoid inventing
  a collections access API before this task decided what collections' check should look like; now that
  collections answer from the same store, both halves of the composition come from one call shape.
  `recipes` still calls `findById` for the collection **name** in `findById` and `updateById`, and for
  the create/update collection-assignment validation — those need the row, not a yes/no.
- **`RECIPES_COLLECTION_RESOURCE` becomes public on `RecipesCollectionService`** — the key stays
  declared once, by the module that owns it, and `recipes` names it rather than repeating the string.
  This is the same shape `limits` and `permissions` already rely on for the other three keys, one
  visibility wider.
- **The unshare event fires only when a granted permission was removed**, decided by a `roleOf` read
  taken before `revoke`. `PermissionsFacade.revoke` keeps its `void` signature, so the other three
  modules are untouched, at the cost of one extra lookup on the collection unshare path. Firing
  unconditionally would detach recipes on a cancelled invite — a no-op in practice, since a pending
  invitee cannot reach the collection to file recipes into it, but the event would stop describing
  what happened.
- **Three migrations, drop last** — `V22__` copies collections, `V23__` copies meal plans, `V24__`
  drops all four legacy tables. One copy per module keeps T1's "data and code move together per
  module" rule intact with two modules in one task, and isolating the irreversible step in its own
  file makes it the last thing that runs and the easiest thing to hold back if a pre-ship check on
  production data fails.
- **`findEntriesWithRecipes` drops its permission join outright** rather than taking an id set —
  `MealPlanService.generateShoppingListItems` already calls `requireEditor` for every plan id before
  it queries, so the join was a second, redundant access filter. Its `:email` parameter goes with it.
- **The calendar's plan filter moves to an intersection in Java** — today the `INNER JOIN
  MealPlanPermission` silently drops a requested plan the caller cannot reach, and the intersection
  preserves that exactly, including returning no entries rather than a 403.
- **`findAll` on recipes short-circuits on neither id set; `findAllUnassigned` short-circuits on the
  recipe set only** — the asymmetry T2 established, now with a second set to reason about. An empty
  collection set means "no collection is reachable", which is a true answer for both queries, not an
  absence of one.
- **The "already shared" silent no-op is removed from both services** — `permissions` refuses with a
  409 (`ALREADY_HAS_ACCESS`), as it already does for shopping lists and recipes. This is a
  client-visible change for collections and meal plans, and the last of the four to make it.
- **The collection and plan name are the invite labels**, mirroring T1 and T2. Both columns are
  `VARCHAR(255)`, matching `resource_invite.label`.
- **`recipes` and `planning` reach `RECIPES_COLLECTION` / `RECIPE` accessible-id sets through
  `RecipeFacade.getDirectlyAccessibleRecipeIds` / `getAccessibleCollectionIds` and
  `RecipesCollectionService.accessibleCollectionIds`, not through direct
  `permissionsFacade.accessibleResources(RECIPES_COLLECTION_RESOURCE, ...)` / `RECIPE_RESOURCE` calls**
  as planned above. Routing through the owning module's facade instead of naming another module's
  resource key keeps `planning` and the calendar path from knowing `RECIPE` and `RECIPES_COLLECTION`
  exist as resource types, which is closer to the boundary ADR-0007 draws than the original plan was.
  Found during implementation and kept; `docs/backend/modules/permissions/module.md` already describes
  the implemented shape.
- **`MealPlanDto.role` becomes `ResourceRole` and the four duplicated type families are deleted** —
  the two remaining `UserRole` enums, the two `SharedUserDto` records, and the four
  `Share*Request` / `Unshare*Request` records. This is the decision T1 made, finished here.
- **Both modules' `*AccessDeniedException` go with their handler branches** — 403s for all four
  resource types now come from `PermissionsExceptionHandler` with the shared title and body. No
  backend consumer reads either body, and `mobile/lib/` parses none.
- **The docs pass sweeps T1's two `docs/tasks/` references** in `limits/db.md` and
  `shopping-lists/db.md`. Both describe the half-migrated state this task ends, so they have to be
  rewritten regardless, and `CLAUDE.md` forbids module docs citing task directories.

## Assumptions to verify

- **Assumption:** every existing `recipes_collection_permission` and `meal_plan_permissions` row has
  exactly one `OWNER` per resource.
  **If wrong:** `V22__` / `V23__` violate `uq_resource_permission_owner` and the migration aborts.
  Check against production before shipping with
  `SELECT recipes_collection_id FROM recipes_collection_permission GROUP BY recipes_collection_id HAVING count(*) FILTER (WHERE role='OWNER') <> 1`
  and the `plan_id` equivalent.
- **Assumption:** `RecipesCollectionRepository`, `RecipeRepository`, `MealPlanRepository`,
  `MealPlanEntryRepository`, the two services and `R__recompute_limit_usage.sql` are the only readers
  of the two legacy tables — that is what a full-text search over `src/` finds.
  **If wrong:** `V24__` breaks a reader at runtime rather than at compile time, since a missed JPQL
  reference fails only when the query runs.
- **Assumption:** nothing outside the four legacy tables depends on them — no foreign key points
  *into* them, and no view or index outside their own `CREATE TABLE` references them. `V19__` adds an
  index on `shopping_list_permission`, which drops with the table.
  **If wrong:** `V24__` fails on a dependent object and the deploy stops mid-migration.
- **Assumption:** Hibernate 6 renders an empty `IN` collection as a false predicate and an empty
  `NOT IN` as a true one, rather than emitting invalid SQL. T2 already relies on the first for
  `findAllByUserEmail`; this task extends the reliance to the calendar's `CASE` branches and to
  `findAllUnassignedByUserEmail`'s `NOT IN :collectionIds`.
  **If wrong:** a caller with no accessible collections gets a 500 on the recipe list or the calendar.
  Cover both with a test whose user holds a recipe and no collection, and fall back to short-circuits
  plus query variants if it does not hold.
- **Assumption:** `NOT IN :collectionIds` is equivalent to today's `NOT EXISTS` over the permission
  table for `findAllUnassigned`. Both mean "this recipe's collection is not one the caller can
  reach", including when the recipe has no collection at all, which the `IS NULL` branch covers
  separately.
  **If wrong:** recipes appear in, or vanish from, the unassigned list — the screen that is supposed
  to show exactly what no visible collection contains.
- **Assumption:** `limit_usage` is byte-identical before and after the `RECIPES_COLLECTION` and
  `MEAL_PLAN` slices are repointed, and identical to what it was before T1 ran.
  **If wrong:** a copy lost or duplicated ownership rows — an access-control bug and a quota bug at
  once. `RecomputeMigration.run(dataSource)` and the existing cases in `ShoppingListIntegrationTest`
  and `RecipesCollectionIntegrationTest` are the pattern to copy.
- **Assumption:** Flyway runs `R__recompute_limit_usage.sql` after `V24__` within the same migration
  run, so the recompute never executes its old body against dropped tables.
  **If wrong:** the deploy fails on the repeatable migration. Verified by running the full migration
  chain from an empty database and from a T2-era database in the integration suite.
- **Assumption:** no caller depends on the "already shared" no-op returning 204 for collections or
  meal plans. `SharingDialog` surfaces the failure either way, and T5 carries the mobile catch-up.
  **If wrong:** re-sharing to an existing user surfaces an error where it used to look successful —
  which is the intended behaviour change, not a regression.
- **Assumption:** `MealPlanService.findAll`'s `MealPlanAccessDeniedException` branch is genuinely
  unreachable — the plan list came from the permission join, so a plan without a permission row could
  not appear.
  **If wrong:** a case that used to 403 now returns a DTO with a null role.

## Required reading

- `plans/T1-task-design.md` — the facade contract, the two-table model, the per-task copy, the
  `/permissions` rename, and the shared public types this task finishes rolling out.
- `plans/T2-task-design.md` — the composition rule this task keeps and the two deferrals it collects:
  the collection access check and the surviving `RecipesCollectionPermission` joins.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — the migrated
  service to mirror, method for method.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionService.java` and
  `.../planning/MealPlanService.java` — every call site this task rewrites.
- `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanEntryRepository.java` — the calendar
  query, and the two access branches that become id tests.
- `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the two slices repointed
  here, and the FLOW-exclusion pattern to preserve when editing them.
- `docs/ADRs/0007-shared-permissions-module.md` — the boundary this task's query rewrites restore in
  full, and why composition stays with `recipes`.
- `docs/ADRs/0008-invite-label-snapshot.md` — why the label is supplied by the inviting module.
- `backend/src/test/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionIntegrationTest.java`
  and `.../planning/MealPlanIntegrationTest.java` — the suites this task rewrites, including the
  recompute assertions to copy.
- `docs/backend/standards/module-structure.md` and `java-patterns.md` — facade, exception-handler,
  visibility and record conventions the edits must land in.
- `docs/backend/standards/integration-tests.md` — the suite shape, and the rule about seeding and
  reading through the module's own business methods.
- `docs/tasks/2026-08-26-share-invites/tasks.md` > T3 — scope, out of scope, and the verification
  steps this design must satisfy.
