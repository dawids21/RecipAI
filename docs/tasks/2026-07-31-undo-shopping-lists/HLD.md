# Snackbar undo for destructive actions on the shopping list detail screen — High-level design

**Date:** 2026-08-03
**ADRs:** None

## Summary

Add a 5-second snackbar with an UNDO action after the three destructive actions on the shopping list detail screen
(delete item, Delete All Checked, Uncheck All), as specified in [`requirements.md`](requirements.md). The store captures
what each destructive action actually touched, the detail service holds that snapshot for the life of the screen, and
undo replays the inverse as ordinary local mutations.

## Approach

### Chosen

**Capture-and-replay through the item store.**

Each of the three destructive operations on `ShoppingListItemStoreService` reports back a snapshot of the items it
actually affected. That snapshot is taken inside the existing per-list lock, at the point where the private mutation
cores already read the pre-state and currently discard it — so capture is atomic with the mutation and cannot observe a
reconcile that landed halfway through. The store itself holds no undo state.

`ShoppingListDetailService` keeps the most recent snapshot in a single slot. The detail screen shows the snackbar when
a destructive call reports affected items, and calls back into the service when the user taps UNDO. The service replays
the inverse through the same store surface every other mutation uses: restoring deleted items, or returning unchecked
items to their prior checked state. Undo is therefore an ordinary local change and inherits the outbox, offline
queueing, version gating and the existing rejection-toast path without adding a parallel route to the server.

Three properties fall out of the structure rather than needing enforcement:

- **Undo dies with the screen.** The detail service is a lazy singleton the screen resets on dispose, which is exactly
  the lifetime the requirements ask for.
- **Only the most recent action is undoable.** A single slot is overwritten by the next destructive action, and
  `ScaffoldMessenger` already replaces the visible snackbar.
- **The undo window needs no timer of its own.** The snackbar's own lifetime is the window; once it is gone there is no
  affordance that can reach the slot, so the slot going stale is harmless.

**What this gives up.** Fidelity for shared lists. A delete is usually pushed and acknowledged well within the undo
window, so undoing it re-creates the item rather than resurrecting it: other members observe a new item with a new
server id, and the round trip is paid twice. The requirements accept this explicitly.

### Rejected alternatives

- **Defer the action until the snackbar times out** — removes the re-create entirely, but only by having the screen mask
  the store's item list for five seconds. That makes the view a second source of truth and reintroduces the
  read-then-write divergence [ADR-0004](../../ADRs/0004-shopping-list-item-store-aggregate.md) exists to close.
- **Defer only the push** — keeps the store authoritative and still avoids the re-create, but lands in
  `ShoppingListSyncService`: it adds a timing dimension to the exact push ordering
  [ADR-0005](../../ADRs/0005-shopping-list-sync-test-seam.md) was written to pin down, and stalls the per-list FIFO
  queue behind the held entry. Too much delicate surface for a snackbar.
- **Backend soft delete with an undelete call** — the only option where undoing a delete is correct rather than
  approximated for other members, but it needs a schema migration, changes every server read path and the client's
  remote-deletion detection, and still covers only two of the three actions.
- **Whole-list snapshot restored wholesale** — trivial to write and uniform across all three actions, but it re-asserts
  values for items another member changed during the window, and would enqueue an update for every item in the list.

## Feature areas

### Item store — capture and restore

**Key behaviors.**

- The three destructive operations report the pre-state of the items they affected, captured within the same locked
  section that performs the mutation.
- Bulk operations report every item they touched, not a count — so undo acts on the actual affected set rather than
  re-deriving it, and an item that was already unchecked is never touched by an Uncheck All undo.
- A restore path re-establishes a deleted item with its original text, quantity, unit, position and checked state,
  queued for sync like any other local change.
- Restore is a local mutation like the rest: it participates in the per-list lock, the outbox and version gating, and
  introduces no new consistency boundary.

### Detail service — the undo slot

**Key behaviors.**

- Holds at most one undoable action at a time; a new destructive action replaces whatever was held.
- Reports to the caller whether an action produced anything undoable and how many items it affected, so the screen can
  decide whether to show a snackbar and what to say.
- Performing undo replays the captured inverse and clears the slot.
- Actions unrelated to the pending undo — adding, editing, checking items, renaming the list — leave the slot untouched.
- The slot is not observable state; nothing in the UI reacts to it changing.

### Detail screen — the snackbar surface

**Key behaviors.**

- After a destructive action that affected at least one item, shows a snackbar naming the number of affected items and
  offering UNDO, for the specified window.
- Shows nothing at all when a bulk action affected zero items.
- Tapping UNDO asks the service to replay the inverse; letting the snackbar expire or leaving the screen commits the
  action.
- A rejected restore surfaces through the existing rejection-toast subscription, with no undo-specific error UI.

### Testing

**Key behaviors.**

- Service-level tests assert on the captured snapshot directly — that the right items were captured, that replay
  restores position and checked state, and that a second destructive action displaces the first.
- Widget-level tests cover the observable contract: snackbar presence, copy and count, its absence for zero-item bulk
  actions, restoration into the correct section, and that unrelated actions during the window neither dismiss the
  snackbar nor break the undo.

## Out of scope

Beyond the anti-requirements already listed in `requirements.md`:

- **Holding back the outbox push during the undo window**, and any other change to `ShoppingListSyncService`. Undo uses
  the sync path exactly as it stands.
- **Backend support for restoring a deleted item under its original id.** Undo remains client-only.
- **Undo for item imports.** The import path mutates lists that are not open, so covering it would require the undo
  state to live in the app-scoped store rather than the screen-scoped service.
- **Reactive undo availability.** No `ValueListenable` for whether undo is possible, since no UI surface other than the
  snackbar needs to know.

## Open questions

- Whether a restored item re-uses its original local identity or is given a fresh one. With no undo stack there are no
  other entries whose references could dangle, so the argument is now about avoiding a duplicate row rather than about
  keeping history valid.
- A delete may or may not have been acknowledged by the server when undo runs — the row may still be present locally as
  a tombstone, or may be gone entirely. One observable behavior, but potentially two restore paths; task-design should
  decide whether to unify them or handle them separately.
- The shape of the captured snapshot: one form covering both deletes and unchecks, or a split by action kind. Settled
  that it is data rather than a closure; the rest is a task-design detail.
