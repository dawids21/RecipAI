# Snackbar undo for destructive actions on the shopping list detail screen — Implementation Plan

**Date:** 2026-08-03

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/mobile/standards/state-management.md` — the per-key `Lock` guard the store already uses, and the `dispose()`
  requirement the undo slot has to honour.
- `docs/mobile/standards/architecture.md` — repository / service / view responsibilities; the slot belongs to the
  service, the snackbar to the view.
- `docs/mobile/standards/theming.md` — the priority order that makes the 5-second window a new private `const` in the
  screen file rather than an `AppAnimations` entry.
- `docs/mobile/standards/logging.md` — `recipai.shopping_list.detail` already exists; the new undo log lines go there.
- `docs/backend/standards/java-patterns.md` — DTOs are records; the entity constructor stays package-private.
- `docs/backend/standards/integration-tests.md` — `@SpringBootTest(RANDOM_PORT)` + Testcontainers + `RestClient` +
  AssertJ, the shape `ShoppingListIntegrationTest` already uses.

**Design & ADRs**

- `task-design.md` > Interfaces and method signatures — the exact new/changed signatures on the store, the detail
  service and the backend records.
- `task-design.md` > Pseudo-code — capture-inside-the-lock, `_restoreItem`, slot handling, and the screen helper.
- `task-design.md` > Decisions made — fresh `localId`, tombstone left alone, unconditional `hideCurrentSnackBar()`;
  do not re-litigate these.
- `docs/ADRs/0004-shopping-list-item-store-aggregate.md` — why capture must happen inside the per-list lock and why
  that lock must never span an HTTP call.

**Code to mirror**

- `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart` — `_createItem` (the row it mints and the
  `create` outbox payload) is the template for `_restoreItem`; `deleteAllChecked` / `uncheckAll` are the template for
  `applyRestore` / `applyCheckedAll`; the `resident ? cache : dao` idiom is mandatory in every new core.
- `mobile/lib/features/extraction/share_payload.dart` — the repo's sealed-class shape: `sealed class` base with
  `const` constructor, plain subclasses with `final` fields and `const` constructors.
- `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — `_showRejectionToast` for the
  `ScaffoldMessenger` + `mounted` idiom, and the `PopupMenuButton.onSelected` branches the two bulk actions hang off.
- `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart` — the in-memory ffi database +
  `_TestShoppingListItemDatabaseFactory` + `FakeShoppingListItemRepository` + `_TestScheduler` harness, and the
  `dbItems` / `visibleItems` / `outboxCount` / `seedAcceptedItem` helpers to reuse for capture/replay tests.
- `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java` — the private
  `itemRequest(...)` / `createItem(...)` helpers (26 call sites go through `itemRequest`, so the widening is contained
  to one method plus one overload).

## File inventory

**Mobile — source**

- **CREATE** `mobile/lib/features/shopping_list/undoable_action.dart` — sealed `UndoableAction` with two variants.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart` — three destructive methods
  return their capture; `applyRestore` / `applyCheckedAll` / `_restoreItem` added.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — undo slot, three changed return
  types, `undoLast()`, slot cleared in `dispose()`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — `_runDestructive` helper, undo copy,
  `_undoWindow` const, cached `ScaffoldMessengerState`, hide-on-dispose.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — `createItem` POST body gains
  `'checked': snapshot.checked`.

**Mobile — tests**

- **MODIFY** `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart` — new group for capture/replay
  over the existing harness; adjust the `deleteAllChecked` assertions that now see a return value.

**Backend**

- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/dto/CreateShoppingListItemRequest.java` — nullable
  `Boolean checked` between `unit` and `position`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListItem.java` — constructor gains
  `Boolean checked`, normalising null to `false`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — `createItem` passes
  `request.checked()` through.
- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java` — `itemRequest`
  passes `null`; a 5-arg overload for the checked cases; two new tests.

No migration, no dependency, no configuration change: `shopping_list_items.checked` already exists with
`nullable = false` and a `false` default, and no new package is used.

## Step-by-step plan

1. **Backend: accept `checked` on create** — add `Boolean checked` to `CreateShoppingListItemRequest` (no `@NotNull` —
   existing clients omit it), widen the `ShoppingListItem` constructor to take it and normalise
   `this.checked = checked != null && checked`, and pass `request.checked()` from `ShoppingListService.createItem`.
   - Files: `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/dto/CreateShoppingListItemRequest.java`,
     `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListItem.java`,
     `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java`
   - Verify: `cd backend && ./mvnw -q compile` succeeds.

2. **Backend: tests for the new field** — update the private `itemRequest(String, BigDecimal, String, BigDecimal)`
   helper to pass `null` for `checked` (keeps all 26 existing call sites untouched), add an overload
   `itemRequest(String, BigDecimal, String, Boolean, BigDecimal)`, and add the two cases from the test plan.
   - Files: `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java`
   - Verify: `cd backend && ./mvnw test -Dtest=ShoppingListIntegrationTest` passes.

3. **Mobile: send `checked` on create** — add `'checked': snapshot.checked` to `createItem`'s JSON body. `OutboxPayload`
   already carries the field and `_createItem` already writes it into the payload, so nothing else changes.
   - Files: `mobile/lib/features/shopping_list/shopping_list_item_repository.dart`
   - Verify: `cd mobile && flutter analyze` clean;
     `flutter test test/features/shopping_list/shopping_list_sync_service_test.dart` still passes.

4. **Mobile: `UndoableAction`** — new file with the sealed base exposing `int get itemCount`, plus `DeletedItemsUndo`
   (`List<LocalShoppingListItem> items`) and `UncheckedItemsUndo` (`List<String> localIds`). Mirror
   `share_payload.dart`: `const` constructors, `final` fields, subclasses in the same file.
   - Files: `mobile/lib/features/shopping_list/undoable_action.dart`
   - Verify: `cd mobile && flutter analyze` clean (file compiles, nothing references it yet).

5. **Mobile: store capture** — change `_deleteItem` to `Future<LocalShoppingListItem?>` returning the row **as read,
   before the tombstone `copyWith`**; `applyDelete` forwards it; `deleteAllChecked` collects the non-null returns inside
   its existing locked section; `uncheckAll` returns the checked ids it collected. Signatures become
   `Future<LocalShoppingListItem?>`, `Future<List<LocalShoppingListItem>>`, `Future<List<String>>`.
   - Files: `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart`
   - Verify: `cd mobile && flutter analyze` clean;
     `flutter test test/features/shopping_list/shopping_list_sync_service_test.dart` passes unchanged.

6. **Mobile: store replay** — add `_restoreItem(listId, snapshot)` alongside `_createItem`, minting a fresh
   `localId: _uuid.v4()` with `serverId: null`, `lastAckedVersion: null`, `dirty: true`, `failed: false`,
   `pendingDelete: false`, carrying name/quantity/unit/checked/**position verbatim** from the snapshot, updating the
   cache + notifier only when resident, then `writeItemAppendingOutbox(item, OutboxKind.create, {...})` with the same
   five payload keys `_createItem` uses. Wrap it in `applyRestore(listId, snapshots)`, and add
   `applyCheckedAll(listId, localIds, checked)` looping `_checkItem` — both single locked sections, exactly the shape
   `deleteAllChecked` / `uncheckAll` already have.
   - Files: `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart`
   - Verify: `flutter test test/features/shopping_list/shopping_list_sync_service_test.dart -n "undo capture and replay"`
     passes with the new store-level group from step 7.

7. **Mobile: store-level tests** — add an `undo capture and replay` group to the existing sync-service test file,
   reusing `seedAcceptedItem` / `dbItems` / `visibleItems` / `outboxCount`. Cases in the test plan below.
   - Files: `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart`
   - Verify: `cd mobile && flutter test test/features/shopping_list/shopping_list_sync_service_test.dart` passes.

8. **Mobile: the undo slot** — add `UndoableAction? _pendingUndo` (a plain field, not a notifier — nothing observes it).
   `deleteItem` / `deleteAllChecked` / `uncheckAll` become `Future<UndoableAction?>`: call the store, kick the drain,
   return `null` when the capture is empty (leaving the slot alone), otherwise assign and return the matching variant.
   Log the *actual* affected count from the capture rather than the pre-computed `_items.value` count. Add
   `undoLast()` — read the slot, return early when null, **clear it before replaying**, switch exhaustively on the
   variant into `applyRestore` / `applyCheckedAll(..., true)`, then `_requestDrainForOpenList()`. Clear `_pendingUndo`
   at the top of `dispose()`.
   - Files: `mobile/lib/features/shopping_list/shopping_list_detail_service.dart`
   - Verify: `cd mobile && flutter analyze` — the only errors should be at the three now-`await`ed call sites in the
     screen, fixed in the next step.

9. **Mobile: the snackbar** — add `static const _undoWindow = Duration(seconds: 5);` and a
   `Future<void> _runDestructive(Future<UndoableAction?> Function() action)` helper that awaits the action, returns on
   `!mounted` or `null`, resolves `ScaffoldMessenger.of(context)` **after** the `mounted` re-check, calls
   `hideCurrentSnackBar()` unconditionally, then shows the snackbar with `_undoCopy(...)`, `duration: _undoWindow` and
   `SnackBarAction(label: 'UNDO', onPressed: service.undoLast)`. Route the two per-item `onDelete` closures and the
   `delete_checked` / `uncheck_all` menu branches through it. Cache `ScaffoldMessenger.of(context)` in
   `didChangeDependencies` and call `hideCurrentSnackBar()` on that reference in `dispose()` (`of(context)` is unsafe
   there), before the existing `resetLazySingleton`.
   - Files: `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart`
   - Verify: `cd mobile && flutter analyze` clean; run the app, delete an item, see `1 item deleted` with UNDO, tap it,
     the item returns to its original position.

## Test plan

**Unit tests**

_N/A — the mobile side has no pure-unit test layer; store and service behaviour is covered by the ffi-database tests
below, which is where the existing shopping-list tests live._

**Integration tests**

`ShoppingListIntegrationTest` (`@SpringBootTest` + Testcontainers + `RestClient`):

- `shouldCreateItemAsCheckedWhenCheckedIsTrue` — POST with `checked: true` returns 201 with `checked() == true`, and a
  subsequent GET of the list shows the item still checked.
- `shouldCreateItemAsUncheckedWhenCheckedIsOmitted` — POST with `checked` absent from the body returns 201 with
  `checked() == false` (this is the regression guard for every existing client).

**Flutter tests — store level** (`shopping_list_sync_service_test.dart`, group `undo capture and replay`)

- `applyDelete returns the pre-state of the deleted row` — name, quantity, unit, position and `checked` all match the
  row as it was before the tombstone.
- `applyDelete returns null for an unknown localId` — no capture, no outbox entry.
- `deleteAllChecked returns every checked item and skips unchecked ones` — three items, two checked; the returned list
  has exactly the two, and an already-tombstoned item is not returned twice.
- `deleteAllChecked returns an empty list when nothing is checked` — and appends no outbox entry.
- `uncheckAll returns the ids it actually flipped` — items already unchecked are absent from the result.
- `uncheckAll returns an empty list when nothing is checked`.
- `applyRestore re-creates each snapshot with a fresh localId and the original position and checked state` — the new
  row has `localId != snapshot.localId`, `serverId == null`, `lastAckedVersion == null`, `dirty == true`, and matching
  name/quantity/unit/position/checked.
- `a restored item pushes as a create carrying checked` — `sync.pushNextEntry` returns `PushResult.pushed`, the fake
  backend's item has `checked == true`, and the outbox drains empty.
- `applyRestore leaves the original tombstone untouched` — the tombstone's queued `delete` entry is still present after
  the restore.
- `applyCheckedAll(..., true) re-checks exactly the given ids` — one `update` entry per id, other items untouched.

**Flutter widget tests**

_N/A — deliberately out of scope for this task. The whole snackbar surface is therefore covered by manual verification
below; the store-level tests above stop at the service boundary._

**Manual verification**

The snackbar layer has no automated coverage, so every acceptance criterion in `requirements.md` has to be walked
through on a device or emulator:

- Delete one item — a snackbar reads `1 item deleted` with an UNDO action; tapping UNDO restores it with its original
  text, quantity, unit and position.
- Check two items, then "Delete All Checked" — the snackbar reads `2 items deleted`; UNDO restores both, still checked,
  in their original positions in the Done section.
- With items in both states, "Uncheck All" — the snackbar reads `N items unchecked` for the number actually flipped;
  UNDO re-checks exactly those, leaving already-unchecked items alone.
- "Delete All Checked" and "Uncheck All" with nothing checked — no snackbar appears at all.
- Add and edit an item while the snackbar is visible — it neither disappears nor breaks the undo.
- Two destructive actions in quick succession — the second snackbar replaces the first, and UNDO reverses only the
  second.
- Wait out the 5 seconds — the snackbar disappears and the action is no longer reversible.
- Navigate back while the snackbar is visible, then return to the list — the item stays deleted and no snackbar
  affordance survives.
- Offline undo: enable airplane mode, delete an item, tap UNDO — the item returns immediately and the sync indicator
  shows `offline`; restore connectivity and confirm it lands on the server as a create with the right checked state.
- Force a rejected restore (revoke editor access on a second device, then undo) — the failure surfaces through the
  existing rejection toast, with no undo-specific error UI.
- Shared list: undo a delete that has already synced and confirm the second device sees a new item (accepted behaviour,
  not a defect).

## Verification checklist

- [ ] `cd mobile && flutter analyze` reports no issues
- [ ] `cd mobile && dart format --set-exit-if-changed lib test` passes
- [ ] `cd mobile && flutter test` — all tests pass
- [ ] `cd backend && ./mvnw test` — all tests pass
- [ ] Every acceptance criterion in `requirements.md` is demonstrably met on a device or emulator
- [ ] A POST to `/shopping-lists/{id}/items` **without** `checked` still returns 201 with `checked: false` (old-client
      compatibility)
- [ ] The store's per-list lock is never held across an HTTP call (ADR-0004) — `applyRestore` and `applyCheckedAll`
      contain no repository calls
- [ ] `task-design.md` > Assumptions to verify are resolved or explicitly deferred (see below)
- [ ] No new compiler warnings on either side
- [ ] `INFO` logs on the happy path are clean and carry the real affected count

## Risks surfaced during planning

- **Risk:** deploy ordering — if the mobile build ships before the backend change, a restored checked item is created
  server-side as unchecked.
  **Why it matters:** local and server disagree; the local row reads `checked: true` with `lastAckedVersion: 0`, so the
  next poll cannot correct it (adopt is gated on `s.version > lastAckedVersion`, and `0 > 0` is false). The divergence
  only surfaces later, when another member edits the item and the poll finally adopts an unchecked server value —
  silently pulling the item out of the Done section long after the undo.
  **Mitigation:** ship the backend first (steps 1–2 are ordered first for exactly this reason) and confirm it is live
  before releasing the mobile build.

- **Risk:** `docs/backend/modules/shopping-lists/api.md` currently documents the create body as
  `{"name", "quantity", "unit", "position"}` with "`quantity` and `unit` are nullable" — that line becomes wrong the
  moment step 1 lands, and `docs/mobile/modules/shopping_list/ui.md` will not mention the snackbar.
  **Why it matters:** the API doc is the contract other clients read; a stale nullability note is worse than a missing
  field.
  **Mitigation:** doc updates are excluded from this task by explicit instruction, so both files ship stale. Hand them
  to the `docs-updating` step and make sure it is actually run before this reaches other clients.

- **Risk:** `ShoppingListDetailService.deleteAllChecked` / `uncheckAll` currently log a count derived from
  `_items.value` *before* the store runs.
  **Why it matters:** that count is a UI-side guess; after this change the store returns the authoritative affected set,
  and leaving the old log in place would report a different number from the snackbar the user sees.
  **Mitigation:** step 8 moves both log lines after the store call and sources the count from the capture.

- **Risk:** with widget tests excluded, nothing automated covers the snackbar — its copy, the zero-item silence, the
  replace-on-second-action behaviour, the 5-second expiry, or that an unrelated action does not dismiss it.
  **Why it matters:** seven of the nine acceptance criteria in `requirements.md` live entirely in that layer, so they
  are verifiable only by hand and can regress silently afterwards.
  **Mitigation:** walk the manual-verification list above before merging, and treat it as the standing regression script
  for this screen. If the screen surface is later reworked, add the widget tests then — the `store:`, `itemRepository:`
  and `scheduler:` seams on `setupShoppingList` already make the harness feasible without new production code.

- **Risk:** the design's `UndoableAction` sketch uses `final class ... extends UndoableAction` with an abstract
  `itemCount` getter, while the repo's only sealed precedent (`share_payload.dart`) uses plain `class ... extends`
  with `const` constructors and no abstract members.
  **Why it matters:** cosmetic, but two competing sealed-class shapes in one codebase is the sort of drift standards
  exist to prevent.
  **Mitigation:** follow `share_payload.dart` — `const` constructors and `final` fields — and keep `itemCount` as the
  one abstract getter the design needs for the snackbar copy.

**Assumptions resolved during planning** (no longer risks):

- `new ShoppingListItem(...)` has exactly one caller — confirmed by grep over `backend/src` (main *and* test):
  `ShoppingListService.java:67` only.
- Nothing but the detail screen calls the three detail-service methods, and nothing but the detail service calls the
  three store methods — confirmed by grep over `mobile/lib` and `mobile/test`; `ShoppingListItemImportService` and the
  review widget use `applyCreate`, which is untouched.
- No global Jackson or validation configuration rejects an omitted field — `application.yml` sets only
  `jackson.default-property-inclusion: non_null` (a serialisation setting), and there is no custom `ObjectMapper` bean
  in the shopping-lists path. Step 2's omitted-`checked` test is the standing guard.
