# T2: Build test doubles + one reviewed reference test — Implementation Plan

**Date:** 2026-07-24
**Status:** accepted

## Required reading

**Docs & standards** (from `docs/INDEX.md`)
- `docs/mobile/standards/widget-testing.md` — `test/support/` = type-declarations
  only, no early harness, `setUp`/`tearDown` isolation; the file's discipline
  is why the doubles stay inline.
- `docs/mobile/standards/architecture.md` — repository/service/DAO layer split
  the refactor and the fake respect (fake sits at the repository boundary).
- `docs/mobile/standards/state-management.md` — `ValueNotifier`/`dispose()`
  lifecycle the store and `tearDown` follow.

**Design & ADRs**
- `plans/T2-task-design.md` — the design being implemented (components,
  signatures, data flow, pseudo-code, decisions).
- `HLD.md` > Feature areas (Inert scheduler / Stateful fake backend / Faked auth
  / In-memory database harness / Four-surface assertion) — the double semantics.
- `docs/ADRs/0005-shopping-list-sync-test-seam.md` — what is and isn't a coverage
  target; why the seam exists.

**Code to mirror**
- `mobile/test/features/recipe/main_screen_recipes_tab_widget_test.dart` —
  mocktail `when(...).thenAnswer(...)` stub style and `setUp` shape (adapt: no
  `getIt`, no `pumpWidget`; use `test()` not `testWidgets()`).
- `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` (`_onCreate`) —
  the exact DDL to move verbatim into the factory's `createSchema`.
- `mobile/lib/core/scheduler.dart` — `Scheduler`/`ScheduledTimer` shape the
  inert double implements.
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — the
  interface + exception contract (`ItemVersionConflictException`,
  `ItemDiscardedException`/`DiscardReason`, `ShoppingListNetworkException`,
  `OutboxPayload`) the fake reproduces.

## File inventory

- **CREATE** `mobile/lib/features/shopping_list/shopping_list_item_database_factory.dart`
  — production DB factory: `open()` + `@protected createSchema` (single-sourced DDL).
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` —
  delete `openShoppingListDatabase` + `_onCreate`; drop `dart:io` and
  `path_provider` imports (keep `dart:convert`, `sqflite`).
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart`
  — `open()` builds its `Database` via `const ShoppingListItemDatabaseFactory().open()`.
- **MODIFY** `mobile/pubspec.yaml` — add `sqflite_common_ffi` to `dev_dependencies`.
- **CREATE** `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart`
  — the whole T2 test deliverable: inline doubles, inline test DB factory,
  four-surface read helpers, `setUpAll`/`setUp`/`tearDown`, one reference test.
- `mobile/lib/main.dart` — **UNCHANGED**; `main.dart:43` calls
  `ShoppingListItemStoreService.open()`, whose signature is preserved (confirmed
  sole `.open()` caller).
- `mobile/test/support/mocks.dart` — **UNCHANGED**; reuses the existing
  `MockAuthRepository`. (Note: it also already declares a mocktail
  `MockShoppingListItemRepository`; T2 does **not** use it — the fake is a
  hand-written `implements` class, kept inline in the test file.)

## Step-by-step plan

1. **Add the `sqflite_common_ffi` dev dependency** — the in-memory ffi engine
   the test DB runs on.
   - Files: `mobile/pubspec.yaml`
   - Run `flutter pub add --dev sqflite_common_ffi` (from `mobile/`) so the
     resolver picks a version compatible with `sqflite: ^2.4.3` (the 2.3.x
     line), then confirm it landed under `dev_dependencies` with a caret pin
     matching the file's style.
   - Verify: `flutter pub get` resolves clean; `git diff pubspec.yaml pubspec.lock`
     shows only the new dev dep.

2. **Extract DB creation into `ShoppingListItemDatabaseFactory` (behaviour-
   preserving refactor)** — move `openShoppingListDatabase` → `open()` and
   `_onCreate` → `@protected createSchema` (DDL moved verbatim), repoint the
   store, and strip the DAO's now-unused imports.
   - Files: `shopping_list_item_database_factory.dart` (new),
     `shopping_list_item_dao.dart`, `shopping_list_item_store_service.dart`
   - Factory: `const` ctor; `open()` = `getApplicationDocumentsDirectory()` +
     `openDatabase(path, version: 1, onCreate: createSchema)`; `@protected
     Future<void> createSchema(Database db, int version)` holding the `items` +
     `outbox` `CREATE TABLE`/`CREATE INDEX` block copied from `_onCreate`. Imports:
     `dart:io`, `package:path_provider/path_provider.dart`,
     `package:sqflite/sqflite.dart`, `package:flutter/foundation.dart` (`@protected`).
   - DAO: remove both statics and the `dart:io` + `path_provider` imports.
   - Store: `open()` → `final db = await const ShoppingListItemDatabaseFactory().open();`
   - Verify: `flutter analyze` clean (no unused-import warnings); `flutter test`
     (existing suite) still green.

3. **Write the test file: doubles, inline test factory, read helpers, lifecycle,
   reference test** — the reviewable deliverable.
   - Files: `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart`
   - Doubles (all inline, `_`-private except the fake):
     `_TestScheduler`/`_TestScheduledTimer` (never fire, `cancel()` no-op);
     `FakeShoppingListItemRepository implements ShoppingListItemRepository`
     (per-list `Map<String, Map<String, ShoppingListItem>>`, monotonic id
     counter, `bool offline/transientFailure/rejectWrites`, `putServerItem`,
     `itemsFor`, `dispose()` no-op) reproducing the optimistic-concurrency +
     fault contract from the design's pseudo-code;
     `_TestShoppingListItemDatabaseFactory extends ShoppingListItemDatabaseFactory`
     overriding only `open()` → `databaseFactoryFfi.openDatabase(inMemoryDatabasePath,
     options: OpenDatabaseOptions(version: 1, onCreate: createSchema))`.
   - Read helpers (inline, top-level or closures over the `setUp` vars):
     `dbItems`, `backendItems`, `visibleItems`, `outboxEmpty`.
   - Lifecycle: `setUpAll` → `TestWidgetsFlutterBinding.ensureInitialized()` +
     `sqfliteFfiInit()`; `setUp` → build fresh in-memory db → `dao` → `store` →
     stub `MockAuthRepository` (`watchAuthState` → `Stream<User?>.empty()`,
     `getIdToken` → `'test-token'`) → real `AuthService` → `backend` →
     `_TestScheduler` → `sync = ShoppingListSyncService(...)`; `tearDown` →
     `sync.dispose(); store.dispose(); authService.dispose(); await db.close();`.
   - Reference test: `create is pushed and accepted, converging all four surfaces`
     (skeleton in the design) — `openList` → `applyCreate` → `pushNextEntry` →
     assert `PushResult.pushed` and all four surfaces.
   - Verify: `flutter test test/features/shopping_list/shopping_list_sync_service_test.dart`
     green; `flutter analyze` clean.

## Test plan

**Unit tests** — `shopping_list_sync_service_test.dart` (one reference test in T2;
the catalog is T3):
- `create is pushed and accepted, converging all four surfaces` — after
  `store.openList` + `store.applyCreate(name:'Milk', quantity:2, unit:'l')` +
  `sync.pushNextEntry(listId)`:
  - returns `PushResult.pushed`;
  - **DB surface**: the single `items` row has `serverId != null`,
    `lastAckedVersion == 0`, `dirty == false`;
  - **backend surface**: `backendItems(listId).single.name == 'Milk'`;
  - **visible surface**: `visibleItems(listId).single.serverId == row.serverId`;
  - **outbox surface**: `outboxEmpty() == true`.

_Fault-flag and version/state branches of the fake (offline, transient, 412,
404-gone, rejected) are exercised by T3; T2 only builds the fake and proves the
happy path through it._

**Integration tests** — _N/A — T2 is a unit-level harness; no cross-service or
backend integration._

**Flutter widget tests** — _N/A — pure `test()` unit tests, no widget pumping
(the sync service needs only `TestWidgetsFlutterBinding`, not a widget tree)._

**Manual verification**
- On-device smoke: launch the app, open a shopping list, add/edit an item —
  confirms `ShoppingListItemStoreService.open()` still opens the on-device DB
  through the new factory (the refactor is behaviour-preserving).

## Verification checklist

- [ ] `flutter analyze` is clean (no unused-import or `invalid_use_of_protected_member` warnings).
- [ ] `flutter test test/features/shopping_list/` passes with the new test green.
- [ ] Full `flutter test` suite still green (DB-factory refactor preserved behaviour).
- [ ] `pubspec.lock` shows `sqflite_common_ffi` added under dev deps only.
- [ ] `tasks.md` > T2 "How to verify" succeeds: single test green **and** app
      still opens its on-device DB via the new factory (manual smoke).
- [ ] Design "Assumptions to verify" confirmed during implementation (see Risks below),
      or the open ones documented.
- [ ] **Checkpoint:** user reviews the test's structure (doubles, four-surface
      assertion, single-ordering-per-test shape) and signs off before T3 —
      T3 must not start first (`tasks.md` > Cross-task notes).

## Risks surfaced during planning

- **Risk:** `@protected createSchema` reachability from the inline test subclass.
  **Why it matters:** if the analyzer rejects the subclass tear-off
  (`onCreate: createSchema`) across libraries, the test factory can't share
  production DDL.
  **Mitigation:** access is from a subclass *instance method* (`_Test...open()`),
  which `invalid_use_of_protected_member` permits — expected to pass. If it
  warns, promote to a top-level `createShoppingListSchema(db, version)` both
  factories call (design's stated fallback). Confirm at Step 2.

- **Risk:** `FakeShoppingListItemRepository implements ShoppingListItemRepository`
  must compile without running the concrete class's field initializers
  (`_client`, `_baseUrl = AppConfig.apiBaseUrl`).
  **Why it matters:** `AppConfig.apiBaseUrl` at construction could pull config the
  test env lacks; `implements` avoids the ctor, but this is unverified until it compiles.
  **Mitigation:** `implements` (not `extends`) skips the superclass ctor by
  design; if it still fails, fall back to `extends Mock implements ...` with
  hand-written stateful `thenAnswer`s (design's fallback). Confirm at Step 3.

- **Risk:** `sqflite_common_ffi` version must track the `sqflite: ^2.4.3` (2.x)
  line.
  **Why it matters:** a mismatched major could diverge SQLite behaviour
  (`transaction()`, `AUTOINCREMENT`) the DAO relies on.
  **Mitigation:** use `flutter pub add --dev` (Step 1) so the resolver picks a
  compatible 2.3.x; both wrap the same SQLite engine, so semantic risk is low.

- **Note (open assumption, verify in-flight):** `sync.dispose()` without a prior
  `start()` must be a no-op `removeObserver` — expected safe; if `tearDown`
  throws, skip the sync `dispose()` or call `start()` in `setUp`. Likewise the
  real `AuthService` must tolerate a `watchAuthState()` stubbed as
  `Stream<User?>.empty()` with `idToken` resolving purely via `getIdToken()` —
  if not, stub `Stream.value(null)`.
