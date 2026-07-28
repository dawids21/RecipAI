# T3: Write the full scenario catalog — Task Design

**Date:** 2026-07-28
**Status:** final

## Summary

Fill `shopping_list_sync_service_test.dart` out to 19 scenarios across four
`group()` blocks, reusing T2's reviewed harness unchanged: the same inline
doubles, the same `setUp`/`tearDown` graph, the same four read helpers. Each test
seeds its starting state through a server pull, fixes one exact ordering of
`pushNextEntry` / `fetchAndReconcile` / `requestDrain` / `fanOutPending` calls,
and asserts that ordering's own end state across the DB rows, the fake backend,
the visible-items notifier, and the outbox. Test-side additions are two lines: a
`removeServerItem` state-setter on the fake and an `outboxCount` helper for the
scenarios that expect a non-empty queue.

## Components and responsibilities

- **`shopping_list_sync_service_test.dart`** (MODIFY,
  `mobile/test/features/shopping_list/`) — the whole T3 deliverable. T2's
  doubles, factory, `setUpAll`/`setUp`/`tearDown` and four read helpers stay
  exactly as reviewed; the single existing test moves inside the first `group()`
  unchanged.
- **`FakeShoppingListItemRepository`** (MODIFY, inline) — gains
  `removeServerItem(listId, itemId)`, the removal counterpart to `putServerItem`,
  so a test can stage an out-of-band remote deletion (the only way to reach the
  404-gone branch: the fake's `deleteItem` returns silently on a missing item,
  mirroring the real repository's 404-as-success).
- **`seedAcceptedItem(listId, item)`** (CREATE, inline helper) — the shared
  arrange step: put the given `ShoppingListItem` on the fake, open the list,
  pull, and return the adopted row's `localId`. Takes the domain type rather than
  restating its fields as parameters. Used by the ~12 scenarios that start from
  "already exists and is accepted on both sides".
- **`serverItem({...})`** (CREATE, inline builder) — builds a `ShoppingListItem`
  with scenario-neutral defaults, so a test names only the fields it cares about.
  `ShoppingListItem` requires all seven fields and has no `copyWith`, and the
  out-of-band version bumps in scenarios 7, 8, 14, 16 and 17 construct one too —
  this keeps the field list in exactly one place.
- **`outboxCount(listId)`** (CREATE, inline helper) — a fifth read helper used
  only by scenarios whose expected end state is a *non-empty* queue. `outboxEmpty()`
  stays as T2 defined it for convergent scenarios.

## Interfaces and method signatures

Fake extension (one method, next to `putServerItem`):

```
void removeServerItem(String listId, String itemId);   // out-of-band remote delete
```

Test-local helpers (inline in `main()`, alongside T2's four):

```
ShoppingListItem serverItem({                          // defaults; name what matters
  String id = 'server-1',
  String name = 'Milk',
  double? quantity = 2,
  String? unit = 'l',
  bool checked = false,
  double position = 1.0,
  int version = 0,
});

Future<String> seedAcceptedItem(                       // -> localId of the adopted row
  String listId,
  ShoppingListItem item,
);

Future<int> outboxCount(String listId);                // raw count over the outbox table
```

At the call site the pair reads as the state it establishes:

```
final localId = await seedAcceptedItem(listId, serverItem());

// out-of-band remote change, same builder
backend.putServerItem(listId, serverItem(version: 1, name: 'Oat milk'));
```

Sync-service entry points the catalog drives (all already shipped in T1):

```
Future<PushResult> pushNextEntry(String listId);       // one entry, under the real lock
Future<List<ShoppingListItem>> fetchAndReconcile(String listId);   // pure pull
Future<void> requestDrain(String listId);              // drain loop, awaitable to empty
Future<void> fanOutPending();                          // fan-out over listIdsWithOutbox
ValueListenable<SyncStatus> syncStatusFor(String listId);
```

## Data flow

**Seeding (`seedAcceptedItem`).**
1. `backend.putServerItem(listId, item)`.
2. `await store.openList(listId)` — list resident, notifier created (required
   before `watch`). Idempotent, so repeat calls for a second item are safe.
3. `await sync.fetchAndReconcile(listId)` — `reconcileFromServer` inserts a fresh
   row: `serverId` set, `lastAckedVersion == item.version`, `dirty == false`,
   outbox untouched.
4. Return the `localId` of the row whose `serverId == item.id` (looked up by id,
   not `.single`, so the helper works when a list holds several seeded items).

**Scenario body.** Every test then follows the same three beats:
1. *Arrange* — optionally stage an out-of-band remote change
   (`putServerItem` at a higher version, or `removeServerItem`) and/or set a
   fault flag (`offline`, `transientFailure`, `rejectWrites`).
2. *Act* — apply local mutations through the store, then run sync steps in the
   chosen order, asserting each step's `PushResult` inline.
3. *Assert* — the four surfaces at their explicit end state; stall scenarios also
   assert `sync.syncStatusFor(listId).value`.

## Scenario catalog

Surfaces are abbreviated `db` / `backend` / `visible` / `outbox`. Every scenario
below is one `test()`; no test runs two orderings.

### group `store-op happy paths` (6)

1. **create pushed and accepted** — *T2's existing test, moved in unchanged.*
   End: `db` row has `serverId`, `lastAckedVersion 0`, `dirty false`; `backend`
   one item `Milk`; `visible` matches; `outbox` empty.
2. **edit pushed and accepted** — seed v0; `applyEdit(name: 'Bread', quantity: 1,
   unit: null)`; push → `pushed`.
   End: `db` `{name: Bread, quantity: 1, unit: null, lastAckedVersion: 1, dirty:
   false}`; `backend` `{Bread, version 1}`; `visible` `Bread`; `outbox` empty.
3. **check pushed and accepted** — seed v0; `applyChecked(true)`; push.
   End: `db` `{checked: true, lastAckedVersion: 1, dirty: false}`; `backend`
   `{checked: true, version 1}`; `visible` checked; `outbox` empty.
4. **reorder pushed and accepted** — seed v0 at position 1.0;
   `applyReorder(2.5)`; push.
   End: `db` `{position: 2.5, lastAckedVersion: 1, dirty: false}`; `backend`
   `{position: 2.5, version 1}`; `visible` position 2.5; `outbox` empty.
5. **delete pushed and accepted** — seed v0; `applyDelete`; push.
   End: `db` empty; `backend` empty; `visible` empty; `outbox` empty.
6. **`deleteAllChecked` queues every checked item and drains clean** — three
   `seedAcceptedItem` calls (`server-1` checked, `server-2` unchecked,
   `server-3` checked); `deleteAllChecked`; `await requestDrain(listId)`.
   End: `db` and `backend` hold `server-2` only; `visible` is that one item;
   `outbox` empty; status `notSyncing` (a completed drain resets it).

### group `push outcomes` (7)

7. **412 on an update cascade-discards to the winner** — seed v0; out-of-band
   `putServerItem` same id at `version 1, name 'Oat milk'`; `applyEdit(name:
   'Almond milk')`; push → `pushed`.
   End: `db` `{name: 'Oat milk', lastAckedVersion: 1, dirty: false}`; `backend`
   unchanged at v1; `visible` `'Oat milk'`; `outbox` empty. The local edit is
   intentionally lost — that is the cascade-discard contract.
8. **412 on a delete un-tombstones the item** — seed v0; out-of-band bump to v1;
   `applyDelete` (row tombstoned, drops out of `visible`); push → `pushed`.
   End: `db` row present with `{pendingDelete: false, lastAckedVersion: 1, dirty:
   false}` and the winner's fields; `backend` still holds it at v1; `visible`
   shows it again; `outbox` empty.
9. **404-gone hard-removes the local row** — seed v0;
   `backend.removeServerItem(listId, 'server-1')`; `applyEdit(name: 'Bread')`;
   push → `pushed`.
   End: `db` empty; `backend` empty; `visible` empty; `outbox` empty — converged,
   with no winner to roll back to.
10. **400/403 rejected discards the queued create** — `backend.rejectWrites =
    true`; `openList`; `applyCreate('Milk')`; push → `pushed`.
    End: `db` empty; `backend` empty; `visible` empty; `outbox` empty.
11. **offline stall leaves the entry queued** *(divergence expected)* — seed v0;
    `applyEdit(name: 'Bread')`; `backend.offline = true`; push → `stalled`.
    End: `db` `{name: 'Bread', dirty: true, lastAckedVersion: 0}`; `backend` still
    `{Milk, version 0}`; `visible` `'Bread'`; `outboxCount == 1`; status
    `SyncStatus.offline`.
12. **transient failure then retry succeeds** — seed v0; `applyEdit(name:
    'Bread')`; `transientFailure = true`, push → `stalled`; `transientFailure =
    false`, push → `pushed`.
    End: converged as scenario 2 — `db` `{Bread, lastAckedVersion: 1, dirty:
    false}`, `backend` `{Bread, v1}`, `visible` `Bread`, `outbox` empty.
13. **transient failures escalate to `SyncStatus.failure`** *(divergence
    expected)* — seed v0; `applyEdit(name: 'Bread')`; `transientFailure = true`;
    push six times, each → `stalled` (`_maxRetries` is 5, so the sixth attempt
    escalates).
    End: status `SyncStatus.failure`; `db` `{Bread, dirty: true,
    lastAckedVersion: 0}`; `backend` unchanged at v0; `visible` `'Bread'`;
    `outboxCount == 1`.

### group `ordering` (4)

14. **a still-dirty local edit is not clobbered by a reconcile** *(divergence
    expected)* — seed v0; `applyEdit(name: 'Bread')`; out-of-band bump to v1
    `'Oat milk'`; `await fetchAndReconcile(listId)`.
    End: `db` `{name: 'Bread', dirty: true, lastAckedVersion: 0}` — the pull
    neither adopted nor deleted it; `backend` `{Oat milk, v1}`; `visible`
    `'Bread'`; `outboxCount == 1`.
15. **a queued create-then-edit pushes both entries, clearing `dirty` only at the
    end** — `openList`; `applyCreate('Milk', 2, 'l')`; `applyEdit(name: 'Oat
    milk', quantity: 1, unit: 'l')` → two entries. Push #1 (create) → `pushed`;
    assert mid-way that `db` has `serverId` set, `lastAckedVersion 0` and `dirty`
    **still true** (one entry remains). Push #2 (update) → `pushed`.
    End: `db` `{name: 'Oat milk', lastAckedVersion: 1, dirty: false}`; `backend`
    `{Oat milk, v1}`; `visible` `'Oat milk'`; `outbox` empty.
16. **permutation A: reconcile before push, against one starting divergence** —
    seed v0; `applyEdit(name: 'Bread')`; out-of-band bump to v1 `'Oat milk'`;
    `fetchAndReconcile` (skips the dirty row), then push → `pushed` (412).
    End: `db` `{Oat milk, lastAckedVersion: 1, dirty: false}`; `backend` v1;
    `visible` `'Oat milk'`; `outbox` empty.
17. **permutation B: push before reconcile, same starting divergence** — same
    arrange; push → `pushed` (412 → cascade to the winner), then
    `fetchAndReconcile` (server v1 vs local `lastAckedVersion` 1 — the strict `>`
    gate leaves the row untouched).
    End: stated independently, identical to scenario 16.

### group `multi-list` (2)

18. **two lists drain independently** — `openList` both; `applyCreate` on each;
    `await requestDrain('list-1')`; `await requestDrain('list-2')`.
    End: per list — one clean row with a `serverId`, one backend item, one visible
    item; `outbox` globally empty; both statuses `notSyncing`.
19. **the start/resume fan-out drains every list with a pending outbox** —
    `openList` both; `applyCreate` on each; `await sync.fanOutPending()`.
    End: identical converged end state to scenario 18, reached by awaiting the
    fan-out to quiescence rather than per-list kicks.

## Pseudo-code

Only three scenarios have logic worth spelling out; the rest are arrange → act →
four assertions.

Scenario 15 — the acked-seq / dirty bookkeeping that makes it non-obvious:

```
applyCreate  -> outbox[seq 1] = create{name: Milk}   , row dirty, serverId null
applyEdit    -> outbox[seq 2] = update{name: Oat milk}, row dirty, fields = Oat milk

push #1: head = seq 1 (create)
    backend.createItem(payload of seq 1)  -> winner{server-N, v0, name Milk}
    reconcileAck(localId, winner, ackedSeq: 1)
        drops seq 1; remaining = 1  -> dirty STAYS true
        adopts serverId + lastAckedVersion 0; FIELDS UNTOUCHED (still Oat milk)
    assert here: serverId != null, lastAckedVersion == 0, dirty == true

push #2: head = seq 2 (update)
    baseVersion read live from the row = 0, matches backend -> winner v1
    reconcileAck(..., ackedSeq: 2) -> drops seq 2; remaining = 0 -> dirty false
```

Scenario 13 — the escalation boundary:

```
transientFailure = true
repeat 6 times:
    expect(pushNextEntry(listId) == stalled)
# attempts 1..5: _retry <= _maxRetries -> backoff timer armed (inert, never fires)
# attempt 6:     _retry >  _maxRetries -> SyncStatus.failure
assert status == failure and the divergence (local edited+dirty, backend at v0)
```

Scenario 8 — why the delete un-tombstones rather than discards:

```
applyDelete -> row.pendingDelete = true (drops out of `visible`), delete entry queued
push: deleteItem(serverId, baseVersion 0) vs backend v1
      -> ItemVersionConflictException(winner v1)
      -> cascadeDiscard overwrites fields from the winner AND sets
         pendingDelete = false  <- the un-tombstone; row reappears in `visible`
```

## Decisions made

- **One file, four `group()` blocks; doubles and helpers stay inline.** T2's
  reviewed shape is the approved structure, and the widget-testing standard keeps
  behaviour-bearing code out of `test/support/`. Splitting into per-area files
  would force the doubles and the `setUp` graph into a shared harness file, which
  the standard reserves for type declarations.
- **Seed the starting state via a pull, not a create-push.** `putServerItem` +
  `fetchAndReconcile` yields a clean row at a known server version with no outbox
  residue, in two lines. It also keeps the other 18 scenarios independent of the
  create-push path, so a create regression fails one test rather than cascading.
- **`seedAcceptedItem` takes a `ShoppingListItem`; a `serverItem({...})` builder
  supplies the defaults.** The helper's job is establishing state, not mirroring
  the item's field list — passing the domain type keeps the two concerns apart and
  stops the signature drifting as the model changes. The builder exists because
  `ShoppingListItem` has seven required fields and no `copyWith`, and it serves
  the out-of-band bumps as well as the seeds, so the defaults live in one place.
- **`removeServerItem` added to the fake for the 404-gone arrange.** The fake's
  `deleteItem` returns silently when the item is missing (faithful to the real
  repository, where DELETE treats 404 as success), so it cannot stage a
  vanished item. A state-setter symmetric with `putServerItem` reads as the
  out-of-band remote deletion it represents.
- **Two extra scenarios beyond the HLD catalog: transient→failure escalation and
  `deleteAllChecked`.** Both are named in `requirements.md` (the
  `SyncStatus.failure` edge case and the bulk operations) and neither is covered
  by the catalog's 17. Total 19, marginally over the HLD's "roughly 15–18".
- **Push-driven tests never assert `SyncStatus.notSyncing`.** `_pushHeadEntry`
  sets `syncing` on entry and only `_drain` resets to `notSyncing` on a completed
  loop, so a directly-driven push legitimately ends in `syncing`. Only the
  drain-driven scenarios (6, 18, 19) assert `notSyncing`; the stall scenarios
  assert `offline` / `failure`.
- **Rejection events (`sync.rejections`) are not asserted.** The mandated surfaces
  are the four in the requirements; the stream is a broadcast controller whose
  delivery is asynchronous, so asserting it would require pumping the event queue
  for no coverage the store surfaces don't already give.
- **`outboxEmpty()` kept as T2 wrote it; `outboxCount(listId)` added for the four
  divergence scenarios** that must assert a queue of exactly one entry rather than
  emptiness.
- **Multi-list scenarios drive `fanOutPending()` directly, not `start()`.**
  `start()` additionally registers the lifecycle observer, which is out of scope
  (no timing/lifecycle assertions); the fan-out method is the awaitable seam T1
  exposed for exactly this.
- **Out-of-band remote changes are staged inline, not behind a helper.** A
  `backend.putServerItem(listId, serverItem(version: 1, name: 'Oat milk'))` is one
  self-documenting line at the point it matters; hiding it behind a
  `bumpServerItem` would obscure the divergence each ordering test turns on.

## Assumptions to verify

- **Assumption:** `_maxRetries` is 5 and the sixth consecutive transient failure
  is what sets `SyncStatus.failure`, so scenario 13 needs exactly six pushes.
  **If wrong:** the loop count changes; assert against the constant rather than
  hard-coding if it proves brittle.
- **Assumption:** `reconcileFromServer`'s strict `>` version gate leaves an
  already-adopted row untouched, so the trailing reconcile in scenario 17 is a
  genuine no-op.
  **If wrong:** scenario 17's end state would show a redundant rebuild; the
  assertion set stays the same but the rationale in the test comment changes.
- **Assumption:** a directly-driven `pushNextEntry` leaves the status at
  `syncing` on success (no reset outside `_drain`).
  **If wrong:** the push-driven scenarios can assert status too, rather than
  omitting it.
- **Assumption:** `deleteAllChecked` operates off the resident cache and skips
  already-tombstoned rows, queueing exactly one delete per checked item — two
  entries for scenario 6's three seeded items.
  **If wrong:** scenario 6's `outbox` expectation and drain length change.
- **Assumption:** `putServerItem` with an id already present replaces it, making
  it usable as the out-of-band version bump in scenarios 7, 8, 14, 16 and 17.
  **If wrong:** add an explicit replace path to the fake.
- **Assumption:** the fake's `createItem` honouring `snapshot.checked` (the real
  POST body omits `checked`) is harmless, because a freshly created item is always
  `checked: false`.
  **If wrong:** only if a scenario creates a pre-checked item — none do.
- **Assumption:** `store.watch(listId)` requires a prior `openList`, which
  `seedAcceptedItem` performs, and the multi-list scenarios must call explicitly
  for both lists.
  **If wrong:** `visible` reads throw a null-check error on the missing notifier.
- **Assumption:** consecutive awaited calls to `pushNextEntry` /
  `fetchAndReconcile` / `requestDrain` in one test never deadlock on the per-list
  sync lock, since each is awaited to completion before the next begins.
  **If wrong:** the ordering tests hang rather than fail — watch for timeouts
  first if they do.

## Required reading for implementation planning

- `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart` — the
  approved structure to extend: doubles, `setUp`/`tearDown`, the four read
  helpers, and the reference test's assertion style.
- `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart` —
  decides most end states: `reconcileFromServer`'s dirty guard and strict `>`
  gate, `reconcileAck`'s "fields untouched, dirty iff entries remain",
  `cascadeDiscard`'s un-tombstoning, `discardItem`'s hard removal, and
  `deleteAllChecked`'s selection rule.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — the entry
  points driven and the `_pushHeadEntry` outcome mapping (`PushResult`, status
  transitions, `_maxRetries`).
- `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` —
  `writeItemDroppingEntry`'s remaining-entry count (scenario 15) and the outbox
  FIFO reads backing the `outbox` surface.
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — the
  exception contract the fake reproduces, and DELETE's 404-as-success that
  motivates `removeServerItem`.
- `HLD.md` > Feature areas > Scenario catalog, and > Approach > Chosen —
  single-ordering-per-test and the four-surface rule.
- `docs/ADRs/0005-shopping-list-sync-test-seam.md` — what is and isn't a coverage
  target (the mid-drain coalescing guard stays out).
- `docs/mobile/standards/widget-testing.md` — the `test/support/` discipline
  behind keeping everything in one file.
