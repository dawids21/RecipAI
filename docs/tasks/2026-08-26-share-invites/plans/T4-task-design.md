# T4: Invitee-facing mobile surface — Task Design

**Date:** 2026-08-29

## Summary

A new `invites` feature directory holds a repository over the module's three invitee-facing endpoints,
a session-scoped `InvitesService` owning one `ValueNotifier<AsyncValue<List<Invite>>>`, and a
full-screen `/invites` screen. That single notifier feeds both surfaces: a dot `Badge` on the
`MainScreen` overflow-menu icon while anything is pending, and a permanent "Invites" row inside the
menu carrying a counted `Badge.count`. Accepting calls the matching list service so the resource
appears in its tab immediately; declining is confirmed first.

## Components and responsibilities

### New — `mobile/lib/features/invites/`

- **`invite_resource_type.dart`** (CREATE) — `InviteResourceType` enum over the four opaque resource
  keys the backend sends (`RECIPE`, `RECIPES_COLLECTION`, `SHOPPING_LIST`, `MEAL_PLAN`), with
  `fromApiString` and a `displayName` for the row's type label. Holds no icon — that is the view's
  concern.
- **`invite.dart`** (CREATE) — the `Invite` model: `id`, `resourceType`, `label`, `invitedBy`, plus
  `fromJson`. `label` is the snapshot from ADR-0008 and is rendered as-is; there is no `resourceId`
  to fetch anything with.
- **`invites_repository.dart`** (CREATE) — `http.Client` over `GET /invites`,
  `POST /invites/{id}/accept` and `POST /invites/{id}/decline`, following `LimitsRepository`'s shape
  (`_getAuthHeaders`, throw on non-success). Also declares `InviteGoneException` at the bottom of the
  file, as `shopping_list_item_repository.dart` does for its own exceptions.
- **`invites_service.dart`** (CREATE) — owns `ValueNotifier<AsyncValue<List<Invite>>>` exposed
  read-only, the load guard, the app-resume observer, and the post-accept fan-out to the four list
  services. Registered as a `lazySingleton` and reset with the session (see **Decisions made**).
- **`invites_screen.dart`** (CREATE) — the `/invites` `Scaffold`: its own `AppBar`, a
  `RefreshIndicator` over a `ListView.builder`, the loading / empty / error branches, the decline
  confirmation dialog, the per-row busy state, and all `SnackBar` feedback. Reloads on open, and
  keeps every branch scrollable so the pull gesture still works with nothing in the list. Mirrors
  `recipes_collection_list_screen.dart` apart from that scroll physics (see **Decisions made**).
- **`invite_list_item.dart`** (CREATE) — one row: type icon, `label`, `Shared by {invitedBy}`, and
  the `Decline` / `Accept` buttons. Renders and reports; it takes the `Invite`, a `busy` flag and two
  callbacks, never the service (mobile `architecture.md` > Widget Inputs).
- **`invites_setup.dart`** (CREATE) — `setupInvites({InvitesRepository? invitesRepository})`,
  registering the repository as a singleton and the service as a `lazySingleton` with a dispose
  callback.

### Modified — `mobile/lib/core/`

- **`main_screen.dart`** (MODIFY) — gains a required `InvitesService`. Its `didChangeDependencies`
  calls `loadInvites()` alongside the four existing loads; its `dispose` resets the service beside
  the others. The `PopupMenuButton` is wrapped in a `ValueListenableBuilder` over the invites
  notifier: the count drives a dot `Badge` on the `more_vert` icon and a `Badge.count` on the new,
  permanently present `Invites` menu item, whose `onSelected` branch navigates to `AppRoute.invites`.
  `itemBuilder` stops being `const`.
- **`routes.dart`** (MODIFY) — `AppRoute.invites('invites')` (so `/invites`, nested under `/` exactly
  as `recipesCollections` is), its `GoRoute` resolving `getIt<InvitesService>()` in the builder
  closure, and `InvitesService` passed into the `MainScreen` builder.
- **`main.dart`** (MODIFY) — `setupInvites()` in the DI block, after the four feature setups whose
  services `InvitesService` depends on.

### Modified — tests

- **`test/support/mocks.dart`** (MODIFY) — `MockInvitesRepository`.
- **`test/features/recipe/main_screen_recipes_tab_widget_test.dart`** (MODIFY) — breaks at compile
  time once `MainScreen` takes a required `InvitesService`: its `setUp` needs
  `setupInvites(invitesRepository: mockInvites)`, a `fetchInvites` stub returning `[]`, and
  `invitesService: GetIt.I<InvitesService>()` in the `MainScreen` builder.
- **`test/features/invites/invites_screen_widget_test.dart`** (CREATE) — the new screen under the
  repository-only mocking rule: rows render from a stubbed `fetchInvites`; accept calls
  `acceptInvite` and drops the row; decline shows the confirmation and only calls `declineInvite` on
  confirm; the empty state renders in place when the last invite is answered; opening the screen
  refetches; and a pull on the empty state fires `fetchInvites` again.

### Modified — docs

- **`docs/mobile/modules/invites/`** (CREATE) — `codebase_structure.md` and `ui.md` for the new
  feature, plus its entry in `docs/INDEX.md`.
- **`docs/mobile/modules/core/ui.md`** and **`codebase_structure.md`** (MODIFY) — the overflow menu's
  new item and badge, the `/invites` route, and the new `MainScreen` input.

## Interfaces and method signatures

```dart
// invite_resource_type.dart
enum InviteResourceType {
  recipe, recipesCollection, shoppingList, mealPlan;

  static InviteResourceType fromApiString(String apiString);  // throws ArgumentError, as UserRole does
  String get displayName;   // 'Recipe' | 'Collection' | 'Shopping list' | 'Meal plan'
}

// invite.dart
class Invite {
  final String id;
  final InviteResourceType resourceType;
  final String label;
  final String invitedBy;

  const Invite({required this.id, required this.resourceType,
                required this.label, required this.invitedBy});
  factory Invite.fromJson(Map<String, dynamic> json);
}

// invites_repository.dart
class InvitesRepository {
  Future<List<Invite>> fetchInvites(String? idToken);            // GET  /invites            → 200
  Future<void> acceptInvite(String inviteId, String? idToken);   // POST /invites/{id}/accept  → 204
  Future<void> declineInvite(String inviteId, String? idToken);  // POST /invites/{id}/decline → 204
}

class InviteGoneException implements Exception {                 // thrown on 404 from either call
  final String inviteId;
}

// invites_service.dart
class InvitesService with WidgetsBindingObserver {
  InvitesService({
    required InvitesRepository invitesRepository,
    required AuthService authService,
    required RecipeListService recipeListService,
    required RecipesCollectionListService recipesCollectionListService,
    required ShoppingListListService shoppingListListService,
    required MealPlanListService mealPlanListService,
  });

  ValueListenable<AsyncValue<List<Invite>>> get invites;

  Future<void> loadInvites();               // guarded by _isLoadInvitesRunning
  Future<void> acceptInvite(Invite invite); // rethrows; InviteGoneException means "already gone"
  Future<void> declineInvite(Invite invite);

  @override
  void didChangeAppLifecycleState(AppLifecycleState state);
  void dispose();                           // removeObserver + notifier dispose
}

// invites_screen.dart
class InvitesScreen extends StatefulWidget {
  final InvitesService invitesService;      // the screen drives it, so it takes the service
}

// invite_list_item.dart
class InviteListItem extends StatelessWidget {
  final Invite invite;
  final bool busy;
  final VoidCallback onAccept;
  final VoidCallback onDecline;
}
```

## Data flow

**Load and indicator.**

1. `MainScreen.didChangeDependencies` calls `invitesService.loadInvites()` once per session, beside
   the four existing list loads.
2. The service reads `authService.idToken`, calls `InvitesRepository.fetchInvites`, and publishes the
   result through `AsyncValue.guardAsync` into its notifier.
3. `MainScreen`'s `ValueListenableBuilder` derives `count = value.valueOrDefault(const []).length`.
   `count > 0` shows the dot `Badge` on the overflow icon; the same count fills the `Badge.count` on
   the permanent `Invites` menu row. A loading or failed load yields `0`, so neither surface ever
   renders a badge over unknown state.
4. The same load re-runs on `AppLifecycleState.resumed`, on every opening of the `/invites` screen
   (from its `initState`), and on the screen's pull-to-refresh.

**Accept.**

1. The row's `Accept` is tapped; the screen adds the invite id to its local `_busyIds`, so the row
   greys out and both buttons disable.
2. `InvitesService.acceptInvite` posts to `/invites/{id}/accept`.
3. On 204 the service removes the invite from its notifier — the badge count drops in the same frame
   — and calls the list service(s) for that resource type. Both surfaces update from the one write.
4. The screen clears the busy id and shows a confirming `SnackBar`. If the list is now empty, the
   empty state renders in place; the route is not popped.

**Decline.** Same, behind an `AlertDialog` confirmation, and with no list-service fan-out.

**Type → list service fan-out on accept:**

| `resourceType`       | reloaded                                                     |
|----------------------|--------------------------------------------------------------|
| `RECIPE`             | `RecipeListService.loadRecipes()`                            |
| `RECIPES_COLLECTION` | `RecipesCollectionListService.loadRecipesCollections()` **and** `RecipeListService.loadRecipes()` |
| `SHOPPING_LIST`      | `ShoppingListListService.loadShoppingLists()`                |
| `MEAL_PLAN`          | `MealPlanListService.loadMealPlans()`                        |

`MealPlanCalendarService` already listens to `MealPlanListService.mealPlans` and reloads the calendar
itself, so the calendar is not called directly.

## Pseudo-code

**Answering an invite** — the branch that matters is a 404, which means the invite is already gone
(cancelled by the sharer, or answered on another device). The desired end state has been reached
either way, so the row is dropped rather than an error raised — but on accept nothing was granted, so
no list is reloaded and the user is told.

```
on acceptInvite(invite):
    token = await authService.idToken
    try:
        await repository.acceptInvite(invite.id, token)     # 204
    except InviteGoneException:
        removeFromNotifier(invite.id)                       # already gone; badge drops
        rethrow                                             # screen says "no longer available"
    removeFromNotifier(invite.id)
    await reloadListsFor(invite.resourceType)               # table above
```

```
on declineInvite(invite):
    # identical, minus reloadListsFor; a 404 needs no message —
    # the invite is gone, which is exactly what was asked for
```

**Load guard.**

```
on loadInvites():
    if _isLoadInvitesRunning: return
    _isLoadInvitesRunning = true
    try:
        _invites.value = await guardAsync(() =>
            repository.fetchInvites(await authService.idToken))
    finally:
        _isLoadInvitesRunning = false
```

**The list body** — the empty and error branches are scrollable, so the pull gesture reaches the
`RefreshIndicator` in every state:

```
body: RefreshIndicator(onRefresh: service.loadInvites, child:
    invites.when(
        loading: () => LoadingWidget(),
        error:   (e) => scrollable(ApiErrorWidget(onRetry: service.loadInvites)),
        data:    (list) => list.isEmpty
            ? scrollable(centered('No pending invites'))
            : ListView.builder(... InviteListItem ...)))

# scrollable(child) = ListView(physics: AlwaysScrollableScrollPhysics, children: [child])
```

**Screen-side answering**, which owns the busy set and every message:

```
on tapDecline(invite):
    if not await confirmDialog(invite.label, invite.invitedBy): return
    answer(invite, () => service.declineInvite(invite), 'Invite declined')

on answer(invite, call, successMessage):
    setState: _busyIds.add(invite.id)
    try:
        await call()
        snack(successMessage)
    except InviteGoneException:
        snack('That invite is no longer available')
    except error:
        snack('Failed: $error')
    finally:
        if mounted: setState: _busyIds.remove(invite.id)
```

## Decisions made

- **Dot badge on the overflow icon, counted badge on a permanent menu row** — settled with the user
  over the alternatives in `research/t4-invitee-surface-placement.md`. The dot is the "something is
  waiting" signal; the number lives one layer in, on a row that is always present so invites stay
  reachable when nothing is pending. Answers the HLD's indicator-placement open question.
- **Full-screen `/invites` route, not a dialog** — matches `/recipes-collections`, the app's existing
  full-screen list reached from this same menu, and leaves room for the decline confirmation without
  nesting dialogs.
- **One fetch, one notifier, no polling** — `GET /invites` returns the whole list and there is no
  count endpoint, so the badge is `list.length` from the same state. Loaded on
  `MainScreen.didChangeDependencies`, on `AppLifecycleState.resumed`, on opening the invites screen,
  and on pull-to-refresh. Answers the HLD's refresh-trigger open question; a `Scheduler` poll stays
  additive if it is ever wanted.
- **Opening the screen always reloads** — it is the one moment the user is looking straight at the
  list, and the state behind the badge may be minutes old, or already answered on another device.
  The load guard makes an overlapping open-and-resume load a no-op rather than a double fetch.
- **Every branch of the list scrolls, so pull-to-refresh works with nothing to pull** —
  `RefreshIndicator` only fires over a scrollable that accepts the gesture, so the empty and error
  branches are a `ListView` with `AlwaysScrollableScrollPhysics` (or `SliverFillRemaining` inside a
  `CustomScrollView`), not the bare `Center` that `recipes_collection_list_screen.dart` puts there.
  An empty list is exactly when the user pulls — it is how they check whether an invite has arrived
  yet.
- **`InvitesService` is a session-scoped `lazySingleton`, reset in `MainScreen.dispose()`** — it
  holds the four list services directly, and those are `lazySingleton`s that `MainScreen` resets, so
  an app-lifetime singleton would hold dead references after a logout→login cycle and silently fail
  to refresh the tab. This is the DI standard's own screen-scoped pattern, and it makes the sign-out
  clear structural: the instance is discarded rather than emptied by a listener.
- **The load trigger is `MainScreen.didChangeDependencies`, not an `AuthService` listener** — a
  consequence of the line above: the service is constructed during `MainScreen`'s build, after the
  auth flip has fired. `MainScreen` is built exactly once per sign-in, so the trigger is equivalent,
  and it sits with the four loads it will later drive.
- **`_isLoadInvitesRunning` is the only concurrency guard** — a load already in flight when an invite
  is answered can still land afterwards and re-add the answered row. That state is self-healing:
  answering the phantom row hits a 404, which drops it, and the next resume or pull clears it. The
  same benign race already exists between `createShoppingList` and `loadShoppingLists`, and closing
  it would cost machinery no other service in this app carries.
- **The screen owns the per-row busy state, the service owns the list** — greying one row is screen
  state; the service exposes no per-invite flag. Disabling the buttons is what stops a double tap.
- **A 404 is not an error path** — accept and decline both treat it as "already gone": drop the row,
  refresh nothing, and (on accept only) tell the user, since no access was granted.
- **The list empties in place** — the route is never popped from under the user when the last invite
  is answered or cancelled; the empty state renders instead.
- **Minimal `Invite` model** — `role` and `createdAt` come back in the response but are not rendered
  (there is no role picker, and the server already sorts newest first), so they are not carried.
  Every other model in this app holds only what it renders.
- **`InviteResourceType.fromApiString` throws on an unknown key**, as `UserRole.fromApiString` does.
  The four types are fixed and a fifth is HLD > Out of scope; the whole set ships in one release.
- **Icons are the app's existing ones** — `Icons.restaurant_menu`, `Icons.folder`,
  `Icons.shopping_cart`, `Icons.calendar_today` per type, so the icon already says which tab the
  resource will land in, and `Icons.mail_outline` on the menu row. Rows also carry the type's
  `displayName`, since four icons at 24dp are not self-evident.
- **Decline is confirmed, accept is not** — declining destroys the invite with no undo and no way to
  ask for it back; the sharer is never told. Confirming matches every other destructive action here
  (`SharingDialog._handleUnshare`, the collection delete).
- **No `MaterialBanner`** — the HLD calls for one indicator, and a banner is a widget class used
  nowhere in this app. It can be added later without redoing any of this.

## Assumptions to verify

- **Assumption:** T3 is merged, so `GET /invites` and the two `POST` endpoints behave as
  `docs/backend/modules/permissions/api.md` describes, with all four resource types reachable.
  **If wrong:** the screen renders against endpoints that do not exist yet; T4 cannot be verified
  end to end.
- **Assumption:** T4 needs no repository catch-up from T1's decisions. The `/permissions` path
  rename, the required `ShareRequest.role`, and recipes' 200→204 change all sit on the *sharer's*
  endpoints, which this task does not touch — `tasks.md` assigns every one of them to T5.
  **If wrong:** a `/invites` response shape differs from the documented one and the models need
  adjusting.
- **Assumption:** `MainScreen` is disposed only on sign-out — go_router keeps it alive beneath the
  nested `/invites` route, as it does beneath `/recipes-collections`.
  **If wrong:** the session-scoped service is torn down while the invites screen is open, and the
  scoping decision has to be revisited in favour of an app-lifetime singleton.
- **Assumption:** `Badge` and `Badge.count` render acceptably on the `inversePrimary` app bar —
  Flutter's default is `colorScheme.error` on `onError`, a red distinct from the app's deep-orange
  seed.
  **If wrong:** a theme colour override is needed; per `theming.md` it would come from
  `Theme.of(context)` before any new constant.
- **Assumption:** a `Badge.count` inside a `PopupMenuItem`'s `Row` lays out correctly against the
  item's fixed height.
  **If wrong:** the count moves to a plain trailing `Text` in the same row; nothing else changes.
- **Assumption:** calling `loadMealPlans()` is enough for an accepted meal plan to show up, because
  `MealPlanCalendarService` listens to that notifier and `MealPlanVisibilityService` defaults an
  unseen plan id to visible.
  **If wrong:** the fan-out for `MEAL_PLAN` also has to call `MealPlanCalendarService.loadCalendar()`
  and `ensurePlanVisibility`.
- **Assumption:** `main_screen_recipes_tab_widget_test.dart` is the only existing test that
  constructs `MainScreen`.
  **If wrong:** every other construction site needs the same `setupInvites` addition.

## Required reading

- `docs/tasks/2026-08-26-share-invites/research/t4-invitee-surface-placement.md` — the placement and
  refresh analysis behind the decisions above, and the row design.
- `docs/backend/modules/permissions/api.md` — `GET /invites`, accept/decline, the response fields,
  and the absence of a count endpoint.
- `docs/ADRs/0008-invite-label-snapshot.md` — why the row renders a stored label and never fetches
  the resource.
- `mobile/lib/core/main_screen.dart` — the app bar's conditional-action slot, the overflow menu, the
  `didChangeDependencies` loads and the `dispose` resets this task extends.
- `mobile/lib/features/recipe/collection/recipes_collection_list_screen.dart` — the full-screen list
  screen to mirror: `RefreshIndicator`, `AsyncValue.when` branches, confirmation dialog, snackbars.
- `mobile/lib/features/limits/limits_repository.dart` and `limits_setup.dart` — the repository and
  setup-function shape to copy.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — the `WidgetsBindingObserver`
  precedent for the resume reload (this task needs only the `resumed` branch).
- `docs/mobile/standards/architecture.md` > Widget Inputs, `state-management.md`,
  `dependency-injection.md`, `navigation.md`, `widget-testing.md` — the layering, notifier shape, DI
  registration, route definition and test rules this design follows.
- `HLD.md` > Feature areas > Invitee-facing surface (mobile), and > Open questions — the two
  questions this design closes.
