# T5: Mobile — per-resource limit display and pre-emptive blocking — Task Design

**Date:** 2026-08-23

## Summary

Every capped resource shows its own `used / limit` at the point where the user acts on it — the
recipe form, the collection dialog, the shopping-list dialog, the plan dialog, both extraction
screens, and the shopping-list detail screen — and the action is disabled once the count reaches the
cap.

The two halves of that display come from different places and refresh on different schedules. **Caps**
come from one `GET /limits` fetched once per session and held by a new mobile `LimitsService`.
**Counts** come from the module that owns the resource — `GET /recipes/usage`,
`GET /collections/usage`, `GET /shopping-lists/usage`, `GET /meal-plans/usage`,
`GET /extract/usage` — each of which asks `LimitsFacade` for the caller's recorded usage rather than
counting anything itself, and each fetched fresh when a surface opens. Shopping-list items are the exception
on both halves: the count is the length of the local item store, and the cap comes from
`GET /shopping-lists/{id}/limits`, because an item cap is configured against the list's *owner* rather
than the viewer.

Because the count is the same number `reserve` compares against, a disabled button and a refusal agree
by construction: the action goes grey exactly when the next attempt would have been refused.

Enforcement is unchanged: T1–T4 completed the backend rules, and nothing here reserves, releases or
refuses. Disabling can only ever block — it never admits an operation the server would refuse — and
every surface fails open, so a missing cap or a failed count leaves the action enabled and the server
as the only thing that says no.

## Components and responsibilities

### `backend/.../limits/` — the cap read path

- **`LimitCap.java`** (CREATE) — public record `(resource, kind, limit)`, the wire shape of a resolved
  cap. It deliberately carries no period: the client never does period arithmetic, and the only
  time-derived value it displays (`resetsInSeconds`) rides on the standing instead. `LimitPeriod`
  therefore stays package-private.
- **`LimitStanding.java`** (CREATE) — public record `(used, periodStart, resetsInSeconds)`, replacing
  `LimitUsageDetails`. Same two components as its predecessor plus the reset countdown, with the
  elapsed-period rule applied.
- **`LimitUsageDetails.java`** (DELETE) — superseded by `LimitStanding`.
- **`LimitsController.java`** (CREATE) — package-private `@RestController` on `/limits`. `GET /limits`
  reads the caller's email from the JWT and returns the caps resolved for that subject. Holds no
  domain knowledge: it never names a resource, it returns whatever `limit_config` resolves.
- **`LimitService.java`** (MODIFY) — `currentUsage` becomes `standing(subject, resource)`, which
  resolves configuration (it needs the period) and applies the elapsed-period rule **virtually** — a
  lapsed flow window reports zero used without writing, so a read never mutates the reserve's state.
  Gains `caps(subject)` resolving every configured resource for a subject, and `cap(subject, resource)`
  for one.
- **`LimitsFacade.java`** (MODIFY) — `currentUsage` → `standing`; adds `caps` and `cap`. `caps` and
  `cap` honour `recipai.limits.enabled` and return nothing when it is off; `standing` ignores it, as
  `currentUsage` does today.
- **`LimitConfigRepository.java`** (MODIFY) — `resolveAll(subject)`, returning one row per resource
  with the subject's override beating the default. `resolve` generalised from one resource to all.
- **`LimitsModuleArchitectureTest.java`** (MODIFY) — `LimitCap` and `LimitStanding` take
  `LimitUsageDetails`' place in the allowed public types. The controller stays package-private, so
  `onlyTheFacadeAndSharedTypesArePublic` still holds, and `limitsModuleHasNoDomainKnowledge` is
  untouched because the controller depends only on Spring types.

### `backend/` — the usage endpoints

Each module answers for its own resource, and each answers by asking `LimitsFacade.standing` for the
caller's recorded usage. None of them counts rows. Every one of these services already holds a
`LimitsFacade` for its `reserve`/`release` calls, so no new dependency is introduced anywhere — each
gains one service method and one controller method.

- **`recipes/RecipeController.java`** + **`RecipeService`** (MODIFY) — `GET /recipes/usage`.
- **`recipes/collections/RecipesCollectionController.java`** + service (MODIFY) —
  `GET /collections/usage`.
- **`shoppinglists/ShoppingListController.java`** + service (MODIFY) — `GET /shopping-lists/usage`
  for the caller's own list usage, and `GET /shopping-lists/{id}/limits` returning that list's item
  cap: access checked with the existing `requireEditorPermission`, owner resolved with the existing
  `requireOwnerEmail`, cap asked of `LimitsFacade.cap(owner, SHOPPING_LIST_ITEM)`. The caller never
  learns who the owner is.
- **`planning/MealPlanController.java`** + service (MODIFY) — `GET /meal-plans/usage`.
- **`extraction/ExtractionController.java`** + **`ExtractionService`** (MODIFY) — `GET /extract/usage`.
  Identical in shape to the other four; extraction is no longer a special case, because none of them
  counts source rows any more.

All five return the same body, `{"used": n, "resetsInSeconds": …}`. A subject with no usage row yet —
`standing` returns `Optional.empty()` — reports `used: 0`, not a 404. `resetsInSeconds` is null unless
that subject's cap resolves to a `FLOW` with a period, which is why the shape is uniform even though
only extraction exercises it under the seeded defaults.

### `backend/.../config/security/SecurityConfig.java` (MODIFY)

`/limits/**` added to the authenticated matchers. The chain ends in `anyRequest().denyAll()`, so
without this the new endpoint is refused before it is reached.

### `backend/src/test/` — call-site migration

61 `limitsFacade.currentUsage(...)` call sites across `LimitsIntegrationTest`,
`ExtractionIntegrationTest`, `RecipeIntegrationTest`, `RecipesCollectionIntegrationTest`,
`ShoppingListIntegrationTest` and `MealPlanIntegrationTest` become `limitsFacade.standing(...)`. The
translation is a rename in all but one case, because `standing` keeps `currentUsage`'s
`Optional.empty()` meaning exactly: **no usage row for this (resource, subject)**. It is not "no
configuration" — a subject with configuration but no row is still empty, as today.

| Today | After |
| --- | --- |
| `currentUsage(s, r)).isEmpty()` — 14 sites | `standing(s, r)).isEmpty()` — unchanged meaning |
| `currentUsage(s, r).orElseThrow().used()` | `standing(s, r).orElseThrow().used()` |
| `LimitUsageDetails before = currentUsage(...)` | `LimitStanding before = standing(...)` |
| `after.periodStart()).isEqualTo(before.periodStart())` — 9 sites | unchanged; all sit on live windows |

The one test whose assertion changes is
**`LimitsIntegrationTest.shouldNotRestartElapsedPeriodWhenMaxIsZero`** (`:269`). It seeds a lapsed
`DAY` window with `used = 2` and asserts the refused reserve left it at 2. Under the adjusted view a
lapsed window reports zero, so `isEqualTo(2)` becomes `isZero()` — and it keeps its teeth: had the
reserve wrongly granted, the row would carry `used = 1` on a *live* window and the standing would
report 1, not 0.

### `mobile/lib/features/limits/` (CREATE)

- **`limit_cap.dart`** — the model mirroring `LimitCap`, with `LimitKind` as a Dart enum.
- **`limit_usage.dart`** — the model mirroring `LimitStanding`'s wire shape (`used`,
  `resetsInSeconds`), shared by all five features because all five endpoints return it.
- **`limits_repository.dart`** — `GET /limits`, decoding to `Map<String, LimitCap>` keyed by resource.
- **`limits_service.dart`** — holds the session's caps. Subscribes to `authService.isAuthenticated` at
  construction: loads on `true`, clears on `false`. Exposes the caps notifier and a synchronous
  `capFor(resource)`. Its `dispose()` removes the auth listener and disposes the notifier.
- **`limits_setup.dart`** — DI registration, following `recipe_setup.dart`.
- **`limit_counter.dart`** — the `used / limit` display every surface renders, plus `formatResetIn`.
  Purely presentational: it takes its numbers as parameters and resolves nothing from `getIt`,
  because each surface has a different count source.

### `mobile/lib/features/*/` — the usage state

Each service gains a usage notifier, a load method and a `dispose()` line. The usage read lives with
the resource it describes, mirroring the backend split:

- **`recipe/recipe_list_service.dart`** — `recipeUsage`, `loadRecipeUsage()`
- **`recipe/collection/recipes_collection_list_service.dart`** — `collectionUsage`
- **`shopping_list/shopping_list_list_service.dart`** — `listUsage`
- **`planning/meal_plan_list_service.dart`** — `planUsage`
- **`extraction/extraction_service.dart`** — `extractionUsage`

All five hold the same `LimitUsage` model from `features/limits/`, since all five endpoints return the
same body. Each feature's repository gains the matching fetch method. No repository gains a `429`
branch — see *Decisions made*.

### `mobile/lib/features/shopping_list/` — the per-list cap and the two add surfaces

- **`shopping_list_repository.dart`** (MODIFY) — `fetchItemCap(listId, idToken)` calling
  `GET /shopping-lists/{id}/limits`. The per-list read is a shopping-list request that happens to
  return a cap, so it belongs to the feature that owns the route.
- **`shopping_list_detail_service.dart`** (MODIFY) — owns the open list's cap as a notifier, loaded
  once in `openShoppingList`. It does **not** track the item count: the count is the length of the
  existing `items` notifier, which the store already keeps live. Nothing new to tear down beyond the
  notifier itself.
- **`shopping_list_item_add_widget.dart`** (MODIFY) — a new `enabled` flag; when false the `TextField`
  is disabled.
- **`shopping_list_detail_screen.dart`** (MODIFY) — renders the counter beside the add row, passes
  `enabled:` to the add widget, and guards `_createEphemeralItemAfter` so the Enter-to-insert row and
  its chain stop at the cap.

### `mobile/lib/` — the create surfaces

- **`main.dart`** — `setupLimits()` called after `setupAuth()`.
- **`recipe/create_recipe_screen.dart`** — loads the count on `initState`, and passes a counter widget
  and a `saveBlocked` flag down.
- **`recipe/recipe_form_widget.dart`** — two new optional parameters (`limitCounter`, `saveBlocked`),
  rendered/applied above and on the save button. The edit screen passes neither, which is what keeps
  the display create-only in a widget shared by both.
- **`recipe/collection/recipes_collection_create_dialog.dart`** (CREATE) — the inline `AlertDialog` in
  `recipes_collection_list_screen.dart` becomes a real `StatefulWidget`, because it now loads on open.
  Sibling: `recipes_collection_rename_dialog.dart`.
- **`shopping_list/shopping_list_create_dialog.dart`** (CREATE) — same extraction out of
  `shopping_list_list_fab.dart`. Sibling: `shopping_list_rename_dialog.dart`.
- **`planning/plan_form_dialog.dart`** (MODIFY) — gains `MealPlanListService`; loads and displays only
  when `existingPlan == null`, and disables its `FilledButton` at the cap.
- **`planning/meal_plan_drawer.dart`** (MODIFY) — passes the service into the dialog.
- **`extraction/url_extraction_screen.dart`**, **`extraction/image_extraction_screen.dart`** (MODIFY) —
  load usage on `initState`, render the counter (with "resets in …" when the standing carries one),
  and disable the extract FAB at the cap.

### `mobile/test/`

- **`support/mocks.dart`** (MODIFY) — `MockLimitsRepository`.
- **`features/recipe/main_screen_recipes_tab_widget_test.dart`** (MODIFY) — `setupLimits(...)` joins
  the `setUp` ordering with a stubbed caps fetch. Without it the auth subscription drives a real HTTP
  call.

### Documentation

- `docs/backend/modules/limits/codebase_structure.md` — loses "no HTTP endpoints", gains the
  controller, `caps`/`cap`/`standing`, and the kill-switch's effect on the cap read; a new `api.md`
  for `GET /limits`.
- `docs/backend/modules/{recipes,shopping-lists,planning,extraction}/api.md` — the usage endpoints,
  and `GET /shopping-lists/{id}/limits`.
- `docs/backend/standards/integration-tests.md` — its worked example is `LimitsFacade.currentUsage`
  (lines 42–53); it becomes `standing`.
- `docs/INDEX.md` — the limits entry says "(no HTTP endpoints)"; corrected. New mobile `limits` entry.
- `docs/mobile/modules/limits/` — new `codebase_structure.md` and `ui.md`; the counter noted on each
  existing area's `ui.md`.

## Interfaces and method signatures

### Crossing the limits module boundary

```java
public record LimitCap(String resource, LimitKind kind, int limit) {}

public record LimitStanding(int used, Instant periodStart, Long resetsInSeconds) {}

// LimitsFacade
public List<LimitCap> caps(String subject)                          // empty when limits disabled
public Optional<LimitCap> cap(String subject, String resource)      // empty when limits disabled
public Optional<LimitStanding> standing(String subject, String resource)
```

`standing` returns `Optional.empty()` when **no usage row exists** for that subject and resource — the
same meaning `currentUsage` carries today, which is what makes the 61-site migration a rename.

### Internal to `limits`

```java
// LimitConfigRepository — one row per resource, override beating default
List<LimitConfig> resolveAll(String subject)
```

### HTTP

```
GET /limits                      -> 200 [LimitCap, ...]                (subject: jwt email; [] when disabled)
GET /recipes/usage               -> 200 {"used": 3, "resetsInSeconds": null}
GET /collections/usage           -> 200 {"used": 1, "resetsInSeconds": null}
GET /shopping-lists/usage        -> 200 {"used": 2, "resetsInSeconds": null}
GET /meal-plans/usage            -> 200 {"used": 1, "resetsInSeconds": null}
GET /extract/usage               -> 200 {"used": 1, "resetsInSeconds": null}
GET /shopping-lists/{id}/limits  -> 200 LimitCap | 204 (disabled or unresolvable)
                                    403 not an editor of the list
                                    404 no such list
```

No endpoint accepts a subject parameter. The route determines the subject, because `limits` cannot
authorise an opaque string and a caller-supplied subject would let anyone read anyone.

### Mobile

```dart
// features/limits/limit_cap.dart
class LimitCap {
  final String resource;
  final LimitKind kind;   // stock | flow
  final int limit;
  factory LimitCap.fromJson(Map<String, dynamic> json);
}

// features/limits/limits_repository.dart
Future<Map<String, LimitCap>> fetchCaps(String? idToken);

// features/limits/limits_service.dart
class LimitsService {
  ValueListenable<AsyncValue<Map<String, LimitCap>>> get caps;
  LimitCap? capFor(String resource);   // synchronous; null while loading, on failure, or when disabled
  void dispose();                      // removes the auth listener
}

// features/limits/limit_counter.dart
class LimitCounter extends StatelessWidget {
  const LimitCounter({required this.used, required this.limit, this.resetsInSeconds, required this.noun});
}
String formatResetIn(int seconds);

// features/limits/limit_usage.dart
class LimitUsage {
  final int used;
  final int? resetsInSeconds;   // null unless a live FLOW window is running
  factory LimitUsage.fromJson(Map<String, dynamic> json);
}

// features/*/…_service.dart — the same pair on all five, e.g.
ValueListenable<AsyncValue<LimitUsage>> get recipeUsage;
Future<void> loadRecipeUsage();

// features/shopping_list/shopping_list_detail_service.dart
ValueListenable<AsyncValue<LimitCap?>> get itemCap;   // null cap = no limit known; count is items.length
```

## Data flow

**Caps, once per session.** `LimitsService` is constructed at `setupLimits()` and subscribes to
`authService.isAuthenticated`. On the flip to `true` it calls `GET /limits`; the controller reads
`jwt.getClaimAsString("email")`, `LimitService.caps` resolves every configured resource for that
subject in one query, and the client keys the result by resource. On the flip to `false` the map is
cleared, so a logout-then-login as another account gets that account's caps. Nothing else refreshes
it — caps are near-static configuration, and an operator's change is picked up at the next app start.

**Usage, on open.** The screen or dialog calls its service's load method in `initState` (or in the
dialog's `initState` for the two extracted dialogs). The service calls its repository, which hits that
module's usage endpoint; the controller reads the caller's email from the JWT and the service asks
`LimitsFacade.standing(email, ITS_OWN_RESOURCE_KEY)`, mapping an absent row to `used: 0`. The surface
renders `LimitCounter` from that number and `LimitsService.capFor(resource)`, and passes
`used >= limit` to whatever disables its action. Nothing refreshes it while the surface is open — it
is a snapshot taken when the user arrived, and the surface closes after the one action it gates.

All five resources take this path identically, extraction included. `resetsInSeconds` comes back
populated only where the subject's cap resolves to a `FLOW` with a period; under the seeded defaults
that is nowhere, so the countdown never renders until an operator sets one — see *Decisions made*.

**Items.** The cap and the count come from different places.

The cap is fetched once, in `ShoppingListDetailService.openShoppingList`, via
`GET /shopping-lists/{id}/limits`. `ShoppingListService` checks editor permission, resolves the owner,
and asks `limits` for the `SHOPPING_LIST_ITEM` cap configured against that owner — which is why the
session cap cache cannot answer this one: on a shared list the relevant override belongs to someone
else.

The count is local. `_store.watch(listId)` already drives the `items` notifier with the list's visible
(non-`pendingDelete`) rows — the same set the screen renders — so the count is its length, recomputed
by the store on every mutation and every poll reconcile. It must be the **flat** length, not the
active section's: the cap counts checked items too.

**Blocking.** Six surfaces disable at `used >= limit`: the recipe form's save button, three dialogs'
create buttons, both extraction FABs, and — on the detail screen — *both* item add surfaces. For the
five server-counted resources this threshold is the same comparison `reserve`'s upsert makes, against
the same row, so the button greys out exactly when the next attempt would be refused. If either number
is missing (caps still loading, caps empty because limits are disabled, usage fetch failed) the action
stays enabled and the server remains the only thing that refuses.

## Pseudo-code

**The elapsed-period rule, applied without writing.** This has to agree with what `reserve`'s upsert
would do at this instant, or the screen shows a spent allowance the very next request would restore.

```
standing(subject, resource):
    row = usageRepository.findById(resource, subject)
    if row is null: return empty                       # unchanged from currentUsage

    config = configRepository.resolve(resource, subject)   # may be absent
    now    = clock.instant()

    if config?.period != null and row.periodStart <= config.period.cutoffFrom(now):
        # the window has lapsed; the next reserve would restart it at 1
        return LimitStanding(used = 0, periodStart = null, resetsInSeconds = null)

    resets = null
    if config?.kind == FLOW and config.period != null:
        resets = max(1, seconds between now and config.period.nextStart(row.periodStart))

    return LimitStanding(row.used, row.periodStart, resets)
```

`cutoffFrom` and `nextStart` are `LimitPeriod`'s existing methods — the same ones `reserve` and the
refusal's `retryAfterSeconds` already use — so there is no second copy of the period arithmetic.

**Gating both item add surfaces.** The ephemeral row is not a persistent widget, so it takes a guard at
its opening point rather than an `enabled` flag. One guard covers both entry points, because
Enter-on-an-existing-item and the chain-continuation both route through the same method.

```
atItemCap():
    cap   = itemCap.value.valueOrNull                  # null while loading or when unresolvable
    count = items.value.valueOrNull?.length            # FLAT length — checked items count
    return cap != null and count != null and count >= cap.limit

_createEphemeralItemAfter(index):
    if atItemCap(): return                             # swallow the Enter; the counter reads "50 / 50"
    setState: _ephemeralAfterIndex = index

# add widget
ShoppingListItemAddWidget(enabled: !atItemCap(), ...)
```

The ordering works out on a chained add without extra plumbing. In `ShoppingListItemWidget`'s
`onSubmitted` (`:201-205`), `_parseAndSave()` runs *before* `widget.onSubmitted?.call()`, and the store
applies a create synchronously in memory before its DB write-through. So by the time
`_createEphemeralItemAfter(i + 1)` reads the count, the just-added item is already in it, and the guard
declines to open the next row exactly at the cap.

A row already open when the cap is reached by an incoming poll is left open rather than yanked from
under the cursor; committing it is refused and discarded by T4's existing path, with its
"This list is full" toast.

## Decisions made

- **The endpoints report recorded usage, not a count of owned rows** — every one of the five asks
  `LimitsFacade.standing` for the same row `reserve` compares against, so a disabled button and a
  refusal cannot disagree: the surface greys out precisely when the next attempt would be refused, and
  there is no second definition of "how many do I have" to keep in step with the first. It also means
  no module grows a counting query, no ownership predicate has to be kept aligned with
  `R__recompute_limit_usage.sql`, and extraction stops being a special case. Accepted cost: the number
  inherits `limit_usage`'s drift risk — a missed release shows the user an inflated figure and blocks
  them early, repaired by re-running the recompute, which is the failure mode ADR-0006 already names as
  this design's principal cost.
- **Each module still owns its own usage route** — the value comes from `limits`, but the endpoint
  lives beside the resource it describes, so `limits` keeps no resource vocabulary at the HTTP edge and
  exposes only caps. Cost: five near-identical controller methods instead of one generic read.
- **All five return the same body, and an absent usage row reports `used: 0`** — a subject who has
  never created anything has no row, which is a standing of zero rather than a missing resource; 404
  would make every first-run surface an error case.
- **`standing` replaces `currentUsage`; `LimitUsageDetails` is deleted** — one type for the module
  boundary and the test assertions, richer than what it replaces by exactly the `resetsInSeconds`
  extraction needs. Feasible as a rename across 61 sites because `Optional.empty()` keeps meaning
  "no usage row".
- **The standing read never writes** — a lapsed window reports zero used and the row is left alone.
  Restarting the period on a read would make the display a mutation and re-anchor the user's reset time
  to whenever they last opened a screen.
- **`LimitCap` carries no period, so `LimitPeriod` stays package-private** — the client never does
  period arithmetic; the only time-derived value it shows is `resetsInSeconds`, which rides on the
  standing. Keeps the module's public surface at what callers genuinely need.
- **`caps`/`cap` honour `recipai.limits.enabled`; `standing` does not** — with the client now
  *disabling* rather than merely displaying, reporting caps while the flag is off would give local
  development disabled Create buttons at 5 recipes while the backend happily accepts the write. No cap
  means nothing displayed and nothing disabled, which is exactly how the server behaves. `standing`
  keeps `currentUsage`'s flag-blind behaviour; a count with no cap is harmless because nothing consumes
  it.
- **Caps load off the auth flag, not off a screen** — `LimitsService` subscribes to
  `authService.isAuthenticated`, so the fetch is genuinely once per session regardless of navigation,
  and a logout clears the map rather than leaving the previous account's caps in place. New pattern
  here — no service listens to auth today — at the cost of a listener to remove in `dispose()`.
- **Usage is fetched per surface open and never refreshed while open** — a create surface exists to
  perform one action and then closes, so a snapshot on arrival is as fresh as it needs to be. This is
  also why the usage state lives on the existing feature services rather than in a shared limits
  service: with per-module endpoints, a shared fetcher would have to know all five routes, which is
  exactly the resource vocabulary `limits` is kept free of.
- **No 429 parsing; the existing generic error text stays** — with every surface disabled at the cap, a
  refusal reaching the user means the count was stale, missing, or raced, and those are rare. The
  accepted cost is that T5's original outcome — a blocked action naming the resource — holds only where
  the count fetch succeeded, and that T3's `'Plan limit exceeded'` string match survives untouched.
- **Both item add surfaces are gated, or neither is** — disabling only the bottom add widget would be
  theatre: Enter on any item opens the ephemeral row and chains indefinitely through the same
  `service.addItem`. The guard goes on `_createEphemeralItemAfter`, which both entry points share.
- **The item count is read from the local store; only the cap comes from the server** — items are the
  one limited resource the device holds a complete mirror of, and `_store.watch(listId)` already exposes
  exactly the rows the screen renders. Counting them shows what the user sees, including creates still
  queued in the outbox, and costs one request per open instead of one per drain. Accepted costs: this
  count is computed on the device where the other five are served; on a shared list it can lag another
  editor's changes by a poll interval, which can disable the add row while the server would still have
  accepted. Disabling only ever blocks, so the failure is conservative, and T4's refusal path still
  covers the other direction.
- **The item cap is fetched per list, not taken from the session cache** — the cap value is configured
  against the list's *owner*, so on a shared list the session cache holds the wrong number. Costs a new
  round-trip on a screen that makes no server call for the list today.
- **The item cap is fetched once per open and never refreshed** — near-static configuration, unlike the
  count. An operator changing a cap mid-session is picked up the next time the screen opens.
- **Every surface fails open** — a missing cap or a failed count leaves the action enabled. A stale or
  absent display can then never lock a user out of something the server would have allowed, which keeps
  the HLD's rule that the server is the only thing that refuses.
- **Extraction's reset countdown is written but latent** — the seeded `EXTRACTION` default is `FLOW`
  with no period, an "N ever" allowance that never resets, so `resetsInSeconds` is always null and the
  countdown never renders until an operator sets a period with SQL. T5 stays a display task and changes
  no configuration.
- **The two inline `showDialog` bodies become real widgets** — a dialog that loads on open needs state,
  and both features already have a `*_rename_dialog.dart` sibling to mirror.
- **`RecipeFormWidget` takes the counter and the blocked flag as parameters** — the widget is shared by
  create and edit, and passing nothing from the edit screen is what keeps the display create-only
  without the form knowing which mode it is in.
- **`LimitCounter` resolves nothing from `getIt`** — unlike a single ambient indicator, each surface has
  its own count source, so the numbers are parameters and the widget stays presentational.
- **`LimitStanding` is the wire shape for all five usage endpoints** — it already carries exactly
  `used` and `resetsInSeconds`, it is public for the module boundary regardless, and one shared type
  spares five near-identical per-module DTOs. On the client the mirror image holds: one `LimitUsage`
  model in `features/limits/`, imported by five feature repositories. That is a cross-feature import
  from a repository, which the architecture standard permits — it forbids repositories depending on
  *services and views*, which a plain model is not.

## Assumptions to verify

- **Assumption:** Spring prefers the literal `GET /shopping-lists/usage` over `GET /shopping-lists/{id}`
  where the path variable is a `UUID`. `planning` already coexists this way (`/meal-plans/calendar`
  alongside `/{id}`), but `planning` has no `GET /{id}`.
  **If wrong:** the usage endpoints need an explicit ordering or a distinct path segment; the shape of
  the design is unaffected.
- **Assumption:** `limit_usage` is accurate for the resources being displayed — i.e. T2's and T4's
  release-path audits caught every deletion path, and the rollout recompute has been run.
  **If wrong:** the counter overstates what the user holds and blocks them early, with no way to clear
  it from the app. This is the risk the design takes on by reading recorded usage instead of counting
  rows; the repair is re-running `R__recompute_limit_usage.sql`, and the symptom is worth watching for
  during T5 verification precisely because the display is the first thing that would reveal it.
- **Assumption:** `requireEditorPermission` is the right gate for the per-list cap, because
  `ShoppingListService.findById` already requires editor rights to read the list at all.
  **If wrong:** if a viewer role can reach the detail screen, the gate must widen or the counter must be
  hidden for viewers.
- **Assumption:** the visible set `_store.watch(listId)` exposes — rows with `pendingDelete == false` —
  is the same set the server counts against `SHOPPING_LIST_ITEM` once the outbox is empty and a poll has
  reconciled.
  **If wrong:** the two numbers disagree at rest, and the add row disables at the wrong point.
- **Assumption:** no `currentUsage` call site depends on the *stored* value of a lapsed periodic FLOW
  window beyond `shouldNotRestartElapsedPeriodWhenMaxIsZero`. The only tests seeding a period are the
  `FLOW`/`DAY` cases in `LimitsIntegrationTest`, and the rest leave a live window behind a `reserve`.
  **If wrong:** that call site asserts `periodStart()).isNull()` instead, or moves onto the
  `LimitExceededException`'s raw `used()`.
- **Assumption:** `AuthService.isAuthenticated` flips exactly once per login and once per logout, so a
  listener-driven fetch does not fan out into repeated requests.
  **If wrong:** `LimitsService.load()` needs the in-flight guard the state-management standard describes.
- **Assumption:** disabling the add `TextField` mid-session is acceptable UX when the user has just
  typed the item that reached the cap — the field loses focus and the keyboard drops, against the
  widget's deliberate "keep focus for quick consecutive entry" behaviour.
  **If wrong:** the field stays enabled and shows the refusal on submit instead, which is the option
  weighed and rejected during design.
- **Assumption:** every surface listed has somewhere to put a one-line counter without layout work.
  **If wrong:** the affected surface needs a small layout change the implementation plan must budget for.
- **Assumption:** adding `setupLimits()` to `main()` and to the one widget test is the whole DI fallout.
  **If wrong:** every other entry point constructing these services needs the registration too.

## Required reading for implementation planning

- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitService.java` — the reserve path whose
  elapsed-period behaviour the standing read must agree with exactly.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitConfigRepository.java` — the `resolve` query
  `resolveAll` generalises.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsModuleArchitectureTest.java` — the two rules
  constraining where the controller and the new records may live.
- `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsIntegrationTest.java` — the densest set of
  call sites to migrate, and `shouldNotRestartElapsedPeriodWhenMaxIsZero` at `:269`.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — the
  `requireEditorPermission` / `requireOwnerEmail` pair the per-list cap reuses (see the `createItem`
  reserve at the same two calls).
- `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the repair for a drifted
  `limit_usage`, which is now what every displayed number is read from.
- `backend/src/main/java/xyz/stasiak/recipai/config/security/SecurityConfig.java` — the
  `anyRequest().denyAll()` terminator `/limits/**` must be registered ahead of.
- `docs/backend/standards/integration-tests.md` — enabling limits for a `@Nested` class inside a suite
  that runs with them off, and the read-through-the-facade rule (with the example that must be updated).
- `docs/mobile/standards/state-management.md` and `dependency-injection.md` — the notifier shape,
  concurrency guard and `dispose()` obligation the new service follows.
- `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — `_createEphemeralItemAfter`
  (`:370`), `_commitEphemeralItem` (`:381`) and the ephemeral row's construction (`:460-484`).
- `mobile/lib/features/shopping_list/shopping_list_item_widget.dart` `:201-205` — the
  `_parseAndSave()`-before-`onSubmitted()` ordering the chain guard depends on.
- `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart` — `watch` and
  `_visibleItems`, the set the item count is read from.
- `mobile/lib/features/auth/auth_service.dart` — `isAuthenticated`, the notifier `LimitsService`
  subscribes to.
- `mobile/lib/features/recipe/collection/recipes_collection_rename_dialog.dart` and
  `mobile/lib/features/shopping_list/shopping_list_rename_dialog.dart` — the stateful-dialog pattern the
  two extracted create dialogs mirror.
- `mobile/test/features/recipe/main_screen_recipes_tab_widget_test.dart` — the `setUp` ordering the new
  `setupLimits` must join.
- `plans/T4-task-design.md` > *Correction after first implementation* — why the item cap's configuration
  subject is the owner while its usage subject is the list.
- `docs/ADRs/0006-shared-limits-module.md` > *Consequences* — the boundary the new endpoints sit inside.
- `HLD.md` > Feature areas > *Mobile*, *Limits module (new)* — the standing report behaviour and the
  never-compute-on-device rule, which the local item count is read against.
