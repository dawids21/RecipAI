# Sharing and permissions as implemented today

## Summary

Sharing is implemented four separate times — once per shareable resource type (recipes, recipes
collections, shopping lists, meal plans) — with no shared abstraction on the backend. Each module owns its
own `*_permission` table, its own `UserRole` enum, its own `SharedUserDto`, and its own copy of the
share/unshare/list-shared-users logic. The single piece of genuinely shared code is on the mobile side: one
`SharingDialog` widget that all four features reuse. Permissions are keyed on **email string**, not on an
account id, and there is **no users table and no user registry anywhere in the backend** — the app cannot
enumerate its registered accounts from its own database.

## Key findings

### Data model

- Four permission tables, structurally identical, all with a composite primary key of `(email, <resource>_id)`
  and a `role VARCHAR CHECK (role IN ('OWNER','EDITOR'))`:
    - `recipe_permission (email, recipe_id, role)`
    - `recipes_collection_permission (email, recipes_collection_id, role)`
    - `shopping_list_permission (email, shopping_list_id, role)`
    - `meal_plan_permissions (email, plan_id, role)` — note the plural table name and the `plan_id`
      (not `meal_plan_id`) column, the one naming inconsistency in the set
- There is **no foreign key from a permission row to any user record**, because no user record exists. The
  `email` column is free text validated only by `@Email` at the request boundary.
- Roles are `OWNER` and `EDITOR` only. `OWNER` is written exactly once, at resource creation, by the
  creating service. Every share writes `EDITOR`, hardcoded.
- Each module declares its own package-private `UserRole` enum — four separate enums with identical
  contents (`recipes/UserRole.java`, `recipes/collections/UserRole.java`, `shoppinglists/UserRole.java`,
  `planning/UserRole.java`). Same for `SharedUserDto` (four copies) and the `Share*Request` /
  `Unshare*Request` records (four pairs, each just `record X(@NotBlank @Email String email)`).
- Permission rows are the **system of record for ownership**, and the `limits` module reads them directly:
  `R__recompute_limit_usage.sql` rebuilds `limit_usage` by counting `role = 'OWNER'` rows in each of the
  four permission tables. So the permission tables are load-bearing beyond access control.

### Where permissions are enforced

Enforcement is entirely inside the service layer of each module — there is no Spring Security method
security, no `@PreAuthorize`, no filter or interceptor doing resource-level checks. `SecurityConfig` only
distinguishes authenticated from anonymous, per URL prefix:

```
/recipes/**, /extract/**, /users/**, /shopping-lists/**, /collections/**, /meal-plans/**, /limits/**
    → .authenticated()
anyRequest() → denyAll()
```

(`/users/**` is listed but no `/users` controller exists — dead config.)

The caller's identity is read in every controller method as `jwt.getClaimAsString("email")` and passed down
as a `String userEmail` parameter. Every service method that touches a resource takes that email and
re-resolves the permission itself. The four modules do this in three slightly different shapes:

- **Recipes** — `RecipeService.validateRecipeAccess(userEmail, recipe)` returns the `UserRole` or throws
  `RecipeAccessDeniedException`. Uniquely, it has a **fallback path**: if there is no direct
  `recipe_permission` row and the recipe belongs to a collection the caller can access, the caller is
  granted a synthetic `EDITOR` role. This is the "collection-derived access" the requirements refer to; it
  is computed on the fly and never materialised as a row.
- **Collections / shopping lists** — `permissionRepository.findById(new XPermissionId(email, id))
  .orElseThrow(...)`, i.e. *any* permission row is enough for read/share/unshare; `hasOwnerRights()` is
  checked only for delete.
- **Meal plans** — same lookup, then `permission.hasEditorRights()`, which returns true for `OWNER` or
  `EDITOR` — so in practice also "any row".

List endpoints filter by joining the permission table (`RecipeRepository.findAllByUserEmail` joins both
`RecipePermission` and `RecipesCollectionPermission`; the others join their single permission table). This
is the mechanism a pending invite must *not* trip: a resource is visible precisely because a permission row
exists.

### Who may share

Sharing is **not** owner-only in any of the four modules. Any principal holding a permission row (or, for
recipes, collection-derived access) may share the resource onward with anyone. Unshare is likewise open to
any holder, with two guards: the target must not be `OWNER`, and (recipes only) the caller must not be
unsharing themselves.

### The share operation, today

`RecipeService.shareRecipe` is representative of all four:

1. Load the resource; 404 if missing.
2. Validate the requester's access; 403 if none.
3. If the target email already has a permission row → **log a warning and return** (silently idempotent).
4. Otherwise insert a permission row with `role = EDITOR`.

There is no notification, no record of who shared, and no timestamp — a permission row carries only
`(email, resource_id, role)`. Nothing in the schema can answer "who granted this?" or "when?".

### Deletion paths

All four services delete every permission row for the resource before deleting the resource itself
(`deleteAllByRecipeId`, `deleteAllByShoppingListId`, `deleteAllByPlanId`,
`deleteAllByRecipesCollectionId`), then call `limitsFacade.release(...)`. These are the four places that
would need to also cancel pending invites.

One cross-module side effect already exists and is worth noting as prior art for invite cleanup:
`RecipesCollectionService.unshareRecipesCollection` publishes a `RecipesCollectionUnshared` event, which
`RecipeService.handleRecipesCollectionUnshared` consumes (`@TransactionalEventListener(BEFORE_COMMIT)`) to
detach that user's owned recipes from the collection.

### Endpoints

| Resource | Share | Unshare | List |
|---|---|---|---|
| Recipe | `POST /recipes/{id}/share` | `POST /recipes/{id}/unshare` | `GET /recipes/{id}/shared_users` |
| Collection | `POST /collections/{id}/share` | `POST /collections/{id}/unshare` | `GET /collections/{id}/users` |
| Shopping list | `POST /shopping-lists/{id}/share` | `POST /shopping-lists/{id}/unshare` | `GET /shopping-lists/{id}/users` |
| Meal plan | `POST /meal-plans/{id}/share` | `POST /meal-plans/{id}/unshare` | `GET /meal-plans/{id}/users` |

All share/unshare bodies are `{"email": "..."}`. Note the listing path inconsistency: recipes use
`/shared_users`, the other three use `/users`. Response shape is uniform:
`[{"email": "...", "role": "OWNER"|"EDITOR"}]`, `OWNER` first (`ORDER BY role DESC`).

### Mobile side

- **One shared widget**: `mobile/lib/core/widgets/sharing_dialog.dart` — an `AlertDialog` with an email
  field + Share button, and a "Shared with" list where each entry shows email, role display name, and a
  remove button (suppressed for the current user). It is fully generic: its inputs are a title, a
  `ValueListenable<AsyncValue<List<SharedUser>>>`, and `onShare` / `onUnshare` callbacks. Its own
  `SharedUser` model is `{email, role (String), isCurrentUser}` — role is already a display string by the
  time it reaches the widget, so a "pending" flag would be a new field here.
- **Four thin wrappers** feed it: `RecipeSharingDialog`, `ShoppingListSharingDialog`,
  `MealPlanSharingDialog`, and — for collections — an inline `SharingDialog` built directly in
  `recipes_collection_list_screen.dart::_showSharingDialog` (no dedicated wrapper class).
- **Four permission models**, one per feature, all identical: `RecipePermission`,
  `RecipesCollectionPermission`, `ShoppingListPermission`, `MealPlanPermission`, each `{email, role}` with
  a `fromJson`. They share one enum, `mobile/lib/shared/user_role.dart` (`owner`, `editor`, with
  `toApiString` / `fromApiString` / `displayName`) — unlike the backend, mobile has a single role type.
- **`isCurrentUser`** is computed in each service by comparing `permission.email` against
  `_authService.email` (e.g. `recipe_detail_service.dart:117-122`).
- **Role-based UI gating** is sparse and owner-vs-editor only: delete actions
  (`recipe_detail_screen.dart:256`, `meal_plan_drawer.dart:82`, `plan_list_tile.dart:74`,
  `shopping_list_detail_screen.dart:571`) and collection re-assignment (`recipe_form_widget.dart:140`).
  The Share action itself is **never gated** — it is offered to editors too, matching the backend.
- **App shell**: `main_screen.dart` has a three-item `BottomNavigationBar` (Recipes / Planning / Shopping)
  with a per-tab FAB. There is no existing badge, notification, or indicator pattern anywhere in the app —
  an invites indicator would be a new UI concept.

### Can we find out which accounts are registered?

**Not from the backend.** There is no `users` table (confirmed against every Flyway migration: the only
tables are `recipes`, `recipe_images`, `recipe_permission`, `recipes_collections`,
`recipes_collection_permission`, `shopping_lists`, `shopping_list_permission`, `shopping_list_items`,
`meal_plans`, `meal_plan_permissions`, `meal_plan_entries`, `limit_config`, `limit_usage`). There is no
user, account or profile entity in the Java source, and no `/users` controller. `provisioning/` — despite
the name — has nothing to do with user provisioning; it converts recipe ingredients into shopping list
items.

Identity lives entirely in **Firebase Authentication** (issuer
`https://securetoken.google.com/recipai-751ae`, Google Sign-In only on mobile). The backend is a pure OAuth2
resource server: it trusts the `email` claim and stores it, and never registers or looks up a user. There
is **no Firebase Admin SDK dependency** in `pom.xml`, so the backend cannot call `listUsers()` either.

What the backend *can* tell you is a **derived, partial** list: every email that appears as a subject
somewhere in its own data — `SELECT DISTINCT email` across the four permission tables, plus
`limit_config.subject` / `limit_usage.subject`. That set is neither complete (a user who signed in but
created nothing has no rows) nor sound (an email that was shared with but never signed up also has rows).
Distinguishing the two is exactly the gap the invite feature runs into: **there is no way, today, to check
whether an invited email corresponds to a real account.**

The authoritative list exists only in the Firebase console / Firebase Admin API, outside this codebase.

## Implications for the share-invites task

- Four near-identical implementations means the invite work is either replicated four times or is the
  moment to introduce a shared abstraction. Nothing in the backend currently shares sharing code — not even
  a common `UserRole` — so a shared invites module would be a new pattern (though `limits` is precedent for
  a cross-cutting module with a facade, see ADR-0006).
- "Grants no access while pending" has to contend with the fact that a permission row *is* access: the
  list queries join the permission tables unconditionally, and every service-layer check treats any row as
  sufficient. Whatever represents a pending invite must be invisible to `findAllByUserEmail` and friends,
  to `validateRecipeAccess` and the three equivalent lookups, and to the `role = 'OWNER'` counting in
  `R__recompute_limit_usage.sql`.
- The `role` field the requirements want on the invite has no counterpart today — share is hardcoded to
  `EDITOR` in all four services, and the request DTOs carry only an email.
- Invite listing for the invitee needs a query keyed on email alone, across all resource types. No such
  cross-resource query exists today; every query is scoped to a single module's tables.
- `SecurityConfig` allowlists URL prefixes and ends in `anyRequest().denyAll()`, so any endpoint on a new
  prefix must be added there explicitly. (The `/users/**` entry already listed there is dead — no such
  controller exists.)
- On mobile, the pending/accepted distinction reaches the UI through `SharedUser` and the shared
  `SharingDialog`, which all four features already funnel through. The invitee-facing surface and its
  indicator are entirely new; there is no badge pattern to copy.
- The open question "sharer loses access while an invite is pending" is sharpened by the fact that
  permission rows carry no author: if an invite must know who sent it, that is new information the system
  has never recorded.

## Open questions / gaps

- No timestamps or authorship on permission rows — if invites need "invited by" or "invited at", that is
  new.
- Recipe access via a collection is computed, not stored. An invite to a recipe for someone who already has
  collection-derived access will produce a permission row that duplicates access they already had; the
  requirements say to allow it, but the behaviour on accept (a row shadowing a computed `EDITOR`) is
  untested territory.
- Whether an invite to an unregistered email should be verifiable at all is unanswerable without Firebase
  Admin access — the requirements assume it is not, and current architecture agrees.
- Backend integration tests exist per module (`RecipeIntegrationTest`, `RecipesCollectionIntegrationTest`,
  `ShoppingListIntegrationTest`, `MealPlanIntegrationTest`) and all four already cover sharing; there is no
  shared sharing test fixture to extend.

## Sources

- `docs/backend/modules/{recipes,shopping-lists,planning}/db.md` — the four permission table definitions,
  roles and relationships
- `docs/backend/modules/{recipes,shopping-lists,planning}/api.md` — share/unshare/shared-users endpoint
  contracts and their documented notes
- `docs/backend/modules/limits/db.md`, `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql`
  — how `limits` reads `role = 'OWNER'` out of the permission tables
- `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java` — `shareRecipe`, `unshareRecipe`,
  `getSharedUsers`, `validateRecipeAccess` and the collection-derived access fallback
- `backend/src/main/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionService.java` — collection
  share/unshare and the `RecipesCollectionUnshared` event
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java`,
  `planning/MealPlanService.java` — the other two share implementations and their delete paths
- `backend/src/main/java/xyz/stasiak/recipai/{recipes,shoppinglists,planning,recipes/collections}/UserRole.java`,
  `SharedUserDto.java`, `Share*Request.java` — the duplicated per-module types
- `backend/src/main/java/xyz/stasiak/recipai/config/security/SecurityConfig.java`,
  `config/security/DevAuthConfig.java` — URL-level authentication, the absent resource-level security, and
  the dev-profile email convention
- `backend/src/main/resources/application.yml`, `backend/pom.xml`,
  `backend/src/main/resources/db/migration/*.sql` — Firebase issuer, absence of a Firebase Admin dependency,
  absence of any user table
- `mobile/lib/core/widgets/sharing_dialog.dart` — the one shared sharing UI and its `SharedUser` model
- `mobile/lib/features/{recipe,shopping_list,planning}/*_sharing_dialog.dart`,
  `recipe/collection/recipes_collection_list_screen.dart` — the four call sites
- `mobile/lib/shared/user_role.dart`, `mobile/lib/features/*/*_permission.dart` — the mobile role enum and
  the four permission models
- `mobile/lib/features/auth/auth_repository.dart`, `auth_service.dart` — Firebase/Google Sign-In as the sole
  identity source and where the current user's email comes from
- `mobile/lib/core/main_screen.dart` — the bottom navigation shell, and the absence of any indicator pattern
- `docs/project/architecture.md` — the documented auth flow and module roles
- `docs/tasks/2026-08-26-share-invites/requirements.md` — the task this research serves
