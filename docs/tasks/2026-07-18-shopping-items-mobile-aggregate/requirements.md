# Shopping-List Items — Serialised Local-Store Aggregate

**Date:** 2026-07-18
**Type:** refactor
**Status:** requirements

## Summary

Route all local-store access for shopping-list *items* (in-memory cache +
sqflite DB + outbox) through a single serialised point so that every
read-modify-write runs atomically, eliminating a class of transient concurrency
bugs and simplifying the current `_busy`/`_pending` mutual-exclusion code.

## Context

The mobile shopping-list item store today guards concurrency with a `_busy`
set in `ShoppingListSyncService`, which only serialises pull-vs-push per list.
The UI-driven mutations (`applyCreate/Edit/Checked/Reorder/Delete`) and the
reconcile family write the same in-memory cache and DB **without any lock**.
Because each store method reads a cache snapshot, `await`s a sqflite
transaction, then writes the cache back using the pre-`await` snapshot, a UI
edit that lands mid-reconcile can be silently clobbered in the cache — leaving
the cache, DB, and outbox incoherent (the DB stays correct because sqflite
serialises transactions, but the cache and notifier drift). See
`research/serialized-local-store-aggregate.md` for the concrete lost-update
walkthrough (poll's `reconcileFromServer` vs. a user's `applyChecked`).

This work is **preventive**. No user has reported losing an edit; the
motivation is twofold:

1. **Simplify** code the author finds overly complicated (the `_busy`/`_pending`
   gating).
2. **Harden** the store against the transient cache/DB/outbox divergence
   described above.

It is follow-up work, **not** a blocker for merging the current
`rewrite-shopping-list-details` branch.

## Requirements

- All local-store access for shopping-list items — reads and mutations, from
  both the UI-driven path and the sync (pull/push) path — passes through one
  serialised point, so each logical read-modify-write (cache + DB + outbox,
  including the cache/notifier write-back) completes atomically before the next
  begins.
- The new serialisation lives in a **new dedicated service** (an aggregate-like
  consistency boundary), not by converting the existing repository into an
  aggregate.
- The `ShoppingListSyncService` performs its pull/push reconciliation **through**
  this new service rather than reaching into cache/store logic itself.
- **Correctness of end-state is the primary goal**: no user mutation is silently
  lost, and the cache, DB, and outbox never diverge from one another.
- **No user-visible behavior change.** In the absence of contention, the app
  behaves exactly as today.
- **Conflict-resolution semantics are unchanged.** Today's dirty-gating rules
  (a locally-dirty item is not overwritten by a concurrent server value until it
  has been pushed and acked) stay exactly as they are. The refactor only
  guarantees those existing rules are applied atomically — it does not redefine
  which value wins.

## Anti-requirements

- **Backend** — out of scope. No changes to the shopping-list API or server
  logic.
- **Sync protocol / outbox wire format** — out of scope. The on-the-wire
  contract and the append-only outbox format are not being redesigned.
- **Shopping *lists*** (as opposed to items) — out of scope. Only the item store
  is being serialised.
- **Other features** (recipes, collections, planning, extraction) — out of
  scope.
- **Conflict-resolution rules** — explicitly *not* being changed (see
  Requirements).
- **Repository-as-aggregate** — explicitly not the chosen shape; a new service
  is wanted instead. (The exact structure is a design-phase decision.)

## Constraints & assumptions

- **Single-isolate Dart.** Concurrency is cooperative — interleaving happens
  only at `await` points, so serialisation means ensuring one logical critical
  section (across all its awaits) completes before the next starts.
- **sqflite already serialises the DB.** All DB calls are queued and transaction
  blocks are exclusive; the unguarded surface is the **in-memory cache +
  `ValueNotifier`**, which must be brought inside the serialised section.
- **Layering.** Stay within the existing Repository → Service → View
  architecture (`docs/mobile/standards/architecture.md`).
- **Callers may change.** Reworking `ShoppingListDetailService` and
  `ShoppingListSyncService` call sites is acceptable if needed.
- **No network inside the critical section** (assumption to carry into design):
  the serialised section is local-only; HTTP stays outside it, with only the
  reconcile of the response re-entering the serialised path.
- The **solution mechanism is deferred to the design phase** — package vs.
  hand-rolled serialiser, per-list vs. global granularity, where the
  serialisation point lives, and the fate of `_busy` are not decided here.

## Acceptance criteria

- [ ] All local shopping-list item store access (UI-driven mutations, bulk ops,
      and sync pull/push reconcile) goes through the new serialised service.
- [ ] The store is a new dedicated service; the existing repository is **not**
      converted into an aggregate.
- [ ] Conflict-resolution semantics are observably identical to today.
- [ ] No user-visible behavior change under normal (uncontended) use.
- [ ] Manual on-device testing shows existing shopping-list flows (add, edit,
      check/uncheck, reorder, delete, bulk delete-checked / uncheck-all, share,
      offline edit + reconnect) work with no regressions.
- [ ] The design deliverable proposes concrete, hand-stageable concurrency /
      conflict test scenarios (see below) that the author can run to build
      confidence that the race is eliminated.

### Suggested manual concurrency test scenarios (to be fleshed out in design)

These are staging ideas for the design phase to refine — the point is to force
a mutation to overlap a sync operation:

- **Edit during a pull** — open a list, trigger/await a poll, and check or edit
  an item while the reconcile is in flight; confirm the local change survives and
  the cache/DB agree afterward.
- **Rapid toggling during active sync** — repeatedly check/uncheck an item while
  a push/pull cycle is running; confirm the final state is consistent and no tap
  is lost.
- **Bulk action overlapping a poll** — trigger `deleteAllChecked` / `uncheckAll`
  while a sync is running; confirm all intended items are affected atomically.
- **Rapid double-taps** — two quick mutations on the same item; confirm both are
  applied (queued), not one dropped.
- **Offline edits reconciling on reconnect** — make several edits offline, then
  reconnect and let the outbox drain; confirm all local intents are pushed and
  the reconciled state matches.

## Edge cases

- **Poll-vs-edit race** — the documented lost-update: a UI mutation landing
  during a reconcile's transaction `await` clobbering the cache write-back. This
  is the primary bug being closed.
- **Bulk actions overlapping sync** — `deleteAllChecked` / `uncheckAll`
  (`shopping_list_detail_service.dart`) looping per-item mutations while a poll
  or drain runs.
- **Rapid double-taps** — successive mutations on the same item that must both be
  applied (queued), not dropped.
- No additional concurrent sources are considered in scope beyond these.

## Integration points

- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — the
  current local item store (in-memory cache + `ValueNotifier` over the DB,
  appends outbox entries); the `apply*` and `reconcile*` mutations.
- `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` — sqflite DAO
  for the items + append-only outbox tables; `OutboxKind` enum.
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — the
  UI-driven add/edit/check/delete/reorder path and bulk operations.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — the
  poll/drain sync loop and the `_busy`/`_pending` gate that this refactor
  reworks.
- `mobile/lib/features/shopping_list/shopping_list_setup.dart` — DI wiring for
  the module, where the new service would be registered.

## Open questions

All deferred to the design phase:

- Does the new service fully replace `_busy`, or only its local-store-exclusion
  half (leaving the pull-reconcile-vs-in-flight-push logical rule as a separate
  concern)?
- Where does the serialisation point live — a new `LocalStore`/aggregate class
  that both the repository and sync service call, or folded into a reworked
  store?
- Serialiser mechanism: the `synchronized` package `Lock` vs. the hand-rolled
  `Future`-chain queue already used in `core/logging/app_log_sink.dart`.
- Granularity: per-list locks (preserving today's concurrent-lists behavior) vs.
  a single global lock (simpler).
- How to make the interleave deterministically testable (e.g. a fake DAO that
  yields control at the transaction `await`), if any automated coverage is added
  beyond the manual scenarios.
