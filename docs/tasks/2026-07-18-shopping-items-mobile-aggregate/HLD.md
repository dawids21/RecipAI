# Shopping-List Items — Serialised Store Aggregate — High-level design

**Date:** 2026-07-18
**Status:** final
**ADRs:** docs/ADRs/0004-shopping-list-item-store-aggregate.md

## Summary

Route all local-store access for shopping-list *items* (in-memory cache + sqflite
DB + outbox + `ValueNotifier`) through a single new dedicated store object whose
every read-modify-write runs atomically per list, closing the poll-vs-edit
lost-update race and letting `_busy` shrink to a bare single-flight-drain guard.

## Approach

### Chosen

**Extracted store aggregate.** A new dedicated item-store object becomes the one
consistency boundary for items. It owns the in-memory cache, the per-list
`ValueNotifier`s, the outbox coordination, and a **per-list** async serialiser.
The local read-modify-write logic that lives in `ShoppingListItemRepository`
today (`openList` / `watch` / `applyCreate/Edit/Checked/Reorder/Delete` /
`reconcileFromServer` / `reconcileAck` / `reconcileDeleteAck` / `cascadeDiscard`
/ `discardItem` / `_visibleItems`) moves into the store; the item **HTTP**
endpoints (`fetchServerItems` / `createItem` / `updateItem` / `deleteItem`)
remain in a network-facing repository. Every mutation runs its whole
cache + DB + outbox + notifier write-back inside the serialised section, so a UI
mutation can no longer land *inside* a reconcile's transaction `await` and
clobber the cache write-back. The **network stays outside** the critical section:
the serialiser wraps only the local pre-read and the post-response reconcile,
never the HTTP call. `ShoppingListDetailService` watches and mutates through the
store; `ShoppingListSyncService` performs its pull/push reconcile through the
store instead of reaching into cache logic.

Because the cache becomes private to the store, "all local access goes through
one serialised point" holds **structurally**, not by convention.

What this gives up relative to the runners-up: more code moves than a
lock-in-front facade (A) would need, and every mutation still writes an
in-memory cache that must be kept coherent with the DB — rather than deleting
the cache outright (D) so no second copy can ever exist. B keeps the cache
because deleting it regresses the instant tap feedback users have today (see
Rejected).

### Rejected alternatives

- **A — Serialising facade (lock in front of today's repository).** Closes the
  same race with the least churn, but enforces the "single point of access"
  invariant only by convention: the repository's mutation methods stay public
  and callable, so the boundary silently regresses the first time a caller
  bypasses the facade. B makes the boundary structural instead.
- **D — Cache-free store (DB as the single source of truth).** Strongest
  non-divergence guarantee, but its clean form publishes the notifier only after
  the DB commit, so every checkbox tap lags by a disk round-trip — a visible
  regression against the "no user-visible change" requirement. The optimistic
  pre-write that would hide the lag reintroduces the divergent copy D exists to
  remove.
- **C — Command-queue / actor store.** Reentrancy-free by construction, but the
  mailbox model is less familiar to future maintainers than a lock and buys
  little for this store's modest composition.
- **Widen `_busy` to also cover UI mutations (no new service).** The true
  smallest diff, but the requirements mandate a new dedicated service and forbid
  leaving serialisation in the sync service.

## Feature areas

### Item store aggregate (new consistency boundary)

**Key behaviors.**
- Owns the in-memory cache, the per-list `ValueNotifier`s, and the outbox
  coordination for items; nothing outside the store reads or writes them.
- Every logical read-modify-write — UI mutation, bulk op, and pull/push reconcile
  — completes atomically across cache + DB + outbox + notifier before the next
  begins, serialised **per list** (unrelated lists still proceed concurrently).
- The serialised section is **local-only**; no network call is ever held inside
  it. Only the local pre-read and the reconcile of a response re-enter it.
- Conflict resolution is unchanged: dirty-gating (a locally-dirty item is never
  overwritten by a concurrent server value) and version-gating (a stale server
  response never regresses an item past its last-acked version) behave exactly as
  today — the store only guarantees they apply atomically.
- In the uncontended common case a mutation's notifier write-back lands in the
  same event-loop turn as today (instant feedback); under contention it defers
  only by the length of one *local* transaction.

### Item network repository (HTTP)

**Key behaviors.**
- Retains the item HTTP endpoints (list fetch, create, update, delete) and their
  response classification (2xx / 412 / 404 / 400-403 / network) unchanged.
- Holds no local cache or notifier state; returns raw results to callers.

### Sync reconciliation through the store

**Key behaviors.**
- The poll path fetches the server list via the network repository, then hands
  the result to the store to reconcile; the drain path pushes an outbox entry via
  the network repository, then hands the outcome (ack / conflict / discard) to
  the store to reconcile.
- `_busy`'s store-exclusion and pull-vs-in-flight-push roles are **removed** — the
  store's per-list lock plus the unchanged dirty-/version-gating cover both, so a
  poll's reconcile may now run alongside a drain safely and no longer drops when a
  drain is active.
- A **single-flight-drain guard** remains in the sync service: at most one drain
  loop runs per list, with extra kicks coalesced. This guard knows nothing about
  the store — it exists only so two concurrent drains can't read the same outbox
  head and double-push it across the network.

### Detail service and DI wiring

**Key behaviors.**
- `ShoppingListDetailService` watches and mutates through the store rather than
  the repository; observable UI behavior is unchanged.
- The feature setup registers the new store and wires the network repository, the
  store, the sync service, and the detail service to it.

### Concurrency confidence (test scenarios)

**Key behaviors.**
- The design carries the requirements' hand-stageable manual scenarios (edit
  during a pull, rapid toggling during active sync, bulk action overlapping a
  poll, rapid double-taps, offline edits reconciling on reconnect), each phrased
  to force a mutation to overlap a sync operation and then assert cache/DB/outbox
  agreement afterward.

## Out of scope

- The serialiser **mechanism** and **reentrancy discipline** are deferred to
  task-design (see Open questions), not out of scope.
- Everything the requirements list as an anti-requirement stands: backend and
  shopping-list API, the sync wire protocol and append-only outbox format,
  shopping *lists* (as opposed to items), other features, and the
  conflict-resolution *rules* (unchanged by construction here).

## Open questions

- **Serialiser mechanism** — the `synchronized` package `Lock` (reentrant mode
  available) vs. the in-repo hand-rolled `Future`-chain
  (`core/logging/app_log_sink.dart`). Trades a new dependency for built-in
  reentrancy and less bespoke code.
- **Reentrancy / composition** — how bulk actions (`deleteAllChecked` /
  `uncheckAll`) that loop per-item mutations avoid self-deadlock: a reentrant
  lock, a locked-public / unlocked-private split, or batch methods that take the
  lock once.
- **Store ownership boundary** — whether the store owns the cache/notifiers and
  delegates DB work to the existing DAO, or subsumes more of the DAO surface; and
  the exact home/name of the surviving single-flight-drain guard.
