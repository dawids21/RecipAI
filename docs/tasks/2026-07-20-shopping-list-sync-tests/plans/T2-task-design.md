# T2: Build test doubles + one reviewed reference test — Task Design

**Date:** 2026-07-22
**Status:** draft

## Summary

Stand up the deterministic unit-test harness for the shopping-list item sync
path and one reference scenario. The unit under test is the real
`ShoppingListSyncService` + `ShoppingListItemStoreService` +
`ShoppingListItemDao` over an in-memory `sqflite_common_ffi` database, composed
by hand with three inlined test doubles — a `_TestScheduler` that never fires, a
stateful `FakeShoppingListItemRepository`, and a mocked `AuthRepository` under a
real `AuthService`. DB creation moves behind a new
`ShoppingListItemDatabaseFactory`, whose test subclass returns the in-memory ffi
database. One test (`create` pushed and accepted) drives T1's `pushNextEntry`
step and asserts the explicit end state across all four surfaces. The doubles'
and assertions' *shape* is what the user reviews before T3.

## Components and responsibilities

- **`shopping_list_sync_service_test.dart`** (CREATE,
  `mobile/test/features/shopping_list/`) — the whole T2 deliverable: the inlined
  doubles + test DB factory, the `setUpAll`/`setUp`/`tearDown` lifecycle, and the
  single reference test. Per the widget-testing "no early harness" rule,
  everything behaviour-bearing lives here, not in a shared file.
- **`_TestScheduler` / `_TestScheduledTimer`** (CREATE, inline) — implement
  `Scheduler` / `ScheduledTimer`; `periodic`/`oneShot` return an inert handle
  whose `cancel()` is a no-op and **never invoke a callback**, so no
  poll/drain/backoff/offline timer fires mid-test.
- **`FakeShoppingListItemRepository`** (CREATE, inline) — `implements
  ShoppingListItemRepository`; holds authoritative per-list item state and
  enforces the backend's optimistic-concurrency contract (create→v0,
  update→version bump, stale `baseVersion`→412 winner, missing→404-gone). Kept
  deliberately thin: a direct state-setter for out-of-band remote changes, three
  plain `bool` fault flags, and one accessor for the backend surface. No
  counters, no per-list state.
- **`_TestShoppingListItemDatabaseFactory`** (CREATE, inline) — `extends`
  the production `ShoppingListItemDatabaseFactory` and overrides only `open()` to
  return a `sqflite_common_ffi` in-memory database, **reusing the inherited
  schema** so test and production DDL can never drift. Lives in test code because
  it imports the `sqflite_common_ffi` dev dependency.
- **`ShoppingListItemDatabaseFactory`** (CREATE,
  `mobile/lib/features/shopping_list/shopping_list_item_database_factory.dart`) —
  owns DB creation: `open()` opens the on-device database, and a `@protected
  createSchema` holds the items + outbox DDL (moved out of the DAO). Production
  and test factories differ only in `open()`; the schema is single-sourced here.
- **`ShoppingListItemDao`** (MODIFY,
  `mobile/lib/features/shopping_list/shopping_list_item_dao.dart`) — drop
  `openShoppingListDatabase` and `_onCreate` (moved to the factory) and their
  `dart:io` / `path_provider` imports; the DAO becomes a pure wrapper over an
  injected `Database`.
- **`ShoppingListItemStoreService`** (MODIFY,
  `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart`) —
  `open()` builds its `Database` via `const ShoppingListItemDatabaseFactory()`
  instead of the removed DAO static. `main.dart`'s `.open()` call is unchanged.
- **`pubspec.yaml`** (MODIFY, `mobile/`) — add `sqflite_common_ffi` to
  `dev_dependencies` (acceptance criterion; not currently present).
- **`test/support/mocks.dart`** (REUSE, no change) — `MockAuthRepository` is
  already declared here and is reused. The stateful fake, `_TestScheduler`, and
  test DB factory carry behaviour, so they stay inline in the test file, not in
  `support/` (type-declarations only).

## Interfaces and method signatures

Production DB factory (schema single-sourced here):

```
// shopping_list_item_database_factory.dart
class ShoppingListItemDatabaseFactory {
  const ShoppingListItemDatabaseFactory();

  Future<Database> open() async {                 // on-device, was DAO.openShoppingListDatabase
    final dir = await getApplicationDocumentsDirectory();
    final path = '${dir.path}${Platform.pathSeparator}shopping_list_items.db';
    return openDatabase(path, version: 1, onCreate: createSchema);
  }

  @protected
  Future<void> createSchema(Database db, int version) async { /* items + outbox DDL */ }
}
```

Test DB factory (inline; overrides open, inherits the schema):

```
class _TestShoppingListItemDatabaseFactory extends ShoppingListItemDatabaseFactory {
  const _TestShoppingListItemDatabaseFactory();
  @override
  Future<Database> open() => databaseFactoryFfi.openDatabase(
        inMemoryDatabasePath,
        options: OpenDatabaseOptions(version: 1, onCreate: createSchema),
      );
}
```

Test scheduler double:

```
class _TestScheduler implements Scheduler {
  ScheduledTimer periodic(Duration d, void Function() cb) => const _TestScheduledTimer();
  ScheduledTimer oneShot(Duration d, void Function() cb) => const _TestScheduledTimer();
}
class _TestScheduledTimer implements ScheduledTimer {
  const _TestScheduledTimer();
  void cancel() {}
}
```

Stateful fake backend (thin — plain bool flags, no counters/per-list state):

```
class FakeShoppingListItemRepository implements ShoppingListItemRepository {
  // --- production interface (faithful) ---
  Future<List<ShoppingListItem>> fetchServerItems(String listId, String? idToken);
  Future<ShoppingListItem> createItem(String listId, OutboxPayload snapshot, String? idToken);
  Future<ShoppingListItem> updateItem(String listId, String itemId,
      {required int baseVersion, required OutboxPayload snapshot, required String? idToken});
  Future<void> deleteItem(String listId, String itemId, int baseVersion, String? idToken);
  void dispose() {}                       // no-op; no http.Client to close

  // --- test controls (all global bools, toggled by the test) ---
  bool offline = false;                   // every call throws the network exception
  bool transientFailure = false;          // writes throw a generic 5xx (retry branch)
  bool rejectWrites = false;              // writes throw ItemDiscardedException(rejected)

  void putServerItem(String listId, ShoppingListItem item);  // out-of-band remote change
  List<ShoppingListItem> itemsFor(String listId);            // backend-surface accessor
}
```

Four-surface read helpers (thin wrappers, inline in the test file — not a shared
harness):

```
Future<List<LocalShoppingListItem>> dbItems(String listId);   // dao.readItems
List<ShoppingListItem>              backendItems(String listId); // backend.itemsFor
List<LocalShoppingListItem>         visibleItems(String listId); // store.watch(listId).value
Future<bool>                        outboxEmpty();               // dao.listIdsWithOutbox().isEmpty
```

## Data flow

**Test lifecycle.**
1. `setUpAll`: `TestWidgetsFlutterBinding.ensureInitialized()` (the sync service
   mixes in `WidgetsBindingObserver` and touches `WidgetsBinding.instance` in
   `dispose()`); `sqfliteFfiInit()` once.
2. `setUp`: build a fresh in-memory DB and wire the graph by hand —
   `db = await _TestShoppingListItemDatabaseFactory().open()` → `dao =
   ShoppingListItemDao(db)` → `store = ShoppingListItemStoreService(dao: dao)` →
   stub `MockAuthRepository` (`watchAuthState` → empty stream, `getIdToken` →
   `'test-token'`) → `authService = AuthService(authRepository: mockAuth)` →
   `backend = FakeShoppingListItemRepository()` → `scheduler = _TestScheduler()` →
   `sync = ShoppingListSyncService(itemRepository: backend, store: store,
   authService: authService, scheduler: scheduler)`. No `getIt`, no
   `setupShoppingList` (it would also fire `start()`).
3. `tearDown`: `sync.dispose(); store.dispose(); authService.dispose(); await
   db.close();`.

**Reference scenario — create pushed and accepted.**
1. `store.openList(listId)` → list resident, notifier seeded `[]`.
2. `store.applyCreate(listId, name: 'Milk', …)` → cache + notifier updated, one
   `create` outbox entry, row `dirty`, `serverId == null`.
3. `sync.pushNextEntry(listId)` → acquires the real sync lock → reads head entry
   → `backend.createItem` mints `serverId`, returns winner at `version 0` →
   `store.reconcileAck` adopts id/version, drops the entry, clears `dirty`.
   Returns `PushResult.pushed`.
4. Assert the four surfaces at their explicit end state (below).

## Pseudo-code

Fake backend — optimistic concurrency + fault flags (the fidelity target):

```
_maybeFail(listId):                         # global flags, checked in order
    if offline:           throw ShoppingListNetworkException()
    if transientFailure:  throw Exception('transient 5xx')            # -> retry branch
    if rejectWrites:      throw ItemDiscardedException(rejected)      # 400/403

fetchServerItems(listId, _):
    if offline: throw ShoppingListNetworkException()
    return items[listId].values.toList()

createItem(listId, snapshot, _):
    _maybeFail(listId)
    item = ShoppingListItem(id: nextId(), ...snapshot, version: 0)
    items[listId][item.id] = item
    return item

updateItem(listId, itemId, baseVersion, snapshot, _):
    _maybeFail(listId)
    cur = items[listId][itemId]
    if cur == null:                 throw ItemDiscardedException(gone)      # 404
    if cur.version != baseVersion:  throw ItemVersionConflictException(cur) # 412 winner
    winner = ShoppingListItem(id: itemId, ...snapshot, version: cur.version + 1)
    items[listId][itemId] = winner
    return winner

deleteItem(listId, itemId, baseVersion, _):
    _maybeFail(listId)
    cur = items[listId][itemId]
    if cur == null:                 return                                  # 404/204 no-op
    if cur.version != baseVersion:  throw ItemVersionConflictException(cur) # 412
    items[listId].remove(itemId)
```

A transient-then-retry scenario (T3) flips `transientFailure` on, pushes (stalls),
flips it off, pushes again (succeeds) — no counter needed.

Reference test skeleton (shape every later test mirrors; surfaces read via the
thin helpers):

```
test('create is pushed and accepted, converging all four surfaces', () async {
    await store.openList(listId)
    await store.applyCreate(listId, name: 'Milk', quantity: 2, unit: 'l')

    expect(await sync.pushNextEntry(listId), PushResult.pushed)

    row = (await dbItems(listId)).single
    expect(row.serverId, isNotNull); expect(row.lastAckedVersion, 0); expect(row.dirty, isFalse)
    expect(backendItems(listId).single.name, 'Milk')
    expect(visibleItems(listId).single.serverId, row.serverId)
    expect(await outboxEmpty(), isTrue)
})
```

## Decisions made

- **DB creation moves behind `ShoppingListItemDatabaseFactory`; the test
  subclass swaps only `open()`.** Replaces the DAO's static `openShoppingList
  Database`/`_onCreate`. Both factories share the `@protected createSchema`, so
  the in-memory test DB is built from the same DDL as production — no drift — and
  the test's ffi/in-memory concern stays out of `lib`.
- **Fake backend is thin, with plain `bool` fault flags.** `offline`,
  `transientFailure`, `rejectWrites` are global booleans the test toggles; 412 and
  404-gone still fall out of genuine version/state. No counters and no per-list
  fault state — the test drives retry by toggling a flag between pushes.
- **Fake `implements ShoppingListItemRepository` (not a mocktail mock).** The
  repository is a concrete class, but `implements` yields its public interface
  without running its constructor (no `http.Client`/`_baseUrl`), giving a
  hand-written stateful fake — the fidelity the HLD requires that a mock can't.
- **Doubles + test factory inlined in the test file; only `MockAuthRepository` in
  `support/`.** They carry behaviour, so per the widget-testing standard
  (`support/` = type declarations only) and tasks.md ("no early harness") they
  stay in the test file. T3 may extract the reusable ones (fake, test factory,
  scheduler) once repetition shows the shape.
- **Fresh in-memory DB opened per test (not once with truncation between tests).**
  The store's cache/notifiers and the sync service are rebuilt per test anyway
  (they can't leak state across tests), so a shared DB would save nothing on the
  dominant cost; opening an in-memory SQLite DB and running two `CREATE TABLE`s is
  sub-millisecond, so per-test isolation is effectively free.
- **Compose the graph by hand, not via `getIt`/`setupShoppingList`.** This is a
  unit test of the sync path, and `setupShoppingList` would register singletons
  *and* fire `unawaited(start())` — an un-driven fan-out. Direct construction
  keeps the ordering fully under test control.
- **Reference scenario = create, push-only, via `pushNextEntry`.** Matches
  tasks.md's chosen store-op happy path; drives the single-entry step directly
  (no drain loop) for precise ordering, as the HLD prescribes for push scenarios.
- **Four thin read helpers wrap each surface** (`dbItems`, `backendItems`,
  `visibleItems`, `outboxEmpty`), inline in the test file. They keep each
  assertion a one-liner and name the surface at the call site; the underlying
  reads stay on the real objects (`dao.readItems`, `backend.itemsFor`,
  `store.watch(...).value`, `dao.listIdsWithOutbox`). Not a shared harness — T3
  extracts them only if repetition warrants.
- **`TestWidgetsFlutterBinding.ensureInitialized()` in `setUpAll`.** The sync
  service's `WidgetsBindingObserver` mix-in reaches `WidgetsBinding.instance` in
  `dispose()`; plain `test()` needs the binding initialized. Tests use `test()`,
  not `testWidgets()` (no widget pumping).
- **Deterministic server-id minting (monotonic counter in the fake).** Stable ids
  make backend-surface assertions readable.

## Assumptions to verify

- **Assumption:** moving `openShoppingListDatabase`/`_onCreate` out of the DAO is
  fully contained — the only caller is `ShoppingListItemStoreService.open()`
  (itself called only from `main.dart:43`), and `_onCreate` is DAO-internal.
  **If wrong:** another caller would need repointing at the factory.
- **Assumption:** a `@protected createSchema` on `ShoppingListItemDatabaseFactory`
  is reachable from the test subclass's `open()` (protected = subclass-accessible
  across libraries) and passable as an `onCreate` tear-off.
  **If wrong:** promote it to a shared top-level `createShoppingListSchema(db,
  version)` function both factories call.
- **Assumption:** `class FakeShoppingListItemRepository implements
  ShoppingListItemRepository` compiles by overriding only the public members,
  without invoking the concrete class's field initializers (`_client`,
  `_baseUrl`).
  **If wrong:** fall back to `extends Mock implements ...` with hand-written
  stateful `thenAnswer`s, or extract a repository interface.
- **Assumption:** `sqflite_common_ffi`'s in-memory DB honours `transaction()` and
  `INTEGER PRIMARY KEY AUTOINCREMENT` (outbox `seq`) identically to on-device
  sqflite, which the DAO relies on for atomic writes and FIFO ordering.
  **If wrong:** DAO behaviour under test would diverge from production — but ffi
  wraps the same SQLite engine, so risk is low.
- **Assumption:** the real `AuthService` constructor tolerates a
  `watchAuthState()` stubbed as an empty stream, and `idToken` resolves purely via
  `getIdToken()` regardless of emitted users.
  **If wrong:** stub `watchAuthState` to emit `null` first (`Stream.value(null)`).
- **Assumption:** calling `sync.dispose()` without a prior `start()` (so
  `addObserver` never ran) is safe — `removeObserver` on an unregistered observer
  is a no-op.
  **If wrong:** call `start()` in `setUp` (and drive/await its fan-out) or skip
  the sync `dispose()` in `tearDown`.
- **Assumption:** each `open()` on the test factory yields an isolated in-memory
  database, not one shared across tests in the run.
  **If wrong:** use a unique db name per test, or assert-close between tests.

## Required reading for implementation planning

- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — the T1
  seams the test drives (`pushNextEntry`, `PushResult`, `fetchAndReconcile`,
  `dispose`) and the `WidgetsBindingObserver` mix-in behind the binding decision.
- `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart` — the
  store API the test uses/asserts (`openList`, `watch`, `applyCreate`,
  `reconcileAck`) and its `open()` that switches to the factory.
- `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` — the DDL being
  moved to the factory, and `readItems`/`nextOutboxEntry`/`listIdsWithOutbox` that
  back the DB + outbox surfaces.
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — the
  interface the fake implements and the exception contract it reproduces
  (`ItemVersionConflictException`, `ItemDiscardedException`/`DiscardReason`,
  `ShoppingListNetworkException`, `OutboxPayload`).
- `mobile/lib/features/shopping_list/shopping_list_item.dart` and
  `local_shopping_list_item.dart` — building winners / asserting rows.
- `mobile/lib/features/auth/auth_service.dart` and `auth_repository.dart` — real
  `AuthService` over `MockAuthRepository`; which methods to stub.
- `mobile/lib/core/scheduler.dart` — `Scheduler`/`ScheduledTimer` the test double
  implements.
- `mobile/lib/main.dart` — confirms `ShoppingListItemStoreService.open()` is the
  sole `.open()` caller, unaffected by the factory swap.
- `mobile/test/support/mocks.dart` — `MockAuthRepository` (already declared).
- `mobile/test/features/recipe/main_screen_recipes_tab_widget_test.dart` —
  mocktail stub/`setUp` style to mirror (adapted: no `getIt` here).
- `docs/mobile/standards/widget-testing.md` — repository-boundary rule, `support/`
  discipline, mocktail patterns.
- `HLD.md` > Feature areas (Inert scheduler / Stateful fake backend / Faked auth /
  In-memory database harness / Four-surface assertion) and
  `docs/ADRs/0005-shopping-list-sync-test-seam.md`.
</content>
