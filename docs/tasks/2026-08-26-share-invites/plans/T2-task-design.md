# T2: Recipes migrated, with collection-derived access composed by `recipes` — Task Design

**Date:** 2026-08-27

## Summary

`recipe_permission` is copied into `resource_permission` under the `RECIPE` key by a one-off `V21__`
migration, and `RecipeService` stops owning permission logic: it asks `PermissionsFacade` for the
direct role and falls back to `recipes.collections` for the synthetic `EDITOR`, composing the two
answers itself per ADR-0007. `POST /recipes/{uuid}/share` creates an invite labelled with the recipe
name instead of granting access, and `GET /recipes/{uuid}/shared_users` becomes
`GET /recipes/{uuid}/permissions`. The two recipe list queries keep their join to
`recipes_collection_permission` — which T3 will move — and take the accessible recipe ids as a
parameter instead of joining `recipe_permission`.

## Components and responsibilities

### Modified — `recipes`

- **`RecipeService`** (MODIFY, `recipes/RecipeService.java`) — drops `RecipePermissionRepository` for
  `PermissionsFacade`. `validateRecipeAccess` becomes the composition of two role answers;
  `shareRecipe` creates an invite and supplies the recipe name as the label; `unshareRecipe` hands
  both guards to `revoke`; `getSharedUsers` becomes `getPermissions`; `save` grants ownership through
  the facade; `deleteById` reports the deletion; `handleRecipesCollectionUnshared` resolves ownership
  through the facade. `findAll` and `findAllUnassigned` resolve the accessible recipe ids first and
  pass them to the repository.
- **`RecipeRepository`** (MODIFY) — `findAllByUserEmail` and `findAllUnassignedByUserEmail` lose
  their `RecipePermission` join and gain a `recipeIds` parameter. Both keep their
  `RecipesCollectionPermission` join untouched; T3 rewrites that half when it drops the table.
- **`RecipeController`** (MODIFY) — `ShareRequest` / `UnshareRequest` in the signatures;
  `getSharedUsers` renamed to `getPermissions`, returning `List<PermissionDto>` on the renamed path
  `GET /recipes/{id}/permissions`; `share` and `unshare` returning 204 instead of 200.
- **`RecipeDetailsDto`** (MODIFY) — its `role` field becomes `ResourceRole`.
- **`RecipesExceptionHandler`** (MODIFY) — loses its `RecipeAccessDeniedException` branch, and its
  surviving `RecipeNotFoundException` branch is rewritten to the shape every other module already
  uses: `@RestControllerAdvice`, a bare `ProblemDetail` return, and no `ResponseEntity` wrapper.
- **`RecipesCollectionService`** (MODIFY, `recipes/collections/`) — untouched by T2. It answers "can
  this email reach this collection?" through the `findById` it already exposes; T3 owns whatever
  becomes of that check when collections migrate.
- **DELETE** — `RecipePermission`, `RecipePermissionId`, `RecipePermissionRepository`,
  `recipes/UserRole`, `recipes/SharedUserDto`, `ShareRecipeRequest`, `UnshareRecipeRequest`,
  `RecipeAccessDeniedException`, `recipes/ErrorResponse` — the last is reachable only from the two
  handler branches above, so it dies with them.

### Modified — `planning`

- **`MealPlanEntryRepository`** (MODIFY, `planning/MealPlanEntryRepository.java`) —
  `findCalendarEntries`' `hasRecipeAccess` `CASE` swaps its `RecipePermission` `EXISTS` for one over
  `xyz.stasiak.recipai.permissions.ResourcePermission` filtered by `resource_type = 'RECIPE'`. Its
  `RecipesCollectionPermission` `EXISTS` is untouched until T3.

### Modified — migrations and tests

- **`V21__recipe_permission_to_resource_permission.sql`** (CREATE) — copies `recipe_permission` into
  `resource_permission` under the `RECIPE` type key. The old table is left in place, unread and
  unwritten, until T3 drops it.
- **`R__recompute_limit_usage.sql`** (MODIFY) — the `RECIPE` slice reads `resource_permission`
  filtered by `resource_type = 'RECIPE'`. `RECIPES_COLLECTION` and `MEAL_PLAN` are untouched until T3.
- **`RecipeIntegrationTest`** (MODIFY) — its sharing tests assert the handshake; a new case covers a
  recipe reachable only through a shared collection; a recompute case follows the one
  `RecipesCollectionIntegrationTest` and `ShoppingListIntegrationTest` already run.
- **`MealPlanIntegrationTest`** (MODIFY) — a calendar case where the caller reaches the entry's recipe
  through a `resource_permission` row, so the repointed `EXISTS` is covered.
- **`backend/http/recipes.http`** (MODIFY) — the renamed path and the `role` field.
- **Docs** — `docs/backend/modules/recipes/{module.md,api.md,db.md}` and the `permissions` module's
  `db.md` migration note.

## Interfaces and method signatures

### `RecipeService` — the composition

```java
// Replaces validateRecipeAccess. Direct permission wins; a reachable collection
// yields a synthetic EDITOR; neither is a refusal.
private ResourceRole resolveAccess(String userEmail, Recipe recipe);

// Unchanged names, new types.
List<PermissionDto> getPermissions(UUID recipeId, String userEmail);
void shareRecipe(ShareRequest request, UUID recipeId, String requesterEmail);
void unshareRecipe(String targetEmail, UUID recipeId, String requesterEmail);
```

`resolveAccess` is the only place in `recipes` that knows recipes belong to collections. Every other
method calls it and acts on the `ResourceRole` it returns, exactly as they act on `validateRecipeAccess`
today.

### `RecipeRepository` — the two list queries

```java
@Query("""
        SELECT DISTINCT r FROM Recipe r
        LEFT JOIN xyz.stasiak.recipai.recipes.collections.RecipesCollectionPermission cp
               ON cp.id.recipesCollectionId = r.recipesCollectionId
        WHERE r.id IN :recipeIds
           OR cp.id.email = :email
        ORDER BY r.createdAt
        """)
List<Recipe> findAllByUserEmail(@Param("recipeIds") Collection<UUID> recipeIds,
                                @Param("email") String email);

@Query("""
        SELECT r FROM Recipe r
        WHERE r.id IN :recipeIds
        AND (r.recipesCollectionId IS NULL
             OR NOT EXISTS (SELECT 1 FROM RecipesCollectionPermission rcp
                           WHERE rcp.id.recipesCollectionId = r.recipesCollectionId
                           AND rcp.id.email = :email))
        ORDER BY r.createdAt
        """)
List<Recipe> findAllUnassignedByUserEmail(@Param("recipeIds") Collection<UUID> recipeIds,
                                          @Param("email") String email);
```

The `INNER JOIN RecipePermission` in the second query becomes the `r.id IN :recipeIds` predicate —
the join was only ever a membership test.

### `MealPlanEntryRepository.findCalendarEntries` — the repointed branch

```
WHEN EXISTS (
    SELECT 1 FROM xyz.stasiak.recipai.permissions.ResourcePermission rp
    WHERE rp.id.resourceType = 'RECIPE'
    AND rp.id.resourceId = e.recipeId
    AND rp.id.email = :email
) THEN true
```

### `RecipesExceptionHandler` — the shape it adopts

```java
@RestControllerAdvice
class RecipesExceptionHandler {

    @ExceptionHandler(RecipeNotFoundException.class)
    ProblemDetail handleRecipeNotFound(RecipeNotFoundException ex) {
        // ProblemDetail.forStatusAndDetail(NOT_FOUND, ex.getMessage()), title "Recipe Not Found"
    }
}
```

Identical to `RecipesCollectionsExceptionHandler` next door and to `PermissionsExceptionHandler`,
which now serves this module's 403.

### HTTP surface

| Method | Path | Body / result |
|---|---|---|
| `POST` | `/recipes/{id}/share` | `{"email":…, "role":"EDITOR"}` → 204, creates an invite |
| `POST` | `/recipes/{id}/unshare` | `{"email":…}` → 204, revokes a permission **or** cancels a pending invite |
| `GET` | `/recipes/{id}/permissions` | `[{email, role, pending}]` — replaces `GET /recipes/{id}/shared_users` |

`share` and `unshare` move from 200 to 204, matching the shopping-list endpoints — both return an
empty body, and 204 is what that means. `RecipeController` returns `ResponseEntity.noContent().build()`
for both, as `ShoppingListController` does.

### Schema

```sql
INSERT INTO resource_permission (email, resource_type, resource_id, role)
SELECT email, 'RECIPE', recipe_id, role FROM recipe_permission;
```

No new tables and no index changes — `V20__` built both tables and all their indexes.

## Data flow

### Resolving access to a recipe

1. `permissionsFacade.roleOf(RECIPE, recipe.id, email)` — present means a direct permission, and its
   role (`OWNER` or `EDITOR`) is the answer.
2. Absent, and the recipe has no collection: refuse.
3. Absent, with a collection: `recipesCollectionService.findById(collectionId, email)`. It returns
   normally for a caller who can reach the collection, which yields a synthetic `EDITOR`; it throws
   `RecipesCollectionAccessDeniedException` (or `RecipesCollectionNotFoundException`) for one who
   cannot, which is the refusal.
4. Refusal throws `ResourceAccessDeniedException` — a 403 whose body now comes from
   `PermissionsExceptionHandler` rather than `RecipesExceptionHandler`.

### Listing recipes

1. `permissionsFacade.accessibleResources(RECIPE, email)` → `Map<UUID, ResourceRole>`; its key set is
   every directly-permitted recipe.
2. `recipeRepository.findAllByUserEmail(access.keySet(), email)` — the `IN` covers the direct half,
   the surviving join covers the collection-derived half, and `DISTINCT` collapses a recipe that is
   both.
3. An empty key set is **not** short-circuited, unlike shopping lists': a user with no direct recipe
   permission may still reach recipes through a shared collection. Hibernate 6 renders an empty
   `IN` list as a false predicate, leaving the `OR` branch to answer.

`findAllUnassigned` follows the same first step, but its empty key set **is** a short-circuit —
an unassigned recipe is by definition not reached through a collection.

### Sharing (invite creation)

1. `RecipeController.shareRecipe` passes `ShareRequest(email, role)` down.
2. `RecipeService.shareRecipe` loads the recipe — 404 if absent — and calls `resolveAccess` for the
   requester; editors and collection-derived editors may still share onward, unchanged.
3. `permissionsFacade.invite(RECIPE, id, targetEmail, role, recipe.getName(), requesterEmail)`.
4. The refusal rules see only *granted* rows, so an invite to someone who reaches the recipe through
   a shared collection is created rather than refused — which is what the requirements ask for.
   Accepting it writes a direct `EDITOR` row that shadows the composition with the same answer.

### Deleting a recipe

1. `resolveAccess`, then refuse unless the role is `OWNER` — today's owner-only guard, now over the
   composed role.
2. `eventPublisher.publishEvent(new RecipeDeleted(...))` — unchanged, and still first.
3. `permissionsFacade.resourceDeleted(RECIPE, id)` replaces `deleteAllByRecipeId`, so pending invites
   go with the permissions.
4. `recipeRepository.deleteById(id)`, `recipeImagesService.deleteAllImages(id)`,
   `limitsFacade.release(userEmail, RECIPE)` — unchanged, and still last.

## Pseudo-code

### `RecipeService.resolveAccess` — composing two answers

```
resolveAccess(userEmail, recipe):
    direct = permissionsFacade.roleOf(RECIPE, recipe.id, userEmail)
    if direct present:
        return direct                      # OWNER stays OWNER; a direct EDITOR stays EDITOR

    if recipe.recipesCollectionId != null:
        try:
            recipesCollectionService.findById(recipe.recipesCollectionId, userEmail)
            log.debug("{} reaches recipe {} via collection {}", ...)
            return EDITOR                  # synthetic, never materialised
        except RecipesCollectionAccessDeniedException, RecipesCollectionNotFoundException:
            pass                           # narrower than today's `catch (Exception e)`

    throw ResourceAccessDenied(RECIPE, recipe.id)
```

Composition never *lowers* an answer: a direct row wins outright, so a direct `EDITOR` on a recipe in
a collection the caller owns still reads `EDITOR`, exactly as today.

### `RecipeService.unshareRecipe` — both guards move to `revoke`

```
unshareRecipe(targetEmail, recipeId, requesterEmail):
    recipe = recipeRepository.findById(recipeId) orElseThrow RecipeNotFound
    resolveAccess(requesterEmail, recipe)                  # 403 if the requester cannot reach it
    permissionsFacade.revoke(RECIPE, recipeId, targetEmail, requesterEmail)
```

The self-unshare check and the never-unshare-an-OWNER check both disappear from `recipes`; `revoke`
holds them for all four modules. The self-unshare refusal changes shape as a result — today recipes
throws `IllegalArgumentException`, and after this it is the shared 403.

### `RecipeService.handleRecipesCollectionUnshared` — one lookup instead of N

```
handleRecipesCollectionUnshared(event):
    owned = permissionsFacade.accessibleResources(RECIPE, event.userEmail)   # one query
    for recipe in recipeRepository.findAllByRecipesCollectionIdOrderByCreatedAt(event.collectionId):
        if owned[recipe.id] == OWNER:
            recipe.recipesCollectionId = null
            recipeRepository.save(recipe)
```

Behaviour is unchanged; the per-recipe `getUserRole` call becomes one map lookup. The listener stays
`@TransactionalEventListener(BEFORE_COMMIT)`, so it still runs inside the unshare transaction and
sees the collection permission already removed.

## Decisions made

- **Collection access is asked through `RecipesCollectionService.findById` and a caught exception** —
  today's call, unchanged, so T2 does not invent a collections query API before T3 has decided what
  that module's access check should look like. The cost is exception-as-control-flow in the access
  path and a collection row loaded to answer a yes/no question; the catch is narrowed from today's
  `catch (Exception e)` to the two exceptions that actually mean "no access", so a genuine fault
  stops being swallowed as a refusal. **T3 is free to replace this.** `resolveAccess` is the single
  call site, and the only thing T2 depends on is that *some* call answers "can this email reach this
  collection?" — if T3 changes the collection access check's shape, it changes that one call with it.
- **Both recipe list queries keep their `RecipesCollectionPermission` join** and take the accessible
  recipe ids as a parameter. Only the half T2 owns moves. T3 rewrites the surviving half when it
  drops the table — a deliberate second visit to these two queries, taken in exchange for T2 not
  needing a new collections API.
- **`planning`'s calendar query repoints its recipe `EXISTS` at `ResourcePermission`** rather than
  waiting for T3 or moving the rule into Java. Without it the calendar reports "no access" for every
  recipe shared after T2 ships, silently hiding recipes the caller can open. Naming another module's
  package-private entity in JPQL is not new here — the same query already names
  `RecipesCollectionPermission` by its fully-qualified name, and JPQL resolves entity names at
  runtime, so Java visibility does not apply. It does put a `permissions` table in a `planning`
  query, which the `permissions` architecture test does not forbid (it constrains the module's
  outgoing dependencies, not its incoming ones).
- **`resolveAccess` returns `ResourceRole` and refuses by throwing** — the facade's `requireEditor`
  cannot be used here, because it refuses before the collection fallback gets a chance. `roleOf` plus
  an explicit throw is the only shape that composes.
- **`findAll` does not short-circuit on an empty permission map, `findAllUnassigned` does** — a user
  with no direct recipe permission can still reach recipes through a shared collection, so the empty
  case is a real query for the first and a guaranteed empty result for the second.
- **The recipe name is the invite label**, mirroring the shopping list's name in T1. `recipes.name` is
  `VARCHAR(255)`, matching `resource_invite.label`.
- **`getPermissions` lists direct permissions and pending invites only** — collection-derived access
  is not listed today and is not added here. Who appears in the dialog is unchanged.
- **One versioned copy per task** — `V21__` copies `recipe_permission` only, following T1. Data and
  code move together per module, so no module ever has rows in two places.
- **`share` and `unshare` return 204, not 200** — both have always returned an empty body, and the
  four modules should not disagree about how they say so while they are being unified. This one *is*
  client-visible: `recipe_repository.dart` accepts only `200` from both calls today (the shopping-list
  repository already expects `204`), so T5's mobile catch-up gains a third item for recipes alongside
  the `/permissions` path rename and the required `role` field. Recipe sharing is already broken from
  T2 until that catch-up lands, so this adds nothing to the outage — only to the fix.
- **`RecipesExceptionHandler` moves to `ProblemDetail`** while it is open. It is the last handler in
  the backend still returning a hand-rolled `ErrorResponse`, and `docs/backend/standards/module-structure.md`
  specifies the `ProblemDetail` shape every other module follows. Leaving it would put this module's
  404 in one error format and its 403 — now served by `PermissionsExceptionHandler` — in another, on
  the same endpoints. The switch is safe to make here rather than in its own task because no client
  reads either body: a search of `mobile/lib/` finds no error-body parsing at all, only status codes.

## Assumptions to verify

- **Assumption:** every existing `recipe_permission` row has exactly one `OWNER` per recipe.
  **If wrong:** `V21__`'s insert violates `uq_resource_permission_owner` (built in `V20__`) and the
  migration aborts. Check with
  `SELECT recipe_id FROM recipe_permission GROUP BY recipe_id HAVING count(*) FILTER (WHERE role='OWNER') <> 1`
  against production before shipping.
- **Assumption:** `RecipeRepository`, `MealPlanEntryRepository` and `R__recompute_limit_usage.sql` are
  the only readers of `recipe_permission`. These three are what a full-text search finds.
  **If wrong:** a fourth reader keeps reading a table that stops being written after T2 — silent
  divergence that looks correct until someone shares a recipe.
- **Assumption:** Hibernate 6 renders an empty `IN` collection as a false predicate rather than
  invalid SQL, which `findAllByUserEmail` relies on for a user whose only access is collection-derived.
  **If wrong:** that user's recipe list 500s. Cover it with a test that shares only a collection, and
  fall back to a short-circuit plus a collection-only query if it does not hold.
- **Assumption:** `hasRecipeAccess`'s repointed `EXISTS` is the only recipe-access rule in `planning`.
  `MealPlanService` reaches the rest through `RecipeFacade.getRecipes`, which composes correctly by
  going through `RecipeService.findAll`.
  **If wrong:** the calendar and the shopping-list generation wizard disagree about which recipes a
  caller can see.
- **Assumption:** `limit_usage` is byte-identical before and after the `RECIPE` slice is repointed.
  **If wrong:** the copy lost or duplicated ownership rows — an access-control bug and a quota bug at
  once. `RecomputeMigration.run(dataSource)` and the cases in `RecipesCollectionIntegrationTest` are
  the pattern to copy.
- **Assumption:** no consumer reads a recipe error body. Both recipe error shapes change here — the
  403 because `PermissionsExceptionHandler` now serves it, the 404 because the handler moves to
  `ProblemDetail` — so `{"message": …}` stops being returned by this module entirely. `mobile/lib/`
  parses no error body, and `ErrorResponse` has no other reference in the backend.
  **If wrong:** a client keying off `message` misbehaves — T4/T5 carry that catch-up for all four
  modules regardless.
- **Assumption:** `RecipesCollectionNotFoundException` from the fallback means "no access" and not a
  fault. A recipe whose `recipes_collection_id` points at a deleted collection is unreachable today
  by the same path, since `ON DELETE SET NULL` should have cleared it.
  **If wrong:** a data-integrity fault is reported as a 403 rather than surfacing.

## Required reading

- `plans/T1-task-design.md` — the precedents this task copies: the facade contract, the per-task
  copy, the `/permissions` rename, and the shared public types.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — the migrated
  service to mirror, method for method, everywhere composition is not involved.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java` — every call site this task
  rewrites, including the three shapes of access check and the collection fallback.
- `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanEntryRepository.java` — the calendar
  query and the FQN-in-JPQL pattern the repointed `EXISTS` follows.
- `docs/ADRs/0007-shared-permissions-module.md` — the composition boundary, and why the module must
  not learn that recipes belong to collections.
- `docs/ADRs/0008-invite-label-snapshot.md` — why the label is supplied by the inviting module.
- `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the `RECIPE` slice, and the
  FLOW-exclusion pattern to preserve when editing it.
- `backend/src/test/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionIntegrationTest.java`
  — the recompute assertions to copy for `RECIPE`.
- `docs/backend/standards/module-structure.md` — the `ProblemDetail` handler shape and the facade,
  visibility and application-service conventions this task's edits must land in.
- `docs/backend/standards/integration-tests.md` — the suite shape, and the rule about seeding and
  reading through the module's own business methods.
- `docs/tasks/2026-08-26-share-invites/tasks.md` > T2 — scope, out of scope, and the verification
  steps this design must satisfy.
