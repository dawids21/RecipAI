# T4 — where the invitee's indicator and invites list live

## Summary

The app shell has one global `AppBar` (in `MainScreen`) and a three-item `BottomNavigationBar`, and no badge
or notification pattern anywhere to copy. The strongest fit is a **badged icon button in the `MainScreen`
app bar, rendered only while invites are pending, opening a full-screen `/invites` route** — it reuses the
one conditional-app-bar-action slot the app already has (the "Manage Plans" button), the one full-screen
list route pattern it already has (`/recipes-collections`), and Flutter's built-in `Badge.count`, which is
exactly the Material 3 "large badge on a top app bar icon" pattern. A fourth bottom-nav destination is the
main alternative and is rejected: an invites tab is empty almost always, and Material 3 reserves
destinations for persistent top-level areas.

The second open question — whether the list needs its own refresh trigger — is largely settled by the API:
`GET /invites` returns the full list and there is **no count endpoint**, so the indicator's number is
`list.length` from the same fetch. One service, one notifier, one load. Load on the sign-in flip
(`LimitsService` precedent) and on app resume (`ShoppingListSyncService` precedent), plus pull-to-refresh in
the list. No polling timer.

## Constraints that narrow the design

### From the codebase

- **One global app bar.** `mobile/lib/core/main_screen.dart:132` builds the only `AppBar` shared across the
  three tabs. Sub-routes (recipe detail, shopping list detail, collections, the extraction screens) each
  build their own `Scaffold`, so anything in this app bar is visible on the main screen only — not while the
  user is deep in a recipe. That matches the HLD's wording ("a user opening the app sees that invites are
  waiting") and is not a defect.
- **The app bar already has a conditional-action slot.** `actions:` renders a "Manage Plans" `IconButton`
  only when the Planning tab is selected, then the `PopupMenuButton`. A second conditional action is the
  same shape the file already uses.
- **The bottom bar is the Material 2 `BottomNavigationBar`**, not M3's `NavigationBar`. Badging a
  destination works with either (`BottomNavigationBarItem.icon` takes any widget), but the M3
  `NavigationBar`/`NavigationDestination` route is the documented one.
- **No badge pattern exists.** `step_number_badge.dart` is an unrelated custom circle for recipe steps.
  Flutter's `Badge` / `Badge.count` widget is available (Flutter 3.44.6, Material library) and is unused.
- **Full-screen list route precedent:** `/recipes-collections` (`AppRoute.recipesCollections`) is a
  full-screen `Scaffold` list reached from the overflow menu, with services injected in the `routes.dart`
  builder closure. An `/invites` route is the same shape.
- **Two refresh precedents, deliberately different.** `LimitsService` subscribes to
  `AuthService.isAuthenticated` and loads once per session, nothing else. `ShoppingListSyncService` polls on
  a 10s `Scheduler` timer, cancels its timers on `AppLifecycleState.paused`, and re-polls on `resumed` via
  `WidgetsBindingObserver`. Both are available to copy; they sit at opposite ends of the cost scale.
- **Cross-service calls are allowed** through the other service's public API
  (`docs/mobile/standards/architecture.md` > Rules), which matters because accepting an invite has to make
  the resource show up in its own tab.
- **Theme:** `ColorScheme.fromSeed(Colors.deepOrange)`; the app bar is painted
  `theme.colorScheme.inversePrimary` (the peach in the screenshot). Flutter's `Badge` defaults to
  `colorScheme.error` on `onError`, which is a red distinct from the app's orange — worth an eyeball check
  on-device but no custom colour is expected.

### From the API and the HLD

- `GET /invites` returns every pending invite for the caller across all four resource types, newest first,
  each with `id`, `resourceType`, `label`, `invitedBy`, `role`, `createdAt`. There is **no `resourceId`** —
  the resource is unreadable while pending — and **no separate count endpoint**
  (`docs/backend/modules/permissions/api.md`).
- Accept and decline are `POST /invites/{id}/accept|decline`, both 204, both 404 if the invite is gone.
- The indicator is **the only discovery mechanism**; nothing is sent outside the app (HLD > Out of scope).
- The label is a snapshot and may be stale (ADR-0008); the list renders it and does not fetch the resource.
- There is no role picker — nothing to choose on accept.

## Options for the indicator

### A. Badged icon button in the `MainScreen` app bar — recommended

```
┌──────────────────────────────────────────────┐
│  RecipAI                            ✉③   ⋮   │
└──────────────────────────────────────────────┘
```

An `IconButton` rendered before the `PopupMenuButton`, wrapped in `Badge.count`, shown only while the count
is greater than zero, navigating to `/invites`.

- **For:** Material 3 names top app bars as a primary badge location. It costs one conditional widget in a
  file that already renders one. It is visible from all three tabs. Hidden at zero, it adds no permanent
  chrome to an app bar the screenshot shows is nearly empty. It is the one place a "something is waiting for
  you" affordance is universally understood.
- **Against:** an icon that appears and disappears is a layout shift and is undiscoverable when absent —
  a user cannot go looking for invites. That is acceptable here (nothing can be pending without the icon
  showing) but see the fallback below. Also invisible on sub-routes.
- **Fallback worth considering:** a permanent `Invites` item in the overflow menu, so there is a stable
  entry point regardless of count. Cheap (one `PopupMenuItem`), and it makes the surface findable when
  empty. The trade is a fifth item in a menu that already has four.

### B. Badge on the overflow menu button, invites as a menu item

Badge the existing `PopupMenuButton` (`⋮③`) and add an "Invites" row inside.

- **For:** zero new chrome; nothing appears or disappears.
- **Against:** the overflow badge means "something in this menu changed", which is vague — the user must
  open the menu to learn what. It also permanently associates the badge with a menu that will later carry
  unrelated items. Material 3's badge guidance is about a specific icon's content, not a container of
  actions.

### C. Fourth bottom-navigation destination

```
  🍴 Recipes    📅 Planning    🛒 Shopping    ✉③ Invites
```

- **For:** the most discoverable placement; a badged destination is the canonical M3 pattern and the one the
  search results describe as producing the best discoverability, because there is no intermediate layer.
- **Against:** a destination must be a persistent top-level area of the app. This one is empty almost all of
  the time — a permanent tab for a state that is normally "nothing here". It also shrinks the three existing
  tabs on a narrow screen and changes an app-shell decision (`_selectedIndex`, `IndexedStack`, the
  per-index FAB and `endDrawer` conditionals) far beyond what T4 needs. Reasonable only if invites are
  expected to be frequent, which for a household recipe app they are not.

### D. Badge each existing destination with its own type's invites

Recipes tab badges recipe + collection invites, Shopping badges shopping-list invites, Planning badges
meal-plan invites.

- **For:** the invite is surfaced next to where the resource will land; closest to the Google Photos
  "Sharing tab" model, where an album invite appears in the tab the album will join.
- **Against:** it needs three badges and three counts, splits one cross-type list into three surfaces, and
  the HLD explicitly wants one list showing all four types together. It also gives the user no single place
  to answer everything. Highest cost of the options, for a worse fit to the stated behaviour.

### E. `MaterialBanner` above the tab content

A persistent banner ("2 invites waiting — View") pinned above the body.

- **For:** unmissable; M3 banners exist for exactly this "important, dismissible, persistent" role.
- **Against:** it eats vertical space on a grid screen that is already dense, and it is a new widget class
  in this codebase (`MaterialBanner` is used nowhere; there are 76 `showSnackBar` calls and no banners). It
  also competes with content rather than sitting in chrome. Reasonable as a *second* surface if the badge
  proves too subtle, not as the first.

### F. `SnackBar` on app open

- **Against:** transient. A user who misses it has no path back, which contradicts "the indicator is the
  only discovery mechanism" and "it clears once no invites remain" — a snackbar clears itself. Rejected.

### Indicator shape: dot or count?

Material 3 distinguishes a **small badge** (a bare dot — "something is new") from a **large badge** (a
number — "how many"). The count is free here because the list fetch already returns it, and a number tells
the user whether the trip is worth it. Use `Badge.count`, which caps at `maxCount` (default 999) — a cap
that will never be reached in this app.

Accessibility: a Flutter `Badge` renders as visual decoration and is not announced on its own. Give the
`IconButton` a `tooltip` that includes the count (`'Invites (3)'`), which TalkBack reads.

## Options for the list surface

1. **Full-screen route `/invites` — recommended.** Matches `/recipes-collections`: `AppRoute.invites`,
   services injected in the `routes.dart` builder closure, a `Scaffold` with its own `AppBar`. Room for a
   real empty state, pull-to-refresh, and per-row confirmation dialogs without dialog-inside-dialog. Also
   the only option that survives if invites later gain detail (who else has access, when it was sent).
2. **`AlertDialog`.** Cheap and matches `SharingDialog`, but a decline confirmation would be a dialog over a
   dialog, and pull-to-refresh in a dialog is awkward. Fine only if the list is capped at a handful of rows.
3. **Modal bottom sheet.** Comfortable for a short list and easy to dismiss, but the same
   nested-confirmation problem, and no precedent in the app.
4. **`endDrawer`.** There is a precedent (`MealPlanDrawer` on the Planning tab), but the drawer slot is
   already conditional on the selected tab, and a drawer is for tools attached to the current screen, not a
   global inbox. Rejected.

### Row design

```
┌────────────────────────────────────────────────────────┐
│  🛒   Weekly Groceries                                 │
│       Shared by alice@example.com          Decline  Accept │
└────────────────────────────────────────────────────────┘
```

- **Leading icon by `resourceType`**, reusing the icons the app already assigns those things:
  `Icons.restaurant_menu` (recipe), `Icons.folder` (collection — the overflow menu's icon),
  `Icons.shopping_cart` (shopping list), `Icons.calendar_today` (meal plan). Reusing them means the icon
  already tells the user which tab it will land in. Consider a plain-text type label too
  ("Shopping list"), since four icons at 24dp are not self-evident.
- **Title:** `label`, the stored snapshot. **Subtitle:** `Shared by {invitedBy}`.
- **Actions:** the search results describe tick/cross icon pairs as the common mobile idiom, but with two
  actions and plenty of width, labelled `TextButton`s are unambiguous and match every other confirmation in
  this app. Put `Accept` last (trailing, filled or primary-coloured) and `Decline` before it.
- **Confirm the decline.** Declining destroys the invite with no undo and no way for the invitee to ask for
  it back — the sharer must re-send. `SharingDialog._handleUnshare` already sets the precedent of
  confirming a destructive share action.
- **Grey out the row while its call is in flight**, and remove it on 204. A 404 (cancelled underneath the
  user, or already answered on another device) should quietly drop the row and refresh rather than error —
  the desired end state has been reached either way.
- **Empty state.** The list can empty while open (the user answers the last invite, or the sharer cancels).
  Show "No pending invites" in place rather than popping the route out from under the user.

## Refresh trigger — the second open question

The API decides most of this: there is one endpoint, it returns the whole list, and the count is its length.
So the indicator and the list are the same piece of state and there is nothing to keep in sync. One
`InvitesService` owning a `ValueNotifier<AsyncValue<List<Invite>>>`, exposed read-only, with the app bar
reading `.length` through a `ValueListenableBuilder`.

When to load:

- **On the sign-in flip.** `LimitsService` already listens to `AuthService.isAuthenticated`, loads on true
  and clears on false. The clear matters: signing in as another account must not show the previous
  account's invites.
- **On app resume.** `ShoppingListSyncService`'s `WidgetsBindingObserver` is the precedent. This is what
  makes the flow work in practice — the sharer says "I shared it with you", the user switches to the app,
  and the badge is there.
- **On pull-to-refresh** in the list.
- **After accept or decline**, locally, by dropping the answered row (with a background reload to catch
  anything cancelled meanwhile).

**Against a polling timer:** the shopping-list sync poll exists because two people edit one list
simultaneously and staleness is measured in seconds. An invite is a rare event with no urgency; polling
would spend a request every N seconds forever to learn the list is still empty. If polling is wanted later,
`Scheduler` is already injectable and the change is additive.

### The integration accept must not forget

Accepting makes a resource appear in one of the three tabs, but nothing reloads those lists. `RecipeGrid`,
`ShoppingListList` and the calendar load in `MainScreen.didChangeDependencies` once. After a successful
accept, the matching list service needs its load method called — cross-service calls through a public API
are permitted by the architecture standard. The cleanest wiring given the widget-inputs rule is an
`onAccepted(resourceType)` callback supplied at the route builder in `routes.dart`, where the four list
services are already resolvable, rather than handing `InvitesService` four service references. Note that
`RECIPES_COLLECTION` has to refresh both the collections list and the recipe grid.

## Recommendation

1. Badged `IconButton` in the `MainScreen` app bar, before the overflow menu, rendered only when the count
   is above zero, `Badge.count`, tooltip carrying the count.
2. Tapping it goes to a full-screen `/invites` route (`AppRoute.invites`), one `ListTile`-shaped row per
   invite, type icon + label + "Shared by", `Decline` / `Accept`, decline confirmed, empty state in place.
3. One `InvitesService` feeding both, loaded on the auth flip, on app resume, and on pull-to-refresh; no
   polling timer.
4. Decide separately whether to add a permanent "Invites" overflow-menu item as a stable entry point.

## Open questions for the user

- **Permanent entry point or not?** If the badge is the only way in, invites are unreachable when the count
  is zero — which is harmless but means a user can never go "check". Adding the overflow item costs almost
  nothing; leaving it out keeps the menu at four items.
- **Which icon.** `Icons.mail_outline` reads "inbox, things waiting"; `Icons.person_add_alt` reads "someone
  shared with you". The second is more literal about the content, the first is the more conventional
  badge host.
- **Is a `MaterialBanner` wanted as a louder second surface** for a first release, on the theory that a new
  badge in an app that has never had one may go unnoticed? It can be added later without redoing anything.

## Sources

- `mobile/lib/core/main_screen.dart`, `routes.dart`, `theme.dart` — the app shell, the app-bar action slot,
  the route pattern, the theme colours.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart`,
  `mobile/lib/features/limits/limits_service.dart` — the two existing refresh strategies.
- `docs/backend/modules/permissions/api.md` — `GET /invites`, accept/decline, and the absence of a count
  endpoint.
- `docs/tasks/2026-08-26-share-invites/HLD.md`, `tasks.md` — T4's scope and the two open questions.
- `docs/mobile/standards/architecture.md`, `state-management.md`, `theming.md` — layering, notifier shape,
  and the styling priority order.
- [Badge – Material Design 3](https://m3.material.io/components/badges/guidelines) — small badge (dot, "something
  is new") vs large badge (count), and top app bars / navigation bars / tabs as the placements.
- [Navigation bar – Material Design 3](https://m3.material.io/components/navigation-bar/guidelines) — three to
  five destinations, badges in the destination icon's upper right, labels required.
- [Badge class – Flutter API](https://api.flutter.dev/flutter/material/Badge-class.html) — `Badge` /
  `Badge.count`, `isLabelVisible`, `maxCount` defaulting to 999, and the intended use decorating a navigation
  item's or button's icon.
- [NavigationBar class – Flutter API](https://api.flutter.dev/flutter/material/NavigationBar-class.html) — the M3
  destination widget, for the fourth-tab option.
- [Designing Notifications for Apps – UX Magazine](https://uxmag.com/articles/designing-notifications-for-apps) —
  badges anchored to navigation destinations give better discoverability than a separate bell; badge fatigue
  when everything is badged.
- [Notifications UI design – Setproduct](https://www.setproduct.com/blog/notifications-ui-design) — keeping the
  entry point consistent and in a place the user already looks.
- [How to accept a shared album invite – Apple Support](https://support.apple.com/en-qa/119865) and the Google
  Photos "Sharing tab" flow — the precedent behind option D: the invite surfaces in the tab the resource
  will join, and accepting is an explicit "Join".
- [iOS accepting-an-invite flows – Page Flows](https://pageflows.com/ios/flows/accepting-an-invite/),
  [Pending Invitation – CollectUI](https://collectui.com/challenges/pending-invitation) — the common row shape:
  who sent it, what it is, accept and decline side by side.
