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
  Future<bool> outboxEmpty() async =>
      (await dao.listIdsWithOutbox()).isEmpty;

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

  test('create is pushed and accepted, converging all four surfaces', () async {
    await store.openList(listId);
    await store.applyCreate(listId, name: 'Milk', quantity: 2, unit: 'l');

    expect(await sync.pushNextEntry(listId), PushResult.pushed);

    final row = (await dbItems(listId)).single;
    expect(row.serverId, isNotNull);
    expect(row.lastAckedVersion, 0);
    expect(row.dirty, isFalse);
    expect(backendItems(listId).single.name, 'Milk');
    expect(visibleItems(listId).single.serverId, row.serverId);
    expect(await outboxEmpty(), isTrue);
  });
}
