# T4: Invitee-facing mobile surface — Implementation Plan

**Date:** 2026-08-29

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/mobile/standards/architecture.md` — the Repository→Service→View layering, and **Widget Inputs**:
  `InvitesScreen` drives the service so it takes it; `InviteListItem` only renders, so it takes an
  `Invite`, a `bool` and two callbacks.
- `docs/mobile/standards/state-management.md` — `ValueNotifier<AsyncValue<T>>` exposed read-only as
  `ValueListenable`, `AsyncValue.guardAsync()`, the `_isXxxRunning` guard, and the `dispose()`
  requirement on any service owning a notifier.
- `docs/mobile/standards/dependency-injection.md` — the `setup<Feature>()` shape, named required
  constructor params (never `getIt<>()` in a class body), and the **screen-scoped** rule this task
  uses: `lazySingleton` + `resetLazySingleton` in the owning screen's `dispose()`.
- `docs/mobile/standards/navigation.md` — the `AppRoute` enum, `context.goNamed`, and services
  injected in the `routes.dart` builder closure.
- `docs/mobile/standards/widget-testing.md` — repository-only mocking, the `SharedPreferences` →
  `GetIt.I.reset()` → `PreferencesService` → `setup*()` ordering in `setUp`, `test/support/mocks.dart`
  holding type declarations only, and the single-route `GoRouter` + `NavigatorObserver` pattern.
- `docs/mobile/standards/theming.md` — `Theme.of(context)` first, then `AppSpacing`; relevant to the
  `Badge` colour question and the row's spacing.
- `docs/mobile/standards/logging.md` — `recipai.<feature>.<layer>` logger naming, and the rule that a
  previously-silent background failure gets a `WARNING`.
- `docs/backend/modules/permissions/api.md` — `GET /invites` (200, the six response fields, no
  `resourceId`, no count endpoint) and `POST /invites/{id}/accept|decline` (204, 404 when gone).
- `docs/project/local-development.md` — `./recipai.sh start-backend` and the `dev`-profile bearer
  rule (`Bearer bob` → `bob@local.test`), for the manual end-to-end.

**Design & ADRs**

- `plans/T4-task-design.md` — the whole document; in particular **Interfaces and method signatures**,
  **Data flow** (the type → list-service fan-out table), **Pseudo-code**, and **Decisions made**.
  Every decision there is settled — do not re-open them.
- `research/t4-invitee-surface-placement.md` — the placement analysis and the row design. Note it
  *recommends* a badged app-bar `IconButton`; the task design overrode that with the dot-on-overflow
  + counted-menu-row shape. **The task design wins.**
- `docs/ADRs/0008-invite-label-snapshot.md` — the row renders the stored `label` as-is and never
  fetches the resource; there is nothing to fetch it with.
- `tasks.md` > T4 — scope, out of scope, and "How to verify".

**Code to mirror**

- `mobile/lib/features/limits/limits_repository.dart` — the exact repository shape:
  `final http.Client _client`, `_baseUrl = AppConfig.apiBaseUrl`, `_getAuthHeaders(String? idToken)`,
  status-code check then `throw Exception('Failed to …: ${response.statusCode}')`.
- `mobile/lib/features/limits/limits_setup.dart` — the setup-function shape (nullable repository
  parameter, `registerSingleton` for the repository, dispose callback on the service).
- `mobile/lib/features/recipe/collection/recipes_collection_list_service.dart` — the notifier +
  read-only getter + `_isLoadXRunning` guard + `guardAsync` + `dispose()` shape to copy line for line.
- `mobile/lib/features/recipe/collection/recipes_collection_list_screen.dart` — the full-screen list
  screen: `Scaffold` + own `AppBar` painted `inversePrimary`, `RefreshIndicator` over a
  `ValueListenableBuilder`, `AsyncValue.when` branches, `LoadingWidget` / `ApiErrorWidget`, the
  `showDialog<bool>` delete confirmation (with `context.pop(false/true)` and an `error`-coloured
  destructive `TextButton`), and the capture-messenger-then-`if (mounted)` SnackBar idiom.
- `mobile/lib/features/recipe/collection/recipes_collection_list_item.dart` — the row shape:
  `Card(margin: AppSpacing.cardMargin)` wrapping a `ListTile` with
  `contentPadding: AppSpacing.listTilePadding`.
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` (tail) — exceptions declared
  at the bottom of the repository file, each with a doc comment saying what throws it.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — `with WidgetsBindingObserver`,
  `didChangeAppLifecycleState`, and `removeObserver(this)` in `dispose()`. This task needs only the
  `resumed` branch.
- `mobile/lib/shared/user_role.dart` — the `fromApiString` / `displayName` enum shape, including
  `throw ArgumentError('Unknown …: $apiString')` on an unrecognised key.
- `mobile/lib/features/recipe/collection/recipes_collection.dart` — the minimal model + `fromJson`
  shape.
- `mobile/test/features/recipe/main_screen_recipes_tab_widget_test.dart` — the complete widget-test
  harness (mock construction, stubs, six `setup*()` calls, single-route `GoRouter`, `_NavPushSpy`)
  the new invites-screen test copies.
- `backend/src/main/java/xyz/stasiak/recipai/permissions/dto/PendingInviteDto.java` — the wire shape
  the model parses (`id`, `resourceType` as a plain `String`, `label`, `invitedBy`, `role`,
  `createdAt`).

## File inventory

**Mobile — new feature**

- **CREATE** `mobile/lib/features/invites/invite_resource_type.dart` — enum over the four resource keys, `fromApiString`, `displayName`.
- **CREATE** `mobile/lib/features/invites/invite.dart` — `Invite` model (`id`, `resourceType`, `label`, `invitedBy`) with `fromJson`.
- **CREATE** `mobile/lib/features/invites/invites_repository.dart` — `GET /invites`, accept, decline; declares `InviteGoneException`.
- **CREATE** `mobile/lib/features/invites/invites_service.dart` — the invites notifier, load guard, resume observer, accept/decline fan-out.
- **CREATE** `mobile/lib/features/invites/invites_screen.dart` — the `/invites` screen: refresh, branches, confirmation, busy set, snackbars.
- **CREATE** `mobile/lib/features/invites/invite_list_item.dart` — one row: icon, label, type, sender, Decline/Accept.
- **CREATE** `mobile/lib/features/invites/invites_setup.dart` — `setupInvites({InvitesRepository? invitesRepository})`.

**Mobile — app shell**

- **MODIFY** `mobile/lib/core/main_screen.dart` — required `InvitesService`; load in `didChangeDependencies`, reset in `dispose`; badged overflow button and permanent counted `Invites` menu row.
- **MODIFY** `mobile/lib/core/routes.dart` — `AppRoute.invites`, its nested `GoRoute`, and `invitesService:` on the `MainScreen` builder.
- **MODIFY** `mobile/lib/main.dart` — `setupInvites()` in the DI block, after `setupShoppingList()`.

**Mobile — tests**

- **MODIFY** `mobile/test/support/mocks.dart` — `MockInvitesRepository`.
- **MODIFY** `mobile/test/features/recipe/main_screen_recipes_tab_widget_test.dart` — mock + `fetchInvites` stub + `setupInvites(...)` + `invitesService:` on the `MainScreen` builder.
- **CREATE** `mobile/test/features/invites/invites_screen_widget_test.dart` — the new screen's widget test.

**Docs** (T4 owns these per `tasks.md` > Cross-task notes)

- **CREATE** `docs/mobile/modules/invites/codebase_structure.md` — file tree for `features/invites/`.
- **CREATE** `docs/mobile/modules/invites/ui.md` — the screen, the row, the indicator, the load triggers, the accept fan-out.
- **MODIFY** `docs/INDEX.md` — an `#### Invites (mobile/modules/invites/)` entry under Mobile Module Documentation.
- **MODIFY** `docs/mobile/modules/core/ui.md` — the overflow menu's `Invites` item and dot badge, and `/invites` in the Main App Routes list.
- **MODIFY** `docs/mobile/modules/core/codebase_structure.md` — `test/features/invites/` in the test tree.
- **MODIFY** `docs/project/architecture.md` — an `invites` bullet in the mobile feature list.

## Step-by-step plan

### 1. Models and repository

`InviteResourceType` copies `UserRole`'s switch-based `fromApiString` (throwing
`ArgumentError` on anything else) and `displayName` (`'Recipe'`, `'Collection'`, `'Shopping list'`,
`'Meal plan'`). It carries **no icon** — that belongs to `invite_list_item.dart`.

`Invite.fromJson` reads `id`, `resourceType` (through `fromApiString`), `label` and `invitedBy`;
`role` and `createdAt` are present on the wire and deliberately not carried. No `toJson` — nothing
serialises an invite.

`InvitesRepository` mirrors `LimitsRepository` exactly (own `http.Client`, `_baseUrl`,
`_getAuthHeaders`). `fetchInvites` decodes a 200 body as a `List<dynamic>` and maps it; anything else
throws `Exception('Failed to load invites: ${response.statusCode}')`. `acceptInvite` and
`declineInvite` `POST` with the auth headers and no body: `204` returns, `404` throws
`InviteGoneException(inviteId)`, anything else throws a plain `Exception`. `InviteGoneException` goes
at the bottom of the file with a doc comment, as `shopping_list_item_repository.dart` does.

- Files: `mobile/lib/features/invites/invite_resource_type.dart`,
  `mobile/lib/features/invites/invite.dart`, `mobile/lib/features/invites/invites_repository.dart`
- Verify: `cd mobile && flutter analyze` reports no issues; `dart format --output=none --set-exit-if-changed lib` passes.

### 2. Service and DI registration

`InvitesService with WidgetsBindingObserver` takes the six dependencies from the design's signature.
Its constructor body calls `WidgetsBinding.instance.addObserver(this)` (the service is built lazily
during `MainScreen`'s build, so there is no `start()` to hang it off as `ShoppingListSyncService`
has). It owns `ValueNotifier<AsyncValue<List<Invite>>> _invites`, seeded
`const AsyncValue.loading()`, exposed as `ValueListenable<AsyncValue<List<Invite>>> get invites`.

`loadInvites()` follows the design's pseudo-code, with two points to get right:

- It pre-sets `_invites.value = const AsyncValue.loading()` **only when there is no data to keep**:

  ```dart
  // A reload keeps the rows and the badge count on screen; the badge reads a
  // loading value as zero, so blanking it here blinks the dot off on every
  // app resume. An error holds nothing worth keeping, and Retry needs the
  // spinner as its feedback.
  if (_invites.value is AsyncError) {
    _invites.value = const AsyncValue.loading();
  }
  ```

  This is a deliberate deviation from the four list services, all of which pre-set it
  unconditionally (`recipe_list_service.dart:76`, `shopping_list_list_service.dart:48`, and the
  collection and meal-plan equivalents). Invites is the only `AsyncValue` list that reloads on
  `AppLifecycleState.resumed` and the only one feeding persistent app-bar chrome, which is what makes
  the unconditional form wrong here and right there. Keep the comment — it is the whole reason a
  reader will not "fix" the conditional away.
- The guard uses `try/finally` around the whole body so a throw inside `guardAsync`'s own machinery
  cannot wedge `_isLoadInvitesRunning` at `true`.

Add `static final _log = Logger('recipai.invites.service')` and log a `WARNING` when the load lands
on `AsyncError` — a resume-triggered failure is otherwise a silent background failure with the badge
quietly reading zero, which `logging.md` asks to be logged. Do not log the token or the body.

`acceptInvite` / `declineInvite` follow the pseudo-code exactly: accept removes the row and rethrows
on `InviteGoneException` (nothing was granted, so no list reload and the screen has something to
say); decline removes the row and **swallows** `InviteGoneException` (gone is what was asked for).
Both remove by rebuilding the list off `_invites.value.valueOrNull` — a removal against a `loading`
or `error` value is a no-op, which the conditional above keeps unreachable in practice: the notifier
only holds `loading` during the cold start or an error retry, and neither has rows to answer.

`_reloadListsFor` is an exhaustive `switch` over `InviteResourceType` per the design's table, so a
fifth type later fails to compile rather than silently refreshing nothing. `MealPlanCalendarService`
is not called: `MealPlanListService.loadMealPlans()` calls
`MealPlanVisibilityService.ensurePlanVisibility` for every plan, whose notifier write and whose own
`mealPlans` write each drive `MealPlanCalendarService.loadCalendar()` (verified in
`meal_plan_calendar_service.dart:27-28` and `meal_plan_list_service.dart:58`).

`didChangeAppLifecycleState` reloads on `AppLifecycleState.resumed` only. `dispose()` removes the
observer and disposes the notifier.

`setupInvites` mirrors `setupLimits`, except the service is a **`registerLazySingleton`** with
`dispose: (service) => service.dispose()` — it must be resettable with the session (see the design's
scoping decision). The repository stays a plain `registerSingleton`. `setupInvites()` goes in
`main()` immediately after `setupShoppingList()`, so all four list services are registered first.

- Files: `mobile/lib/features/invites/invites_service.dart`,
  `mobile/lib/features/invites/invites_setup.dart`, `mobile/lib/main.dart`
- Verify: `cd mobile && flutter analyze`; `cd mobile && flutter test` — the existing suite still passes (nothing resolves `InvitesService` yet).

### 3. Screen, row, and route

`InviteListItem` is a `Card(margin: AppSpacing.cardMargin)` wrapping an `Opacity(opacity: busy ? 0.5 : 1)`
over a `Column(mainAxisSize: MainAxisSize.min)` of a `ListTile`
(`contentPadding: AppSpacing.listTilePadding`, `leading:` the per-type icon, `title:` `invite.label`
in `titleMedium`, `subtitle:` `'${invite.resourceType.displayName} · Shared by ${invite.invitedBy}'`)
and a right-aligned `Padding` + `Row` of `TextButton('Decline')` and `FilledButton('Accept')`, both
with `onPressed: busy ? null : callback`. The icon comes from a private `switch` expression on
`invite.resourceType`: `Icons.restaurant_menu`, `Icons.folder`, `Icons.shopping_cart`,
`Icons.calendar_today`. `FilledButton` is already used in `plan_form_dialog.dart` and
`meal_entry_form_dialog.dart`.

`InvitesScreen` is a `StatefulWidget` holding `final Set<String> _busyIds = {}`. `initState` calls
`widget.invitesService.loadInvites()` directly (the first notifier write is after an `await`, so it
cannot land during build). `build` returns a `Scaffold` with its own `AppBar` titled `Invites`
painted `theme.colorScheme.inversePrimary`, and a `RefreshIndicator(onRefresh: widget.invitesService.loadInvites)`
over a `ValueListenableBuilder` on `invites`, branching per the design's pseudo-code.

The empty and error branches go through a private
`Widget _scrollable(Widget child)` that returns a `LayoutBuilder` wrapping
`ListView(physics: const AlwaysScrollableScrollPhysics(), children: [SizedBox(height: constraints.maxHeight, child: child)])`
— the `SizedBox` is what keeps the centred text centred, and the physics is what lets
`RefreshIndicator` fire with nothing in the list. The `loading` branch stays a bare `LoadingWidget()`;
there is nothing to pull while it is loading.

`_handleDecline` shows the `showDialog<bool>` confirmation first (title `Decline invite`, body naming
`invite.label` and `invite.invitedBy`, `Cancel` / an `error`-coloured `Decline`, both popping through
`context.pop(false/true)`), returning early unless it resolves `true`. Both handlers funnel into one
private `_answer(invite, call, successMessage)` implementing the design's pseudo-code: capture
`ScaffoldMessenger.of(context)` before the first `await`, `setState` the id into `_busyIds`, run the
call, snack the success message, catch `InviteGoneException` →
`'That invite is no longer available'`, catch anything else → `'Failed: $e'`, and in `finally`
`if (mounted) setState(() => _busyIds.remove(invite.id))`. The route is never popped.

In `routes.dart`, add `invites('invites'), // '/invites'` to the `AppRoute` enum next to
`recipesCollections`, and its `GoRoute` inside `AppRoute.main`'s `routes:` list — builder
`InvitesScreen(invitesService: getIt<InvitesService>())`.

- Files: `mobile/lib/features/invites/invite_list_item.dart`,
  `mobile/lib/features/invites/invites_screen.dart`, `mobile/lib/core/routes.dart`
- Verify: `cd mobile && flutter analyze`; `cd mobile && flutter test` still green (the route exists but nothing navigates to it yet).

### 4. App-shell wiring and the existing test's catch-up

`MainScreen` gains `final InvitesService invitesService` as a required constructor param;
`didChangeDependencies` calls `widget.invitesService.loadInvites()` alongside the four existing
loads; `dispose` gains the guarded `getIt.resetLazySingleton<InvitesService>()` beside the others.

In `build`'s `actions:`, wrap the `PopupMenuButton` in a `ValueListenableBuilder` over
`widget.invitesService.invites`, deriving `final count = value.valueOrDefault(const <Invite>[]).length`.
Then:

- `icon: Badge(isLabelVisible: count > 0, child: const Icon(Icons.more_vert))` — the dot. Passing
  `icon:` replaces `PopupMenuButton`'s default `more_vert`, so the icon must be supplied explicitly.
- A new first `PopupMenuItem<String>(value: 'invites', …)` whose child is a `Row` of
  `Icon(Icons.mail_outline)`, `SizedBox(width: AppSpacing.small)`, `Text('Invites')`, and — only when
  `count > 0` — a `Spacer()` and `Badge.count(count: count)`. The row is present regardless of count.
- `onSelected` gains `else if (value == 'invites') { context.goNamed(AppRoute.invites.name); }`.
- `itemBuilder` loses its `const` (it now closes over `count`); the four existing
  `PopupMenuItem`s stay individually `const`.

Then wire `invitesService: getIt<InvitesService>()` into the `MainScreen` builder in `routes.dart`.

The existing widget test breaks at compile time here, so it is fixed in the same commit: add
`MockInvitesRepository` to `test/support/mocks.dart`, declare and construct it in the test, stub
`when(() => invitesRepository.fetchInvites(any())).thenAnswer((_) async => <Invite>[])`, call
`setupInvites(invitesRepository: invitesRepository)` **last** among the `setup*()` calls (it resolves
all four list services), and pass `invitesService: GetIt.I<InvitesService>()` to `MainScreen`.

- Files: `mobile/lib/core/main_screen.dart`, `mobile/lib/core/routes.dart`,
  `mobile/test/support/mocks.dart`,
  `mobile/test/features/recipe/main_screen_recipes_tab_widget_test.dart`
- Verify: `cd mobile && flutter test` — all three existing `main_screen_recipes_tab_widget_test.dart` cases pass and no test file fails to compile.

### 5. Invites screen widget test

New `mobile/test/features/invites/` directory with `invites_screen_widget_test.dart`. Because of the
repository-only mocking rule, its `setUp` is the full harness copied from
`main_screen_recipes_tab_widget_test.dart` — every repository mocked, `PreferencesService` registered
before any `setup*()`, all six existing setups plus `setupInvites` in `main()` order — and the
router has two routes: `/invites` building the real `InvitesScreen(invitesService: GetIt.I<InvitesService>())`,
and a stub `AppRoute.main` so a stray navigation has somewhere to go. Cases are listed under
**Test plan** below.

Two stubs are easy to forget and both let a real HTTP call escape: `fetchQuotas` (per
`widget-testing.md`) and whichever list repository the accept fan-out reaches — stub
`fetchShoppingLists` (or the type used in the accept case) so the post-accept reload resolves.

- Files: `mobile/test/features/invites/invites_screen_widget_test.dart`
- Verify: `cd mobile && flutter test test/features/invites/invites_screen_widget_test.dart` passes; then `cd mobile && flutter test` for the whole suite.

### 6. Manual end-to-end

Start the backend (`./recipai.sh start-backend`), then from another shell seed one invite of each
type to `bob@local.test` as `alice` (four `POST /<resource>/{id}/share` calls with
`{"email":"bob@local.test","role":"EDITOR"}`; create the four resources first if the dev DB is
empty). Run the app as `bob`:
`cd mobile && flutter run --dart-define=DEV_AUTH_ENABLED=true` (the default base URL is already the
emulator's `http://10.0.2.2:8080`), signing in with the name `bob`.

Walk `tasks.md` > T4 > "How to verify" — see the checklist below.

- Files: none
- Verify: the walk-through in the Verification checklist completes.

### 7. Docs

Write `docs/mobile/modules/invites/codebase_structure.md` (the seven-file tree, one line each, in the
style of `docs/mobile/modules/limits/codebase_structure.md`) and `docs/mobile/modules/invites/ui.md`
(the `/invites` screen and its four states, the row, the decline confirmation, the two indicator
surfaces in the app shell, the four load triggers, and the accept fan-out table). Add the `Invites`
entry to `docs/INDEX.md` under Mobile Module Documentation, the `invites` bullet to
`docs/project/architecture.md`'s mobile feature list, and update
`docs/mobile/modules/core/{ui.md,codebase_structure.md}` for the menu item, the badge, the `/invites`
route and the new test directory.

Present tense, stating current behaviour only — no `docs/tasks/` references, no "was X before".

- Files: `docs/mobile/modules/invites/{codebase_structure.md,ui.md}`, `docs/INDEX.md`,
  `docs/project/architecture.md`, `docs/mobile/modules/core/{ui.md,codebase_structure.md}`
- Verify: `grep -rn "docs/tasks" docs/mobile/modules/` is empty; every file named in `docs/mobile/modules/invites/codebase_structure.md` exists on disk.

## Test plan

**Unit tests**

_N/A — this app has no service-level unit tests; behaviour is covered by widget tests running real
services over mocked repositories (`docs/mobile/standards/widget-testing.md`). `shopping_list_sync_service_test.dart`
is the one exception and exists only because its concurrency cannot be driven through a screen._

**Flutter widget tests**

`test/features/invites/invites_screen_widget_test.dart` (new):

- renders one `InviteListItem` per pending invite, showing the label, the type's display name and `Shared by {invitedBy}`
- renders a row for each of the four resource types with its own icon
- fetches invites when the screen opens (`verify(() => invitesRepository.fetchInvites(any())).called(1)` after the first settle)
- renders `No pending invites` when the fetch returns an empty list
- renders `ApiErrorWidget` when the fetch throws, and re-fetches when `Retry` is tapped
- fires `fetchInvites` again on a pull-to-refresh over the **empty** state (`tester.fling` on the `ListView`, then `pumpAndSettle`)
- keeps the existing rows on screen while a reload is in flight — hold the second `fetchInvites` open with a `Completer`, pull to refresh, and assert the rows are still rendered and no `LoadingWidget` is present (this is the conditional pre-set of `AsyncValue.loading()` from step 2)
- shows `LoadingWidget` when `Retry` is tapped from the error state — same `Completer` trick, asserting the spinner does appear when there was no data to keep
- accept calls `acceptInvite` with that invite's id and removes the row
- accept reloads the matching list service (stub `fetchShoppingLists`, accept a `SHOPPING_LIST` invite, `verify` it was called after the accept)
- accept surfaces `That invite is no longer available` and still drops the row when the repository throws `InviteGoneException`
- decline shows the confirmation dialog and does **not** call `declineInvite` when it is cancelled
- decline calls `declineInvite` and drops the row when it is confirmed
- decline swallows `InviteGoneException` — the row goes and `Invite declined` is shown
- both buttons are disabled while a call is in flight (hold the repository future open with a `Completer`, tap `Accept`, assert `onPressed` is null on both, then complete)
- the empty state renders in place when the last invite is answered, and the screen is still on `/invites`

`test/features/recipe/main_screen_recipes_tab_widget_test.dart` (modified): the three existing cases
stand unchanged — the edit is `setUp`-only compile catch-up.

**Integration tests**

_N/A — backend integration coverage for `/invites` landed with T1; this task adds no backend code._

**Manual verification**

- The four rows render legibly on a real phone: label, type name and sender do not overflow, and the
  two buttons fit on the narrowest supported width.
- `Badge` (dot) on the `inversePrimary` app bar and `Badge.count` inside the `PopupMenuItem` are
  legible and correctly laid out against the item's fixed height — the two design assumptions that
  can only be settled by looking.
- Backgrounding the app, having `alice` share a fifth resource, and foregrounding it makes the dot
  appear without any other interaction (the resume trigger).
- Accepting each of the four types makes the resource appear in its tab and behave as a shared
  resource — including a meal plan showing up in the calendar, not just the plan drawer.

## Verification checklist

- [ ] `cd mobile && flutter analyze` reports no issues
- [ ] `cd mobile && dart format --output=none --set-exit-if-changed lib test` passes
- [ ] `cd mobile && flutter test` — whole suite green
- [ ] `tasks.md` > T4 "How to verify" succeeds end to end: with one invite of each of the four types
      pending, opening the app shows the dot; the menu row shows `4`; opening it lists all four with
      their labels and senders; accepting one makes that resource appear in its own tab and behave
      normally; declining another removes it; answering the last two clears the dot and leaves the
      `No pending invites` empty state in place
- [ ] Sign out and back in as a different dev user — the previous account's invites are gone and the
      dot reflects the new account (this is what the session-scoped `lazySingleton` buys)
- [ ] Every "Assumptions to verify" in `T4-task-design.md` is resolved, or explicitly deferred with a
      note (see Risks below — five of the seven are already settled by this plan's investigation)
- [ ] No new analyzer warnings, and no `TODO`s left in `lib/features/invites/`
- [ ] Logs contain no bearer token and no response body from the invites calls

## Risks surfaced during planning

- **Risk:** Four of the task design's seven assumptions are already resolved by this plan's reading of
  the code and are not open work.
  **Why it matters:** the implementer should not spend time re-verifying them.
  **Mitigation:** treat as settled — (1) T3 is merged on this branch (`35eb449`) and
  `InviteController` serves all three endpoints with `PendingInviteDto`'s six fields, resource-type
  keys `RECIPE` / `RECIPES_COLLECTION` / `SHOPPING_LIST` / `MEAL_PLAN`; (2) `loadMealPlans()` is
  sufficient for an accepted meal plan, since it calls `ensurePlanVisibility` per plan and both that
  notifier and `mealPlans` drive `MealPlanCalendarService.loadCalendar()`; (3)
  `main_screen_recipes_tab_widget_test.dart` is the only file that constructs `MainScreen`
  (`grep -rn "MainScreen(" mobile/` finds it and `routes.dart` only); (4) T4 needs no repository
  catch-up — the `/permissions` rename, the required `role` and recipes' 200→204 all sit on sharer
  endpoints assigned to T5, and this task touches none of them.

- **Risk:** The remaining three assumptions are visual or lifecycle facts that only running the app
  settles: the two `Badge` rendering questions, and `MainScreen` surviving beneath the nested
  `/invites` route.
  **Why it matters:** the `Badge` ones have cheap documented fallbacks (a `Theme` colour override; the
  count as a plain trailing `Text`), but the `MainScreen` one would overturn the service's scoping
  decision.
  **Mitigation:** check all three the first time the screen runs (step 6). The route is nested exactly
  as `/recipes-collections` is, which demonstrably keeps `MainScreen` alive today, so a surprise here
  is unlikely — but if `InvitesService` is disposed while the invites screen is open, stop and take
  the scoping question back to the design rather than patching around it.

- **Risk:** `research/t4-invitee-surface-placement.md` recommends an indicator and an accept-fan-out
  wiring that the task design deliberately overrode — a badged app-bar `IconButton` instead of the
  dot-on-overflow, and an `onAccepted(resourceType)` callback supplied at the route builder instead of
  handing `InvitesService` the four list services.
  **Why it matters:** the research doc is in Required reading, and an implementer reading it after the
  design could build the wrong surface or the wrong wiring.
  **Mitigation:** the task design wins on both; read the research for the row design and the analysis,
  not for the recommendation.

- **Risk:** The invites-screen widget test needs the *entire* app's DI graph stood up — six `setup*()`
  calls and every repository mocked — because `InvitesService` holds the four list services and
  `setupShoppingList` needs a store.
  **Why it matters:** it looks like far more scaffolding than a one-screen test should need, and the
  temptation is to mock `InvitesService` directly, which `widget-testing.md` forbids.
  **Mitigation:** copy `main_screen_recipes_tab_widget_test.dart`'s `setUp` verbatim and add
  `setupInvites` last. If a third screen test ends up repeating it, that is the 2–3-screen threshold
  `widget-testing.md` names for extracting a shared harness — raise it then, not in this task.

- **Risk:** `MainScreen.dispose()` resets `InvitesService` and the four list services it holds
  references to, in one pass.
  **Why it matters:** if `InvitesService` were reset *after* a list service and something re-resolved
  it in between, it would close over disposed notifiers.
  **Mitigation:** nothing re-resolves it during `dispose()`, so order is not load-bearing — but put the
  `InvitesService` reset **first** in the block anyway, so the service holding the references goes
  before the things it references.
