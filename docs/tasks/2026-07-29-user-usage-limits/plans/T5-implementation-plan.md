# T5: Mobile — refusal messaging and recipe standing — Implementation Plan

**Date:** 2026-08-23

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/backend/standards/java-patterns.md` — records for DTOs, package-private visibility; `LimitCap`
  and `LimitStanding` are the two exceptions that must be `public` because they cross the module boundary.
- `docs/backend/standards/module-structure.md` — facade-only cross-module access, `/…` plural kebab-case
  routes, `jwt.getClaimAsString("email")` for identity, `@Slf4j` `log.debug` at handler entry.
- `docs/backend/standards/integration-tests.md` — `@SpringBootTest(RANDOM_PORT)` + `RestClient` +
  Testcontainers, `shouldXxxWhenYyy` naming, the read-through-the-facade rule, and the
  `@Nested … recipai.limits.enabled=true` pattern the new endpoint tests slot into.
- `docs/mobile/standards/architecture.md` — Repository → Service → View, views never touch repositories;
  the new `features/limits/` follows this and `LimitUsage` is a plain model, which repositories may import
  across features.
- `docs/mobile/standards/state-management.md` — `ValueNotifier<AsyncValue<T>>`, read-only
  `ValueListenable` getters, `AsyncValue.guardAsync`, `_isXxxRunning` guards, and the `dispose()`
  obligation for every service that owns a notifier.
- `docs/mobile/standards/dependency-injection.md` — `setup<Feature>()` shape, constructor injection only
  (never `getIt<>()` inside a class body), external deps as nullable named setup parameters.
- `docs/mobile/standards/navigation.md` — services reach screens through route-builder closures, which is
  how `LimitsService` gets to the five surfaces.
- `docs/mobile/standards/widget-testing.md` — repository-only mocking, `test/support/` holds type
  declarations only, `SharedPreferences.setMockInitialValues` → `GetIt.I.reset()` → `PreferencesService`
  → `setup*()` ordering.
- `docs/mobile/standards/theming.md` — `Theme.of(context)` then `AppSpacing`/`AppAnimations` before any
  new constant, for the counter's spacing and its de-emphasised text style.
- `docs/project/local-development.md` — `./recipai.sh start-backend`, `Bearer alice` grammar (bare name,
  no `@`), and `RECIPAI_LIMITS_ENABLED=true`, which the dev profile leaves off.

**Design & ADRs**

- `plans/T5-task-design.md` > *Components and responsibilities* — the authoritative file-by-file list.
- `plans/T5-task-design.md` > *Interfaces and method signatures* — the exact `LimitCap`, `LimitStanding`,
  facade and Dart signatures to implement.
- `plans/T5-task-design.md` > *Pseudo-code* — the virtual elapsed-period rule and the item-cap guard;
  both are subtle enough that transcription beats re-derivation.
- `plans/T5-task-design.md` > *Decisions made* — settled; do not re-open (notably: no 429 parsing,
  `caps`/`cap` honour the kill-switch while `standing` ignores it, the item count is local).
- `plans/T4-task-design.md` > *Correction after first implementation* — why the item cap's **configuration**
  subject is the owner's email while its **usage** subject is the list UUID.
- `docs/ADRs/0006-shared-limits-module.md` > *Consequences* — the opaque-subject boundary the new endpoints
  must not breach, and the drift risk the displayed number inherits.
- `HLD.md` > Feature areas > *Mobile*, *Limits module (new)* — the never-compute-on-device rule.

**Code to mirror**

- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitService.java` — `reserve`'s
  `cutoffFrom`/`nextStart` arithmetic that `standing` must agree with exactly; `@Transactional` placement.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitConfigRepository.java` — the
  `ORDER BY c.subject NULLS LAST LIMIT 1` override-beats-default ordering `resolveAll` generalises.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsFacade.java` — the kill-switch guard + `log.debug`
  prologue every public method opens with.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionController.java` — a
  package-private controller with package-private handler methods (the style for four of the five new
  usage endpoints; `RecipeController`'s handlers are `public`, so match that file locally).
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` `createItem` — the
  `requireEditorPermission` → `requireOwnerEmail` pair the per-list cap read reuses verbatim.
- `backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java` `:1047-1110` — the
  `@Nested @TestPropertySource LimitsEnforced` class with its seeded override, API-only teardown and
  closing `assertThat(usedFor(SUBJECT)).isZero()`.
- `backend/src/test/java/xyz/stasiak/recipai/extraction/ExtractionIntegrationTest.java` `:80-105` — a suite
  that runs limits-on at class level, its `restClient(token)` helper and `usedFor` facade read.
- `mobile/lib/features/recipe/recipe_setup.dart` — the `setup*()` shape `setupLimits` copies.
- `mobile/lib/features/auth/auth_service.dart` — `isAuthenticated`, the `ValueListenable<bool>`
  `LimitsService` subscribes to, and its own `dispose()` discipline.
- `mobile/lib/features/recipe/recipe_list_service.dart` `loadRecipes` — the
  guard → `AsyncValue.guardAsync` → token-then-fetch body every new `load…Usage()` copies.
- `mobile/lib/features/shopping_list/shopping_list_rename_dialog.dart` and
  `mobile/lib/features/recipe/collection/recipes_collection_rename_dialog.dart` — the stateful-dialog
  pattern (`initState`/`dispose` on the controller, `context.pop(value)`) the two extracted create dialogs mirror.
- `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart` `_visibleItems` (`:543`) — the
  `!pendingDelete` set whose length is the item count.
- `mobile/lib/features/shopping_list/shopping_list_item_widget.dart` `:201-205` — the
  `_parseAndSave()`-before-`onSubmitted()` ordering the chain guard depends on.

## File inventory

**Backend — `limits` module**

- **CREATE** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitCap.java` — public record `(resource, kind, limit)`.
- **CREATE** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitStanding.java` — public record `(used, periodStart, resetsInSeconds)`.
- **DELETE** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitUsageDetails.java` — superseded by `LimitStanding`.
- **CREATE** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsController.java` — package-private `GET /limits`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitService.java` — `currentUsage` → `standing`; add `caps`, `cap`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsFacade.java` — same rename; add kill-switched `caps`/`cap`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitConfigRepository.java` — add `resolveAll(subject)`.

**Backend — consuming modules**

- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeController.java` — `GET /recipes/usage`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java` — `usage(userEmail)`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionController.java` — `GET /collections/usage`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionService.java` — `usage(userEmail)`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListController.java` — `GET /shopping-lists/usage`, `GET /shopping-lists/{id}/limits`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — `usage(userEmail)`, `itemCap(listId, userEmail)`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanController.java` — `GET /meal-plans/usage`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanService.java` — `usage(userEmail)`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractionController.java` — `GET /extract/usage`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractionService.java` — `usage(userEmail)`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/config/security/SecurityConfig.java` — `/limits/**` authenticated.

**Backend — tests**

- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsModuleArchitectureTest.java` — swap `LimitUsageDetails` for `LimitCap` + `LimitStanding`.
- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsIntegrationTest.java` — 33 call sites renamed; `shouldNotRestartElapsedPeriodWhenMaxIsZero` (`:269`) asserts `isZero()`; new `standing`/`caps`/`cap` cases.
- **CREATE** `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsApiIntegrationTest.java` — HTTP tests for `GET /limits` (the existing suite has no web environment).
- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/extraction/ExtractionIntegrationTest.java` — 3 call sites; `GET /extract/usage` cases.
- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java` — 8 call sites; `GET /recipes/usage` cases.
- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionIntegrationTest.java` — 8 call sites; `GET /collections/usage` cases.
- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java` — 14 call sites; `GET /shopping-lists/usage` and `GET /shopping-lists/{id}/limits` cases.
- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/planning/MealPlanIntegrationTest.java` — 6 call sites; `GET /meal-plans/usage` cases.

**Mobile — new `features/limits/`**

- **CREATE** `mobile/lib/features/limits/limit_cap.dart` — `LimitCap`, `LimitKind`, and the `LimitResources` key constants.
- **CREATE** `mobile/lib/features/limits/limit_usage.dart` — `LimitUsage(used, resetsInSeconds)`, shared by five features.
- **CREATE** `mobile/lib/features/limits/limits_repository.dart` — `GET /limits` → `Map<String, LimitCap>`.
- **CREATE** `mobile/lib/features/limits/limits_service.dart` — session caps, auth-driven load/clear, `capFor`.
- **CREATE** `mobile/lib/features/limits/limits_setup.dart` — DI registration.
- **CREATE** `mobile/lib/features/limits/limit_counter.dart` — the `used / limit` widget and `formatResetIn`.

**Mobile — wiring**

- **MODIFY** `mobile/lib/main.dart` — `setupLimits()` after `setupAuth()`; dispose `LimitsService` before `AuthService`.
- **MODIFY** `mobile/lib/core/routes.dart` — pass `LimitsService` into the five limit-aware screens.
- **MODIFY** `mobile/lib/core/main_screen.dart` — accept `limitsService`; hand it to `ShoppingListListFab` and `MealPlanDrawer`.

**Mobile — usage state**

- **MODIFY** `mobile/lib/features/recipe/recipe_repository.dart` — `fetchRecipeUsage`.
- **MODIFY** `mobile/lib/features/recipe/recipe_list_service.dart` — `recipeUsage` notifier + `loadRecipeUsage()`.
- **MODIFY** `mobile/lib/features/recipe/collection/recipes_collection_repository.dart` — `fetchCollectionUsage`.
- **MODIFY** `mobile/lib/features/recipe/collection/recipes_collection_list_service.dart` — `collectionUsage` + loader + **new** `dispose()`.
- **MODIFY** `mobile/lib/features/recipe/collection/recipes_collection_setup.dart` — register the new `dispose:`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_repository.dart` — `fetchListUsage`, `fetchItemCap`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_list_service.dart` — `listUsage` + loader + **new** `dispose()`.
- **MODIFY** `mobile/lib/features/planning/meal_plan_repository.dart` — `fetchPlanUsage`.
- **MODIFY** `mobile/lib/features/planning/meal_plan_list_service.dart` — `planUsage` + loader; extend `dispose()`.
- **MODIFY** `mobile/lib/features/extraction/extraction_repository.dart` — `fetchExtractionUsage`.
- **MODIFY** `mobile/lib/features/extraction/extraction_service.dart` — `extractionUsage` + loader + **new** `dispose()`.
- **MODIFY** `mobile/lib/features/extraction/extraction_setup.dart` — register the new `dispose:`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_setup.dart` — register `ShoppingListListService`'s new `dispose:`.

**Mobile — surfaces**

- **MODIFY** `mobile/lib/features/recipe/create_recipe_screen.dart` — load usage on `initState`; pass counter + blocked flag.
- **MODIFY** `mobile/lib/features/recipe/recipe_form_widget.dart` — optional `limitCounter` and `saveBlocked`.
- **CREATE** `mobile/lib/features/recipe/collection/recipes_collection_create_dialog.dart` — extracted stateful create dialog.
- **MODIFY** `mobile/lib/features/recipe/collection/recipes_collection_list_screen.dart` — use the dialog; accept `limitsService`.
- **CREATE** `mobile/lib/features/shopping_list/shopping_list_create_dialog.dart` — extracted stateful create dialog.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_list_fab.dart` — use the dialog; accept `limitsService`.
- **MODIFY** `mobile/lib/features/planning/plan_form_dialog.dart` — optional `mealPlanListService`/`limitsService`; counter and disabled create.
- **MODIFY** `mobile/lib/features/planning/meal_plan_drawer.dart` — accept and forward `limitsService`.
- **MODIFY** `mobile/lib/features/extraction/url_extraction_screen.dart` — counter + disabled extract FAB.
- **MODIFY** `mobile/lib/features/extraction/image_extraction_screen.dart` — counter + disabled extract button.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — `itemCap` notifier loaded in `openShoppingList`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_item_add_widget.dart` — `enabled` flag.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — counter, `enabled:`, `_createEphemeralItemAfter` guard.

**Mobile — tests**

- **MODIFY** `mobile/test/support/mocks.dart` — `MockLimitsRepository`.
- **MODIFY** `mobile/test/features/recipe/main_screen_recipes_tab_widget_test.dart` — `setupLimits(...)` in `setUp`, stubbed caps, `limitsService:` on `MainScreen`.

**Out of scope here:** the documentation refresh the design lists
(`docs/backend/modules/limits/*`, the four `api.md` files, `docs/backend/standards/integration-tests.md`,
`docs/INDEX.md`, the new `docs/mobile/modules/limits/`). That is the `docs-updating` step's work — see
*Risks surfaced during planning* for the one item it must not miss.

## Step-by-step plan

### 1. `LimitStanding` and `LimitCap`, and the 61-site rename

Introduce both records, delete `LimitUsageDetails`, and reshape `LimitService`/`LimitsFacade`. This is one
commit because nothing compiles between the delete and the last call site.

- `LimitCap`: `public record LimitCap(String resource, LimitKind kind, int limit) {}`.
- `LimitStanding`: `public record LimitStanding(int used, Instant periodStart, Long resetsInSeconds) {}`.
- `LimitConfigRepository.resolveAll(subject)`: keep the query JPQL and dedupe in a `default` method, so the
  override-beats-default ordering stays identical to `resolve`'s:

  ```java
  @Query("""
          SELECT c FROM LimitConfig c
           WHERE c.subject = :subject OR c.subject IS NULL
           ORDER BY c.resource, c.subject NULLS LAST
          """)
  List<LimitConfig> findResolutionCandidates(@Param("subject") String subject);

  default List<LimitConfig> resolveAll(String subject) {
      Map<String, LimitConfig> byResource = new LinkedHashMap<>();
      for (LimitConfig config : findResolutionCandidates(subject)) {
          byResource.putIfAbsent(config.getResource(), config);   // override sorts first
      }
      return List.copyOf(byResource.values());
  }
  ```

- `LimitService.standing(subject, resource)` — transcribe the design's pseudo-code. Three things must hold:
  it stays `@Transactional(readOnly = true)` and **writes nothing**; the config lookup is optional
  (`resolve(...)` may be empty — `shouldClearWithNoConfigurationAtAll` seeds usage with no config at all,
  so an `orElseThrow` here breaks it); and the lapse test reuses `config.getPeriod().cutoffFrom(now)`
  rather than re-deriving it.
- `LimitService.caps(subject)` / `cap(subject, resource)` — map `LimitConfig` → `LimitCap`.
- `LimitsFacade` — `currentUsage` → `standing` (flag-blind, as today); `caps` returns `List.of()` and `cap`
  returns `Optional.empty()` when `recipai.limits.enabled` is false, each with the module's `log.debug` prologue.
- Rename all 61 test call sites (`sed` the six files, then fix the 29 `LimitUsageDetails` mentions including
  five imports). One assertion genuinely changes: `LimitsIntegrationTest.shouldNotRestartElapsedPeriodWhenMaxIsZero`
  (`:269`) becomes `assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isZero()`.
- `LimitsModuleArchitectureTest`: `IS_A_SHARED_PUBLIC_TYPE` drops `LimitUsageDetails`, gains `LimitCap` and `LimitStanding`.

- Files: the seven `limits/` main files, `LimitsModuleArchitectureTest.java`, and the six test files listed in the inventory.
- Verify: `cd backend && ./mvnw -q test -Dtest='LimitsIntegrationTest,LimitPeriodTest,LimitsModuleArchitectureTest'` passes,
  then `./mvnw -q test` passes whole.

### 2. `GET /limits` and the security matcher

- `LimitsController` — package-private `@RestController @RequestMapping("/limits")`, one
  `@GetMapping` reading `jwt.getClaimAsString("email")` and returning `limitsFacade.caps(email)`.
  It must reference no resource name and no type outside `limits` + Spring, or
  `limitsModuleHasNoDomainKnowledge` fails.
- `SecurityConfig` — add `"/limits/**"` to the existing `.authenticated()` matcher list. Without it the
  chain's `anyRequest().denyAll()` answers 403.
- `LimitsApiIntegrationTest` (new) — `@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})`
  with `RANDOM_PORT`; the existing `LimitsIntegrationTest` has no web environment and is left alone.

- Files: `LimitsController.java`, `SecurityConfig.java`, `LimitsApiIntegrationTest.java`
- Verify: `./mvnw -q test -Dtest=LimitsApiIntegrationTest`, then live —
  `RECIPAI_LIMITS_ENABLED=true ./recipai.sh start-backend` and
  `curl -sS -H "Authorization: Bearer alice" localhost:8080/limits | jq` returns the six seeded caps;
  restart with the flag off and the same call returns `[]`.

### 3. The five usage endpoints and the per-list item cap

Each service gains one method delegating to `LimitsFacade.standing(userEmail, ITS_OWN_RESOURCE)`, mapping
`Optional.empty()` to `new LimitStanding(0, null, null)`; each controller gains one `@GetMapping("/usage")`
reading the email from the JWT. No module grows a counting query.

`ShoppingListService` additionally gains the per-list cap, reusing `createItem`'s exact pair:

```java
Optional<LimitCap> itemCap(UUID listId, String userEmail) {
    requireEditorPermission(listId, userEmail);          // 404 no list, 403 not an editor
    return limitsFacade.cap(requireOwnerEmail(listId), SHOPPING_LIST_ITEM_RESOURCE);
}
```

The controller maps that to `200 LimitCap` or `204` via `ResponseEntity.of(...)`/`noContent()`. The owner's
email never reaches the response.

- Files: the five controller/service pairs listed in the inventory.
- Verify: routing first — with the backend running,
  `curl -sS -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer alice" localhost:8080/recipes/usage`
  returns `200` and **not** a UUID-conversion 400, confirming the literal beats `GET /{id}`; repeat for
  `/shopping-lists/usage`. Then `./mvnw -q test`.

### 4. Backend endpoint tests

Add cases to each module's `LimitsEnforced` nested class (`ExtractionIntegrationTest` runs limits-on at
class level, so its cases go at the top level). Follow the existing teardown discipline: delete through the
API, clean only fabricated rows directly, and keep the closing `usedFor(SUBJECT)` zero assertion.

- Files: the five module integration tests.
- Verify: `./mvnw -q test` — full suite green, and `tasks.md` > T2/T3/T4 "How to verify" behaviours unchanged.

### 5. Mobile `features/limits/` module and DI

Create the six files. `LimitsService` subscribes to `authService.isAuthenticated` in its constructor,
loads on `true`, resets to `const AsyncValue.data({})` on `false`, and removes the listener in `dispose()`.
Add an `_isLoadRunning` guard even though the flag is expected to flip once per session — the state-management
standard asks for it and it costs one field.

`capFor(resource)` returns `caps.value.valueOrNull?[resource]` — null while loading, on failure, and when
the server reports `[]` because limits are disabled. Every consumer treats null as "no cap, stay enabled".

Wire `setupLimits()` into `main()` immediately after `setupAuth()` (it needs `AuthService`), and dispose it
in `_RecipAIAppState.dispose()` **before** `getIt<AuthService>().dispose()`, since it holds a listener on that
service's notifier.

- Files: the six `features/limits/*.dart` files, `mobile/lib/main.dart`
- Verify: `cd mobile && dart analyze` clean; `flutter test` still green.

### 6. Usage state on the five feature services

Five repositories gain a fetch method returning `LimitUsage`; five services gain
`ValueNotifier<AsyncValue<LimitUsage>>` + `load…Usage()` following `loadRecipes`' shape. Three services
(`RecipesCollectionListService`, `ShoppingListListService`, `ExtractionService`) have **no `dispose()` today** —
add one and register the `dispose:` callback in their setup functions; `ExtractionService` also has no
notifier today, so this is its first.

- Files: the five repository/service pairs, `recipes_collection_setup.dart`, `extraction_setup.dart`, `shopping_list_setup.dart`
- Verify: `dart analyze` clean; `flutter test` green (the widget test resolves all of these through `setup*()`).

### 7. Recipe surface

`RecipeFormWidget` takes `Widget? limitCounter` and `bool saveBlocked = false`; it renders the counter above
the form body and ORs `saveBlocked` into the save button's existing `_isLoading ? null : _saveRecipe` guard.
`CreateRecipeScreen` calls `loadRecipeUsage()` in `initState`, wraps `recipeUsage` and
`LimitsService.capFor(LimitResources.recipe)` in a `ValueListenableBuilder`, and passes both down.
`EditRecipeScreen` passes neither, which is what keeps the display create-only. `routes.dart` injects
`getIt<LimitsService>()` into `CreateRecipeScreen`.

- Files: `recipe_form_widget.dart`, `create_recipe_screen.dart`, `core/routes.dart`
- Verify: with a `RECIPE` limit of 3 and 3 recipes owned, the create screen shows `3 / 3 recipes` and
  Create Recipe is greyed; delete one and it re-enables on the next open.

### 8. Collection and shopping-list create dialogs

Extract the two inline `AlertDialog` bodies into `StatefulWidget`s mirroring their `*_rename_dialog.dart`
siblings, each taking its list service plus `LimitsService`, calling the usage loader in `initState`,
rendering the counter and disabling Create at the cap. Both call sites keep their existing
`showDialog<…>` → create → snackbar flow; the dialogs return the trimmed name (or null) rather than a bool,
matching the rename dialogs. `MainScreen` gains `limitsService` and forwards it to `ShoppingListListFab`;
`routes.dart` supplies it to `MainScreen` and `RecipesCollectionListScreen`.

- Files: the two new dialogs, `recipes_collection_list_screen.dart`, `shopping_list_list_fab.dart`,
  `core/main_screen.dart`, `core/routes.dart`
- Verify: at a `SHOPPING_LIST` cap of 2 with 2 lists, the FAB's dialog shows `2 / 2 lists` with Create
  disabled; the same for collections.

### 9. Plan dialog and the two extraction screens

`PlanFormDialog` gains optional `mealPlanListService` and `limitsService`; it loads and renders only when
`existingPlan == null`, and disables its `FilledButton` at the cap. `MealPlanDrawer` forwards both, passing
them only at the create call site (`:117`), not the edit one (`:147`). Both extraction screens call
`loadExtractionUsage()` in `initState` and gate their extract action — note the image screen's action is an
inline `ElevatedButton`, not a FAB, and it is already conditional on `_selectedImage != null`.

- Files: `plan_form_dialog.dart`, `meal_plan_drawer.dart`, `url_extraction_screen.dart`,
  `image_extraction_screen.dart`, `core/routes.dart`
- Verify: exhaust the extraction budget (`RECIPAI_LIMITS_ENABLED=true`, two extractions at the seeded
  default of 2), reopen the URL extraction screen and confirm `2 / 2 extractions` with the FAB disabled.

### 10. Shopping-list detail: per-list cap and both add surfaces

`ShoppingListRepository.fetchItemCap(listId, idToken)` calls `GET /shopping-lists/{id}/limits` and maps
`204` to null. `ShoppingListDetailService` exposes `ValueListenable<AsyncValue<LimitCap?>> itemCap`, loaded
once inside `openShoppingList` and disposed alongside the others. The count is
`items.value.valueOrNull?.length` — the **flat** length, since checked items count against the cap.

`ShoppingListItemAddWidget` gains `bool enabled = true`, applied to its `TextField`.
`shopping_list_detail_screen.dart` renders the counter beside the add row, passes `enabled: !_atItemCap()`,
and returns early from `_createEphemeralItemAfter` when `_atItemCap()` — that one guard covers both the
Enter-on-an-item entry and the chain continuation, because both route through it. A row already open when a
poll pushes the list to the cap stays open; committing it is refused and discarded by T4's existing path
with its "This list is full" toast.

- Files: `shopping_list_repository.dart`, `shopping_list_detail_service.dart`,
  `shopping_list_item_add_widget.dart`, `shopping_list_detail_screen.dart`
- Verify: `dart analyze` clean, `flutter test` green; manually, with the item cap lowered to 3, add three
  items and confirm the add field disables, the counter reads `3 / 3 items`, Enter on an existing item
  opens no new row, and adding to a second list still works.

### 11. Mobile test fallout

`MockLimitsRepository` in `test/support/mocks.dart`; `setupLimits(limitsRepository: …)` joins the widget
test's `setUp` after `setupAuth`, with `fetchCaps` stubbed to `{}` so nothing reaches the network, and
`MainScreen` gains `limitsService: GetIt.I<LimitsService>()`.

- Files: `mobile/test/support/mocks.dart`, `mobile/test/features/recipe/main_screen_recipes_tab_widget_test.dart`
- Verify: `cd mobile && flutter test` — all four test files green.

## Test plan

**Unit tests**

_N/A — the project has no backend unit-test layer for services (`LimitPeriodTest` is the sole pure unit test
and its behaviour is unchanged), and the mobile logic added here is notifier plumbing exercised through the
widget test._

**Integration tests**

`LimitsIntegrationTest` (existing suite, limits-on, facade-level)
- `standing` returns empty when no usage row exists for the subject and resource
- `standing` returns the stored `used` and `periodStart` on a live window
- `standing` reports zero used and a null `periodStart` on a lapsed periodic FLOW window
- `standing` leaves the stored row untouched after reporting a lapsed window as zero (re-read via SQL)
- `standing` returns a `resetsInSeconds` inside the remaining window for a live FLOW+`DAY` config
- `standing` returns a null `resetsInSeconds` for a STOCK cap and for a FLOW cap with no period
- `standing` succeeds when the subject has a usage row but no configuration at all
- `shouldNotRestartElapsedPeriodWhenMaxIsZero` — refused reserve leaves the row at 2 while the standing reads zero
- `caps` returns one row per configured resource, the subject's override beating the default
- `caps` returns an empty list when `recipai.limits.enabled=false`
- `cap` returns the resolved cap for one resource, and empty when limits are disabled

`LimitsApiIntegrationTest` (new, RANDOM_PORT)
- `GET /limits` returns 200 and the caps resolved for the caller's email
- `GET /limits` reflects a subject override rather than the default for that resource
- `GET /limits` returns 401 without a bearer token
- `GET /limits` returns 200 with an empty array when limits are disabled (nested class, flag off)

`RecipeIntegrationTest.LimitsEnforced`
- `GET /recipes/usage` returns `used: 0` for a subject that has created nothing
- `GET /recipes/usage` returns `used: 2` after two creates and `used: 1` after one delete
- `GET /recipes/usage` matches the `used` carried on the 429 body when the cap is hit
- `GET /recipes/usage` resolves `/usage` rather than `GET /recipes/{id}` (no 400/404)
- `GET /recipes/usage` returns 401 without a token

`RecipesCollectionIntegrationTest.LimitsEnforced` / `MealPlanIntegrationTest.LimitsEnforced`
- `GET /collections/usage` (resp. `/meal-plans/usage`) returns `used: 0` before any create, and tracks create then delete

`ShoppingListIntegrationTest.LimitsEnforced`
- `GET /shopping-lists/usage` returns `used: 0` before any create, and tracks create then delete
- `GET /shopping-lists/{id}/limits` returns the item cap configured against the list's **owner**, read by the owner
- `GET /shopping-lists/{id}/limits` returns that same owner-configured cap when read by a shared **editor**,
  not the editor's own override
- `GET /shopping-lists/{id}/limits` returns 403 for a user with no permission on the list
- `GET /shopping-lists/{id}/limits` returns 404 for an unknown list id
- `GET /shopping-lists/{id}/limits` returns 204 when limits are disabled (outer suite, flag off)

`ExtractionIntegrationTest`
- `GET /extract/usage` returns `used: 0` before any extraction and `used: 1` after one
- `GET /extract/usage` returns a null `resetsInSeconds` under the seeded FLOW-with-no-period default
- `GET /extract/usage` returns the exhausted standing after the budget is spent

**Flutter widget/integration tests**

- `main_screen_recipes_tab_widget_test.dart` — the three existing cases still pass with `setupLimits` and a
  stubbed `fetchCaps` in the `setUp` chain (regression only; the design adds no new widget test, and T4's
  precedent of dropping the detail-screen widget test stands).

**Manual verification**

- The full `tasks.md` > T5 "How to verify" walk-through against a `RECIPAI_LIMITS_ENABLED=true` backend,
  including raising a limit with SQL: reopening the screen still shows the old cap, and the new one
  shows after an app restart, because the client loads caps once per session.
- Each of the six gated surfaces at the cap: counter text correct, action visibly disabled, and no layout
  overflow on a narrow device.
- The kill-switch path: with limits off, every counter is absent and every action enabled.
- The fail-open path: kill the backend after login, open a create surface, and confirm the action stays
  enabled rather than locking the user out.
- The item chain: Enter-to-insert at the cap opens no further row, and the keyboard/focus behaviour of the
  disabled add field is acceptable (design assumption).

## Verification checklist

- [ ] `cd backend && ./mvnw -q test` — full suite green, including all six migrated test files
- [ ] `cd mobile && dart analyze` — no new issues; `dart format --output=none --set-exit-if-changed lib test`
- [ ] `cd mobile && flutter test` — green
- [ ] `LimitsModuleArchitectureTest` passes with the controller in place (both rules)
- [ ] `grep -rn "currentUsage\|LimitUsageDetails" backend/src` returns nothing
- [ ] `/limits` reachable authenticated and refused (401) unauthenticated; not 403, which would mean the
      security matcher was missed
- [ ] `GET /recipes/usage` and `GET /shopping-lists/usage` resolve ahead of their `GET /{id}` siblings
- [ ] `tasks.md` > T5 "How to verify" succeeds end-to-end — with the divergence noted in Risks below
      acknowledged rather than silently skipped
- [ ] `plans/T5-task-design.md` > Assumptions to verify are each confirmed or explicitly deferred
- [ ] No new compiler warnings; `INFO` logs clean on the happy path (the new reads log at `debug`)
- [ ] Every surface still acts when its cap or count is missing (fail-open spot-checked, not assumed)

## Risks surfaced during planning

- **Risk:** `LimitStanding` carries `periodStart`, but the design's HTTP contract shows the usage body as
  `{"used": n, "resetsInSeconds": …}`. Returning the record directly serialises `periodStart` too.
  **Why it matters:** the wire shape is wider than the documented contract, and `periodStart` is internal
  bookkeeping the client has no use for.
  **Mitigation:** return the record as-is — the design explicitly rejects per-module DTOs, the extra field is
  inert, and the Dart `LimitUsage.fromJson` reads only the two keys it needs. Note the third field when the
  `api.md` files are written so the docs match what ships.

- **Risk:** `tasks.md` > T5 and the T5 task design describe different user-visible outcomes. `tasks.md`
  promises a *refusal message naming the resource and the standing*; the design's *Decisions made* settles on
  **no 429 parsing**, so a blocked action is a greyed control with a counter, and any refusal that still gets
  through shows the pre-existing generic error.
  **Why it matters:** "How to verify" in `tasks.md` asks for a message on the fourth recipe attempt, which the
  design deliberately makes unreachable. Verifying literally against `tasks.md` will read as a failure.
  **Mitigation:** verify against the design's outcome (disabled control + counter) and record the divergence on
  the PR; the design already accepts it in writing. Flagged to the user — not silently reconciled.

- **Risk:** `routes.dart`, `main_screen.dart` and three `*_setup.dart` files are DI ripple the design's
  component list does not name. Screens receive services through route-builder closures, so five screens plus
  `MainScreen` need a `limitsService` parameter, and three services acquire their first-ever `dispose()`.
  **Why it matters:** the design's assumption *"adding `setupLimits()` to `main()` and to the one widget test
  is the whole DI fallout"* is too narrow; missing the `dispose:` registrations leaks notifiers on `GetIt.reset()`.
  **Mitigation:** captured in the file inventory and steps 5–9; nothing further needed, but do not treat the
  design's file list as complete.

- **Risk:** `standing` now resolves configuration where `currentUsage` did not, and `SHOPPING_LIST_ITEM`'s
  usage subject (a list UUID) is *not* its configuration subject (the owner's email).
  **Why it matters:** a `standing(listId, SHOPPING_LIST_ITEM)` call resolves the **default** config row, not the
  owner's override — 14 migrated call sites in `ShoppingListIntegrationTest` do exactly this.
  **Mitigation:** verified safe as the code stands — the seeded default is `STOCK` with no period, so the lapse
  and reset branches are inert and behaviour is byte-identical to today. It stops being safe if anyone ever
  configures a periodic FLOW default for `SHOPPING_LIST_ITEM`; `standing` is not the right entry point for that
  resource and no production code calls it that way.

- **Risk:** the design says both extraction screens disable "the extract FAB", but
  `image_extraction_screen.dart` has no FAB — the action is an inline `ElevatedButton` that only renders once
  an image is picked.
  **Why it matters:** a literal reading leaves the image screen ungated.
  **Mitigation:** gate the `ElevatedButton` and render the counter unconditionally (not only when an image is
  selected), so an exhausted budget is visible before the user picks a photo.

- **Risk:** every displayed number is read from `limit_usage`, so a missed release from T2 or T4 now surfaces
  as an inflated counter that disables a control the server would have accepted.
  **Why it matters:** the user has no in-app way to clear it, and pre-emptive blocking turns a cosmetic drift
  into a hard block.
  **Mitigation:** the repair is re-running `R__recompute_limit_usage.sql`. Watch for it during manual
  verification specifically — this display is the first thing that would reveal such a bug. Fail-open covers the
  missing-data direction but not the overstated one.

- **Risk:** `docs/backend/standards/integration-tests.md` (`:42-53`) carries a worked example built on
  `LimitsFacade.currentUsage` / `LimitUsageDetails`, both of which this task deletes.
  **Why it matters:** the standard becomes uncompilable guidance the moment step 1 merges, and standards are
  read as authoritative.
  **Mitigation:** out of scope for this plan by the skill's docs rule, but it must not be missed — call it out
  explicitly to the `docs-updating` step rather than leaving it to a general sweep.
