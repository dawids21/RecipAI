# Possible Implementations of Local-User Undo/Redo for Shopping-List Items

Options analysis for adding per-user undo/redo to shopping-list **items** in the
RecipAI mobile app. The general-practice survey lives in
[`how-others-implement-undo-redo.md`](how-others-implement-undo-redo.md); this
document does not repeat it — it maps those patterns onto this codebase and
enumerates the concrete implementation choices, with their costs.

## Summary

The existing item store is already most of an undo engine. Every local mutation
funnels through `ShoppingListItemStoreService.apply*`, runs inside a per-list
lock, and has the item's **pre-state in hand** at the moment it overwrites it —
which is exactly the information an inverse command needs and exactly the
information the outbox does *not* record. Because undo can be expressed as an
ordinary local mutation, it inherits the outbox, offline queueing, version
gating, 412-rollback and the rejection toast for free, so the expensive parts of
undo (sync, conflict policy) are already built and paid for. The genuine open
decisions are four: **where the stack lives** (store = app-scoped, detail
service = screen-scoped), **how deletes are resurrected** (the server hard-
deletes and the local row is dropped on ack), **how stale entries behave**, and
**how far past a single-step snackbar** the feature should go.

## Key findings

- **Origin isolation is free here.** Yjs-style `trackedOrigins` tagging is
  unnecessary: local mutations (`apply*`, `deleteAllChecked`, `uncheckAll`) and
  remote reconciles (`reconcileFromServer`, `reconcileAck`, `cascadeDiscard`,
  `discardItem`) are already **different methods** on the store. Recording
  history only in the former gives per-user isolation structurally.
- **The pre-state is available for free, but only inside the lock.** Every
  private core — `_editItem` (`shopping_list_item_store_service.dart:379`),
  `_checkItem` (`:406`), `_reorderItem` (`:422`), `_deleteItem` (`:442`) — reads
  the current item, builds `updated = item.copyWith(...)`, and discards `item`.
  Capturing it there is a one-line change; capturing it from outside the store
  reintroduces a read-then-write gap that ADR-0004 exists to close.
- **The outbox cannot be inverted.** `_snapshotPayload` (`:456`) stores the
  **post**-state, and entries are deleted on ack
  (`ShoppingListItemDao.writeItemDroppingEntry`). Deriving undo from the outbox
  would only ever cover unsynced changes.
- **Conflict policy already exists and is the "apply anyway, let the server
  arbitrate" strategy.** An undo pushed as a normal update carries
  `baseVersion: item.lastAckedVersion` read live at push time
  (`shopping_list_sync_service.dart:344-357`); if someone else changed the item
  first the server returns 412, `cascadeDiscard` rolls the item to the winner and
  the screen already shows *"X was changed elsewhere and rolled back"*
  (`shopping_list_detail_screen.dart:81`). No conflict predicates need
  inventing — only a decision whether that UX is acceptable for undo.
- **Undo works offline with no extra work**, because it is just another local
  mutation appended to the outbox. The cost is that both the original action and
  its inverse are pushed when connectivity returns (two round-trips, two version
  bumps).
- **Delete is the one case that leaks past the client.** The server hard-deletes
  (`DELETE /shopping-lists/{id}/items/{itemId}?baseVersion=`), and on ack
  `reconcileDeleteAck` removes the local row entirely — so undoing a synced
  delete is necessarily a *re-create* with a new `serverId`.
- **`localId` can be preserved across a delete/undo**, which is the non-obvious
  win: if restore re-inserts the row under its **original** `localId` with
  `serverId: null`, every other stack entry referencing that item stays valid and
  no id-remapping layer is needed.
- **Bulk actions already compute their affected set inside the lock**
  (`deleteAllChecked` `:103`, `uncheckAll` `:119`), so Hagoel's
  "decompose bulk actions, record what actually changed" requirement is a hook,
  not a rewrite.
- **`applyCreate` returns `Future<void>`** (`:61`) and mints the `localId`
  internally, so any stack living *outside* the store cannot undo a create
  without an API change.
- **The DB has no migration path yet** — `openDatabase(path, version: 1,
  onCreate: createSchema)` with no `onUpgrade`
  (`shopping_list_item_database_factory.dart:17`). Any persisted undo log means
  introducing versioned migrations as a prerequisite.

## Scope and lifetime: where the stack lives

This is the first fork, and it is decided by DI, not by preference:

| Host | Lifetime | Consequence |
| --- | --- | --- |
| `ShoppingListItemStoreService` | App singleton (`shopping_list_setup.dart:29`) | Stack survives leaving and re-entering the screen; naturally per-list (`Map<listId, _History>`); dies on app restart unless persisted. |
| `ShoppingListDetailService` | Lazy singleton, `resetLazySingleton` on screen dispose (`shopping_list_detail_screen.dart:72`) | Stack dies when the user navigates back — a hard "session = one visit to the list" scope, matching the survey's session-scope consensus without any extra code. |

The store also holds imports (`ShoppingListItemImportService.importItems`), which
mutate lists that are **not open**. If undo should ever cover an import, the
stack must live in the store; if it should not, the detail service is the natural
boundary.

## Candidate implementations

### A. Snackbar undo, no stack (the 5% version)

Show a `SnackBar` with an `UNDO` action after destructive actions only —
`deleteItem`, `deleteAllChecked`, `uncheckAll` — whose callback replays the
inverse through the existing store methods. `ScaffoldMessenger` is already wired
into the screen for rejection toasts.

- **Cost:** small; a payload capture per destructive action plus screen wiring.
- **Covers:** the "oops" moments that actually happen. Not edits, checks or
  reorders.
- **Constraint:** Material guidance puts snackbars at 4–10 seconds, so the undo
  window is short and a second destructive action dismisses the first snackbar
  (the classic "delete three items fast" failure).
- **Fit:** no schema change, no store API change beyond a delete payload, and it
  composes forward — it is a strict subset of B/C.

### B. Command stack in `ShoppingListDetailService`

The detail service pushes an `UndoEntry { undo, redo }` for each of its mutation
methods, closing over the pre-state it reads from `_items.value` before calling
the store. Undo invokes the existing public `apply*` methods.

- **Cost:** medium; entirely additive, no store changes except returning the new
  `localId` from `applyCreate` and adding a restore path for deletes.
- **Weakness:** the pre-state is read **outside** the store lock, so a poll's
  reconcile landing between the read and the mutation records a stale inverse.
  This is the same class of bug ADR-0004 was written to eliminate — reintroducing
  it in a new layer is the main argument against B.
- **Weakness:** bulk operations are opaque from the service (it only knows the
  count), so grouping requires the store to report the affected ids anyway.

### C. Command stack inside the store aggregate (per list)

The store keeps `Map<String, _UndoHistory>` alongside `_cache`, `_notifiers` and
`_locks`. Each private core appends an inverse **inside** the existing lock
acquisition; bulk methods append a single grouped entry covering the ids they
already collected. New public surface: `undo(listId)`, `redo(listId)`,
`ValueListenable<UndoAvailability> undoState(listId)` — consumed by the detail
service and rendered by the screen.

- **Strength:** capture is atomic with the mutation, so no stale-inverse window;
  grouping is free for bulk ops; remote/local isolation is structural; `localId`
  minting and restore are internal so no API leakage.
- **Strength:** consistent with ADR-0004's framing of the store as *the* single
  consistency boundary for items — undo is a local read-modify-write like any
  other.
- **Cost:** medium-high; the store grows a second responsibility, and `dispose()`
  plus the per-list maps need extending. Undo/redo must run through the same
  `_lockFor(listId).synchronized(...)` wrapper and must **not** be re-entrant
  into locked public methods (call the private cores, as the bulk methods
  already do).

### D. Persisted undo log (SQLite table)

C, plus an `undo_log` table mirroring `outbox` so history survives app restart.

- **Cost:** high — requires introducing `onUpgrade` migrations to a DB that has
  never had one, plus prune/cap logic and stale-entry validation on hydrate.
- **Value:** low against the surveyed consensus that undo history is discarded on
  restart and users do not expect otherwise. Worth deferring unless a product
  requirement says an undo must survive a crash.

### E. Whole-list memento (anti-option, documented to be ruled out)

Snapshot `_visibleItems(listId)` before each mutation and restore it wholesale.
Trivial to write, and wrong for this domain: shopping lists are shared
(`shopping_list_permission`), so restoring a snapshot re-asserts values for items
another user changed in the meantime, and would enqueue one outbox update per
item in the list. Every surveyed multi-user implementation rejects this.

### F. Inverting the outbox (trap, documented to be ruled out)

Superficially attractive — the queue of changes is right there — but entries hold
the post-state and are deleted on ack, so this can only undo changes that have
not yet synced. Cancelling a *queued* entry also races the drain, which holds the
sync lock across HTTP.

## The hard cases, specifically here

### Undoing a delete

Three sub-options, in ascending cost:

1. **Re-create through `applyCreate`** with the captured payload. Simplest;
   yields a **new `localId`** (uuid minted at `:352`) and a new `serverId`. Any
   other stack entry referencing the old `localId` becomes dangling, so this
   effectively forces "clear the stack on delete-undo".
2. **`applyRestore(listId, snapshot)`** — a new store method that re-inserts the
   row under its **original `localId`** with `serverId: null`,
   `lastAckedVersion: null`, `dirty: true`, and queues an `OutboxKind.create`.
   Same server cost, but local identity is preserved, so the rest of the stack
   survives. **This is the option worth taking**; the recommendation is an
   inference from the codebase, not a finding from the survey.
3. **Longer-lived tombstone** — stop hard-deleting the row on ack, add a
   `deleted` column, and make undo an un-tombstone. Largest change (schema
   migration, every read path must filter, `reconcileFromServer` semantics
   change) and it still cannot avoid a server-side re-create, since the API has
   no undelete (contrast Google AIP-164 soft delete).

Either way, other devices observe the restored item as a **new** item with a new
id. That is unavoidable without a backend change and should be an explicit
product decision.

### Undoing a create

`applyDelete` on the created item works today, including when the create is still
queued: the outbox is FIFO per list, so the create is acked (populating
`serverId`/`lastAckedVersion`) before the delete entry is pushed, which is what
keeps the non-null assertions in `_pushOne` (`:359-364`) safe. The user-visible
cost is a create→delete round trip.

### Stale entries

An entry can be invalidated by: a remote delete adopted through
`reconcileFromServer` (`:198-204`), a `discardItem` after a permanent 4xx, or a
`cascadeDiscard` after a 412. In all three the row is gone or overwritten, and
today the private cores **silently no-op** (`if (item == null) return;`), so a
stale undo would appear to do nothing. Options:

- **Skip and pop** until an applicable entry is found (Hagoel), optionally with a
  subtle notice.
- **Validate on push** — attempt it and let the existing 412 → `cascadeDiscard` →
  toast path explain the outcome. Zero new code, at the cost of a network
  round-trip to learn the answer.
- **Invalidate eagerly** — have the reconcile paths prune entries referencing
  removed `localId`s. Cheap in C (the store sees both sides), impossible in B
  without new store events.

### Grouping

- Bulk ops (`deleteAllChecked`, `uncheckAll`, `importItems`) → one grouped entry
  over the ids the method already collected.
- The ephemeral add-chain (Enter repeatedly, `_createEphemeralItemAfter`) → one
  entry per item is the natural and probably correct reading of the gesture.
- Inline edits are committed on submit/blur, not per keystroke, so no
  time-window coalescing (Yjs `captureTimeout`) is needed.

### Reorder

Undo restores the captured fractional `position`, not an index — which is
Collabs' recommended shape and survives concurrent inserts. Two caveats: the
restored position can collide with a newly-inserted neighbour (harmless;
`compareTo` breaks ties by `localId`), and a check/uncheck moves the item between
the active and Done sections, so a reorder-undo may look like nothing happened if
the item has since been checked.

## Where the UI would go

- **Snackbar with `UNDO`** for destructive actions (option A's surface, reusable
  by B/C).
- **App-bar undo/redo icon buttons** driven by a `ValueListenable` for enablement
  — the only surface that makes redo discoverable at all on mobile.
- **Popup-menu entries** — cheapest to add to the existing `PopupMenuButton`, but
  buries a high-frequency action two taps deep.

## Testing implications

- The store already has an in-memory ffi test seam
  (`ShoppingListItemDatabaseFactory.createSchema` is shared between production
  and the test subclass), so option C's history logic is unit-testable directly
  against a real DB, including the interleaving cases.
- `ShoppingListSyncService` is deterministically testable via the injected
  `Scheduler` and the awaitable `pushNextEntry` seam (ADR-0005), so
  "undo → push → 412 → rollback" is expressible as an exact ordering with no
  timer dependence.
- Screen-level behaviour (snackbar, button enablement) follows the widget-testing
  standard: real services over mocktail repositories, per ADR-0002.

## Open questions

- **Product scope:** single-step destructive undo (A) or a real stack (C)? The
  survey's finding that most mobile list apps ship only A is a real signal, but
  says nothing about this product's users.
- **Does undo need to cover check/uncheck?** It is by far the highest-frequency
  mutation, so it dominates stack contents and could crowd out the destructive
  entries the user actually wants back.
- **Is a re-created item with a new `serverId` acceptable** to other members of a
  shared list, or does undoing a synced delete need backend support?
- **Should the stack be cleared** when the user leaves the screen, when a poll
  brings a remote change to an item in the stack, or never?
- **Redo after a conflict** — clear, keep, or rewrite. The survey flags this as
  the least documented area anywhere; nothing in this codebase points to an
  answer either.
- **Whether the `failed` column** on `items`, currently always written `false`,
  was intended for a related purpose — worth confirming before adding new state
  flags next to it.

## Sources

### In-repository (primary)

- `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart` —
  the per-list lock, the `apply*`/reconcile split, the private cores holding the
  pre-state, bulk id collection, `_snapshotPayload`.
- `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` — outbox
  append/drop semantics, `OutboxKind`, `writeItemDroppingEntry`.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — FIFO
  drain, live `baseVersion` read at push time, 412 → `cascadeDiscard`, rejection
  events.
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` and
  `shopping_list_detail_screen.dart` — mutation entry points, existing
  `ScaffoldMessenger` usage, service lifetime via `resetLazySingleton`.
- `mobile/lib/features/shopping_list/shopping_list_item_database_factory.dart` —
  schema, and the absence of an `onUpgrade` migration path.
- `mobile/lib/features/shopping_list/shopping_list_setup.dart` — store is an app
  singleton, detail service is a screen-scoped lazy singleton.
- [ADR-0004](../../../ADRs/0004-shopping-list-item-store-aggregate.md) — the
  store as single consistency boundary; local-only critical section rule.
- [ADR-0005](../../../ADRs/0005-shopping-list-sync-test-seam.md) — the injected
  scheduler and single-entry push seam relied on by the test plan.
- `docs/backend/modules/shopping-lists/api.md` — hard delete, `baseVersion`
  gating, 412 returning the raw winning item.

### External

- [`how-others-implement-undo-redo.md`](how-others-implement-undo-redo.md) — the
  survey this analysis maps onto the codebase (Command over Memento, local-undo
  taxonomy, origin tagging, conflict strategies, grouping, bulk decomposition,
  delete resurrection).
- [undo — pub.dev](https://pub.dev/packages/undo) — `ChangeStack` / `Change`
  (old state + execute + undo), `Change.group()` batching, `SimpleStack`,
  `canUndo`/`canRedo`, zero dependencies, v1.6.0. The closest off-the-shelf
  option; it supplies the stack mechanics only, not the sync or conflict
  behaviour, which is where this codebase's work actually is.
- [Snackbars — Material Design](https://m2.material.io/design/components/snackbars.html)
  — the 4–10 second window and "display an Undo action to let users amend
  choices", bounding what option A can promise.
- [You Don't Know Undo/Redo — Isaac Hagoel](https://dev.to/isaachagoel/you-dont-know-undoredo-4hol)
  — skip-conflicting-entries strategy and the bulk-action decomposition rule
  applied to `deleteAllChecked`/`uncheckAll`.
- [AIP-164: Soft delete](https://google.aip.dev/164) — the server-side
  alternative to client-held delete payloads, referenced as the backend change
  that option 3 would need.
