# Snackbar undo for destructive actions on the shopping list detail screen

**Date:** 2026-07-31
**Type:** feature
**Status:** requirements

## Summary

Show a snackbar with an UNDO action after destructive actions on the shopping list detail screen, so a user who
mis-taps can recover what they lost without having to remember what it was.

## Context

A user accidentally deleted an item from a shopping list and could not remember what it had been. The item was gone
with no recovery path. This is the motivating incident: the value is not only in reversing the action but in doing so
without requiring the user to reconstruct the deleted content from memory.

Prior research for this task explored the full design space — see
[`research/possible-implementations.md`](research/possible-implementations.md) and
[`research/how-others-implement-undo-redo.md`](research/how-others-implement-undo-redo.md). This document scopes the
smallest useful slice of that space: single-step, snackbar-only undo for destructive actions.

## Requirements

Three actions on the shopping list detail screen show an undo snackbar:

- **Delete item** (per-item delete)
- **Delete All Checked** (popup menu)
- **Uncheck All** (popup menu)

Behaviour:

- The snackbar states the number of affected items. Copy:
  - deletes — `1 item deleted` / `3 items deleted`
  - unchecks — `4 items unchecked`
- The snackbar carries an `UNDO` action and is visible for **5 seconds**, after which it disappears and the action is
  committed.
- Other actions taken on the screen while the snackbar is visible (adding, editing, checking items, renaming the list)
  do **not** dismiss it and do **not** invalidate the pending undo.
- Undo restores items to their **original position**, not to the end of the list or section.
- Undoing **Delete All Checked** restores the items in their checked state, back into the Done section.
- Undoing **Uncheck All** restores each affected item's prior checked state; items that were already unchecked are
  untouched.
- Performing a second destructive action replaces the first snackbar. Only the most recent destructive action is
  undoable.

## Anti-requirements

Explicitly out of scope:

- **Multi-step undo stack** and **redo** in any form. Only the last destructive action is recoverable.
- **App-bar undo/redo buttons** or popup-menu undo entries. The snackbar is the only surface.
- **Undo for non-destructive mutations** — inline edits, individual check/uncheck, and drag-and-drop reorder.
- **Undo for "Delete List"**. It is owner-only, already gated behind a confirmation dialog, and navigates away from the
  screen.
- **Persisted undo history**. Nothing survives leaving the screen, let alone an app restart. This avoids the DB
  migration prerequisite noted in the research.
- **Undo for discarded ephemeral add rows.** The half-typed in-progress row in the add-item widget is plumbing, not a
  user-visible destructive action; it is ignored entirely.

## Constraints & assumptions

- Undo is expressed as an ordinary local mutation through the existing item store, so it inherits the outbox, offline
  queueing, version gating and rejection handling rather than introducing a parallel path.
- **Undoing a synced delete necessarily re-creates the item with a new server id.** Other members of a shared list
  observe it as a brand-new item. This is accepted; it cannot be avoided without a backend change, since the server
  hard-deletes.
- **Position collisions after restore are acceptable.** If concurrent activity means the restored position is no longer
  unique, ordering is by position then item id, so the item still lands where the user expects.
- Assumed: undo works offline, queued like any other local change, and is pushed when connectivity returns.
- Assumed: undo availability is bounded by the screen's lifetime, matching the detail service's existing
  `resetLazySingleton`-on-dispose scope.

## Acceptance criteria

- [ ] Deleting a single item shows a 5-second snackbar reading `1 item deleted` with an UNDO action.
- [ ] Tapping UNDO restores the item with its original text, quantity, unit and position.
- [ ] Checking items A and B, then "Delete All Checked", shows `2 items deleted`; UNDO restores both, still checked, in
      their original positions in the Done section.
- [ ] "Uncheck All" shows `N items unchecked`; UNDO restores each item's prior checked state.
- [ ] "Delete All Checked" and "Uncheck All" show **no snackbar** when they affect zero items.
- [ ] Adding or editing an item while the snackbar is visible neither dismisses it nor breaks the undo.
- [ ] After 5 seconds the snackbar disappears and the action is no longer reversible.
- [ ] Navigating back from the detail screen makes the undo unreachable.
- [ ] A rejected restore surfaces through the existing rejection message path — the same message shown when adding an
      item fails — with no undo-specific error UI.

## Edge cases

- **Rapid successive destructive actions.** Each snackbar replaces the previous one. Only the most recent action is
  recoverable; earlier ones are permanently committed. Accepted as a deliberate simplification.
- **Navigating away with the snackbar visible.** Leaving the detail screen commits the action; undo is unreachable
  afterwards.
- **Zero-item bulk actions.** "Delete All Checked" or "Uncheck All" with nothing checked shows no snackbar at all.
- **Rejected restore.** Handled by the existing rejection path, not by bespoke undo error handling.
- **Partial failure in a bulk restore.** Restoring N items is N separate pushes; the feature is best-effort. Items that
  restore successfully stay restored; rejected ones report through the existing rejection path. No aggregate
  "1 of 5 could not be restored" message.
- **Concurrent remote changes.** Another member's poll-delivered changes between the destructive action and the undo do
  not block the restore; position collisions resolve by the position-then-id ordering above.

## Integration points

- `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — the three destructive action entry points
  (per-item `onDelete`, and the `delete_checked` / `uncheck_all` popup menu branches). `ScaffoldMessenger` is already
  wired here for rejection toasts.
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — `deleteItem`, `deleteAllChecked`,
  `uncheckAll`.
- `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart` — the local mutation cores that hold the
  pre-state; a restore path is needed for deletes.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — the existing push/rejection behaviour the undo
  reuses unchanged.
- `docs/ADRs/0004-shopping-list-item-store-aggregate.md` — the store as the single consistency boundary for items.

## Open questions

- Exact snackbar copy is proposed above and can be adjusted during implementation; it is not a blocking decision.
