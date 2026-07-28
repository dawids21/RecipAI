# T3: Write the full scenario catalog — Implementation Plan

**Date:** 2026-07-28
**Status:** draft

## Required reading

**Docs & standards** (from `docs/INDEX.md`)
- `docs/mobile/standards/widget-testing.md` — `test/support/` holds type
  declarations only and harnesses are not built early; this is why all 19
  scenarios, the doubles and the helpers stay in one file.
- `docs/mobile/standards/state-management.md` — the `ValueNotifier` / `dispose()`
  contract behind the `visible` surface and the `tearDown` graph T2 established.

**Design & ADRs**
- `plans/T3-task-design.md` > Scenario catalog — the 19 scenarios and their
  explicit end states; this plan implements it verbatim.
- `plans/T3-task-design.md` > Pseudo-code — scenarios 8, 13 and 15, the three
  with non-obvious bookkeeping.
- `plans/T3-task-design.md` > Decisions made — settled points not to re-open
  (seed via pull, no `sync.rejections` assertions, no `notSyncing` on
  push-driven tests, `fanOutPending` over `start()`).
- `HLD.md` > Approach > Chosen — single-ordering-per-test and the four-surface
  rule.
- `docs/ADRs/0005-shopping-list-sync-test-seam.md` — the mid-drain coalescing
  guard is deliberately not a coverage target.

**Code to mirror**
- `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart` —
  the approved structure: inline doubles, `setUpAll`/`setUp`/`tearDown`, the four
  read helpers, and the reference test's assertion style. Every new test copies
  this shape.
- `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart` —
  decides most end states: `reconcileFromServer`'s dirty guard and strict `>`
  gate (line 178), `reconcileAck`'s "fields untouched, dirty iff entries remain",
  `cascadeDiscard`'s un-tombstoning (`pendingDelete: false, dirty: false`),
  `discardItem`'s hard removal, `deleteAllChecked`'s `!pendingDelete && checked`
  selection.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` —
  `_pushHeadEntry`'s outcome mapping, `_maxRetries = 5`, and where each
  `SyncStatus` transition happens.
- `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` —
  `writeItemDroppingEntry`'s `_countOutboxForItem` (scenario 15's mid-way
  `dirty == true`), and the exact shape the new `outboxCount` mirrors: a
  `rawQuery('SELECT COUNT(*) AS c ...')` read through `Sqflite.firstIntValue`,
  placed with the other public outbox reads (`nextOutboxEntry`,
  `listIdsWithOutbox`).
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — the
  exception contract the fake reproduces and DELETE's 404-as-success that
  motivates `removeServerItem`.

## File inventory

- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` —
  add `Future<int> outboxCount(String listId)`, a public per-list outbox count.
- **MODIFY** `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart`
  — the entire T3 deliverable: `removeServerItem` on the fake, three new inline
  helpers, `outboxEmpty` repointed at the DAO, four `group()` blocks, 18 new
  tests.

_No other file changes._ No dependency and no `test/support/` entry. The DAO
addition is the one production change; the test-only scope lines have been
removed from `task-design.md` > Components and `tasks.md` > T3 > Out of scope so
the upstream docs match.

## Step-by-step plan

Each step ends with the file compiling and the suite green, so each is a clean
commit. Run every command from `mobile/`.

1. **DAO outbox count + harness additions + regroup the reference test** — add
   the per-list count to the DAO, the fake's `removeServerItem`, the inline
   helpers, and wrap T2's existing test in the first `group()`.
   - Files: `mobile/lib/features/shopping_list/shopping_list_item_dao.dart`,
     `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart`
   - DAO: `Future<int> outboxCount(String listId)` beside `nextOutboxEntry` /
     `listIdsWithOutbox`, mirroring the private `_countOutboxForItem` —
     `rawQuery('SELECT COUNT(*) AS c FROM outbox WHERE list_id = ?', [listId])`
     returned through `Sqflite.firstIntValue(...) ?? 0`. No new import
     (`package:sqflite/sqflite.dart` is already there) and no store passthrough:
     the sync service has no use for it, and the test holds the `dao` directly.
   - `removeServerItem(String listId, String itemId)` next to `putServerItem`:
     `_items[listId]?.remove(itemId);`
   - `serverItem({String id = 'server-1', String name = 'Milk', double? quantity
     = 2, String? unit = 'l', bool checked = false, double position = 1.0, int
     version = 0})` → a `ShoppingListItem`. Top-level function above `main()`
     (it closes over nothing).
   - `seedAcceptedItem(String listId, ShoppingListItem item)` → `Future<String>`,
     inline in `main()` alongside the four read helpers: `backend.putServerItem`
     → `await store.openList(listId)` → `await sync.fetchAndReconcile(listId)` →
     `(await dbItems(listId)).firstWhere((r) => r.serverId == item.id).localId`.
     Look up by `serverId`, not `.single` — scenario 6 seeds three items.
   - `outboxCount(String listId)` → `Future<int>`, inline in `main()` beside the
     other read helpers: `dao.outboxCount(listId)`.
   - Repoint `outboxEmpty` at the same method — `Future<bool> outboxEmpty(String
     listId) async => await dao.outboxCount(listId) == 0`. It gains a `listId`
     parameter and becomes **per-list**, where T2's version was global
     (`listIdsWithOutbox().isEmpty`); every `outboxEmpty()` call site therefore
     passes a list id.
   - Wrap the existing test in `group('store-op happy paths', () { ... })`. Its
     body changes by exactly one line: `outboxEmpty()` → `outboxEmpty(listId)`.
   - Verify: `flutter test test/features/shopping_list/` → 1 test green;
     `flutter analyze` clean; full `flutter test` green (the DAO gains a method,
     changes none).

2. **`group('store-op happy paths')` — scenarios 2–6** (5 new tests).
   - Files: same test file
   - Scenarios 2–5 share one arrange: `final localId = await
     seedAcceptedItem(listId, serverItem());` then the store op, then
     `expect(await sync.pushNextEntry(listId), PushResult.pushed);`, then the
     four surfaces at the design's stated end state.
   - `applyEdit` requires `name`, `quantity` **and** `unit` — the design's
     shorthand omits them. Scenario 2 passes `name: 'Bread', quantity: 1, unit:
     null` (the end state asserts all three changed). Elsewhere restate the
     seed's `quantity: 2, unit: 'l'` so only the named field moves.
   - Scenario 6 seeds three items (`serverItem(id: 'server-1', checked: true)`,
     `serverItem(id: 'server-2', name: 'Bread', position: 2)`,
     `serverItem(id: 'server-3', name: 'Eggs', position: 3, checked: true)`),
     then `deleteAllChecked` → `await sync.requestDrain(listId)`. Assert `db`
     and `backend` hold `server-2` only, `visible` is that one item,
     `outboxEmpty(listId)`, and `sync.syncStatusFor(listId).value ==
     SyncStatus.notSyncing`. Select rows by `serverId`/`name`, never by index —
     `visible` is unsorted cache-insertion order and `localId`s are random uuids.
   - Verify: `flutter test test/features/shopping_list/` → 6 green.

3. **`group('push outcomes')` — scenarios 7–13** (7 new tests).
   - Files: same test file
   - 7 and 8 stage the out-of-band bump inline:
     `backend.putServerItem(listId, serverItem(version: 1, name: 'Oat milk'))`
     (same `id`, so it replaces). Both push to `PushResult.pushed` — a 412 is a
     resolved outcome, not a stall.
   - 9 uses `backend.removeServerItem(listId, 'server-1')` before the edit; the
     update path throws `ItemDiscardedException(gone)` from genuine missing
     state.
   - 10 sets `backend.rejectWrites = true` before `applyCreate` — no seed, so it
     needs its own `await store.openList(listId)`.
   - 11 asserts `PushResult.stalled`, `outboxCount(listId) == 1`, and
     `SyncStatus.offline`.
   - 12 flips `transientFailure` true → push (`stalled`) → false → push
     (`pushed`); the backoff timer armed by the first push is inert and never
     fires.
   - 13 loops `for (var i = 0; i < 6; i++) expect(await
     sync.pushNextEntry(listId), PushResult.stalled);` — attempts 1–5 arm
     backoff (`attempt <= _maxRetries`), the 6th sets `SyncStatus.failure`.
     `_maxRetries` is private, so 6 is hard-coded with a comment naming it.
   - Verify: `flutter test test/features/shopping_list/` → 13 green.

4. **`group('ordering')` — scenarios 14–17** (4 new tests).
   - Files: same test file
   - 14, 16 and 17 share the same arrange (seed v0 → `applyEdit(name: 'Bread',
     quantity: 2, unit: 'l')` → out-of-band bump to v1 `'Oat milk'`) and differ
     only in the step order that follows. Write the arrange out in each test —
     do not extract it; the ordering *is* the subject.
   - 16 and 17 state identical end states independently, with no cross-reference
     between the two test bodies.
   - 15 has no seed: `openList` → `applyCreate('Milk', 2, 'l')` → read the new
     `localId` from `visibleItems(listId).single.localId` → `applyEdit(name:
     'Oat milk', quantity: 1, unit: 'l')`. Assert mid-way after push #1 that
     `serverId != null`, `lastAckedVersion == 0` and `dirty` is **still true**,
     then push #2 and assert the converged end state.
   - Verify: `flutter test test/features/shopping_list/` → 17 green.

5. **`group('multi-list')` — scenarios 18–19** (2 new tests) and final sweep.
   - Files: same test file
   - Both open `'list-1'` and `'list-2'` explicitly (no seed helper runs, so
     nothing else creates the notifiers) and `applyCreate` on each.
   - 18 awaits `requestDrain` per list; 19 awaits `sync.fanOutPending()`. Assert
     the same converged end state in both, plus `notSyncing` and
     `outboxEmpty(listId)` for each list — now a genuine per-list check rather
     than one global one.
   - Confirm the four `group()` names and 19 test names read as the design's
     catalog, and that no test runs two orderings.
   - Verify: `flutter test test/features/shopping_list/` → 19 green;
     `flutter analyze` clean; full `flutter test` still green.

## Test plan

The deliverable *is* the test plan. Cases, by group, in
`shopping_list_sync_service_test.dart`:

**`store-op happy paths`**
1. `create is pushed and accepted, converging all four surfaces` (T2's, moved in)
2. `edit is pushed and accepted` — `db {Bread, quantity 1, unit null,
   lastAckedVersion 1, dirty false}`, `backend {Bread, v1}`, `visible Bread`,
   outbox empty
3. `check is pushed and accepted` — `db {checked true, lastAckedVersion 1, dirty
   false}`, `backend {checked true, v1}`, outbox empty
4. `reorder is pushed and accepted` — `db {position 2.5, lastAckedVersion 1,
   dirty false}`, `backend {position 2.5, v1}`, outbox empty
5. `delete is pushed and accepted` — `db`, `backend`, `visible` all empty, outbox
   empty
6. `deleteAllChecked queues every checked item and drains clean` — `db` and
   `backend` hold `server-2` only, `visible` is that item, outbox empty, status
   `notSyncing`

**`push outcomes`**
7. `412 on an update cascade-discards to the winner` — `db {Oat milk,
   lastAckedVersion 1, dirty false}`, `backend` unchanged at v1, `visible Oat
   milk`, outbox empty (the local edit is intentionally lost)
8. `412 on a delete un-tombstones the item` — `db` row present with
   `{pendingDelete false, lastAckedVersion 1, dirty false}` and the winner's
   fields, `backend` still holds it at v1, `visible` shows it again, outbox empty
9. `404-gone hard-removes the local row` — `db`, `backend`, `visible` empty,
   outbox empty
10. `400/403 rejected discards the queued create` — `db`, `backend`, `visible`
    empty, outbox empty
11. `offline stall leaves the entry queued` *(divergence)* — `stalled`; `db
    {Bread, dirty true, lastAckedVersion 0}`, `backend {Milk, v0}`, `visible
    Bread`, `outboxCount == 1`, status `offline`
12. `transient failure then retry succeeds` — converged as case 2
13. `transient failures escalate to SyncStatus.failure` *(divergence)* — six
    `stalled` pushes; status `failure`, `db {Bread, dirty true,
    lastAckedVersion 0}`, `backend` at v0, `outboxCount == 1`

**`ordering`**
14. `a still-dirty local edit is not clobbered by a reconcile` *(divergence)* —
    `db {Bread, dirty true, lastAckedVersion 0}`, `backend {Oat milk, v1}`,
    `visible Bread`, `outboxCount == 1`
15. `a queued create-then-edit pushes both entries, clearing dirty only at the
    end` — mid-way `dirty == true` with `serverId` set and `lastAckedVersion 0`;
    end `db {Oat milk, lastAckedVersion 1, dirty false}`, `backend {Oat milk,
    v1}`, outbox empty
16. `permutation A: reconcile before push` — `db {Oat milk, lastAckedVersion 1,
    dirty false}`, `backend` v1, `visible Oat milk`, outbox empty
17. `permutation B: push before reconcile` — identical end state, stated
    independently; the trailing reconcile is a no-op under the strict `>` gate

**`multi-list`**
18. `two lists drain independently` — per list one clean row with a `serverId`,
    one backend item, one visible item, and `outboxEmpty(listId)`; both statuses
    `notSyncing`
19. `the start/resume fan-out drains every list with a pending outbox` — same
    converged end state, reached by awaiting `fanOutPending()`

**Integration tests** — _N/A — T3 is unit-level; the sync path is exercised
against a fake backend and an in-memory DB, with no cross-service integration._

**Flutter widget tests** — _N/A — plain `test()` bodies; no widget tree is
pumped (only `TestWidgetsFlutterBinding` for the ffi binding)._

**Manual verification** — _N/A — no production code changes._

## Verification checklist

- [ ] `flutter analyze` is clean (no unused helper / unused import warnings).
- [ ] `flutter test test/features/shopping_list/` runs 19 tests green.
- [ ] Full `flutter test` suite still green.
- [ ] `git diff` touches only the test file and
      `mobile/lib/features/shopping_list/shopping_list_item_dao.dart`, and the
      DAO diff is purely additive (one new method, nothing else edited) — no
      `pubspec.yaml`, no `test/support/`.
- [ ] `tasks.md` > T3 "How to verify" succeeds: the catalog is green and the
      scenario list matches the HLD catalog's coverage areas (store-op happy
      paths, push outcomes, ordering edge cases, multi-list).
- [ ] Every test fixes exactly one ordering and asserts its own explicit end
      state (no test compares two orderings; 16 and 17 state theirs separately).
- [ ] Each test asserts all four surfaces, with `outboxCount(listId) == 1` in
      place of `outboxEmpty(listId)` for the divergence scenarios (11, 13, 14).
- [ ] `task-design.md` > Assumptions to verify are all resolved (see Risks).
- [ ] The suite passes twice in a row and in a random order
      (`flutter test test/features/shopping_list/ --test-randomize-ordering-seed=random`)
      — proves no cross-test leakage through the fake or the shared `db`.

## Risks surfaced during planning

All eight of `task-design.md` > Assumptions to verify were confirmed by reading
the production sources during this plan, and none needs in-flight verification:
`_maxRetries = 5` with the 6th attempt escalating
(`shopping_list_sync_service.dart:49,311`); the strict `>` version gate
(`shopping_list_item_store_service.dart:178`); `_pushHeadEntry` setting `syncing`
with only `_drain` resetting it (`:274,233`); `deleteAllChecked` selecting
`!pendingDelete && checked` off the resident cache (`:103–117`); `putServerItem`
replacing by id; `createItem` honouring `snapshot.checked`; `watch` requiring a
prior `openList` (`:42–44`); and no lock re-entrancy across sequentially awaited
entry points. The remaining risks are new:

- **Risk:** routing `outboxEmpty` through `dao.outboxCount` changes it from a
  global check (`listIdsWithOutbox().isEmpty`) to a per-list one, so it gains a
  `listId` parameter.
  **Why it matters:** T2's reviewed reference test was supposed to move into the
  first `group()` untouched; it now changes by one line, and scenarios 18/19 lose
  the incidental cross-list assertion the global version gave them.
  **Mitigation:** make both explicit — the one-line change is called out in Step
  1, and 18/19 assert `outboxEmpty(listId)` per list, which is stricter about
  *which* list drained than the single global check it replaces.

- **Risk:** the design writes store mutations in shorthand (`applyEdit(name:
  'Bread')`), but `applyEdit` requires `name`, `quantity` *and* `unit`, and
  `applyChecked` / `applyReorder` / `applyDelete` all require a `localId`.
  **Why it matters:** passing `quantity`/`unit` carelessly silently changes the
  asserted end state — an edit meant to move only `name` would also blank `unit`.
  **Mitigation:** restate the seed's `quantity: 2, unit: 'l'` on every edit that
  is not deliberately changing them; scenario 2 is the only one where all three
  move.

- **Risk:** `visibleItems()` returns `_cache.values` unsorted (cache-insertion
  order) and `localId`s are random uuids.
  **Why it matters:** scenario 6's three-item and scenarios 18/19's two-list
  assertions would be order-dependent and flaky if written against indices.
  **Mitigation:** select by `serverId` or `name` in every multi-row assertion;
  `.single` only where exactly one row is expected.
