import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:recipai_mobile/core/scheduler.dart';
import 'package:recipai_mobile/features/auth/auth_service.dart';
import 'package:recipai_mobile/features/shopping_list/local_shopping_list_item.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_item.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_item_dao.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_item_database_factory.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_item_repository.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_item_store_service.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_sync_service.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../../support/mocks.dart';

/// Never fires a callback; `cancel()` is a no-op. Keeps poll/drain/backoff/
/// offline timers inert for the duration of a test.
class _TestScheduler implements Scheduler {
  @override
  ScheduledTimer periodic(Duration duration, void Function() callback) =>
      const _TestScheduledTimer();

  @override
  ScheduledTimer oneShot(Duration duration, void Function() callback) =>
      const _TestScheduledTimer();
}

class _TestScheduledTimer implements ScheduledTimer {
  const _TestScheduledTimer();

  @override
  void cancel() {}
}

/// `open()` only; inherits [ShoppingListItemDatabaseFactory.createSchema] so
/// the in-memory test DB is built from the same DDL as production.
class _TestShoppingListItemDatabaseFactory
    extends ShoppingListItemDatabaseFactory {
  const _TestShoppingListItemDatabaseFactory();

  @override
  Future<Database> open() => databaseFactoryFfi.openDatabase(
    inMemoryDatabasePath,
    options: OpenDatabaseOptions(version: 1, onCreate: createSchema),
  );
}

/// Stateful in-memory backend reproducing the real repository's optimistic-
/// concurrency contract: create -> version 0, update -> version bump, stale
/// `baseVersion` -> 412 winner, missing -> 404-gone. Thin by design: a
/// direct state-setter for out-of-band remote changes, three plain `bool`
/// fault flags, and one accessor for the backend surface.
class FakeShoppingListItemRepository implements ShoppingListItemRepository {
  final _items = <String, Map<String, ShoppingListItem>>{};
  var _nextId = 1;

  bool offline = false;
  bool transientFailure = false;
  bool rejectWrites = false;

  void _maybeFail() {
    if (offline) throw const ShoppingListNetworkException();
    if (transientFailure) throw Exception('transient 5xx');
    if (rejectWrites) throw const ItemDiscardedException(DiscardReason.rejected);
  }

  void putServerItem(String listId, ShoppingListItem item) {
    _items.putIfAbsent(listId, () => {})[item.id] = item;
  }

  void removeServerItem(String listId, String itemId) {
    _items[listId]?.remove(itemId);
  }

  List<ShoppingListItem> itemsFor(String listId) =>
      (_items[listId] ?? const {}).values.toList();

  @override
  Future<List<ShoppingListItem>> fetchServerItems(
    String listId,
    String? idToken,
  ) async {
    if (offline) throw const ShoppingListNetworkException();
    return itemsFor(listId);
  }

  @override
  Future<ShoppingListItem> createItem(
    String listId,
    OutboxPayload snapshot,
    String? idToken,
  ) async {
    _maybeFail();
    final item = ShoppingListItem(
      id: 'server-${_nextId++}',
      name: snapshot.name,
      quantity: snapshot.quantity,
      unit: snapshot.unit,
      checked: snapshot.checked,
      position: snapshot.position,
      version: 0,
    );
    _items.putIfAbsent(listId, () => {})[item.id] = item;
    return item;
  }

  @override
  Future<ShoppingListItem> updateItem(
    String listId,
    String itemId, {
    required int baseVersion,
    required OutboxPayload snapshot,
    required String? idToken,
  }) async {
    _maybeFail();
    final current = _items[listId]?[itemId];
    if (current == null) throw const ItemDiscardedException(DiscardReason.gone);
    if (current.version != baseVersion) {
      throw ItemVersionConflictException(current);
    }
    final winner = ShoppingListItem(
      id: itemId,
      name: snapshot.name,
      quantity: snapshot.quantity,
      unit: snapshot.unit,
      checked: snapshot.checked,
      position: snapshot.position,
      version: current.version + 1,
    );
    _items[listId]![itemId] = winner;
    return winner;
  }

  @override
  Future<void> deleteItem(
    String listId,
    String itemId,
    int baseVersion,
    String? idToken,
  ) async {
    _maybeFail();
    final current = _items[listId]?[itemId];
    if (current == null) return;
    if (current.version != baseVersion) {
      throw ItemVersionConflictException(current);
    }
    _items[listId]!.remove(itemId);
  }

  @override
  void dispose() {}
}

/// Builds a [ShoppingListItem] with scenario-neutral defaults, so a test
/// names only the fields it cares about. Used both to seed accepted items and
/// to stage out-of-band remote version bumps.
ShoppingListItem serverItem({
  String id = 'server-1',
  String name = 'Milk',
  double? quantity = 2,
  String? unit = 'l',
  bool checked = false,
  double position = 1.0,
  int version = 0,
}) {
  return ShoppingListItem(
    id: id,
    name: name,
    quantity: quantity,
    unit: unit,
    checked: checked,
    position: position,
    version: version,
  );
}

void main() {
  const listId = 'list-1';

  late Database db;
  late ShoppingListItemDao dao;
  late ShoppingListItemStoreService store;
  late MockAuthRepository mockAuthRepository;
  late AuthService authService;
  late FakeShoppingListItemRepository backend;
  late _TestScheduler scheduler;
  late ShoppingListSyncService sync;

  Future<List<LocalShoppingListItem>> dbItems(String listId) =>
      dao.readItems(listId);
  List<ShoppingListItem> backendItems(String listId) =>
      backend.itemsFor(listId);
  List<LocalShoppingListItem> visibleItems(String listId) =>
      store.watch(listId).value;
  Future<bool> outboxEmpty(String listId) async =>
      await dao.outboxCount(listId) == 0;
  Future<int> outboxCount(String listId) => dao.outboxCount(listId);

  Future<String> seedAcceptedItem(String listId, ShoppingListItem item) async {
    backend.putServerItem(listId, item);
    await store.openList(listId);
    await sync.fetchAndReconcile(listId);
    return (await dbItems(
      listId,
    )).firstWhere((row) => row.serverId == item.id).localId;
  }

  setUpAll(() {
    TestWidgetsFlutterBinding.ensureInitialized();
    sqfliteFfiInit();
  });

  setUp(() async {
    db = await const _TestShoppingListItemDatabaseFactory().open();
    dao = ShoppingListItemDao(db);
    store = ShoppingListItemStoreService(dao: dao);

    mockAuthRepository = MockAuthRepository();
    when(
      () => mockAuthRepository.watchAuthState(),
    ).thenAnswer((_) => const Stream<User?>.empty());
    when(
      () => mockAuthRepository.getIdToken(),
    ).thenAnswer((_) async => 'test-token');
    authService = AuthService(authRepository: mockAuthRepository);

    backend = FakeShoppingListItemRepository();
    scheduler = _TestScheduler();
    sync = ShoppingListSyncService(
      itemRepository: backend,
      store: store,
      authService: authService,
      scheduler: scheduler,
    );
  });

  tearDown(() async {
    sync.dispose();
    store.dispose();
    authService.dispose();
    await db.close();
  });

  group('store-op happy paths', () {
    test(
      'create is pushed and accepted, converging all four surfaces',
      () async {
        await store.openList(listId);
        await store.applyCreate(listId, name: 'Milk', quantity: 2, unit: 'l');

        expect(await sync.pushNextEntry(listId), PushResult.pushed);

        final row = (await dbItems(listId)).single;
        expect(row.serverId, isNotNull);
        expect(row.lastAckedVersion, 0);
        expect(row.dirty, isFalse);
        expect(backendItems(listId).single.name, 'Milk');
        expect(visibleItems(listId).single.serverId, row.serverId);
        expect(await outboxEmpty(listId), isTrue);
      },
    );

    test('edit is pushed and accepted', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      await store.applyEdit(
        listId,
        localId,
        name: 'Bread',
        quantity: 1,
        unit: null,
      );

      expect(await sync.pushNextEntry(listId), PushResult.pushed);

      final row = (await dbItems(listId)).single;
      expect(row.name, 'Bread');
      expect(row.quantity, 1);
      expect(row.unit, isNull);
      expect(row.lastAckedVersion, 1);
      expect(row.dirty, isFalse);
      final backendItem = backendItems(listId).single;
      expect(backendItem.name, 'Bread');
      expect(backendItem.version, 1);
      expect(visibleItems(listId).single.name, 'Bread');
      expect(await outboxEmpty(listId), isTrue);
    });

    test('check is pushed and accepted', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      await store.applyChecked(listId, localId, true);

      expect(await sync.pushNextEntry(listId), PushResult.pushed);

      final row = (await dbItems(listId)).single;
      expect(row.checked, isTrue);
      expect(row.lastAckedVersion, 1);
      expect(row.dirty, isFalse);
      final backendItem = backendItems(listId).single;
      expect(backendItem.checked, isTrue);
      expect(backendItem.version, 1);
      expect(visibleItems(listId).single.checked, isTrue);
      expect(await outboxEmpty(listId), isTrue);
    });

    test('reorder is pushed and accepted', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      await store.applyReorder(listId, localId, 2.5);

      expect(await sync.pushNextEntry(listId), PushResult.pushed);

      final row = (await dbItems(listId)).single;
      expect(row.position, 2.5);
      expect(row.lastAckedVersion, 1);
      expect(row.dirty, isFalse);
      final backendItem = backendItems(listId).single;
      expect(backendItem.position, 2.5);
      expect(backendItem.version, 1);
      expect(visibleItems(listId).single.position, 2.5);
      expect(await outboxEmpty(listId), isTrue);
    });

    test('delete is pushed and accepted', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      await store.applyDelete(listId, localId);

      expect(await sync.pushNextEntry(listId), PushResult.pushed);

      expect(await dbItems(listId), isEmpty);
      expect(backendItems(listId), isEmpty);
      expect(visibleItems(listId), isEmpty);
      expect(await outboxEmpty(listId), isTrue);
    });

    test(
      'deleteAllChecked queues every checked item and drains clean',
      () async {
        await seedAcceptedItem(
          listId,
          serverItem(id: 'server-1', checked: true),
        );
        await seedAcceptedItem(
          listId,
          serverItem(id: 'server-2', name: 'Bread', position: 2),
        );
        await seedAcceptedItem(
          listId,
          serverItem(id: 'server-3', name: 'Eggs', position: 3, checked: true),
        );

        await store.deleteAllChecked(listId);
        await sync.requestDrain(listId);

        expect((await dbItems(listId)).map((row) => row.serverId), [
          'server-2',
        ]);
        expect(backendItems(listId).map((item) => item.id), ['server-2']);
        expect(visibleItems(listId).map((item) => item.serverId), ['server-2']);
        expect(await outboxEmpty(listId), isTrue);
        expect(sync.syncStatusFor(listId).value, SyncStatus.notSyncing);
      },
    );
  });

  group('push outcomes', () {
    test('412 on an update cascade-discards to the winner', () async {
      await seedAcceptedItem(listId, serverItem());
      backend.putServerItem(listId, serverItem(version: 1, name: 'Oat milk'));
      final localId = (await dbItems(listId)).single.localId;
      await store.applyEdit(
        listId,
        localId,
        name: 'Almond milk',
        quantity: 2,
        unit: 'l',
      );

      expect(await sync.pushNextEntry(listId), PushResult.pushed);

      final row = (await dbItems(listId)).single;
      expect(row.name, 'Oat milk');
      expect(row.lastAckedVersion, 1);
      expect(row.dirty, isFalse);
      expect(backendItems(listId).single.version, 1);
      expect(visibleItems(listId).single.name, 'Oat milk');
      expect(await outboxEmpty(listId), isTrue);
    });

    test('412 on a delete un-tombstones the item', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      backend.putServerItem(listId, serverItem(version: 1, name: 'Oat milk'));
      await store.applyDelete(listId, localId);

      expect(await sync.pushNextEntry(listId), PushResult.pushed);

      final row = (await dbItems(listId)).single;
      expect(row.pendingDelete, isFalse);
      expect(row.lastAckedVersion, 1);
      expect(row.dirty, isFalse);
      expect(row.name, 'Oat milk');
      expect(backendItems(listId).single.version, 1);
      expect(visibleItems(listId).single.name, 'Oat milk');
      expect(await outboxEmpty(listId), isTrue);
    });

    test('404-gone hard-removes the local row', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      backend.removeServerItem(listId, 'server-1');
      await store.applyEdit(
        listId,
        localId,
        name: 'Bread',
        quantity: 2,
        unit: 'l',
      );

      expect(await sync.pushNextEntry(listId), PushResult.pushed);

      expect(await dbItems(listId), isEmpty);
      expect(backendItems(listId), isEmpty);
      expect(visibleItems(listId), isEmpty);
      expect(await outboxEmpty(listId), isTrue);
    });

    test('400/403 rejected discards the queued create', () async {
      backend.rejectWrites = true;
      await store.openList(listId);
      await store.applyCreate(listId, name: 'Milk', quantity: 2, unit: 'l');

      expect(await sync.pushNextEntry(listId), PushResult.pushed);

      expect(await dbItems(listId), isEmpty);
      expect(backendItems(listId), isEmpty);
      expect(visibleItems(listId), isEmpty);
      expect(await outboxEmpty(listId), isTrue);
    });

    test('offline stall leaves the entry queued', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      await store.applyEdit(
        listId,
        localId,
        name: 'Bread',
        quantity: 2,
        unit: 'l',
      );
      backend.offline = true;

      expect(await sync.pushNextEntry(listId), PushResult.stalled);

      final row = (await dbItems(listId)).single;
      expect(row.name, 'Bread');
      expect(row.dirty, isTrue);
      expect(row.lastAckedVersion, 0);
      expect(backendItems(listId).single.name, 'Milk');
      expect(visibleItems(listId).single.name, 'Bread');
      expect(await outboxCount(listId), 1);
      expect(sync.syncStatusFor(listId).value, SyncStatus.offline);
    });

    test('transient failure then retry succeeds', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      await store.applyEdit(
        listId,
        localId,
        name: 'Bread',
        quantity: 2,
        unit: 'l',
      );

      backend.transientFailure = true;
      expect(await sync.pushNextEntry(listId), PushResult.stalled);
      backend.transientFailure = false;
      expect(await sync.pushNextEntry(listId), PushResult.pushed);

      final row = (await dbItems(listId)).single;
      expect(row.name, 'Bread');
      expect(row.lastAckedVersion, 1);
      expect(row.dirty, isFalse);
      final backendItem = backendItems(listId).single;
      expect(backendItem.name, 'Bread');
      expect(backendItem.version, 1);
      expect(visibleItems(listId).single.name, 'Bread');
      expect(await outboxEmpty(listId), isTrue);
    });

    test('transient failures escalate to SyncStatus.failure', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      await store.applyEdit(
        listId,
        localId,
        name: 'Bread',
        quantity: 2,
        unit: 'l',
      );
      backend.transientFailure = true;

      for (var i = 0; i < 6; i++) {
        // _maxRetries = 5: attempts 1-5 arm backoff, the 6th escalates.
        expect(await sync.pushNextEntry(listId), PushResult.stalled);
      }

      expect(sync.syncStatusFor(listId).value, SyncStatus.failure);
      final row = (await dbItems(listId)).single;
      expect(row.name, 'Bread');
      expect(row.dirty, isTrue);
      expect(row.lastAckedVersion, 0);
      expect(backendItems(listId).single.version, 0);
      expect(await outboxCount(listId), 1);
    });
  });

  group('ordering', () {
    test('a still-dirty local edit is not clobbered by a reconcile', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      await store.applyEdit(
        listId,
        localId,
        name: 'Bread',
        quantity: 2,
        unit: 'l',
      );
      backend.putServerItem(listId, serverItem(version: 1, name: 'Oat milk'));

      await sync.fetchAndReconcile(listId);

      final row = (await dbItems(listId)).single;
      expect(row.name, 'Bread');
      expect(row.dirty, isTrue);
      expect(row.lastAckedVersion, 0);
      expect(backendItems(listId).single.name, 'Oat milk');
      expect(visibleItems(listId).single.name, 'Bread');
      expect(await outboxCount(listId), 1);
    });

    test(
      'a queued create-then-edit pushes both entries, clearing dirty only at the end',
      () async {
        await store.openList(listId);
        await store.applyCreate(listId, name: 'Milk', quantity: 2, unit: 'l');
        final localId = visibleItems(listId).single.localId;
        await store.applyEdit(
          listId,
          localId,
          name: 'Oat milk',
          quantity: 1,
          unit: 'l',
        );

        expect(await sync.pushNextEntry(listId), PushResult.pushed);

        final midway = (await dbItems(listId)).single;
        expect(midway.serverId, isNotNull);
        expect(midway.lastAckedVersion, 0);
        expect(midway.dirty, isTrue);

        expect(await sync.pushNextEntry(listId), PushResult.pushed);

        final row = (await dbItems(listId)).single;
        expect(row.name, 'Oat milk');
        expect(row.lastAckedVersion, 1);
        expect(row.dirty, isFalse);
        final backendItem = backendItems(listId).single;
        expect(backendItem.name, 'Oat milk');
        expect(backendItem.version, 1);
        expect(visibleItems(listId).single.name, 'Oat milk');
        expect(await outboxEmpty(listId), isTrue);
      },
    );

    test('permutation A: reconcile before push', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      await store.applyEdit(
        listId,
        localId,
        name: 'Bread',
        quantity: 2,
        unit: 'l',
      );
      backend.putServerItem(listId, serverItem(version: 1, name: 'Oat milk'));

      await sync.fetchAndReconcile(listId);
      expect(await sync.pushNextEntry(listId), PushResult.pushed);

      final row = (await dbItems(listId)).single;
      expect(row.name, 'Oat milk');
      expect(row.lastAckedVersion, 1);
      expect(row.dirty, isFalse);
      expect(backendItems(listId).single.version, 1);
      expect(visibleItems(listId).single.name, 'Oat milk');
      expect(await outboxEmpty(listId), isTrue);
    });

    test('permutation B: push before reconcile', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      await store.applyEdit(
        listId,
        localId,
        name: 'Bread',
        quantity: 2,
        unit: 'l',
      );
      backend.putServerItem(listId, serverItem(version: 1, name: 'Oat milk'));

      expect(await sync.pushNextEntry(listId), PushResult.pushed);
      await sync.fetchAndReconcile(listId);

      final row = (await dbItems(listId)).single;
      expect(row.name, 'Oat milk');
      expect(row.lastAckedVersion, 1);
      expect(row.dirty, isFalse);
      expect(backendItems(listId).single.version, 1);
      expect(visibleItems(listId).single.name, 'Oat milk');
      expect(await outboxEmpty(listId), isTrue);
    });
  });

  group('multi-list', () {
    test('two lists drain independently', () async {
      await store.openList('list-1');
      await store.openList('list-2');
      await store.applyCreate('list-1', name: 'Milk', quantity: 2, unit: 'l');
      await store.applyCreate('list-2', name: 'Bread', quantity: 1, unit: 'l');

      await sync.requestDrain('list-1');
      await sync.requestDrain('list-2');

      final row1 = (await dbItems('list-1')).single;
      expect(row1.serverId, isNotNull);
      expect(row1.dirty, isFalse);
      expect(backendItems('list-1').single.name, 'Milk');
      expect(visibleItems('list-1').single.name, 'Milk');
      expect(await outboxEmpty('list-1'), isTrue);
      expect(sync.syncStatusFor('list-1').value, SyncStatus.notSyncing);

      final row2 = (await dbItems('list-2')).single;
      expect(row2.serverId, isNotNull);
      expect(row2.dirty, isFalse);
      expect(backendItems('list-2').single.name, 'Bread');
      expect(visibleItems('list-2').single.name, 'Bread');
      expect(await outboxEmpty('list-2'), isTrue);
      expect(sync.syncStatusFor('list-2').value, SyncStatus.notSyncing);
    });

    test(
      'the start/resume fan-out drains every list with a pending outbox',
      () async {
        await store.openList('list-1');
        await store.openList('list-2');
        await store.applyCreate('list-1', name: 'Milk', quantity: 2, unit: 'l');
        await store.applyCreate(
          'list-2',
          name: 'Bread',
          quantity: 1,
          unit: 'l',
        );

        await sync.fanOutPending();

        final row1 = (await dbItems('list-1')).single;
        expect(row1.serverId, isNotNull);
        expect(row1.dirty, isFalse);
        expect(backendItems('list-1').single.name, 'Milk');
        expect(visibleItems('list-1').single.name, 'Milk');
        expect(await outboxEmpty('list-1'), isTrue);
        expect(sync.syncStatusFor('list-1').value, SyncStatus.notSyncing);

        final row2 = (await dbItems('list-2')).single;
        expect(row2.serverId, isNotNull);
        expect(row2.dirty, isFalse);
        expect(backendItems('list-2').single.name, 'Bread');
        expect(visibleItems('list-2').single.name, 'Bread');
        expect(await outboxEmpty('list-2'), isTrue);
        expect(sync.syncStatusFor('list-2').value, SyncStatus.notSyncing);
      },
    );
  });

  group('undo capture and replay', () {
    test('applyDelete returns the pre-state of the deleted row', () async {
      final localId = await seedAcceptedItem(
        listId,
        serverItem(name: 'Milk', quantity: 2, unit: 'l', position: 3.5),
      );

      final captured = await store.applyDelete(listId, localId);

      expect(captured, isNotNull);
      expect(captured!.name, 'Milk');
      expect(captured.quantity, 2);
      expect(captured.unit, 'l');
      expect(captured.position, 3.5);
      expect(captured.checked, isFalse);
    });

    test('applyDelete returns null for an unknown localId', () async {
      await store.openList(listId);

      final captured = await store.applyDelete(listId, 'missing-local-id');

      expect(captured, isNull);
      expect(await outboxCount(listId), 0);
    });

    test(
      'deleteAllChecked returns every checked item and skips unchecked ones',
      () async {
        final checkedId1 = await seedAcceptedItem(
          listId,
          serverItem(id: 'server-1', name: 'Milk', checked: true),
        );
        await seedAcceptedItem(
          listId,
          serverItem(id: 'server-2', name: 'Bread', position: 2),
        );
        final checkedId2 = await seedAcceptedItem(
          listId,
          serverItem(id: 'server-3', name: 'Eggs', position: 3, checked: true),
        );

        final removed = await store.deleteAllChecked(listId);

        expect(
          removed.map((i) => i.localId),
          containsAll([checkedId1, checkedId2]),
        );
        expect(removed, hasLength(2));
      },
    );

    test(
      'deleteAllChecked returns an empty list when nothing is checked',
      () async {
        await seedAcceptedItem(listId, serverItem());

        final removed = await store.deleteAllChecked(listId);

        expect(removed, isEmpty);
        expect(await outboxCount(listId), 0);
      },
    );

    test('uncheckAll returns the ids it actually flipped', () async {
      final checkedId = await seedAcceptedItem(
        listId,
        serverItem(id: 'server-1', checked: true),
      );
      final uncheckedId = await seedAcceptedItem(
        listId,
        serverItem(id: 'server-2', name: 'Bread', position: 2),
      );

      final flipped = await store.uncheckAll(listId);

      expect(flipped, [checkedId]);
      expect(flipped, isNot(contains(uncheckedId)));
    });

    test('uncheckAll returns an empty list when nothing is checked', () async {
      await seedAcceptedItem(listId, serverItem());

      final flipped = await store.uncheckAll(listId);

      expect(flipped, isEmpty);
    });

    test(
      'applyRestore re-creates each snapshot with a fresh localId and the original position and checked state',
      () async {
        final localId = await seedAcceptedItem(
          listId,
          serverItem(
            name: 'Milk',
            quantity: 2,
            unit: 'l',
            position: 3.5,
            checked: true,
          ),
        );
        final snapshot = await store.applyDelete(listId, localId);

        await store.applyRestore(listId, [snapshot!]);

        final restored = visibleItems(listId).single;
        expect(restored.localId, isNot(snapshot.localId));
        expect(restored.serverId, isNull);
        expect(restored.lastAckedVersion, isNull);
        expect(restored.dirty, isTrue);
        expect(restored.name, 'Milk');
        expect(restored.quantity, 2);
        expect(restored.unit, 'l');
        expect(restored.position, 3.5);
        expect(restored.checked, isTrue);
      },
    );

    test('a restored item pushes as a create carrying checked', () async {
      final localId = await seedAcceptedItem(listId, serverItem(checked: true));
      final snapshot = await store.applyDelete(listId, localId);
      await store.applyRestore(listId, [snapshot!]);

      await sync.requestDrain(listId);

      final backendItem = backendItems(listId).single;
      expect(backendItem.checked, isTrue);
      expect(await outboxEmpty(listId), isTrue);
    });

    test('applyRestore leaves the original tombstone untouched', () async {
      final localId = await seedAcceptedItem(listId, serverItem());
      final snapshot = await store.applyDelete(listId, localId);

      await store.applyRestore(listId, [snapshot!]);

      final tombstone = (await dbItems(
        listId,
      )).firstWhere((row) => row.localId == localId);
      expect(tombstone.pendingDelete, isTrue);
      final entry = await dao.nextOutboxEntry(listId);
      expect(entry, isNotNull);
      expect(entry!.itemLocalId, localId);
      expect(entry.kind, OutboxKind.delete);
    });

    test(
      'applyCheckedAll(..., true) re-checks exactly the given ids',
      () async {
        final targetId = await seedAcceptedItem(
          listId,
          serverItem(id: 'server-1'),
        );
        final untouchedId = await seedAcceptedItem(
          listId,
          serverItem(id: 'server-2', name: 'Bread', position: 2),
        );

        await store.applyCheckedAll(listId, [targetId], true);

        final target = visibleItems(
          listId,
        ).firstWhere((i) => i.localId == targetId);
        final untouched = visibleItems(
          listId,
        ).firstWhere((i) => i.localId == untouchedId);
        expect(target.checked, isTrue);
        expect(target.dirty, isTrue);
        expect(untouched.checked, isFalse);
        expect(untouched.dirty, isFalse);
      },
    );
  });
}
