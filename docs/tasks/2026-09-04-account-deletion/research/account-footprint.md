# Account footprint

Research for `requirements.md`, which assumes "an account's entire footprint is the data listed under
Requirements. Anything holding a user's email that is not in that list has been missed." This checks that
assumption against the code.

## Summary

The assumption nearly holds. Identity lives in exactly four columns across two modules — `permissions` and
`limits` — and everything else belonging to an account is reached from there, because no recipe, collection,
shopping list or meal plan carries an owner column of its own. Three things the requirements do not name:
`limit_config` override rows keyed by the user's email, the `SHOPPING_LIST_ITEM` usage rows keyed by the
UUIDs of lists the user owns, and the rows belonging to *other* people that hang off resources the user owns.
The third one matters most: the requirements describe permission and invite removal as "rows carrying their
email", and that reading leaves editors' rows pointing at deleted resources.

## Where an email is stored

Eleven JPA entities exist. Four columns across two tables carry an email; the other seven entities are keyed
by UUID only.

| Column                    | What it holds                                                                                 | Named in requirements |
|---------------------------|-----------------------------------------------------------------------------------------------|-----------------------|
| `resource_permission.email` | OWNER rows on the user's own resources, EDITOR rows on other people's                        | yes                   |
| `resource_invite.email`     | Invites addressed to the user                                                                 | yes                   |
| `resource_invite.invited_by`| Invites the user sent                                                                         | yes                   |
| `limit_usage.subject`       | Recorded usage for `EXTRACTION`, `RECIPE`, `RECIPES_COLLECTION`, `SHOPPING_LIST`, `MEAL_PLAN` | yes                   |
| `limit_config.subject`      | A per-user quota override, `NULL` meaning the resource default                                | **no**                |

Outside Postgres:

- **Firebase Auth** — the user record (UID, email, display name, photo URL, the `google.com` provider link).
  `mobile/pubspec.yaml` pulls only `firebase_core` and `firebase_auth`; with no Analytics, Crashlytics or
  Messaging in the app, that record is the whole Firebase footprint.
- **S3** — objects under `recipes/<recipeId>/`, both `<imageId>.<ext>` and `<imageId>-thumb.<ext>`. Keys carry
  no email, so the bucket is reachable only through the recipe ids the user owns.
- **Backend logs** — emails are logged at INFO on every grant, invite, share and unshare. Nothing rotates or
  scrubs them, and neither the requirements nor the anti-requirements mention them.
- **The device** — covered under "Left behind" below.
- **The AI provider** — `ExtractionService` sends recipe text or an image and nothing else; the email is used
  only to reserve a limit locally. No identity ever leaves for Google GenAI, so there is nothing to erase there.

## What ownership reaches

`recipes`, `recipe_images`, `recipes_collections`, `shopping_lists`, `shopping_list_items`, `meal_plans` and
`meal_plan_entries` have no email column. Ownership is resolved entirely through
`resource_permission` rows with `role = 'OWNER'`, filtered by `resource_type` — the four resource types are
`RECIPE`, `RECIPES_COLLECTION`, `SHOPPING_LIST`, `MEAL_PLAN`.

Foreign keys carry the children once the parents go:

- `shopping_list_items.shopping_list_id` → `ON DELETE CASCADE`
- `meal_plan_entries.plan_id` → `ON DELETE CASCADE`
- `recipe_images.id` → `recipes(id) ON DELETE CASCADE`
- `recipes.recipes_collection_id` → `ON DELETE SET NULL`, so deleting a collection detaches recipes rather
  than removing them, including recipes owned by someone else that sat in it

S3 has no such cascade: `RecipeImagesService.deleteAllImages` lists the `recipes/<id>/` prefix and deletes it
explicitly, logging and swallowing an `S3StorageException` — which is exactly the "partial deletion accepted"
posture the requirements take.

## Three gaps

### 1. `limit_config` override rows

`limit_config.subject` holds an email when an operator has raised or lowered a quota for one person
("e.g. the developer's own account"; never seeded by a migration). The requirements name `limit_usage` and not
this table. Leaving the row behind conflicts with the acceptance criterion *"signing in again with the same
Google account yields a fresh, empty account"* — the reborn account would silently inherit the old override.
The table has no write API at all: operators edit it with SQL, so removing a row needs new repository code.

### 2. `SHOPPING_LIST_ITEM` usage rows are keyed by list UUID

`limit_usage.subject` is an email for the four owner-scoped resources and for `EXTRACTION`, but for
`SHOPPING_LIST_ITEM` it is a **shopping list's UUID** — the count belongs to the list while the quota value is
configured from its owner. "Their `limit_usage` rows", read as rows carrying their email, misses one row per
list they own.

There is also no delete-by-subject query. `LimitUsageRepository.clear` removes a single `(resource, subject)`
row, so a full account clear is five calls for the email plus one per owned shopping list — unless deletion
routes through `ShoppingListService.deleteById`, which already calls
`limitsFacade.clear(id.toString(), SHOPPING_LIST_ITEM_RESOURCE)`.

### 3. Rows on the user's resources that carry someone else's email

Deleting a resource the user owns must clear rows that do not carry the deleted user's email at all:

- **EDITOR permission rows** held by the people it was shared with.
- **Invites addressed to third parties** on that resource — and note that an *editor* can share
  (`shareRecipe` requires editor, not owner), so such an invite may carry neither the deleted user's `email`
  nor their `invited_by`.

`PermissionsFacade.resourceDeleted` already does exactly this per resource — `deleteByIdResourceTypeAndIdResourceId`
plus `deleteByResourceTypeAndResourceId` — and every module's delete path calls it. The requirements' phrasing
("every `resource_permission` row carrying their email") describes only one of the two axes; the footprint is
*rows carrying the email* **plus** *rows attached to resources the email owns*.

## Effects on other accounts

- **Meal plan entries pointing at the deleted user's recipes.** `MealPlanService.handleRecipeDeleted` listens
  for the `RecipeDeleted` event and rewrites each entry into a placeholder carrying the recipe's name, clearing
  `recipe_id` and `serving_size`. `meal_plan_entries.recipe_id` has no foreign key, so if account deletion
  removes recipes with bulk SQL instead of going through `RecipeService.deleteById`, other users' plans are
  left holding ids that resolve to nothing. The recipe *name* survives in those placeholders either way.
- **Owners are not charged for editors.** Usage is owner-keyed and sharing never charges the recipient, so
  removing a user's EDITOR rows leaves other people's quota counts correct with no compensating work.

## Left behind

Consistent with the anti-requirements, but worth stating as part of the footprint:

- **Device storage** — `shopping_list_items.db` (its `items` and `outbox` tables), the rotating log files, and
  three `SharedPreferences` keys: `recipe_filter_collection_id` and `meal_plan_visibility`, both of which end
  up pointing at UUIDs that no longer exist, and `dev_auth_user_name`, which is the dev-profile identity itself.
- **Backend logs** — as above, emails at INFO.

## Mechanics that constrain the design

- **No delete-by-email query exists anywhere.** `ResourcePermissionRepository` deletes only by resource;
  `ResourceInviteRepository` by resource, or by resource plus email. `PermissionService.revoke` cannot stand in
  for the user's own EDITOR rows: it throws when the target equals the requester and throws again on an OWNER row.
- **`resource_invite` is indexed on `email` and `(resource_type, resource_id)`, not `invited_by`** — deleting
  the invites a user sent scans the table today.
- **No Firebase Admin SDK in `backend/pom.xml`.** Deleting an identity means a new dependency and a
  service-account credential. The client-side `FirebaseAuth.currentUser.delete()` is an alternative for the
  self-service flow only; the admin endpoint has no such option.
- **`/users/**` is already an authenticated matcher in `SecurityConfig`** with no controller behind it.
- **`AuthRepository` has no re-authentication method** — `signIn`, `signOut`, `getIdToken`, `watchAuthState`
  are all of it.

## Open questions

- Is a `limit_config` override part of the account (delete it) or an operator artifact about that email
  (keep it)? The "fresh, empty account" criterion argues for deleting it.
- Does deletion reuse each module's own `deleteById` — inheriting the `RecipeDeleted` event, the S3 cleanup,
  the limits release and the per-resource permission clear — or issue bulk deletes that are faster but skip all
  four?
- Are backend logs in scope? They are the one remaining store of the email after a successful deletion.

## Sources

- `docs/tasks/2026-09-04-account-deletion/requirements.md` — the assumed footprint this checks
- `backend/src/main/resources/db/migration/V1…V24` — every table, column, FK and index
- `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — rebuilds usage from `resource_permission`
- `docs/backend/modules/limits/module.md`, `limits/db.md` — the config-subject vs usage-subject split, `clear` semantics
- `backend/…/limits/LimitConfig.java`, `LimitUsage.java`, `LimitUsageRepository.java` — the two email-bearing limits columns
- `backend/…/permissions/` — `PermissionService`, `InviteService`, `PermissionsFacade`, and both repositories
- `backend/…/recipes/RecipeService.java`, `collections/RecipesCollectionService.java`,
  `shoppinglists/ShoppingListService.java`, `planning/MealPlanService.java` — the four delete paths
- `backend/…/recipes/images/ImageService.java`, `RecipeImagesService.java` — the S3 key scheme and prefix delete
- `backend/…/extraction/ExtractionService.java` — confirms no identity reaches the AI provider
- `backend/…/config/security/SecurityConfig.java`, `backend/pom.xml`, `backend/src/main/resources/application.yml`
  — authorized paths, absent Firebase Admin SDK, Firebase project `recipai-751ae`
- `mobile/pubspec.yaml` — Firebase surface is `firebase_auth` alone
- `mobile/lib/features/auth/auth_repository.dart`, `auth_service.dart` — no re-authentication method
- `mobile/lib/core/preferences_service.dart`, `features/shopping_list/shopping_list_item_database_factory.dart`,
  `core/logging/logging_setup.dart` — what stays on the device
