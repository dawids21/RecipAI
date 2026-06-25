import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

import 'local_shopping_list_item.dart';
import 'shopping_list_item_dao.dart';

class ShoppingListItemRepository {
  final ShoppingListItemDao _dao;

  ShoppingListItemRepository({required ShoppingListItemDao dao}) : _dao = dao;

  /// Opens the on-device database and builds the production repository.
  /// Called from `main()` so feature setup can stay synchronous.
  static Future<ShoppingListItemRepository> open() async {
    final db = await ShoppingListItemDao.openShoppingListDatabase();
    return ShoppingListItemRepository(dao: ShoppingListItemDao(db));
  }

  final _cache = <String, Map<String, LocalShoppingListItem>>{};
  final _notifiers =
      <String, ValueNotifier<List<LocalShoppingListItem>>>{};

  static const _uuid = Uuid();

  /// Hydrates the cache from the DB for [listId] if not already resident,
  /// and creates the [ValueNotifier] seeded with the visible items.
  Future<void> openList(String listId) async {
    if (_cache.containsKey(listId)) return;
    final rows = await _dao.readItems(listId);
    _cache[listId] = {for (final item in rows) item.localId: item};
    _notifiers[listId] = ValueNotifier(_visibleItems(listId));
  }

  /// Returns a [ValueListenable] of visible (non-tombstoned) items for [listId].
  /// [openList] must be called first.
  ValueListenable<List<LocalShoppingListItem>> watch(String listId) {
    return _notifiers[listId]!;
  }

  /// Creates a new item, inserting it after [afterLocalId] when given.
  Future<void> applyCreate(
    String listId, {
    required String name,
    required double? quantity,
    required String? unit,
    String? afterLocalId,
  }) async {
    final listCache = _cache[listId]!;
    final visible = listCache.values.where((i) => !i.pendingDelete).toList()
      ..sort((a, b) => a.compareTo(b));

    double position;
    if (afterLocalId != null) {
      final idx = visible.indexWhere((i) => i.localId == afterLocalId);
      if (idx >= 0) {
        final prev = visible[idx];
        position = (idx + 1 < visible.length)
            ? (prev.position + visible[idx + 1].position) / 2.0
            : prev.position + 1.0;
      } else {
        position = visible.isEmpty ? 1.0 : visible.last.position + 1.0;
      }
    } else {
      position =
          visible.isEmpty ? 1.0 : visible.last.position + 1.0;
    }

    final item = LocalShoppingListItem(
      localId: _uuid.v4(),
      serverId: null,
      listId: listId,
      name: name,
      quantity: quantity,
      unit: unit,
      checked: false,
      position: position,
      lastAckedVersion: null,
      dirty: true,
      failed: false,
      pendingDelete: false,
    );

    listCache[item.localId] = item;
    _notifiers[listId]!.value = _visibleItems(listId);
    await _dao.transaction((txn) async {
      await _dao.upsertItemTxn(txn, item);
      await _dao.appendOutboxTxn(txn, item.localId, listId, OutboxKind.create, {
        'name': item.name,
        'quantity': item.quantity,
        'unit': item.unit,
        'checked': item.checked,
        'position': item.position,
      });
    });
  }

  Future<void> applyEdit(
    String localId, {
    required String name,
    required double? quantity,
    required String? unit,
  }) async {
    final listId = _cache.entries
        .firstWhere((e) => e.value.containsKey(localId))
        .key;
    final item = _cache[listId]![localId]!;
    final updated = item.copyWith(
      name: name,
      quantity: quantity,
      unit: unit,
      dirty: true,
    );
    _cache[listId]![localId] = updated;
    _notifiers[listId]!.value = _visibleItems(listId);
    await _dao.transaction((txn) async {
      await _dao.upsertItemTxn(txn, updated);
      await _dao.appendOutboxTxn(txn, localId, listId, OutboxKind.update, {
        'name': name,
        'quantity': quantity,
        'unit': unit,
      });
    });
  }

  Future<void> applyChecked(String localId, bool checked) async {
    final listId = _cache.entries
        .firstWhere((e) => e.value.containsKey(localId))
        .key;
    final item = _cache[listId]![localId]!;
    final updated = item.copyWith(checked: checked, dirty: true);
    _cache[listId]![localId] = updated;
    _notifiers[listId]!.value = _visibleItems(listId);
    await _dao.transaction((txn) async {
      await _dao.upsertItemTxn(txn, updated);
      await _dao.appendOutboxTxn(txn, localId, listId, OutboxKind.update, {
        'checked': checked,
      });
    });
  }

  Future<void> applyReorder(String localId, double newPosition) async {
    final listId = _cache.entries
        .firstWhere((e) => e.value.containsKey(localId))
        .key;
    final item = _cache[listId]![localId]!;
    final updated = item.copyWith(position: newPosition, dirty: true);
    _cache[listId]![localId] = updated;
    _notifiers[listId]!.value = _visibleItems(listId);
    await _dao.transaction((txn) async {
      await _dao.upsertItemTxn(txn, updated);
      await _dao.appendOutboxTxn(txn, localId, listId, OutboxKind.update, {
        'position': newPosition,
      });
    });
  }

  Future<void> applyDelete(String localId) async {
    final listId = _cache.entries
        .firstWhere((e) => e.value.containsKey(localId))
        .key;
    final item = _cache[listId]![localId]!;
    final updated = item.copyWith(pendingDelete: true, dirty: true);
    _cache[listId]![localId] = updated;
    _notifiers[listId]!.value = _visibleItems(listId);
    await _dao.transaction((txn) async {
      await _dao.upsertItemTxn(txn, updated);
      await _dao.appendOutboxTxn(
        txn,
        localId,
        listId,
        OutboxKind.delete,
        {},
      );
    });
  }

  List<LocalShoppingListItem> _visibleItems(String listId) {
    return _cache[listId]!.values
        .where((item) => !item.pendingDelete)
        .toList();
  }

  void dispose() {
    for (final notifier in _notifiers.values) {
      notifier.dispose();
    }
  }
}
