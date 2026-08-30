# T5: Sharer-facing mobile surface — Task Design

**Date:** 2026-08-29

## Summary

A new `sharing` feature directory becomes the sharer-side counterpart to T4's `invites`: it holds the
generic `SharingDialog` (moved out of `lib/core/widgets/`), one `ResourcePermission` model that
replaces the four byte-identical `*Permission` models, and the typed refusal the 409 handshake needs.
The dialog renders granted users and pending invites in one list, marking pending rows with a chip,
and cancels an invite through the `unshare` call it already makes. The four repositories catch up
with T1/T2's contract changes at the same time: `/permissions` instead of `/users` and
`/shared_users`, a required `role`, and 204 from recipes' share and unshare.

## Components and responsibilities

### New — `mobile/lib/features/sharing/`

- **`resource_permission.dart`** (CREATE) — the `ResourcePermission` model: `email`, `role`
  (`UserRole`), `pending`, plus `fromJson`. One model for all four resource types, matching the
  backend's own `PermissionDto` after T1 collapsed the four duplicated types into it. Replaces
  `recipe_permission.dart`, `recipes_collection_permission.dart`, `shopping_list_permission.dart`
  and `meal_plan_permission.dart`, which are deleted.
- **`share_refused_exception.dart`** (CREATE) — `ShareRefusedReason` (`alreadyInvited`,
  `alreadyHasAccess`) with `fromApiString`, and `ShareRefusedException` carrying the reason and the
  target email. Thrown by all four repositories on a 409 from `share`, so the call sites can word the
  refusal for the user instead of printing a status code. Follows `InviteGoneException`'s shape
  (`invites_repository.dart`), but lives in its own file because four repositories throw it.
- **`sharing_dialog.dart`** (MOVE from `lib/core/widgets/`, then MODIFY) — the one dialog all four
  features funnel through. It stops declaring `SharedUser` and instead renders
  `ResourcePermission`s against a `currentUserEmail` passed in beside them. Owns the row layout (the
  "Pending" chip and the `Invited as {role}` subtitle), the email form, and the confirmation dialog
  whose wording now branches on `pending`. It renders and reports — it takes a `ValueListenable` and
  two callbacks, never a service (mobile `architecture.md` > Widget Inputs).

The three per-feature wrapper dialogs (`recipe_sharing_dialog.dart`,
`shopping_list_sharing_dialog.dart`, `meal_plan_sharing_dialog.dart`) stay in their own features:
each binds one feature's service to this dialog and is opened from that feature's screen. Moving
them would make `sharing` depend on all four features' services. `sharing` has no repository and no
service of its own — each resource module's repository already carries its `share` / `unshare` /
`permissions` endpoints — so there is no `sharing_setup.dart` and nothing to register in `main.dart`.

### Modified — the four repositories

Each of `shopping_list_repository.dart`, `recipe_repository.dart`,
`collection/recipes_collection_repository.dart` and `planning/meal_plan_repository.dart`:

- `fetchSharedUsers` → **`fetchPermissions`**, returning `List<ResourcePermission>`, against
  `GET /<resource>/{id}/permissions` (was `/users`; `/shared_users` for recipes).
- The share body gains `'role': 'EDITOR'` — `ShareRequest.role` is `@NotNull` since T1, so every
  existing `{"email": …}` body is a 400.
- A 409 from `share` maps to `ShareRefusedException`.
- **`recipe_repository.dart` only:** `shareRecipe` and `unshareRecipe` accept **204** instead of 200
  (T2 aligned recipes with the other three), and their blanket `try`/`catch` must let
  `ShareRefusedException` through rather than re-wrapping it as a network error.

### Modified — the four services

`recipe_detail_service.dart`, `collection/recipes_collection_list_service.dart`,
`shopping_list_detail_service.dart` and `planning/meal_plan_sharing_service.dart`:

- The notifier becomes `ValueNotifier<AsyncValue<List<ResourcePermission>>>`, renamed
  `_permissions` / `permissions`, and the `SharedUser` mapping in `loadSharedUsers` disappears — the
  repository's list is published as-is. `loadSharedUsers` → `loadPermissions`; the guard flag and
  the `share`/`unshare` refresh calls follow the rename.
- Each gains `String get currentUserEmail => _authService.email;` so its call site can hand the
  dialog the email the "no remove button on your own row" rule needs. It replaces the per-row
  `isCurrentUser` each service computes from that same value today, so the services end up doing
  strictly less with `AuthService.email` than they do now. They keep `AuthService` regardless — all
  four call it for `idToken`, and `ShoppingListDetailService` also reads `.email` directly for its
  role derivation.
- **`recipes_collection_list_service.dart`** additionally narrows its getter from `ValueNotifier` to
  `ValueListenable`, which `state-management.md` requires and which the retype touches anyway.
- **`shopping_list_detail_service.dart`** additionally skips pending rows when deriving
  `_currentUserRole`, and its doc comment stops naming the `/users` request.

### Modified — the four call sites

`recipe_sharing_dialog.dart`, `shopping_list_sharing_dialog.dart`, `meal_plan_sharing_dialog.dart`,
and the inline `SharingDialog` in `collection/recipes_collection_list_screen.dart` (`_showSharingDialog`):

- Pass `permissions:` and `currentUserEmail:` instead of `sharedUsers:`.
- The share success snackbar becomes `Invitation sent to $email`.
- A new `on ShareRefusedException` branch ahead of the existing `catch`, wording the two reasons.
- `onUnshare` is untouched — the dialog owns the wording difference, and both cases land on the same
  `unshare` call.

The screens that trigger the load (`recipe_detail_screen.dart:50`,
`shopping_list_detail_screen.dart:89`, `recipes_collection_list_screen.dart:126`,
`meal_plan_drawer.dart:231`) change only the method name.

### Modified — tests

- **`test/features/sharing/sharing_dialog_widget_test.dart`** (CREATE) — the dialog pumped directly
  with a plain `ValueNotifier` and callback spies; no mocks at any layer, since the dialog's inputs
  are values and callbacks.

### Modified — docs

- **`docs/mobile/modules/sharing/`** (CREATE) — `codebase_structure.md` and `ui.md` for the feature,
  plus its `docs/INDEX.md` entry, as T4 added for `invites`.
- **`docs/mobile/modules/core/ui.md`** and **`codebase_structure.md`** (MODIFY) — the "Generic
  Sharing Dialog" section and the `core/widgets/sharing_dialog.dart` line move out to the new module
  doc.
- **`docs/mobile/standards/architecture.md`** (MODIFY) — one sentence in **Feature-Based Directory
  Structure**: features are slices other features may consume, so a feature directory may hold the
  views and models several features share; `core/`/`shared/` stays for code with no feature identity.
- **`docs/mobile/modules/{recipe,planning,shopping_list}/`** (MODIFY) — the sharing paragraphs in
  `ui.md` describe pending entries and cancel by cross-referencing the sharing module rather than
  restating it, and the deleted `*_permission.dart` lines leave `codebase_structure.md`.

## Interfaces and method signatures

```dart
// features/sharing/resource_permission.dart
class ResourcePermission {
  final String email;
  final UserRole role;
  final bool pending;     // false = granted access, true = invite awaiting an answer

  const ResourcePermission({required this.email, required this.role, required this.pending});
  factory ResourcePermission.fromJson(Map<String, dynamic> json);
}

// features/sharing/share_refused_exception.dart
enum ShareRefusedReason {
  alreadyInvited, alreadyHasAccess;
  static ShareRefusedReason? fromApiString(String? apiString);   // null when unrecognised
}

class ShareRefusedException implements Exception {
  final ShareRefusedReason reason;
  final String email;
  const ShareRefusedException(this.reason, this.email);
}

// features/sharing/sharing_dialog.dart
class SharingDialog extends StatefulWidget {
  final String title;
  final ValueListenable<AsyncValue<List<ResourcePermission>>> permissions;
  final String currentUserEmail;
  final Future<void> Function(String email) onShare;
  final Future<void> Function(String email) onUnshare;   // granted removal *and* invite cancel
}

// each of the four repositories (shopping lists shown; the others differ only in path,
// noun and — for meal plans — named parameters)
class ShoppingListRepository {
  Future<List<ResourcePermission>> fetchPermissions(String shoppingListId, String? idToken);
  //   GET  /shopping-lists/{id}/permissions            → 200
  Future<void> shareShoppingList(String id, String email, String? idToken);
  //   POST /shopping-lists/{id}/share  {email, role}   → 204, 409 → ShareRefusedException
  Future<void> unshareShoppingList(String id, String email, String? idToken);
  //   POST /shopping-lists/{id}/unshare  {email}       → 204
}

// each of the four services
class ShoppingListDetailService {
  ValueListenable<AsyncValue<List<ResourcePermission>>> get permissions;
  String get currentUserEmail;
  Future<void> loadPermissions(String id);
}
```

## Data flow

**Opening the dialog.**

1. The screen calls `service.loadPermissions(id)` and shows the dialog (unchanged sequencing).
2. The service reads the token, calls `repository.fetchPermissions`, and publishes the
   `List<ResourcePermission>` through `AsyncValue.guardAsync` — no mapping step any more.
3. `SharingDialog`'s `ValueListenableBuilder` renders the list **in the order the backend sent it**
   (owner, granted editors, then pending invites by age — `permissions/api.md`); the dialog never
   sorts or partitions.
4. Each row: email as title; `role.displayName` as subtitle, or `Invited as {role.displayName}` when
   `pending`; a "Pending" chip beside the email when `pending`; and the remove/cancel icon for every
   row whose email is not `currentUserEmail`.

**Sharing.**

1. The form validates the email and calls `onShare(email)`.
2. The service posts `{"email": …, "role": "EDITOR"}`, then re-runs `loadPermissions` on success —
   so the new invite arrives as a pending row from the server rather than being inserted locally.
3. The call site shows `Invitation sent to $email`; a `ShareRefusedException` instead produces
   `$email already has a pending invitation` or `$email already has access`, and the list is not
   reloaded.

**Cancelling an invite / removing a user.** One path, one endpoint:

1. The icon is tapped; the dialog confirms — *"Cancel the invitation for $email?"* for a pending row,
   the existing *"Remove access for $email?"* for a granted one.
2. `onUnshare(email)` runs the feature's existing unshare call. The backend revokes a permission or
   cancels an invite, whichever exists (`permissions/api.md` > `unshare`).
3. The service reloads permissions (and its resource list, as today); the row disappears, and with it
   the invite on the other account's `/invites` screen.

## Pseudo-code

**Mapping the 409** — the only new branch in the repositories. The reason is a top-level property on
the `ProblemDetail` (`PermissionsExceptionHandler` sets it); an unrecognised or missing one falls
through to the generic exception rather than throwing out of the parse, so an unexpected refusal
still reaches the user as an error.

```
on share(id, email, token):
    response = post(.../share, body: {email, role: 'EDITOR'})
    if response.status == 204: return
    if response.status == 409:
        reason = ShareRefusedReason.fromApiString(decode(response.body)['reason'])
        if reason != null: throw ShareRefusedException(reason, email)
    if response.status == 404: throw Exception('<Resource> not found')
    throw Exception('Failed to share <resource>: ${response.status}')
```

**Recipes only** — `shareRecipe`/`unshareRecipe` wrap their whole body in a `try`/`catch` that
re-throws everything as `Network error while sharing recipe: $e`. That would swallow the typed
refusal, so the typed throws have to escape it:

```
try:
    ... as above ...
except ShareRefusedException:
    rethrow                      # not a network error
except e:
    throw Exception('Network error while sharing recipe: $e')
```

**Confirming a removal** — one handler, two vocabularies:

```
on tapRemove(permission):
    confirmed = await confirmDialog(
        title:  permission.pending ? 'Cancel Invitation' : 'Confirm Unshare',
        body:   permission.pending
                    ? 'Cancel the invitation for ${permission.email}? They will not be able to
                       accept it any more.'
                    : 'Remove access for ${permission.email}? They will no longer be able to view
                       or edit this item.',
        action: permission.pending ? 'Cancel Invitation' : 'Unshare')
    if not confirmed: return
    await widget.onUnshare(permission.email)     # errors surface at the call site, as today
```

**Deriving the current user's role** (`ShoppingListDetailService`, which gates "Delete List" on
OWNER) — a pending row can never be the caller's own, but the loop must not treat one as a role
answer if the backend ever changes what it returns:

```
for p in permissions:
    if p.pending: continue
    if p.email == authService.email:
        _currentUserRole.value = p.role
        break
```

## Decisions made

- **One `ResourcePermission` in `features/sharing/`, and `SharedUser` deleted** — the four
  `*Permission` models were byte-identical, and T1/T3 collapsed exactly these types on the backend.
  The dialog now renders the model the repositories return, against a `currentUserEmail` passed
  beside it, so the four services stop mapping one shape into another that differs only by a
  computed boolean.
- **`currentUserEmail` is a getter on each of the four services, not a view-side `AuthService`
  lookup** — settled with the user. Every one of the four already injects `AuthService` for
  `idToken` and already reads `.email` to compute `isCurrentUser` per row, so the getter removes no
  dependency and grants no new reach: it is four one-line pass-throughs replacing four mapping
  loops. The alternatives cost more and buy nothing. Having the views fetch it would put a
  `getIt<AuthService>()` dependency lookup in three widget bodies — something the screens never do
  today, where `getIt` appears only for `resetLazySingleton` in `dispose` — and threading
  `AuthService` from the route builders through `RecipeDetailScreen` and `ShoppingListDetailScreen`
  would touch two route entries, three constructors and those screens' widget tests, which build
  real services over mocked repositories and so get a service getter for free. Keeping it in the
  services also confines `AuthService` to the service layer, as it is everywhere but `main_screen`
  and `login_screen`.
- **`features/sharing/` rather than `lib/core/widgets/` or `lib/shared/`** — the user's call, and it
  mirrors T4: `invites` is the invitee half of this feature, `sharing` is the sharer half. Features
  are slices of the app that other features consume, so a feature directory holding the views and
  models four other features use is the ordinary case, not an exception. It carries no repository
  and no service because the four resource repositories already own the endpoints.
  `architecture.md` > Feature-Based Directory Structure gains a sentence saying features may depend
  on other features, since today it names only `core/` and `shared/` as homes for reusable code.
- **The per-feature wrapper dialogs stay in their features** — each depends on one feature's service
  and is opened by that feature's screen; moving them would point `sharing` at all four features.
- **One list with a "Pending" chip, not two sections** — settled with the user. The dialog stays one
  roster in the backend's order with a status marker, which keeps the empty-state and error branches
  single and the dialog short.
- **`onUnshare(String email)` is unchanged; only the confirmation copy branches** — settled with the
  user. Cancel and unshare are one endpoint by design (T1), so the widget varies its own wording and
  the four call sites keep the snackbar they have. The cancel-time snackbar therefore still reads
  "… unshared successfully"; that is an accepted, one-line-per-site fix for later.
- **409 becomes a typed `ShareRefusedException` with reason-specific copy** — settled with the user.
  With invites, "already invited" is an ordinary mistake rather than a programming error, and the
  raw `Exception: Failed to share recipe: 409` is unreadable. An unknown `reason` degrades to the
  existing generic exception.
- **The share success snackbar names the invitation** — settled with the user: `Invitation sent to
  $email`, matching the Pending row that appears under it, so the sharer learns the handshake exists
  without being told about it anywhere else.
- **A dialog-level widget test only** — settled with the user. `SharingDialog` takes a
  `ValueListenable` and callbacks, so it pumps directly with no mocking at any layer; a screen-level
  test would mostly be new harness. Cases: the three row kinds render (owner, editor, pending with
  its chip and `Invited as` subtitle); the current user's row has no remove icon; a pending row's
  confirmation says "cancel the invitation" and, on confirm, calls `onUnshare` with that email; a
  granted row's says "remove access"; a valid email calls `onShare` and an invalid one does not.
- **`sharedUsers` / `loadSharedUsers` renamed to `permissions` / `loadPermissions`** — the list is no
  longer only users, and the endpoint is now `/permissions`. Mechanical: four services, four
  repositories, four call sites, four load triggers.
- **The client always sends `EDITOR`** — per the HLD's anti-requirement; the dialog offers no role
  picker and `ShareRequest.role` of `OWNER` is a 400 anyway.
- **Pre-existing warts left alone:** an editor still sees a remove icon on the OWNER's row (the
  backend answers 403), and the dialog still has no "you cannot share with yourself" client check
  (the backend answers 409 `ALREADY_HAS_ACCESS`, which this task now words properly). Neither is in
  T5's scope.

## Assumptions to verify

- **Assumption:** T1–T3 are merged, so all four `GET /<resource>/{id}/permissions` endpoints exist,
  return `PermissionDto` with `pending`, and order the rows owner → granted editors → pending by age.
  **If wrong:** the dialog renders an unordered or `pending`-less list, and the dialog would have to
  partition the rows itself.
- **Assumption:** a 409 from `share` always carries a top-level `reason` of `ALREADY_INVITED` or
  `ALREADY_HAS_ACCESS`, and no other endpoint this task touches answers 409.
  **If wrong:** the refusal degrades to the generic exception — visible but unhelpful, not a crash.
- **Assumption:** recipes' `share` and `unshare` return 204 after T2, and the other three already do.
  **If wrong:** the recipe calls throw on success; the fix is one status check each.
- **Assumption:** the four `*Permission` models have no consumers outside their own repository and
  service (a grep found none), so deleting them is contained.
  **If wrong:** the deletion breaks compilation somewhere unplanned — cheap to find, since it is a
  compile error.
- **Assumption:** `AuthService.email` is populated by the time any sharing dialog opens — it is a
  non-nullable `String` fed by the auth flip, and the dialog is only reachable from an authenticated
  screen.
  **If wrong:** `currentUserEmail` is empty, no row matches, and the owner sees a remove icon on
  their own row (the backend refuses it with 403).
- **Assumption:** `MealPlanSharingService` is still constructed ad hoc in `meal_plan_drawer.dart`
  rather than registered in `get_it`, so its new getter needs no DI change.
  **If wrong:** `meal_plan_setup.dart` needs the same treatment as the other three.
- **Assumption:** a `Chip` inside a `ListTile` title row lays out acceptably at the dialog's width
  with a long email address.
  **If wrong:** the email gets `Flexible` + `TextOverflow.ellipsis`, or the marker moves to the
  subtitle line; nothing else changes.

## Required reading

- `docs/backend/modules/permissions/api.md` — `PermissionDto` with `pending`, `ShareRequest.role`,
  the `unshare`-cancels-an-invite rule, and the 409 `reason` contract this task renders.
- `docs/tasks/2026-08-26-share-invites/tasks.md` > T5 — the scope, and the three repository catch-up
  items (path rename, required `role`, recipes' 204) that are not optional polish.
- `plans/T4-task-design.md` — the invitee half of this feature: the `features/invites/` layout this
  one mirrors, and the model/exception conventions it established.
- `mobile/lib/core/widgets/sharing_dialog.dart` — the widget being moved and modified; its
  confirmation dialog and `ValueListenableBuilder` branches are the parts that change.
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — the fullest of the four
  services (role derivation, guard flags, share/unshare refresh), and the one with the extra
  `_currentUserRole` change.
- `mobile/lib/features/recipe/recipe_repository.dart` — the odd one out: `/shared_users`, 200 on
  share/unshare, and the blanket `catch` the typed refusal has to escape.
- `mobile/lib/features/invites/invites_repository.dart` — the `InviteGoneException` precedent for the
  new typed exception.
- `docs/mobile/standards/architecture.md` > Widget Inputs, `state-management.md`,
  `widget-testing.md`, `theming.md` — the layering, read-only notifier, test-boundary and styling
  rules this design follows.
- `HLD.md` > Feature areas > Sharer-facing surface (mobile) — the four behaviours this task delivers.
