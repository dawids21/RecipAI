# T2: Recipes migrated, with collection-derived access composed by `recipes` — Implementation Plan

**Date:** 2026-08-27

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/backend/standards/module-structure.md` — the facade-per-module rule, and the exception-handler
  section whose worked example *is* `RecipesExceptionHandler` in the shape this task gives it: a bare
  `ProblemDetail` from `forStatusAndDetail(...)` + `setTitle(...)`, no `ResponseEntity` wrapper.
- `docs/backend/standards/java-patterns.md` — records for DTOs, and the
  package-private-unless-crossing-a-boundary rule that decides which of the deleted `recipes` types
  had to be public in the first place.
- `docs/backend/standards/integration-tests.md` — `@SpringBootTest(RANDOM_PORT)` +
  `TestcontainersConfiguration` + `TestSecurityConfiguration` + `RestClient`, the `shouldXxxWhenYyy`
  naming, and the rule that a suite seeds and reads through the module's own business surface.
- `docs/backend/modules/recipes/{module.md,api.md,db.md}` — what this task's changes make stale.
- `docs/backend/modules/permissions/{module.md,api.md,db.md}` — the facade contract, the invite
  handshake, and the refusal rules this module now inherits rather than reimplements.
- `docs/backend/modules/limits/{module.md,db.md}` — the recompute's per-resource sources; the `RECIPE`
  line moves here.
- `docs/project/local-development.md` — `./recipai.sh start-backend`, the dev-profile
  `Bearer alice` → `alice@local.test` bypass, and the `backend/http/` suite conventions.

**Design & ADRs**

- `plans/T2-task-design.md` — the whole document; **Interfaces and method signatures**, **Data flow**
  and **Pseudo-code** are assumed here rather than re-derived.
- `plans/T1-task-design.md` and `plans/T1-implementation-plan.md` — the precedents this task copies:
  the per-task versioned copy, the `/permissions` rename, the shared public types, and the
  200-vs-404-ordering care taken in `ShoppingListService`.
- `tasks.md` > T2 — Scope, Out of scope, and the four-step **How to verify** that gates the task.
- `tasks.md` > Cross-task notes — the accepted legacy-permission delete gap, and why the migration
  mechanism is not re-decided here.
- `docs/ADRs/0007-shared-permissions-module.md` — the composition boundary, and why the module must
  not learn that recipes belong to collections.
- `docs/ADRs/0008-invite-label-snapshot.md` — why the recipe name is supplied by `recipes` and stored
  opaquely.

**Code to mirror**

- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — the migrated
  service, method for method, everywhere composition is not involved: `findAll` off
  `accessibleResources`, `shareShoppingList(ShareRequest, …)` calling `invite` with the resource's
  name as the label, `unshareShoppingList` handing both guards to `revoke`, `getPermissions`
  delegating to the facade, `deleteById` calling `resourceDeleted`.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListController.java` — the
  `ShareRequest` / `UnshareRequest` signatures, `GET /{id}/permissions` returning
  `List<PermissionDto>`, and `ResponseEntity.noContent().build()` from `share` and `unshare`.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListRepository.java` —
  `findByIdInOrderByCreatedAtAsc(Collection<UUID>)`, the precedent for a list query taking accessible
  ids as a parameter.
- `backend/src/main/java/xyz/stasiak/recipai/permissions/PermissionsFacade.java` — the exact method
  set available: `roleOf`, `accessibleResources`, `grantOwner`, `invite`, `revoke`, `getPermissions`,
  `resourceDeleted`.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionsExceptionHandler.java`
  — the `ProblemDetail` body shape next door. Copy the body, **not** its `@ControllerAdvice` +
  `public` method declarations; the standard's example uses `@RestControllerAdvice` and a
  package-private method, as `PermissionsExceptionHandler` does.
- `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanEntryRepository.java` — the calendar
  query and the fully-qualified-entity-name-in-JPQL pattern the repointed `EXISTS` follows.
- `backend/src/main/resources/db/migration/V20__resource_permission_and_invite.sql` — migration style
  (bare table names; Flyway's `default-schema: recipai` supplies the schema) and the
  `INSERT … SELECT … FROM shopping_list_permission` copy `V21__` mirrors.
- `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the `SHOPPING_LIST` slice
  is exactly what the `RECIPE` slice becomes, including the FLOW-exclusion
  `COALESCE(override, default) IS DISTINCT FROM 'FLOW'` pattern that must survive the repointing.
- `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java` — the
  handshake helpers to copy verbatim (`getPendingInvites`, `acceptInvite`, `findPendingInviteId`) and
  the rewritten sharing assertions.
- `backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java` > `LimitsEnforced` —
  the recompute cases that already exist for `RECIPE` and must keep passing once the slice is
  repointed.
- `backend/http/shopping-lists.http` and `backend/http/invites.http` — `@name` captures,
  `@var = {{name.response.body.$.id}}`, and the explanatory `###` comments.

## File inventory

**Migrations**

- **CREATE** `backend/src/main/resources/db/migration/V21__recipe_permission_to_resource_permission.sql` — copies `recipe_permission` in under `'RECIPE'`.
- **MODIFY** `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the `RECIPE` slice reads `resource_permission`.

**Recipes — modified**

- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java` — `PermissionsFacade` replaces the permission repository; `resolveAccess` composes two answers.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeRepository.java` — both list queries take `recipeIds`; the `RecipePermission` joins go.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeController.java` — shared request records, `/permissions` path, 204 from share and unshare.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeDetailsDto.java` — `role` becomes `ResourceRole`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipesExceptionHandler.java` — one branch left, rewritten to `@RestControllerAdvice` + bare `ProblemDetail`.

**Recipes — deleted**

- **DELETE** `…/recipes/RecipePermission.java` — superseded by `ResourcePermission`.
- **DELETE** `…/recipes/RecipePermissionId.java` — superseded by `ResourcePermissionId`.
- **DELETE** `…/recipes/RecipePermissionRepository.java` — no reader left after this task.
- **DELETE** `…/recipes/UserRole.java` — superseded by `ResourceRole`.
- **DELETE** `…/recipes/SharedUserDto.java` — superseded by `PermissionDto`.
- **DELETE** `…/recipes/ShareRecipeRequest.java` — superseded by `ShareRequest`.
- **DELETE** `…/recipes/UnshareRecipeRequest.java` — superseded by `UnshareRequest`.
- **DELETE** `…/recipes/RecipeAccessDeniedException.java` — superseded by `ResourceAccessDeniedException`.
- **DELETE** `…/recipes/ErrorResponse.java` — reachable only from the two handler branches that go.

**Planning — modified**

- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanEntryRepository.java` — the calendar's recipe `EXISTS` reads `ResourcePermission`.

**Tests**

- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java` — helpers move to the shared types and the `/permissions` path, sharing tests assert the handshake, and the composition cases are added.
- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/planning/MealPlanIntegrationTest.java` — a calendar case reaching the entry's recipe through a `resource_permission` row.

**HTTP suite**

- **MODIFY** `backend/http/recipes.http` — the renamed path, the `role` field, and the 204s; the stale "unlike every other module" comments go.

**Docs** (named by `task-design.md` > Modified and by `tasks.md` > Cross-task notes; produced by the `docs-updating` step at the end of the task)

- **MODIFY** `docs/backend/modules/recipes/{module.md,api.md,db.md}` — permissions moved out, share is now an invite, `shared_users` renamed to `permissions` and grows `pending`, share/unshare return 204.
- **MODIFY** `docs/backend/modules/permissions/db.md` — `V21__` added to the per-module copy note.
- **MODIFY** `docs/backend/modules/limits/{module.md,db.md}` — `RECIPE` joins `SHOPPING_LIST` in reading `resource_permission`.

## Step-by-step plan

1. **Copy `recipe_permission` into `resource_permission`** — add
   `V21__recipe_permission_to_resource_permission.sql` with exactly the statement in
   `task-design.md` > Schema. Bare table names; no new tables and no indexes — `V20__` built both.
   Leave `recipe_permission` in place, unread and unwritten from step 2 on; T3 drops it.
   `recipe_permission` has no `created_at` column, so nothing is lost by not naming one — the
   destination's `DEFAULT now()` fills it, as it did for shopping lists. Before writing this file,
   run the one-OWNER-per-recipe check from **Risks** below against production.
   - Files: `backend/src/main/resources/db/migration/V21__recipe_permission_to_resource_permission.sql`
   - Verify: `cd backend && ./mvnw test -Dtest=RecipAiApplicationTests` — Flyway applies V21 against
     a fresh Testcontainers Postgres and the context boots.

2. **Migrate `recipes` onto `permissions`, repoint `planning` and the recompute, delete the
   superseded types, and bring both suites over — one commit.** These cannot be sequenced apart:
   deleting the `RecipePermission` entity breaks `MealPlanEntryRepository.findCalendarEntries`'
   JPQL at context load, and once `RecipeService` writes `resource_permission` the recompute's
   `RECIPE` slice reads a table that no longer receives new rows, which fails
   `RecipeIntegrationTest.LimitsEnforced`'s recompute cases. Work in this order:

   a. **`RecipeService`** — drop `RecipePermissionRepository` for `PermissionsFacade`. Add
      `private ResourceRole resolveAccess(String userEmail, Recipe recipe)` per the design's
      pseudo-code: `roleOf` first, then the collection fallback guarded by
      `catch (RecipesCollectionAccessDeniedException | RecipesCollectionNotFoundException _)` —
      narrower than today's `catch (Exception e)` — then `throw new ResourceAccessDeniedException(RECIPE_RESOURCE, recipe.getId())`.
      Replace every `validateRecipeAccess` call site with it and retype the locals to `ResourceRole`.
      Then:
      - `save` — `permissionsFacade.grantOwner(RECIPE_RESOURCE, savedRecipe.getId(), userEmail)`
        replaces the hand-built `RecipePermission`; the DTO carries `ResourceRole.OWNER`.
      - `updateById` — **drop** the `if (userRole != OWNER && userRole != EDITOR) throw` guard; with a
        two-value enum and `resolveAccess` already refusing, it can never fire. Keep the
        `if (userRole == OWNER)` collection-reassignment rule.
      - `deleteById` — refuse unless `resolveAccess` returns `OWNER`; keep
        `publishEvent(new RecipeDeleted(...))` **first** (`MealPlanService.handleRecipeDeleted`
        depends on the ordering), then `permissionsFacade.resourceDeleted(RECIPE_RESOURCE, id)` in
        place of `deleteAllByRecipeId`, then `recipeRepository.deleteById`,
        `recipeImagesService.deleteAllImages`, `limitsFacade.release` — unchanged and still last.
      - `shareRecipe(ShareRequest request, UUID recipeId, String requesterEmail)` — load the recipe
        (404 if absent), `resolveAccess(requesterEmail, recipe)`, then
        `permissionsFacade.invite(RECIPE_RESOURCE, recipeId, request.email(), request.role(), recipe.getName(), requesterEmail)`.
        The already-shared no-op goes; the module's refusal rules replace it.
      - `unshareRecipe` — load the recipe, `resolveAccess(requesterEmail, recipe)`, then
        `permissionsFacade.revoke(RECIPE_RESOURCE, recipeId, targetEmail, requesterEmail)`. Both the
        self-unshare and never-unshare-an-OWNER guards go; `revoke` holds them.
      - `getSharedUsers` → `getPermissions(UUID recipeId, String userEmail)` returning
        `List<PermissionDto>`: load the recipe, `resolveAccess`, then
        `permissionsFacade.getPermissions(RECIPE_RESOURCE, recipeId)`.
      - `findAll` — `accessibleResources(RECIPE_RESOURCE, userEmail)` then
        `findAllByUserEmail(access.keySet(), userEmail)`, **no** empty-map short-circuit.
      - `findAllUnassigned` — same first call, but return `List.of()` on an empty map.
      - `handleRecipesCollectionUnshared` — one `accessibleResources` call outside the loop; keep the
        listener at `@TransactionalEventListener(BEFORE_COMMIT)`.
      Every path keeps loading the recipe before asking about access, so 404 still wins over 403 —
      the ordering T1 had to restore by hand in `ShoppingListService` comes free here.
   b. **`RecipeRepository`** — both queries exactly as `task-design.md` > Interfaces sketches them;
      `import java.util.Collection`.
   c. **`RecipeDetailsDto`** — `ResourceRole role`.
   d. **`RecipeController`** — `ShareRequest` / `UnshareRequest` in the two signatures,
      `ResponseEntity.noContent().build()` from both (see **Risks** — the design contradicts itself
      here and 204 is the settled answer), `getSharedUsers` → `getPermissions` on
      `@GetMapping("/{id}/permissions")` returning `List<PermissionDto>`. No `SecurityConfig` change:
      `/recipes/**` already covers the renamed path.
   e. **`RecipesExceptionHandler`** — `@RestControllerAdvice`, one package-private
      `ProblemDetail handleRecipeNotFound(...)` built with
      `forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage())` and `setTitle("Recipe Not Found")`.
      The access-denied branch goes; `PermissionsExceptionHandler` serves this module's 403 now.
   f. **Delete** the nine superseded `recipes` types listed in the inventory.
   g. **`MealPlanEntryRepository`** — swap the `RecipePermission` `EXISTS` for the
      `ResourcePermission` one in `task-design.md` > Interfaces, filtered by
      `rp.id.resourceType = 'RECIPE'`. Leave the `RecipesCollectionPermission` `EXISTS` alone.
   h. **`R__recompute_limit_usage.sql`** — the `RECIPE` `INSERT` reads
      `FROM resource_permission p WHERE p.resource_type = 'RECIPE' AND p.role = 'OWNER'`, matching the
      `SHOPPING_LIST` slice. The `DELETE` above it is unchanged — it keys off `limit_usage.subject`,
      not the permission table. `RECIPES_COLLECTION` and `MEAL_PLAN` stay as they are.
   i. **Both suites, mechanically** — `RecipeIntegrationTest`: `shareRecipe` sends
      `new ShareRequest(email, ResourceRole.EDITOR)`, `unshareRecipe` sends `new UnshareRequest(email)`,
      `getSharedUsers` becomes `getPermissions` over `/recipes/{id}/permissions` returning
      `List<PermissionDto>`, every `UserRole` becomes `ResourceRole`, and the handshake helpers
      (`getPendingInvites`, `acceptInvite`, `findPendingInviteId`, plus an
      `acceptPendingRecipeInvite`) are copied from `ShoppingListIntegrationTest`. Every existing test
      that shared and then read as the recipient gains an accept step. `MealPlanIntegrationTest`
      needs no mechanical change — it uses `planning`'s own `UserRole` / `SharedUserDto`, and its
      `import xyz.stasiak.recipai.recipes.*` only reaches `RecipeDetailsDto` and `RecipeData`.
   - Files: `…/recipes/RecipeService.java`, `RecipeRepository.java`, `RecipeController.java`,
     `RecipeDetailsDto.java`, `RecipesExceptionHandler.java`, the nine deletions,
     `…/planning/MealPlanEntryRepository.java`,
     `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql`,
     `…/test/…/recipes/RecipeIntegrationTest.java`
   - Verify: `cd backend && ./mvnw -q compile` after (f), then
     `cd backend && ./mvnw test -Dtest=RecipeIntegrationTest,MealPlanIntegrationTest,RecipesCollectionIntegrationTest,ShoppingListIntegrationTest,InviteIntegrationTest`
     and finally `cd backend && ./mvnw test`.

3. **Composition and handshake coverage in `RecipeIntegrationTest`** — the new cases from the
   **Test plan** below, in particular the collection-derived recipe reached with no direct permission
   at all (the empty-`IN` path) and the invite to a recipe already reachable through a shared
   collection.
   - Files: `backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java`
   - Verify: `cd backend && ./mvnw test -Dtest=RecipeIntegrationTest`

4. **Calendar coverage in `MealPlanIntegrationTest`** — one case where the caller reaches the entry's
   recipe through an accepted invite, so the repointed `EXISTS` is exercised; copy the invite helpers
   from `ShoppingListIntegrationTest`.
   - Files: `backend/src/test/java/xyz/stasiak/recipai/planning/MealPlanIntegrationTest.java`
   - Verify: `cd backend && ./mvnw test -Dtest=MealPlanIntegrationTest`

5. **HTTP suite** — in `backend/http/recipes.http`: rename the shared-users request to
   `GET {{baseUrl}}/recipes/{{recipeId}}/permissions`, add `"role": "EDITOR"` to the share body, and
   delete the three comments that describe recipes as the odd one out (`### … note the underscore,
   unlike every other module's /users` and the two `returns 200, unlike every other module`), since
   neither is true any more. Say instead that share creates a pending invite and that
   `backend/http/invites.http` is where `bob` answers it.
   - Files: `backend/http/recipes.http`
   - Verify: `./recipai.sh start-backend`, then run `backend/http/recipes.http` top to bottom as
     `alice`, answering the invite from `backend/http/invites.http` as `bob`.

6. **Docs** — run the `docs-updating` step for the files listed in the inventory.
   - Files: `docs/backend/modules/recipes/{module.md,api.md,db.md}`,
     `docs/backend/modules/permissions/db.md`, `docs/backend/modules/limits/{module.md,db.md}`
   - Verify: `docs/INDEX.md`'s recipes entry still describes what the files contain; no `docs/tasks/`
     references and no past-tense change narration leak into module docs.

## Test plan

**Unit tests**

_N/A — the project has no service-level unit tests for this kind of logic; `recipes` is exercised
through its HTTP surface, per `docs/backend/standards/integration-tests.md`._

**Integration tests**

`RecipeIntegrationTest` (modified — `user1@example.com` owns and shares, `user2@example.com` is
invited, `user@example.com` is the third party):

- `shouldShareAndUnshareRecipes` — **rewritten**: after sharing, user2 is still 403 on
  `GET /recipes/{id}` and the recipe is absent from their `/recipes`; `/permissions` reads
  `[user1 OWNER pending=false, user2 EDITOR pending=true]`; user2 accepts; the recipe reads with
  `role = EDITOR` and user2 can update it but gets 403 on delete; user1 unshares; user2 is 403 again
  and `/permissions` is back to the owner alone; user1 can still delete.
- `shouldAllowEditorsToShareAndUnshareButPreventUnsharingOwner` — **rewritten**: user2 accepts first,
  then invites `user@example.com`, who stays 403 until they accept; user2 cancels a *pending* invite
  and it disappears from `/permissions`; user2 unsharing user1 (the OWNER) is 403.
- `shouldPreventEditorFromUnsharingThemselves` — **new**: user2, after accepting, cannot unshare
  themselves — 403 from `revoke`'s shared guard, where today's `IllegalArgumentException` surfaces
  as a 500.
- `shouldRefuseSecondInviteWhenOneIsAlreadyPending` — **new**: 409 with `reason = ALREADY_INVITED`.
- `shouldRefuseInviteWhenTargetAlreadyHasAccess` — **new**: accept, then re-share — 409 with
  `reason = ALREADY_HAS_ACCESS`; inviting the owner is the same refusal.
- `shouldRemovePendingInviteWhenRecipeIsDeleted` — **new**: user1 deletes a recipe with an unanswered
  invite; user2's `/invites` no longer lists it.
- `shouldReturn404WhenSharingUnknownRecipe` — **new**: the load-then-check ordering holds, 404 not 403.
- `shouldHandleSharedRecipesInUserRecipeList` — gains an accept step; the recipe appears only after.
- `shouldListCollectionDerivedRecipeWithoutAnyDirectPermission` — **new, the empty-`IN` case**: user2
  holds no `RECIPE` permission at all and no pending invite; user1 shares a *collection* containing
  one recipe; user2's `GET /recipes` returns that recipe and does not 500.
- `shouldExcludeCollectionDerivedRecipeFromUnassignedList` — **new**: the same user's
  `GET /recipes?unassigned=true` is empty, exercising the deliberate short-circuit.
- `shouldKeepDirectRoleForRecipeAlsoReachableThroughCollection` — **new**: the owner of a recipe in a
  collection they can also reach still reads `OWNER`; composition never lowers an answer.
- `shouldStillInviteToRecipeAlreadyReachableThroughSharedCollection` — **new**: the refusal rules see
  only granted rows, so the invite is created; after accepting, user2 still reads the recipe as
  `EDITOR` and the direct row shadows the composition with the same answer.
- `shouldNotListCollectionDerivedUsersInPermissions` — **new**: `/permissions` on a collection-shared
  recipe lists the owner only — collection-derived access is not listed, exactly as today.
- `shouldAccessRecipeInSharedCollection`, `shouldAccessRecipeDetailInSharedCollectionWithEditorRole`,
  `shouldReturnNullCollectionNameWhenUserLacksCollectionAccess`,
  `shouldIgnoreCollectionAssignmentChangeByEditor`, `shouldNotAccessRecipeInCollectionWithoutPermission`
  — unchanged in intent; only the role type moves. Collection sharing is still a direct grant in T2,
  so none of them gains an accept step.
- `shouldRemoveOwnedRecipesFromCollectionWhenUnshared` — unchanged; it now covers
  `handleRecipesCollectionUnshared` resolving ownership through the facade.
- `shouldPreventCrossUserAccess`, `shouldIsolateRecipesBetweenUsers` — unchanged; the 403 body now
  comes from `PermissionsExceptionHandler`.
- `LimitsEnforced` — `shouldLeaveRecipientBalanceUntouchedOnShareAndUnshare` gains an accept step; new
  `shouldNotCountPendingInviteTowardsRecipientQuota` asserts an unanswered invite moves neither the
  recipient's nor the owner's `RECIPE` balance.

  No new recompute case is added, and this **diverges from `task-design.md`**, which asks for one:
  `LimitsEnforced` already runs the full set for `RECIPE` — `shouldRepairDriftToActualOwnedCountViaRecompute`,
  `shouldClearUsageForSubjectThatOwnsNothing`, `shouldSpareFlowConfiguredSubjectFromRecompute`,
  `shouldSpareSubjectWithoutOverrideWhenResourceDefaultIsFlow` and
  `shouldChangeNothingOnSecondRecomputeRun` — and each creates its recipes through the API, which after
  step 2 writes `resource_permission`. They therefore assert exactly what a new case would. What they
  cannot cover is whether `V21__`'s *copy* of pre-existing rows was faithful, because an integration
  test has no pre-migration rows; that check is in **Manual verification**.

`MealPlanIntegrationTest` (modified):

- `shouldIndicateRecipeAccessGrantedThroughResourcePermission` — **new**: user1 owns a plan shared
  with user2 and an entry pointing at a recipe user1 owns; user1 invites user2 to that recipe and
  user2 accepts; the calendar reports `hasRecipeAccess = true`. Without the repointed `EXISTS` this
  is `false`.
- `shouldIndicateRestrictedRecipeAccess` — unchanged; still `false` for a recipe user2 cannot reach.
- `shouldGetCalendarView` — unchanged; the owner's own `resource_permission` row answers the `EXISTS`.

`RecipesCollectionIntegrationTest`, `ShoppingListIntegrationTest`, `InviteIntegrationTest`,
`PermissionsModuleArchitectureTest` — unmodified, and all must stay green. The architecture test
matters here: it constrains `permissions`' outgoing dependencies only, so `planning` naming
`ResourcePermission` in JPQL does not trip it.

**Flutter widget/integration tests**

_N/A — T2 is backend only; the mobile catch-up (the `/permissions` rename, the required `role` field,
and recipes' 200 → 204) is T5._

**Manual verification**

- `tasks.md` > T2 > How to verify, steps 1–4, against `./recipai.sh start-backend` with
  `Bearer alice` / `Bearer bob`, driven from `backend/http/recipes.http`, `backend/http/collections.http`
  and `backend/http/invites.http`.
- Step 4 specifically: snapshot `limit_usage` on a database with pre-existing recipes, apply `V21__`,
  run the repeatable recompute, and diff — it must be byte-identical.
- Confirm `SELECT count(*) FROM recipai.resource_permission WHERE resource_type = 'RECIPE'` equals
  `SELECT count(*) FROM recipai.recipe_permission` after `V21__`.

## Verification checklist

- [ ] `cd backend && ./mvnw -q compile` — no new warnings.
- [ ] `cd backend && ./mvnw test` — all new and existing tests pass.
- [ ] `V21__recipe_permission_to_resource_permission.sql` applies cleanly to a fresh database
      (covered by `RecipAiApplicationTests`) **and** to a copy of production data with existing
      `recipe_permission` rows — the partial `uq_resource_permission_owner` is the thing that can
      abort it.
- [ ] `grep -rn "RecipePermission\|recipes\.UserRole\|ShareRecipeRequest\|UnshareRecipeRequest\|shared_users" backend/src`
      returns nothing.
- [ ] `grep -rn "recipe_permission" backend/src/main/resources` matches only `V6__` and `V21__` —
      the recompute no longer reads it.
- [ ] `GET /recipes/{id}/shared_users` is gone (404) and `GET /recipes/{id}/permissions` serves the
      list with a `pending` flag — the rename T5 depends on.
- [ ] `POST /recipes/{id}/share` and `/unshare` return **204**, matching the other three modules.
- [ ] `POST /recipes/{id}/share` without a `role` is a 400, and with `"role":"OWNER"` is a 400 from
      `InvalidInviteRoleException`.
- [ ] `PermissionsModuleArchitectureTest` passes — `recipes` composing two answers did not leak
      domain knowledge into the module.
- [ ] `tasks.md` > T2 > How to verify, steps 1–4, succeed end to end.
- [ ] `backend/http/recipes.http` runs top to bottom, with the invite answered from
      `backend/http/invites.http`.
- [ ] `task-design.md` > Assumptions to verify: each is confirmed, or carried into **Risks** below
      with a decision.
- [ ] Logs at `INFO` on the happy path are clean; the `resolveAccess` collection-fallback line stays
      at `DEBUG`.
- [ ] `recipe_permission` is still present and no longer read or written — the drop is T3's.

## Risks surfaced during planning

- **Risk:** `task-design.md` > Decisions made contains two contradictory bullets — "**Share and
  unshare keep returning 200, not 204**" and "**`share` and `unshare` return 204, not 200**".
  **Why it matters:** an implementer working top-to-bottom hits the stale one first, and the choice is
  client-visible: `mobile/lib/features/recipe/recipe_repository.dart` accepts only `200` from both
  `shareRecipe` and `unshareRecipe` today, and `tasks.md` > T5 lists the 204 catch-up as a required
  item.
  **Mitigation:** 204 is the settled answer — it is what the design's own **HTTP surface** table, its
  `RecipeController` note and `tasks.md` > T5 all state, and the "keep 200" bullet is a leftover.
  Delete the stale bullet from `task-design.md` while implementing.

- **Risk:** four of this task's changes cannot be sequenced apart, and the design presents them as
  independent components. Deleting the `RecipePermission` entity breaks
  `MealPlanEntryRepository.findCalendarEntries`' JPQL at context load, so `planning`'s repoint must
  land in the same commit; and the moment `RecipeService` writes `resource_permission`, the
  recompute's `RECIPE` slice reads a table that receives no new rows, failing
  `RecipeIntegrationTest.LimitsEnforced`'s five existing recompute cases — so the recompute repoint
  must land with them too.
  **Why it matters:** any attempt to split step 2 leaves the branch red, and the recompute half fails
  in a way that looks like a quota bug rather than an ordering mistake.
  **Mitigation:** step 2 spells out the single commit and its internal order.

- **Risk:** `task-design.md` reasons about "Hibernate 6"; the project runs Hibernate **7.4.1.Final**
  (via `spring-boot-starter-parent` 4.1.0).
  **Why it matters:** the `findAllByUserEmail` design depends on an empty `IN` collection rendering as
  a false predicate rather than invalid SQL, and that guarantee is being taken from the wrong
  version's documentation.
  **Mitigation:** the version difference does not change the expected behaviour, but discharge it by
  test rather than by claim — `shouldListCollectionDerivedRecipeWithoutAnyDirectPermission` is exactly
  that case, and the design's fallback (short-circuit plus a collection-only query) applies unchanged
  if it fails.

- **Risk:** `recipe_permission.recipe_id` carries an `ON DELETE`-less foreign key to `recipes` —
  inherited from `user_recipes` in `V1__initial_schema.sql` and renamed by `V6__`. From step 2, the
  delete path no longer clears that table, so `DELETE /recipes/{id}` will 500 for any recipe that
  existed before `V21__` shipped.
  **Why it matters:** it is the accepted gap in `tasks.md` > Cross-task notes, but the confirmation
  matters: **no test can catch it**, because every recipe an integration test deletes was created
  after `V21__` and therefore has no legacy row. The branch will look clean and still break on the
  first production delete.
  **Mitigation:** none in code — this is a deliberate non-fix that T3 closes by dropping the table.
  Keep the branch unreleased, and repeat the note in the PR description so it is not mistaken for a
  regression introduced here.

- **Risk:** the self-unshare refusal changes status, not just shape. `recipes` throws a bare
  `IllegalArgumentException` today and **no handler anywhere in the backend maps it**, so it currently
  surfaces as a 500; after step 2 it is `revoke`'s 403. The design describes the change as
  `IllegalArgumentException` → "the shared 403" without naming the observable status.
  **Why it matters:** a 500 → 403 change is an API contract change that no existing test asserts, and
  it is easy to mis-read as pre-existing behaviour.
  **Mitigation:** `shouldPreventEditorFromUnsharingThemselves` asserts the 403 explicitly.

- **Risk:** `task-design.md` says `RecipesExceptionHandler` should be "identical to
  `RecipesCollectionsExceptionHandler` next door", but that class is `@ControllerAdvice` with `public`
  handler methods.
  **Why it matters:** copying it literally reintroduces the shape this task is meant to standardise.
  **Mitigation:** follow `docs/backend/standards/module-structure.md`, whose worked example is
  `RecipesExceptionHandler` itself in the target shape — `@RestControllerAdvice`, package-private
  method, bare `ProblemDetail`. `PermissionsExceptionHandler` is the truer sibling to mirror.

- **Risk:** two of the design's Assumptions to verify cannot be discharged in CI — the
  one-OWNER-per-recipe check that gates `uq_resource_permission_owner`, and the `limit_usage`
  byte-identity check across the migration.
  **Why it matters:** the first aborts the migration at deploy time rather than in the build; the
  second is a silent access-control-plus-quota bug if the copy lost or duplicated rows.
  **Mitigation:** run
  `SELECT recipe_id FROM recipai.recipe_permission GROUP BY recipe_id HAVING count(*) FILTER (WHERE role = 'OWNER') <> 1;`
  against production **before** writing `V21__`, and split the copy into a data-repair migration first
  if it returns rows. The second is discharged by the production-snapshot diff in **Manual
  verification**; the repointing half is covered by the existing `LimitsEnforced` cases. The
  remaining three assumptions are discharged in-plan: a full-text search over `backend/src` confirms
  `RecipeRepository`, `MealPlanEntryRepository` and `R__recompute_limit_usage.sql` are the only
  readers of `recipe_permission`; `MealPlanService` reaches every other recipe-access decision through
  `RecipeFacade.getRecipes` → `RecipeService.findAll`, which composes correctly; and no error body is
  parsed anywhere in `mobile/lib/`.
