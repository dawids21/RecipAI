# T5: Sharer-facing mobile surface — Implementation Plan

**Date:** 2026-08-30

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/mobile/standards/architecture.md` — the Repository→Service→View layering, **Widget Inputs**
  (`SharingDialog` renders and reports, so it takes a `ValueListenable`, a `String` and two
  callbacks — never a service), and **Feature-Based Directory Structure**, which this task amends.
- `docs/mobile/standards/state-management.md` — `ValueNotifier<AsyncValue<T>>` exposed **read-only**
  as `ValueListenable` (the one line `recipes_collection_list_service.dart` violates today and this
  task fixes), `AsyncValue.guardAsync()`, the `_isXxxRunning` guard, and the `dispose()` requirement.
- `docs/mobile/standards/widget-testing.md` — the directory layout (`test/` mirrors `lib/`) and
  `test/support/mocks.dart` being type declarations only. Note the deviation this task makes: the new
  test pumps a plain widget with **no** DI, no `setup*()` and no mocks — see **Test plan**.
- `docs/mobile/standards/theming.md` — `Theme.of(context)` first, then `AppSpacing`; relevant to the
  `Chip`'s density and the row's spacing.
- `docs/backend/modules/permissions/api.md` — `PermissionDto` (`email`, `role`, `pending`),
  `ShareRequest` with its required `role`, `unshare` cancelling an invite, and the 409 `Invite
  Refused` body carrying a top-level `reason` of `ALREADY_INVITED` / `ALREADY_HAS_ACCESS`.
- `docs/backend/modules/shopping-lists/api.md` > `GET /shopping-lists/{id}/permissions` — the row
  ordering contract (OWNER, then granted EDITORs, then pending by age) the dialog relies on and never
  re-sorts. `docs/backend/modules/recipes/api.md` carries the same three endpoints for recipes and
  collections, `docs/backend/modules/planning/api.md` for meal plans.
- `docs/project/local-development.md` — `./recipai.sh start-backend` and the `dev`-profile bearer rule
  (`Bearer alice` → `alice@local.test`), for the manual end-to-end.

**Design & ADRs**

- `plans/T5-task-design.md` — the whole document; in particular **Interfaces and method signatures**,
  **Pseudo-code** (the 409 mapping, recipes' rethrow escape, the branching confirmation, the pending-
  skipping role loop) and **Decisions made**. Every decision there is settled — do not re-open them.
- `plans/T4-task-design.md` and `mobile/lib/features/invites/` — the invitee half, and the feature
  layout and model/exception conventions `features/sharing/` mirrors.
- `tasks.md` > T5 — scope, out of scope, and "How to verify". The three repository catch-up items
  (path rename, required `role`, recipes' 204) are hard breakage, not polish.

**Code to mirror**

- `mobile/lib/core/widgets/sharing_dialog.dart` — the widget being moved and reworked; its
  `ValueListenableBuilder` branches, `_handleUnshare` confirmation and email form are what change.
- `mobile/lib/features/invites/invites_repository.dart` (tail) — `InviteGoneException`: the typed-
  exception shape and doc-comment style `ShareRefusedException` follows.
- `mobile/lib/shared/user_role.dart` — the `fromApiString` / `displayName` enum shape.
  `ShareRefusedReason.fromApiString` differs deliberately: it returns `null` rather than throwing.
- `mobile/lib/features/invites/invite.dart` — the minimal model + `fromJson` shape
  `ResourcePermission` copies.
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart:278-306` — the fullest of the
  four `loadSharedUsers` implementations (guard flag, `guardAsync`, role derivation).
- `mobile/lib/features/recipe/recipe_repository.dart:185-260` — the odd repository:
  `/shared_users`, `200` on share/unshare, and the blanket `catch` the typed refusal must escape.
- `mobile/lib/features/recipe/collection/recipes_collection_list_screen.dart:125-176` — the inline
  `SharingDialog` call site, the only one not wrapped in a per-feature dialog widget.
- `mobile/test/features/invites/invites_screen_widget_test.dart` — the nearest existing widget test.
  Copy its `expect` / `tester.tap` / `pumpAndSettle` idiom, **not** its `setUp` — the new test needs
  none of that harness.
- `docs/mobile/modules/invites/codebase_structure.md` and `ui.md` — the shape of the two module docs
  `docs/mobile/modules/sharing/` gets.

## File inventory

**Mobile — new `sharing` feature**

- **CREATE** `mobile/lib/features/sharing/resource_permission.dart` — `ResourcePermission` model (`email`, `role`, `pending`) with `fromJson`.
- **CREATE** `mobile/lib/features/sharing/share_refused_exception.dart` — `ShareRefusedReason` enum and `ShareRefusedException` with its body-parsing factory.
- **CREATE** `mobile/lib/features/sharing/sharing_dialog.dart` — the moved dialog, retyped onto `ResourcePermission` + `currentUserEmail`.
- **DELETE** `mobile/lib/core/widgets/sharing_dialog.dart` — moved; `lib/core/widgets/` holds nothing else and goes with it.

**Mobile — deleted models**

- **DELETE** `mobile/lib/features/recipe/recipe_permission.dart` — replaced by `ResourcePermission`.
- **DELETE** `mobile/lib/features/recipe/collection/recipes_collection_permission.dart` — replaced by `ResourcePermission`.
- **DELETE** `mobile/lib/features/shopping_list/shopping_list_permission.dart` — replaced by `ResourcePermission`.
- **DELETE** `mobile/lib/features/planning/meal_plan_permission.dart` — replaced by `ResourcePermission`.

**Mobile — repositories**

- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_repository.dart` — `fetchPermissions` on `/permissions`, `role` in the share body, 409 mapping.
- **MODIFY** `mobile/lib/features/recipe/recipe_repository.dart` — same, plus `/shared_users`→`/permissions` and 200→204 on share and unshare, plus the rethrow escape.
- **MODIFY** `mobile/lib/features/recipe/collection/recipes_collection_repository.dart` — `fetchPermissions` on `/permissions`, `role` in the share body, 409 mapping.
- **MODIFY** `mobile/lib/features/planning/meal_plan_repository.dart` — same, with its named-parameter signatures.

**Mobile — services**

- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — `permissions` notifier, `loadPermissions`, `currentUserEmail` getter, pending-skipping role loop, doc comment.
- **MODIFY** `mobile/lib/features/recipe/recipe_detail_service.dart` — `permissions` notifier, `loadPermissions`, `currentUserEmail` getter.
- **MODIFY** `mobile/lib/features/recipe/collection/recipes_collection_list_service.dart` — same, plus the getter narrowed to `ValueListenable`.
- **MODIFY** `mobile/lib/features/planning/meal_plan_sharing_service.dart` — same, no-arg `loadPermissions()`.

**Mobile — call sites and load triggers**

- **MODIFY** `mobile/lib/features/recipe/recipe_sharing_dialog.dart` — `permissions:`/`currentUserEmail:`, invitation snackbar, `on ShareRefusedException` branch.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_sharing_dialog.dart` — same three edits.
- **MODIFY** `mobile/lib/features/planning/meal_plan_sharing_dialog.dart` — same three edits.
- **MODIFY** `mobile/lib/features/recipe/collection/recipes_collection_list_screen.dart` — the inline dialog: same three edits, plus the `loadPermissions` trigger at `:126`.
- **MODIFY** `mobile/lib/features/recipe/recipe_detail_screen.dart` — `loadPermissions` at `:50`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — `loadPermissions` at `:89`, and the dartdoc reference at `:526`.
- **MODIFY** `mobile/lib/features/planning/meal_plan_drawer.dart` — `loadPermissions()` at `:231`.

**Mobile — tests**

- **CREATE** `mobile/test/features/sharing/sharing_dialog_widget_test.dart` — the dialog pumped directly with a plain `ValueNotifier` and callback spies.

**Docs** (T5 owns these per `tasks.md` > Cross-task notes)

- **CREATE** `docs/mobile/modules/sharing/codebase_structure.md` — the three-file tree plus the test tree.
- **CREATE** `docs/mobile/modules/sharing/ui.md` — the dialog, its row kinds, the confirmation copy, the refusal copy, and the four call sites.
- **MODIFY** `docs/INDEX.md` — a `#### Sharing (mobile/modules/sharing/)` entry under Mobile Module Documentation.
- **MODIFY** `docs/mobile/standards/architecture.md` — one sentence in **Feature-Based Directory Structure**.
- **MODIFY** `docs/project/architecture.md` — a `sharing` bullet in the mobile feature list.
- **MODIFY** `docs/mobile/modules/core/ui.md` — the "Generic Sharing Dialog" section moves out.
- **MODIFY** `docs/mobile/modules/core/codebase_structure.md` — the `widgets/` node goes; `test/features/sharing/` arrives.
- **MODIFY** `docs/mobile/modules/recipe/ui.md` — the two sharing paragraphs cross-reference the sharing module.
- **MODIFY** `docs/mobile/modules/planning/ui.md` — same; **MODIFY** `docs/mobile/modules/planning/codebase_structure.md` — drop the `meal_plan_permission.dart` line.
- **MODIFY** `docs/mobile/modules/shopping_list/ui.md` — the "Share List" menu action cross-references the sharing module; **MODIFY** `docs/mobile/modules/shopping_list/codebase_structure.md` — drop the `shopping_list_permission.dart` line.

## Step-by-step plan

### 1. Repository catch-up: paths, the required `role`, and recipes' 204

Purely mechanical, and the step that makes sharing work again against the migrated backend. No type
changes, so nothing above the repositories moves.

In all four repositories, the shared-users URL becomes `/permissions`:

- `shopping_list_repository.dart:165` — `/shopping-lists/$shoppingListId/users` → `.../permissions`
- `recipes_collection_repository.dart:139` — `/collections/$collectionId/users` → `.../permissions`
- `meal_plan_repository.dart:174` — `/meal-plans/$mealPlanId/users` → `.../permissions`
- `recipe_repository.dart:192` — `/recipes/$recipeId/shared_users` → `/recipes/$recipeId/permissions`

In all four `share` bodies, `json.encode({'email': email})` becomes
`json.encode({'email': email, 'role': 'EDITOR'})` (`jsonEncode` in `meal_plan_repository.dart`). The
`unshare` bodies are unchanged — `UnshareRequest` carries only the email.

In `recipe_repository.dart` only, `shareRecipe:226` and `unshareRecipe:251` change
`response.statusCode == 200` to `== 204`.

The four `*Permission.fromJson` factories read `email` and `role` and ignore the new `pending`
field, so the existing models still parse the new responses — that is what keeps this step green.
Until step 3, a pending invite therefore renders as if it were a granted user; that is expected
mid-branch, not a bug to work around.

- Files: `mobile/lib/features/shopping_list/shopping_list_repository.dart`,
  `mobile/lib/features/recipe/recipe_repository.dart`,
  `mobile/lib/features/recipe/collection/recipes_collection_repository.dart`,
  `mobile/lib/features/planning/meal_plan_repository.dart`
- Verify: `cd mobile && flutter analyze` clean; `cd mobile && flutter test` green. Against a running
  backend (`./recipai.sh start-backend`), `curl -sS -X POST -H "Authorization: Bearer alice" -H 'Content-Type: application/json' -d '{"email":"bob@local.test","role":"EDITOR"}' localhost:8080/shopping-lists/{id}/share -o /dev/null -w '%{http_code}\n'` prints `204`, and
  `curl -sS -H "Authorization: Bearer alice" localhost:8080/shopping-lists/{id}/permissions | jq` shows the pending row.

### 2. The typed 409 refusal

Create `mobile/lib/features/sharing/share_refused_exception.dart` holding both types from the design,
plus one factory that the four repositories share:

```dart
enum ShareRefusedReason {
  alreadyInvited,
  alreadyHasAccess;

  /// Null — not a throw — when the reason is missing or unrecognised, so an
  /// unexpected refusal degrades to the caller's generic exception.
  static ShareRefusedReason? fromApiString(String? apiString) { ... }
}

class ShareRefusedException implements Exception {
  final ShareRefusedReason reason;
  final String email;

  const ShareRefusedException(this.reason, this.email);

  /// Parses a 409 `ProblemDetail` body. Null when the body is not JSON or
  /// carries no recognised `reason`; the caller then throws its own generic
  /// exception. Never throws — a malformed body must not escape the parse.
  static ShareRefusedException? fromResponseBody(String body, String email) { ... }
}
```

`fromResponseBody` is an addition to the design's listed interface, and it is what makes the design's
"an unrecognised or missing one falls through to the generic exception rather than throwing out of
the parse" actually hold: `json.decode` throws on a non-JSON body, so the guard has to exist
somewhere, and one factory beats four `try`/`catch` blocks. Wrap the decode and the `['reason']`
read in a single `try`, return `null` on anything unexpected.

Each of the four `share` methods gains a 409 branch **before** its generic `else` (and beside the
existing 403/404 branches in the collection and meal-plan repositories):

```dart
} else if (response.statusCode == 409) {
  final refusal = ShareRefusedException.fromResponseBody(response.body, email);
  if (refusal != null) throw refusal;
  throw Exception('Failed to share shopping list: ${response.statusCode}');
}
```

`recipe_repository.dart` needs the rethrow escape from the design's pseudo-code — its `shareRecipe`
and `unshareRecipe` bodies sit inside a `try`/`catch (e)` that re-wraps everything as
`Exception('Network error while sharing recipe: $e')`, which would swallow the typed refusal:

```dart
} on ShareRefusedException {
  rethrow;
} catch (e) {
  throw Exception('Network error while sharing recipe: $e');
}
```

`unshareRecipe` never throws the typed exception, so only `shareRecipe` strictly needs the escape —
add it to `shareRecipe` only, and leave `unshareRecipe`'s catch alone. `fetchSharedUsers`' identical
blanket catch (which also re-wraps its own 404) is a pre-existing wart and stays out of scope.

Then handle it at the four call sites, ahead of each existing `catch (e)`:

```dart
} on ShareRefusedException catch (e) {
  _showSnackBar(switch (e.reason) {
    ShareRefusedReason.alreadyInvited => '${e.email} already has a pending invitation',
    ShareRefusedReason.alreadyHasAccess => '${e.email} already has access',
  });
  rethrow;
}
```

The `rethrow` matches the generic branch below it, which is what leaves the typed email in the form
field after a refusal. The collection screen's inline dialog uses `scaffoldMessenger.showSnackBar`
inside an `if (mounted)` instead of `_showSnackBar` — follow the shape already in that file.

- Files: `mobile/lib/features/sharing/share_refused_exception.dart`, the four repositories, and
  `mobile/lib/features/recipe/recipe_sharing_dialog.dart`,
  `mobile/lib/features/shopping_list/shopping_list_sharing_dialog.dart`,
  `mobile/lib/features/planning/meal_plan_sharing_dialog.dart`,
  `mobile/lib/features/recipe/collection/recipes_collection_list_screen.dart`
- Verify: `cd mobile && flutter analyze`; `cd mobile && flutter test`. Manually: share a list with an
  address twice from the running app and read the second snackbar
  (`… already has a pending invitation`), then share it with your own address
  (`… already has access`).

### 3. `ResourcePermission` and the dialog retype

The atomic step: the return type change cascades from repository to service to view, so it lands in
one commit.

**The model.** `mobile/lib/features/sharing/resource_permission.dart` — three final fields, a const
constructor and `fromJson` reading `email`, `role` (via `UserRole.fromApiString`) and
`pending` (`json['pending'] as bool`). No `toJson`; nothing serialises a permission — the share body
is written literally.

**Move the dialog.** `git mv mobile/lib/core/widgets/sharing_dialog.dart
mobile/lib/features/sharing/sharing_dialog.dart` so history follows it, then remove the now-empty
`mobile/lib/core/widgets/` directory. Fix the relative imports (`../../core/async_value.dart`,
`../../core/theme.dart`) and add `resource_permission.dart`.

**Rework the dialog.** Delete the `SharedUser` class. The two fields
`ValueListenable<AsyncValue<List<ResourcePermission>>> permissions` and `String currentUserEmail`
replace `sharedUsers`; `onShare` and `onUnshare` are unchanged. Rename
`_buildSharedUsersList` → `_buildPermissionsList(List<ResourcePermission> permissions)` and build
each row as:

- `title:` — a plain `Text(permission.email)` when granted; when pending, a `Row` of
  `Flexible(child: Text(permission.email, overflow: TextOverflow.ellipsis))`,
  `SizedBox(width: AppSpacing.small)` and a compact `Chip(label: Text('Pending'))`
  (`labelSmall`, `visualDensity: VisualDensity.compact`, `padding: EdgeInsets.zero`,
  `materialTapTargetSize: MaterialTapTargetSize.shrinkWrap`). Apply the `Flexible` + ellipsis from
  the start — it is the design's own documented fallback for a long address and costs nothing.
- `subtitle:` — `Text(permission.role.displayName)`, or
  `Text('Invited as ${permission.role.displayName}')` when pending. Note the role is now a `UserRole`,
  not the pre-rendered `String` `SharedUser` carried.
- `trailing:` — `null` when `permission.email == widget.currentUserEmail`, otherwise the existing
  `IconButton` with `Icons.remove_circle_outline` for both kinds; only the `tooltip` branches
  (`'Cancel invitation'` / `'Remove access'`). Do not invent a second icon — the design branches the
  wording, not the iconography.

Rows render in the order the notifier holds them. Do not sort, partition or group — the backend
already orders OWNER, then granted EDITORs, then pending by age. The `'Shared with'` header and the
`'Not shared with anyone yet'` empty state stay as they are.

`_handleUnshare` takes a `ResourcePermission` instead of a `String` and branches its confirmation per
the design's pseudo-code — title `'Cancel Invitation'` / `'Confirm Unshare'`, the two body strings,
action label `'Cancel Invitation'` / `'Unshare'` — then calls `widget.onUnshare(permission.email)`
unchanged. The `error`-coloured destructive `TextButton` and the `Navigator.of(context).pop(bool)`
pattern stay.

**The four repositories** return `Future<List<ResourcePermission>>` from a renamed `fetchPermissions`,
mapping with `ResourcePermission.fromJson(json as Map<String, dynamic>)`
(`meal_plan_repository.dart:180` currently omits the cast — add it). The failure messages become
`'Failed to load permissions: ${response.statusCode}'`.

**The four services**, each:

- `_sharedUsers` → `_permissions`, typed
  `ValueNotifier<AsyncValue<List<ResourcePermission>>>`; the getter becomes
  `ValueListenable<AsyncValue<List<ResourcePermission>>> get permissions`. In
  `recipes_collection_list_service.dart:29` this also narrows the declared type from `ValueNotifier`,
  which `state-management.md` requires.
- `loadSharedUsers` → `loadPermissions`, `_isLoadSharedUsersRunning` → `_isLoadPermissionsRunning`,
  and the `guardAsync` body returns the repository's list **as-is** — the `SharedUser` mapping goes.
- `String get currentUserEmail => _authService.email;` added beside the other getters.
- The `share` / `unshare` methods' refresh calls follow the rename.
- `dispose()` disposes `_permissions`.
- The `core/widgets/sharing_dialog.dart` import is replaced by `../sharing/resource_permission.dart`
  (`../../sharing/...` from `recipe/collection/`).

`shopping_list_detail_service.dart` additionally: the role-derivation loop gains
`if (permission.pending) continue;` before the email comparison, and the `_currentUserRole` doc
comment at `:42-45` stops naming the `/users` request and names `loadPermissions`.

**The four call sites** pass `permissions:` and `currentUserEmail:` instead of `sharedUsers:`, and
their share-success snackbar becomes `'Invitation sent to $email'` (the collection screen's
`'Collection shared with $email'` included). `onUnshare` and the unshare snackbars are untouched —
the cancel-time wording is a knowingly accepted wart per the design.

**The four load triggers** rename only: `recipe_detail_screen.dart:50`,
`shopping_list_detail_screen.dart:89`, `recipes_collection_list_screen.dart:126`,
`meal_plan_drawer.dart:231` — plus the dartdoc reference at `shopping_list_detail_screen.dart:526`,
which names `ShoppingListDetailService.loadSharedUsers` in a doc link.

**Delete** the four `*_permission.dart` models last; a clean `flutter analyze` is the proof nothing
else referenced them.

- Files: `mobile/lib/features/sharing/{resource_permission.dart,sharing_dialog.dart}`, the four
  repositories, the four services, the four call sites, the four screens/drawer, and the four deleted
  models
- Verify: `cd mobile && flutter analyze` clean;
  `cd mobile && dart format --output=none --set-exit-if-changed lib test` passes;
  `cd mobile && flutter test` green; `git status` shows `lib/core/widgets/` gone.

### 4. Dialog widget test

New `mobile/test/features/sharing/sharing_dialog_widget_test.dart`. Because `SharingDialog` takes
values and callbacks, the harness is three lines — no `GetIt`, no `SharedPreferences`, no `setup*()`,
no mocks:

```dart
Future<void> pumpDialog(
  WidgetTester tester, {
  required ValueListenable<AsyncValue<List<ResourcePermission>>> permissions,
  String currentUserEmail = 'owner@example.com',
  Future<void> Function(String)? onShare,
  Future<void> Function(String)? onUnshare,
}) => tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: SharingDialog(
          title: 'Share List',
          permissions: permissions,
          currentUserEmail: currentUserEmail,
          onShare: onShare ?? (_) async {},
          onUnshare: onUnshare ?? (_) async {},
        ),
      ),
    ));
```

The `MaterialApp` is what gives `_handleUnshare`'s `showDialog` a `Navigator`; the `Scaffold` hosts
the `AlertDialog` directly, so there is nothing to tap open first. Callback spies are plain
`final calls = <String>[];` closures — mocktail is not needed and would only obscure the assertions.
Cases are listed under **Test plan**.

- Files: `mobile/test/features/sharing/sharing_dialog_widget_test.dart`
- Verify: `cd mobile && flutter test test/features/sharing/sharing_dialog_widget_test.dart` passes,
  then `cd mobile && flutter test` for the whole suite.

### 5. Manual end-to-end

Start the backend (`./recipai.sh start-backend`). Create one resource of each of the four types as
`alice` if the dev DB is empty. Run the app as `alice`:
`cd mobile && flutter run --dart-define=DEV_AUTH_ENABLED=true`, signing in with the name `alice`.
Walk `tasks.md` > T5 > "How to verify" for all four resource types, then repeat as `bob` to confirm
the invitee side (T4's `/invites` screen) reflects each cancel and accept.

- Files: none
- Verify: the walk-through in the Verification checklist completes for all four resource types.

### 6. Docs

Write `docs/mobile/modules/sharing/codebase_structure.md` (three `lib` files plus the
`test/features/sharing/` tree, in the style of `docs/mobile/modules/invites/codebase_structure.md`)
and `docs/mobile/modules/sharing/ui.md` (the dialog, its three row kinds and the "Pending" marker,
the branching confirmation, the refusal copy, the invitation snackbar, and the four call sites that
open it). Move the "Generic Sharing Dialog" section out of `docs/mobile/modules/core/ui.md` and the
`widgets/` node out of `docs/mobile/modules/core/codebase_structure.md`, adding
`test/features/sharing/` to that file's test tree. Add the `Sharing` entry to `docs/INDEX.md` and the
`sharing` bullet to `docs/project/architecture.md`'s mobile feature list. Add the one sentence to
`docs/mobile/standards/architecture.md` > Feature-Based Directory Structure: a feature directory may
hold views and models other features consume; `core/` and `shared/` remain for code with no feature
identity. In `docs/mobile/modules/{recipe,planning}/ui.md` and
`docs/mobile/modules/shopping_list/ui.md`, have the sharing paragraphs cross-reference
`docs/mobile/modules/sharing/ui.md` rather than restate the row behaviour, and drop the two
`*_permission.dart` lines from `planning/` and `shopping_list/`'s `codebase_structure.md`.

Present tense, current behaviour only — no `docs/tasks/` references, no "was X before".

- Files: `docs/mobile/modules/sharing/{codebase_structure.md,ui.md}`, `docs/INDEX.md`,
  `docs/mobile/standards/architecture.md`, `docs/project/architecture.md`,
  `docs/mobile/modules/core/{ui.md,codebase_structure.md}`,
  `docs/mobile/modules/{recipe,planning,shopping_list}/ui.md`,
  `docs/mobile/modules/{planning,shopping_list}/codebase_structure.md`
- Verify: `grep -rn "docs/tasks" docs/mobile/` is empty;
  `grep -rn "SharedUser\|core/widgets\|_permission.dart" docs/` returns nothing;
  every file named in `docs/mobile/modules/sharing/codebase_structure.md` exists on disk.

## Test plan

**Unit tests**

_N/A — this app has no service-level unit tests; behaviour is covered by widget tests running real
services over mocked repositories (`docs/mobile/standards/widget-testing.md`)._

**Flutter widget tests**

`test/features/sharing/sharing_dialog_widget_test.dart` (new), all driven by a plain
`ValueNotifier<AsyncValue<List<ResourcePermission>>>`:

- renders a granted OWNER row: the email, the `Owner` subtitle, and no `Pending` chip
- renders a granted EDITOR row: the email and the `Editor` subtitle
- renders a pending row: the email, a `Pending` chip, and the `Invited as Editor` subtitle
- renders the rows in the notifier's order and does not re-sort or group them (assert the `ListTile`
  order for an owner / editor / pending list)
- shows no remove icon on the row whose email equals `currentUserEmail`
- shows the remove icon on every other row, pending included
- `Remove access` is the tooltip on a granted row and `Cancel invitation` on a pending one
- tapping the icon on a pending row shows a confirmation naming `Cancel Invitation` and the email
- confirming that dialog calls `onUnshare` with that row's email
- tapping the icon on a granted row shows the `Confirm Unshare` / `Remove access for …` copy, and
  confirming calls `onUnshare` with that email
- dismissing the confirmation with `Cancel` does not call `onUnshare`
- a valid email plus `Share` calls `onShare` once with the trimmed email, and clears the field
- an invalid email shows `Please enter a valid email address` and does not call `onShare`
- an empty field shows `Please enter an email address` and does not call `onShare`
- the `Share` button is disabled and shows its spinner while `onShare` is in flight (hold the callback
  open with a `Completer`)
- `AsyncValue.loading()` renders a `CircularProgressIndicator`
- `AsyncValue.error(...)` renders the `Error: …` text
- `AsyncValue.data(<ResourcePermission>[])` renders `Not shared with anyone yet`

**Integration tests**

_N/A — the backend contract this task consumes landed with T1–T3; no backend code changes here._

**Manual verification**

- A pending row with a long email address lays out without overflow at dialog width, on a real device
  — the one design assumption only looking can settle.
- Each of the four dialogs opens, lists granted users and pending invites in the backend's order, and
  cancels a pending invite; the row disappears from the dialog and the invite disappears from the
  other account's `/invites` screen.
- Accepting from the other account and reopening the dialog shows the row change from pending to a
  granted `Editor`.
- Sharing a shopping list that the caller already owns surfaces `… already has access`, and sharing
  twice surfaces `… already has a pending invitation`.
- The shopping-list detail screen's owner-only "Delete List" item still appears for the owner after
  the role loop change (i.e. the pending-skipping `continue` did not break role derivation).

## Verification checklist

- [ ] `cd mobile && flutter analyze` reports no issues
- [ ] `cd mobile && dart format --output=none --set-exit-if-changed lib test` passes
- [ ] `cd mobile && flutter test` — whole suite green
- [ ] `grep -rn "SharedUser\|fetchSharedUsers\|loadSharedUsers\|shared_users" mobile/lib mobile/test` returns nothing
- [ ] `mobile/lib/core/widgets/` no longer exists
- [ ] `tasks.md` > T5 "How to verify" succeeds for **all four** resource types: share with a second
      address, reopen the dialog and see the entry marked pending, cancel it and watch it disappear
      from the dialog and from the other account's invites list; then share again, accept from the
      other account, and see the entry change from pending to a granted user
- [ ] Sharing works at all — the pre-T5 400 from the missing `role` and the recipe 200/204 mismatch
      are both gone (steps 1 and 5)
- [ ] Every "Assumptions to verify" in `T5-task-design.md` is resolved or explicitly deferred (six of
      the seven are settled by this plan's investigation — see Risks below)
- [ ] No new analyzer warnings, and no `TODO`s left in `mobile/lib/features/sharing/`
- [ ] Docs state current behaviour in the present tense and reference no `docs/tasks/` path

## Risks surfaced during planning

- **Risk:** Six of the task design's seven assumptions are already settled by this plan's reading of
  the code and are not open work.
  **Why it matters:** the implementer should not spend time re-verifying them.
  **Mitigation:** treat as settled — (1) T1–T3 are merged on this branch (`d22088f`, `2bdab28`,
  `35eb449`) and all four `GET /<resource>/{id}/permissions` endpoints are documented with `pending`;
  (2) the ordering contract holds in code —
  `PermissionsApplicationService.getPermissions` concatenates
  `PermissionService.listGranted` (`findBy…OrderByRoleDesc`) ahead of `InviteService.listPending`
  (`findBy…OrderByCreatedAtAsc`); (3) the 409 `reason` is a top-level `ProblemDetail` property set at
  `PermissionsExceptionHandler.java:33`, and no other status this task touches is 409; (4) recipes'
  share and unshare are documented as `204 No Content` in `docs/backend/modules/recipes/api.md`;
  (5) the four `*Permission` models have no consumers outside their own repository and service
  (`grep -rn "Permission" mobile/lib` confirms); (6) `MealPlanSharingService` is still constructed ad
  hoc in `meal_plan_drawer.dart:224-229` and is not in `get_it`, so the new getter needs no DI change.
  Only the `Chip`-in-`ListTile` layout assumption needs eyes on a device (step 5).

- **Risk:** `ShareRefusedReason.fromApiString` alone cannot deliver the design's "does not throw out
  of the parse" guarantee — `json.decode` throws on a non-JSON 409 body, which is exactly what a proxy
  or gateway would return.
  **Why it matters:** an unguarded decode turns an unexpected refusal into an opaque `FormatException`
  instead of the generic share failure the design wants.
  **Mitigation:** the `ShareRefusedException.fromResponseBody` factory in step 2. It is one addition
  to the design's listed interface, kept in the same file, and it is why the four repositories each
  need only two lines.

- **Risk:** `recipe_repository.dart`'s `fetchSharedUsers` wraps its whole body — including its own
  `Exception('Recipe not found')` — in a blanket `catch` that reports everything as
  `Network error while fetching shared users`. The implementer will be looking directly at it while
  adding the rethrow escape to `shareRecipe`.
  **Why it matters:** it is tempting to "fix on the way past", which widens the diff into behaviour
  T5 did not plan to change.
  **Mitigation:** leave it. The design scopes the escape to `shareRecipe` only. If it is worth fixing,
  it is worth its own change.

- **Risk:** The task design says the deleted `*_permission.dart` lines leave
  `docs/mobile/modules/{recipe,planning,shopping_list}/codebase_structure.md`, but only `planning`
  and `shopping_list` list them — `recipe`'s tree elides them behind a `# Other screens, models, and
  widgets` catch-all line. Likewise `shopping_list/ui.md` has no sharing paragraph to rewrite, only a
  `"Share List"` popup-menu action at `:17`.
  **Why it matters:** an implementer following the design literally will hunt for lines that are not
  there, or invent doc structure to hold the cross-reference.
  **Mitigation:** two `codebase_structure.md` lines to delete, not four; the cross-reference lands as
  a clause on the existing sharing paragraphs in `recipe/ui.md` and `planning/ui.md`, and as a clause
  on the `"Share List"` menu action in `shopping_list/ui.md`.

- **Risk:** After step 1 and before step 3, pending invites render as ordinary granted users — the
  old `*Permission.fromJson` factories silently drop `pending`.
  **Why it matters:** it looks like a regression if the branch is demoed mid-sequence.
  **Mitigation:** none needed; it is contained within the branch and closes in step 3. Do not ship a
  partial T5 — `tasks.md` > Cross-task notes already commits the whole set to one release.

- **Risk:** `recipes_collection_list_service.loadSharedUsers` (`:82-104`) never pre-sets
  `AsyncValue.loading()`, unlike the other three services, so reopening a collection's dialog shows
  the previous collection's rows until the fetch resolves.
  **Why it matters:** with pending rows in the list this stale render becomes more misleading, and the
  retype touches these exact lines.
  **Mitigation:** pre-existing and out of T5's scope. Note it, leave it, and raise it separately if it
  is worth a one-line fix.
