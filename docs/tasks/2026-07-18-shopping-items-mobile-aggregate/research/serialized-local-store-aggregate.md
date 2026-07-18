# Serialising Local-Store Access — a Single Aggregate for Shopping-List Items

Research into replacing the ad-hoc `_busy` mutual-exclusion flag in
`ShoppingListSyncService` with a single, serialised point of access to the local
database + in-memory cache + outbox — a DDD-style **aggregate** whose every
mutation runs as one atomic *read → modify → persist → publish* critical
section. This is analysis of the options, not an implementation plan.

## Summary

The current `_busy` set serialises only **pull vs. push per list**; it does
**not** cover the UI-driven mutations (`applyCreate/Edit/Checked/Reorder/Delete`)
that write the same cache and DB. Because every store method does a
read-modify-write that straddles an `await` (the sqflite transaction), a UI edit
that lands mid-reconcile can be silently clobbered in the in-memory cache even
though sqflite keeps the *database* consistent — the cache, DB, and notifier can
drift out of coherence. Consolidating all store access behind one object whose
methods are serialised by a lock fixes this. Dart is single-isolate, so the
"lock" is not a thread mutex but an **async serialiser**: either the
`synchronized` package's `Lock`, or a hand-rolled `Future`-chain queue (a pattern
already used in this codebase). A plain boolean-drop flag — the one the state
standard documents — is the wrong primitive here, because it *drops* concurrent
callers, and dropping a user's edit loses data.

## Key findings

- **`_busy` is too narrow.** It gates `_poll` against `_drain` per list
  (`shopping_list_sync_service.dart:55,152–176`), but the repository's `apply*`
  and `reconcile*` methods take no lock at all. Pull/push exclusion is solved;
  UI-mutation-vs-reconcile exclusion is not.
- **The DB is already serialised; the cache is not.** sqflite runs one
  connection and makes transaction blocks exclusive — all DB calls are queued.
  The real hazard is the **in-memory `_cache` + `ValueNotifier`**, which each
  method reads before its `await _dao.transaction(...)` and writes after, with no
  guard across the gap.
- **Concrete lost-update exists** (see Details) where the cache ends up
  disagreeing with the DB and the outbox — exactly the class of bug an aggregate
  eliminates.
- **Dart concurrency is cooperative.** No true parallelism within one isolate;
  interleaving happens only at `await`. So serialisation = ensuring one logical
  critical section completes (across all its awaits) before the next begins.
- **Two viable serialiser mechanisms**: (A) the `synchronized` package `Lock`
  (Java-style `lock.synchronized(() async {…})`, optional **reentrant** mode);
  (B) a hand-rolled `Future`-chain mutex — already in this repo at
  `core/logging/app_log_sink.dart:62` (`_writeQueue = _writeQueue.then(...)`).
- **The boolean-drop pattern is not serialisation.** The state standard's
  `if (_isXxxRunning) return;` *skips* the second caller. Correct for idempotent
  loads; wrong for store writes, which must *queue* and all run.
- **Never hold the store lock across the network.** The critical section must be
  local-only (cache + DB). HTTP stays outside; only reconciling the *outcome*
  re-enters the lock. This is finer-grained than today's `_busy`, which is held
  across the whole drain pass including the HTTP call.

## Details

### What `_busy` does today, and what it misses

`_busy` (`shopping_list_sync_service.dart:55`) is a `Set<String>` of list ids.
`_poll` bails if the id is present (`:155`), and both `_poll` and `_drain` add on
entry / remove on exit; a kick arriving mid-drain is deferred via `_pending`
(`:85–91`, `:183–187`). This is a **per-list mutex between the two sync
directions**, living in the *service*.

But the actual store writes live in the **repository**
(`shopping_list_item_repository.dart`): `applyCreate` (`:61`), `applyEdit`
(`:116`), `applyChecked` (`:146`), `applyReorder` (`:166`), `applyDelete`
(`:186`), plus the reconcile family `reconcileFromServer` (`:243`),
`reconcileAck` (`:475`), `reconcileDeleteAck` (`:505`), `cascadeDiscard`
(`:521`), `discardItem` (`:552`). **None of these takes `_busy` or any lock.**
The UI calls them directly through the detail service the instant the user taps —
concurrently with an in-flight poll or drain reconcile that `_busy` was meant to
protect. `_busy` guards the *poll's* reconcile against the *drain's* reconcile,
but nothing guards either against a user edit.

### The concurrency model: why this still matters in single-threaded Dart

Dart runs this app in one isolate with no preemption — code runs to its next
`await` uninterrupted. So there is no torn-write at the CPU level. The hazard is
**logical interleaving at `await` points**: every store method has the shape

```
read cache/DB snapshot          // sync
await _dao.transaction(...)     // <-- other microtasks run here
write cache + bump notifier     // sync, using the pre-await snapshot
```

Between the snapshot and the write-back, another store method can run to
completion. sqflite serialises the two *transactions* (its calls are synchronized
and transaction blocks are exclusive), so the **DB** stays consistent — but the
post-await **cache** write uses a stale snapshot and can overwrite what the
interleaved method just wrote.

### Concrete hazard (cache ↔ DB ↔ outbox divergence)

1. A poll runs `reconcileFromServer` for an open list. It reads `locals` from the
   cache (`:248`), classifies item *X* as clean and adopts the server value into
   `updatedByLocalId` (`:279–291`), then enters `await _dao.transaction`.
2. During that await the user checks *X*. `applyChecked` (`:146`) sets
   `dirty:true`, writes `_cache[listId][X]`, runs its own transaction, and bumps
   the notifier. It also appends an `update` outbox entry.
3. sqflite serialises the two transactions; the DB ends with *X* `dirty:true`,
   checked — correct.
4. Control returns to `reconcileFromServer` after its await, which executes
   `listCache[X] = adopted` (`:308`) — **`dirty:false`, unchecked** — clobbering
   the user's just-applied cache entry, then bumps the notifier to the wrong
   value.

Result: cache says clean/unchecked, DB says dirty/checked, outbox holds an
unsynced update. The screen renders the check as lost; a subsequent pull, seeing
the cache "clean", can adopt the server value and the local intent is visually
gone until the list is reloaded from disk. The dirty-gate in reconcile
(`!local.dirty`, `:279`) does **not** save this — the classification was made
against the pre-await snapshot, when *X* was still clean.

An aggregate whose *entire* read-modify-write (including the cache/notifier
write) is one serialised section makes step 4 impossible: `applyChecked` either
runs fully before reconcile's section or fully after it, never inside it.

### The DDD aggregate framing

An **aggregate** is a consistency boundary: a single object owning a cluster of
data, through which *all* reads and mutations pass, so invariants hold atomically
and no outsider can observe or wedge a half-applied state. Applied here, the
aggregate owns the three things that must stay coherent for one list (or all
lists):

- the **local item state** (sqflite `items` table),
- the **in-memory cache + `ValueNotifier`** projected to the UI,
- the **outbox** (`outbox` table).

Its invariants: cache == the visible projection of the DB rows; every dirty item
has ≥1 outbox entry and vice-versa; `lastAckedVersion` advances only on this
device's acks. Every public method (`applyEdit`, `reconcileFromServer`,
`reconcileAck`, `cascadeDiscard`, …) runs as one serialised transaction over all
three stores. This is essentially the current repository, but with (a) a lock
around each method body and (b) the cache/DB/notifier writes pulled *inside* that
lock. It does not require abandoning the Repository→Service→View layering: the
aggregate **is** the repository (the architecture standard explicitly allows a
repository to "hold local cache or persistence state … an in-memory cache +
`ValueNotifier` over a local DB", `docs/mobile/standards/architecture.md:6`). The
sync service keeps the loop/retry/poll policy but calls the aggregate instead of
reaching into cache logic itself.

### Serialisation mechanisms (the "Boolean" the user asked about)

**Option A — `synchronized` package `Lock`.** A single shared `Lock` per
aggregate (or a `Map<listId, Lock>` for per-list concurrency); each method wraps
its body in `await _lock.synchronized(() async {…})`. The package queues callers
and runs them one at a time to completion. It offers a **reentrant** mode
(`Lock(reentrant: true)`, Zone-based) so a locked method can call another locked
method without deadlock. New dependency, but it is the tekartik author of
sqflite/path_provider already in use — low-risk, well-maintained.

**Option B — hand-rolled `Future`-chain mutex (no new dependency).** Keep a
`Future _tail = Future.value();` and make each method chain onto it:

```dart
Future<T> _serialised<T>(Future<T> Function() action) {
  final result = _tail.then((_) => action());
  _tail = result.then((_) {}, onError: (_) {});
  return result;
}
```

This is the **exact pattern already shipping** in
`core/logging/app_log_sink.dart:62` (`_writeQueue = _writeQueue.then((_) => _append(line))`).
Precedent in-repo, zero new deps, trivially auditable. Downsides vs. A: no
built-in reentrancy (nested calls self-deadlock — must delegate to unlocked
private helpers), and you hand-roll error isolation on the tail so one failure
doesn't poison the chain.

**Option C — boolean-drop flag (the state standard's pattern) — rejected for
mutations.** `docs/mobile/standards/state-management.md:34` prescribes
`if (_isXxxRunning) return;`. That *drops* the second caller, which is right for
idempotent loads (`loadSharedUsers`) but **loses data** for store writes: two
quick taps, the second silently ignored. `_busy` is a variant of this (drop the
poll, defer the drain) and works only because both sides are the sync loop, not
the user. For an aggregate that must run *every* mutation, use a queue (A or B),
not a drop.

### Reentrancy — why nested locked calls are a design decision

A lock is **reentrant** if the caller already holding it can acquire it *again*
without blocking; a **non-reentrant** lock blocks that second acquisition — even
from the same call chain — and, since the release can only happen after the
outer call returns, the result is **self-deadlock** (a permanent hang, not a
crash). This is not hypothetical here: the aggregate composes. Bulk actions loop
over per-item mutations (`deleteAllChecked` / `uncheckAll`,
`shopping_list_detail_service.dart:163–179`), and reconcile paths reuse smaller
store operations. The moment one locked method calls another, the lock re-enters:

```dart
Future<void> applyDelete(String id) => _lock.synchronized(() async { /* … */ });

Future<void> deleteAllChecked(...) => _lock.synchronized(() async {
  for (final item in checked) {
    await applyDelete(item.localId); // ← re-acquires the SAME lock
  }
});
```

With a non-reentrant lock `applyDelete` waits for a lock only its own ancestor
holds → hang. Three ways to handle it:

1. **Reentrant lock** — `Lock(reentrant: true)` (the `synchronized` package uses
   `Zone`s to recognise "already inside this lock" and passes the nested call
   through). Least code. Trade-off: the nested call runs *inside* the outer
   critical section, so it is not a fresh atomic unit.
2. **Locked-public / unlocked-private split** — public methods take the lock and
   delegate to private `_applyDeleteUnlocked(...)` helpers that don't; public
   methods call the *private* helper, never each other. The mandatory discipline
   for the hand-rolled `Future`-chain mutex (Option B — it has **no** reentrancy
   and would self-deadlock). More explicit about exactly what is atomic.
3. **Batch methods** — e.g. `deleteItems(List<localId>)` takes the lock **once**
   and loops over unlocked helpers, making the whole bulk action one atomic
   section rather than N.

Consequence for the mechanism choice: **Option B (the in-repo `Future`-chain) is
non-reentrant, so it forces discipline 2 or 3.** Option A (`synchronized`) *adds*
discipline 1 as a shortcut, but 2/3 usually give cleaner atomicity guarantees and
are worth preferring even with a reentrant lock available.

### Design constraints the aggregate must respect

- **No network inside the lock.** The push path (`_pushOne`,
  `shopping_list_sync_service.dart:249`) does HTTP. The aggregate lock must wrap
  only the local pre-read (`readItem`) and the post-response reconcile
  (`reconcileAck` / `cascadeDiscard` / `discardItem`) — *not* the HTTP call.
  Holding the lock across the network would serialise all list access behind a
  slow request and could stall the poll indefinitely. This makes the aggregate
  lock **finer-grained** than `_busy`, which is deliberately held across the
  whole drain pass (HTTP included) to keep push and pull from interleaving.
- **`_busy`'s second job may still be needed.** `_busy` enforces two things:
  (a) local-store mutual exclusion — the aggregate subsumes this; and (b) a
  *logical* rule that a pull's reconcile shouldn't land while a push for the same
  item is mid-flight (the response is about to rewrite it). With correct
  dirty-gating (a dirty item is never adopted by a pull, `:279`,`:297–299`) this
  may already be safe without a coarse gate — worth verifying rather than
  assuming the aggregate lock alone replaces `_busy` wholesale.
- **Reentrancy / composition.** Nested locked calls (bulk actions, reconcile
  reuse) must not self-deadlock — see the dedicated Reentrancy subsection above.
- **Granularity: per-list vs. global.** Today everything is keyed by `listId`
  and different lists drain concurrently (sync service class doc). A
  `Map<listId, Lock>` preserves that; a single global lock is simpler but
  serialises unrelated lists. Per-list matches the existing design intent.

## Open questions / gaps

- **Does the aggregate fully replace `_busy`, or only its local-store half?**
  Needs a decision on whether pull-reconcile-vs-in-flight-push still needs an
  explicit logical gate once every store write is atomic and dirty-gated.
- **Where does the lock live?** Inside the repository (making it the aggregate),
  or a new dedicated `LocalStore`/aggregate class the repository and sync service
  both call? The former is least churn; the latter is a cleaner DDD boundary.
- **Package vs. hand-rolled.** Adding `synchronized` buys reentrancy and less
  bespoke code; the `Future`-chain has in-repo precedent and no new dependency.
  A team call.
- **Per-list vs. global lock** granularity (see constraints).
- **Testing.** Deterministically exercising the interleave (edit landing during a
  reconcile's transaction await) needs a fake DAO that yields control at the
  await — worth designing alongside the aggregate.

## Sources

- `synchronized` package — [pub.dev](https://pub.dev/packages/synchronized),
  [README](https://github.com/tekartik/synchronized.dart/blob/master/synchronized/README.md),
  [`Lock` API](https://pub.dev/documentation/synchronized/latest/synchronized/Lock-class.html)
  — Java-style `lock.synchronized(() async {…})`, non-reentrant by default,
  optional Zone-based reentrant mode, `MultiLock` (3.3.0+); single-isolate scope.
- sqflite concurrency —
  [usage recommendations](https://github.com/tekartik/sqflite/blob/master/sqflite/doc/usage_recommendations.md),
  [issue #16](https://github.com/tekartik/sqflite/issues/16) — all calls
  synchronized, transaction blocks exclusive; concurrent read/write transactions
  unsupported → the DB layer is already serialised.
- In-repo precedent — `mobile/lib/core/logging/app_log_sink.dart:62` — existing
  `Future`-chain serialiser (`_writeQueue = _writeQueue.then(...)`).
- Codebase — `shopping_list_sync_service.dart` (`_busy`/`_pending` gate),
  `shopping_list_item_repository.dart` (unlocked `apply*`/`reconcile*` store
  mutations), `docs/mobile/standards/state-management.md` (boolean-flag pattern),
  `docs/mobile/standards/architecture.md` (repository may own cache + notifier).
