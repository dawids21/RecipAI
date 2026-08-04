# Snackbar undo for destructive actions on the shopping list detail screen — Task Design

**Date:** 2026-08-03

## Summary

The item store's three destructive operations return the pre-state of the items they actually touched, captured inside
the existing per-list lock. `ShoppingListDetailService` holds the most recent capture in a single slot and returns a
sealed `UndoableAction` to the screen, which shows a 5-second snackbar and calls back on UNDO. Undo replays the inverse
through the store as an ordinary local mutation: deleted items are re-created as fresh rows, unchecked items are
re-checked. A restored item's `checked` state now survives the round trip because the backend's create-item endpoint
gains a `checked` field.

## Components and responsibilities

### Mobile

- **`UndoableAction`** (CREATE, `mobile/lib/features/shopping_list/undoable_action.dart`) — the captured inverse of one
  destructive action, as data. Sealed with one variant per action kind; exposes `itemCount` for the snackbar copy.
- **`ShoppingListItemStoreService`** (MODIFY,
  `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart`) — its three destructive operations change
  from `Future<void>` to returning what they affected, captured inside the lock they already hold. Gains two replay
  entry points: `applyRestore` (re-create deleted items) and `applyCheckedAll` (bulk re-check). Holds no undo state.
- **`ShoppingListDetailService`** (MODIFY, `mobile/lib/features/shopping_list/shopping_list_detail_service.dart`) —
  owns the single undo slot: fills it after a destructive action, replays and clears it on `undoLast()`, drops it on
  `dispose()`. Returns an `UndoableAction?` so the screen knows whether to show a snackbar and what to say. The slot is
  a plain field, not a notifier.
- **`ShoppingListDetailScreen`** (MODIFY, `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart`) — routes
  the three destructive entry points through one helper that shows the undo snackbar, and formats the copy. Hides the
  current snackbar on dispose so no dead affordance outlives the screen.
- **`ShoppingListItemRepository`** (MODIFY, `mobile/lib/features/shopping_list/shopping_list_item_repository.dart`) —
  `createItem`'s POST body gains `checked` from the outbox payload (which already carries it).

### Backend

- **`CreateShoppingListItemRequest`** (MODIFY,
  `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/dto/CreateShoppingListItemRequest.java`) — gains a nullable
  `Boolean checked`. Deliberately not `@NotNull`: existing clients omit it.
- **`ShoppingListItem`** (MODIFY, `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListItem.java`) — the
  package-private constructor gains a `Boolean checked` parameter, normalising null to `false`. It has exactly one
  caller.
- **`ShoppingListService`** (MODIFY,
  `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java`) — `createItem` passes
  `request.checked()` through.

### Documentation

- **`docs/backend/modules/shopping-lists/api.md`** (MODIFY) — the `POST /shopping-lists/{id}/items` request body and its
  note about nullable fields.
- **`docs/mobile/modules/shopping_list/ui.md`** (MODIFY) — the undo snackbar as a screen behaviour.

## Interfaces and method signatures

### `UndoableAction`

```dart
sealed class UndoableAction {
  int get itemCount;
}

/// Items removed by a delete; carries the full pre-state needed to re-create them.
final class DeletedItemsUndo extends UndoableAction {
  final List<LocalShoppingListItem> items;   // pre-mutation rows
}

/// Items switched from checked to unchecked; only the ids are needed, since
/// uncheckAll never touches an already-unchecked item.
final class UncheckedItemsUndo extends UndoableAction {
  final List<String> localIds;
}
```

### `ShoppingListItemStoreService`

Changed signatures — each returns what it actually affected, captured inside the existing locked section:

```dart
Future<LocalShoppingListItem?> applyDelete(String listId, String localId)   // was Future<void>
Future<List<LocalShoppingListItem>> deleteAllChecked(String listId)         // was Future<void>
Future<List<String>> uncheckAll(String listId)                             // was Future<void>
```

New replay entry points, each one locked section over the existing private cores:

```dart
Future<void> applyRestore(String listId, List<LocalShoppingListItem> snapshots)
Future<void> applyCheckedAll(String listId, List<String> localIds, bool checked)
```

New private core, alongside `_createItem` / `_deleteItem`:

```dart
Future<void> _restoreItem(String listId, LocalShoppingListItem snapshot)
```

`_deleteItem` changes to `Future<LocalShoppingListItem?>`, returning the row as read before the tombstone `copyWith`.
`_checkItem` is unchanged.

### `ShoppingListDetailService`

```dart
Future<UndoableAction?> deleteItem(String localId)      // was Future<void>
Future<UndoableAction?> deleteAllChecked()              // was Future<void>
Future<UndoableAction?> uncheckAll()                    // was Future<void>
Future<void> undoLast()
```

All three return `null` when nothing was affected, and otherwise both store the action in the slot and return it. The
returned object is the screen's read-only view of the slot's kind and count; the screen never holds it across frames.

### `ShoppingListItemRepository`

Unchanged signature; `createItem`'s JSON body gains `'checked': snapshot.checked`.

### Backend

```java
public record CreateShoppingListItemRequest(
        @NotBlank @Size(max = 255) String name,
        @PositiveOrZero BigDecimal quantity,
        @Size(max = 64) String unit,
        Boolean checked,
        @NotNull BigDecimal position) {}

ShoppingListItem(UUID shoppingListId, String name, BigDecimal quantity,
                 String unit, Boolean checked, BigDecimal position)
```

## Data flow

### Destructive action → snackbar

1. The screen calls the detail service through one helper (per-item `onDelete`, or the `delete_checked` /
   `uncheck_all` menu branches).
2. The service calls the store, which performs the mutation and returns the affected pre-state from inside its per-list
   lock — so the capture cannot observe a reconcile that landed halfway through.
3. The service wraps a non-empty result in the matching `UndoableAction`, overwrites the slot, and returns it. An empty
   result returns `null` and leaves the slot alone.
4. The screen, if still mounted and the result is non-null, hides the current snackbar and shows the undo snackbar for
   5 seconds with the count-based copy.
5. Letting the snackbar expire, or leaving the screen, commits: nothing further runs.

### UNDO tapped

1. `SnackBarAction` dismisses the snackbar and calls `service.undoLast()`.
2. The service takes the slot, clears it, and switches on the variant:
   - `DeletedItemsUndo` → `store.applyRestore` inserts a **fresh row per snapshot** (new `localId`, `serverId: null`,
     `lastAckedVersion: null`, `dirty: true`) carrying the captured name/quantity/unit/**position**/**checked**, each
     with a `create` outbox entry.
   - `UncheckedItemsUndo` → `store.applyCheckedAll(..., true)` re-checks each captured id through the existing
     `_checkItem` core, appending an `update` entry each.
3. The service kicks a drain. From here it is an ordinary local change: outbox, offline queueing, version gating and the
   existing rejection path all apply unchanged.
4. The tombstone left by the original delete is untouched — it pushes its own `delete` and hard-removes itself, or was
   already gone.

### Restored `checked` reaching the server

The `create` outbox payload already carries `checked`; the repository now sends it, the backend now stores it, and
`reconcileAck` does not overwrite fields. Local and server therefore agree immediately, so a later poll that adopts a
newer version cannot silently pull a restored item out of the Done section.

## Pseudo-code

### Capture inside the existing lock

```
_deleteItem(listId, localId):                # unchanged body, new return
    item = resident ? cache[listId][localId] : dao.readItem(localId)
    if item == null: return null
    ... tombstone + outbox as today ...
    return item                              # PRE-state, before copyWith

deleteAllChecked(listId):
    lock(listId):
        checked = items where !pendingDelete && checked
        removed = []
        for localId in checked:
            snapshot = _deleteItem(listId, localId)
            if snapshot != null: removed.add(snapshot)
        return removed

uncheckAll(listId):
    lock(listId):
        checked = ids of items where !pendingDelete && checked
        for id in checked: _checkItem(listId, id, false)
        return checked                       # ids only; prior state is implied
```

### Restore

```
applyRestore(listId, snapshots):
    lock(listId):
        for s in snapshots: _restoreItem(listId, s)

_restoreItem(listId, s):
    item = LocalShoppingListItem(
        localId: uuid.v4(),                  # fresh identity, never the old one
        serverId: null, lastAckedVersion: null,
        listId: listId,
        name/quantity/unit/checked/position: from s,
        dirty: true, failed: false, pendingDelete: false)
    if resident: cache[listId][item.localId] = item; bump notifier
    dao.writeItemAppendingOutbox(item, create,
        {name, quantity, unit, checked, position})
```

Position is re-used verbatim rather than recomputed — that is what "original position" means. A collision is resolved by
the existing `(position, localId)` ordering; the fresh `localId` only changes the tiebreak, not the neighbourhood.

### Slot handling

```
deleteItem(localId):
    removed = store.applyDelete(openListId, localId)
    requestDrain()
    if removed == null: return null          # already gone; no snackbar
    return _pendingUndo = DeletedItemsUndo([removed])

undoLast():
    action = _pendingUndo
    if action == null: return                # expired, replaced, or screen gone
    _pendingUndo = null                      # clear BEFORE replay: no double-undo
    switch action:
        DeletedItemsUndo(items)     -> store.applyRestore(openListId, items)
        UncheckedItemsUndo(localIds)-> store.applyCheckedAll(openListId, localIds, true)
    requestDrain()

dispose():
    _pendingUndo = null                      # undo dies with the screen
    ... existing teardown ...
```

### Screen

```
_runDestructive(action):
    undoable = await action()
    if !mounted || undoable == null: return
    messenger = ScaffoldMessenger.of(context)
    messenger.hideCurrentSnackBar()          # replace, don't queue
    messenger.showSnackBar(SnackBar(
        content: Text(_undoCopy(undoable)),
        duration: _undoWindow,               # 5 s
        action: SnackBarAction(label: 'UNDO', onPressed: service.undoLast)))

_undoCopy(a) = switch a:
    DeletedItemsUndo(n)      -> '$n item${n == 1 ? '' : 's'} deleted'
    UncheckedItemsUndo(n)    -> '$n item${n == 1 ? '' : 's'} unchecked'
```

`ScaffoldMessenger.of(context)` is resolved only after the `mounted` re-check, never across the `await`. A
`ScaffoldMessengerState` reference captured in `didChangeDependencies` is used in `dispose()` to hide the snackbar on
navigation away — `of(context)` is not safe there.

## Decisions made

- **Undo of a delete always re-creates a fresh item; the tombstone is left alone.** One path instead of two, no new DAO
  surface, and no interaction with a delete push that may be in flight while the store lock is released across the HTTP
  call. The accepted cost is a delete+create round trip even when the delete never left the device, and — if that delete
  comes back 412 — `cascadeDiscard` un-tombstones the original, briefly showing a duplicate. Requirements already accept
  that undoing a synced delete re-creates the item under a new server id.
- **A restored item gets a fresh `localId`.** Forced by the above: the old id may still be occupied by the tombstone,
  and reusing it would clobber that row through `ConflictAlgorithm.replace`, leaving the queued `delete` entry pointed at
  the restored item.
- **`UndoableAction` is a sealed class with one variant per kind.** Each variant carries exactly what its replay needs
  (full rows for deletes, ids only for unchecks), and `undoLast`'s switch is exhaustively checked. Matches `AsyncValue`
  and `SharePayload`.
- **The store's destructive methods change signature rather than gaining capture-only siblings.** Capture is only
  correct inside the lock the mutation already holds, so it belongs to the same call.
- **Bulk replay is one locked section calling the existing per-item cores.** Exactly the shape `deleteAllChecked` and
  `uncheckAll` already use; no new concurrency surface.
- **`hideCurrentSnackBar()` unconditionally before showing the undo snackbar.** `showSnackBar` queues rather than
  replaces, and `ScaffoldFeatureController.close()` asserts `_snackBars.first == controller` (`scaffold.dart:341`), so
  closing a tracked-but-queued controller is a debug-mode crash. The unconditional hide is always safe and guarantees the
  5-second window starts when the action happens, at the cost of truncating a rejection toast that happens to be visible.
- **The undo slot is cleared in `dispose()`.** The snackbar is owned by `ScaffoldMessenger` and can outlive the route, so
  hiding it is belt-and-braces; the cleared slot is what actually makes a late UNDO tap a no-op.
- **`checked` is added to the create endpoint rather than replayed as a follow-up update.** One round trip, client and
  server agree immediately, and the field is fixed for every future caller. Nullable on the DTO so existing clients that
  omit it keep creating unchecked items.
- **The snackbar duration lives as a private `const` in the screen file.** Per the theming standard's priority order it
  is a new constant; `AppAnimations` holds animation timings, which this is not.

## Assumptions to verify

- **Assumption:** `Boolean checked` on `CreateShoppingListItemRequest` is genuinely optional — no global Jackson or
  validation configuration rejects a body that omits it.
  **If wrong:** every existing create from an older client fails with 400; the field would need an explicit default or a
  separate request shape.
- **Assumption:** `new ShoppingListItem(...)` has exactly one caller (`ShoppingListService.createItem`), so widening the
  constructor is contained.
  **If wrong:** more call sites to update; verified by grep over `backend/src/main`, but tests are not covered by that
  grep.
- **Assumption:** a widget test can pump `ShoppingListDetailScreen` with a real store over an in-memory ffi database, the
  way `shopping_list_sync_service_test.dart` builds one, while still mocking only repositories per the widget-testing
  standard.
  **If wrong:** `setupShoppingList` needs a test seam for the `Database`/store beyond the existing `store:` parameter, or
  the snackbar assertions move to a narrower harness.
- **Assumption:** `SnackBarAction` fires `onPressed` at most once, so a double-tap cannot replay an undo twice.
  **If wrong:** harmless — the slot is cleared before replay, so the second call returns immediately.
- **Assumption:** nothing else in the app depends on the three detail-service methods returning `Future<void>`.
  **If wrong:** additional call sites to adjust; only the detail screen appears to call them.
- **Assumption:** the detail screen is the only surface that deletes items, so no other caller silently drops a capture.
  **If wrong:** `ShoppingListItemImportService` and the review widget would need review — they use `applyCreate`, which
  is untouched.

## Required reading for implementation planning

- `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart` — the private mutation cores and the
  `resident ? cache : dao` idiom every new core must follow.
- `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` — `writeItemAppendingOutbox`, the only write the
  restore path needs; no new DAO method is required.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — `_pushOne` and `reconcileAck`, to confirm a
  restored item's `create` entry pushes and acks like any other.
- `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — `_showRejectionToast` and the popup-menu
  `onSelected` branches, the surfaces the snackbar shares and hooks into.
- `docs/ADRs/0004-shopping-list-item-store-aggregate.md` — why capture must happen inside the store's per-list lock and
  why that lock must not span HTTP.
- `docs/mobile/standards/state-management.md` — the `dispose()` requirement and the read-only-exposure rule the slot
  deliberately sits outside of.
- `docs/mobile/standards/widget-testing.md` — repository-only mocking, the `GetIt.I.reset()` lifecycle, and the
  single-route `GoRouter` shape for the snackbar tests.
- `docs/backend/standards/integration-tests.md` — the Testcontainers + `RestClient` pattern for asserting the new
  `checked` field on create.
- `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart` — the in-memory ffi database + real store
  harness to mirror for store-level capture/replay tests.
- `HLD.md` > Feature areas — the behaviours each component owns, and the three open questions this design closes.
