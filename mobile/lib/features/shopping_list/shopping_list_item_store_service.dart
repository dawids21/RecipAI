import 'package:flutter/foundation.dart';
import 'package:synchronized/synchronized.dart';
import 'package:uuid/uuid.dart';

import 'local_shopping_list_item.dart';
import 'shopping_list_item.dart';
import 'shopping_list_item_dao.dart';

/// The consistency boundary for shopping-list items: owns the in-memory
/// cache, per-list [ValueNotifier]s, and the DAO. Every local read-modify-write
/// is serialised per list through [_lockFor], so a UI mutation can never
/// interleave with a reconcile's transaction `await` and clobber the cache
/// write-back (ADR-0004).
class ShoppingListItemStoreService {
  final ShoppingListItemDao _dao;

  ShoppingListItemStoreService({required ShoppingListItemDao dao}) : _dao = dao;

  /// Opens the on-device database and builds the production store.
  /// Called from `main()` so feature setup can stay synchronous.
  static Future<ShoppingListItemStoreService> open() async {
    final db = await ShoppingListItemDao.openShoppingListDatabase();
    return ShoppingListItemStoreService(dao: ShoppingListItemDao(db));
  }

  final _cache = <String, Map<String, LocalShoppingListItem>>{};
  final _notifiers = <String, ValueNotifier<List<LocalShoppingListItem>>>{};
  final _locks = <String, Lock>{};

  static const _uuid = Uuid();

  Lock _lockFor(String listId) => _locks.putIfAbsent(listId, () => Lock());

  // ── lifecycle / reads (lock-free) ──

  /// Hydrates the cache from the DB for [listId] if not already resident,
  /// and creates the [ValueNotifier] seeded with the visible items.
  Future<void> openList(String listId) {
    return _lockFor(listId).synchronized(() async {
      if (_cache.containsKey(listId)) return;
      final rows = await _dao.readItems(listId);
      _cache[listId] = {for (final item in rows) item.localId: item};
      _notifiers[listId] = ValueNotifier(_visibleItems(listId));
    });
  }

  /// Returns a [ValueListenable] of visible (non-tombstoned) items for [listId].
  /// [openList] must be called first.
  ValueListenable<List<LocalShoppingListItem>> watch(String listId) {
    return _notifiers[listId]!;
  }

  /// The oldest queued entry for [listId], or `null` if its queue is empty.
  Future<OutboxEntry?> nextOutboxEntry(String listId) =>
      _dao.nextOutboxEntry(listId);

  /// Distinct list ids with at least one pending outbox entry (start/resume fan-out).
  Future<List<String>> listIdsWithOutbox() => _dao.listIdsWithOutbox();

  /// The item's current local row (serverId + lastAckedVersion), read live at
  /// push time — never frozen into the outbox entry.
  Future<LocalShoppingListItem?> readItem(String localId) =>
      _dao.readItem(localId);

  // ── UI mutations (locked-public → unlocked-private) ──

  /// Creates a new item, inserting it after [afterLocalId] when given.
  Future<void> applyCreate(
    String listId, {
    required String name,
    required double? quantity,
    required String? unit,
    String? afterLocalId,
  }) {
    return _lockFor(listId).synchronized(
      () => _createItem(listId, name, quantity, unit, afterLocalId),
    );
  }

  Future<void> applyEdit(
    String listId,
    String localId, {
    required String name,
    required double? quantity,
    required String? unit,
  }) {
    return _lockFor(
      listId,
    ).synchronized(() => _editItem(listId, localId, name, quantity, unit));
  }

  Future<void> applyChecked(String listId, String localId, bool checked) {
    return _lockFor(
      listId,
    ).synchronized(() => _checkItem(listId, localId, checked));
  }

  Future<void> applyReorder(String listId, String localId, double newPosition) {
    return _lockFor(
      listId,
    ).synchronized(() => _reorderItem(listId, localId, newPosition));
  }

  Future<void> applyDelete(String listId, String localId) {
    return _lockFor(listId).synchronized(() => _deleteItem(listId, localId));
  }

  // ── bulk (single lock acquisition, one atomic section) ──

  Future<void> deleteAllChecked(String listId) {
    return _lockFor(listId).synchronized(() async {
      final checked = _cache[listId]!.values
          .where((i) => i.checked)
          .map((i) => i.localId)
          .toList();
      for (final localId in checked) {
        await _deleteItem(listId, localId);
      }
    });
  }

  Future<void> uncheckAll(String listId) {
    return _lockFor(listId).synchronized(() async {
      final checked = _cache[listId]!.values
          .where((i) => i.checked)
          .map((i) => i.localId)
          .toList();
      for (final localId in checked) {
        await _checkItem(listId, localId, false);
      }
    });
  }

  // ── reconcile a pull / push outcome (locked) ──

  /// Full-list diff into the store (DB + cache coherent, one transaction).
  /// Resident-or-DB, mirroring the T3 reconcile-mutation pattern. Adopt is
  /// version-gated so an out-of-order/stale response can never regress an
  /// item already at a newer [LocalShoppingListItem.lastAckedVersion]; a
  /// dirty item is never touched (fields or last-acked version) and never
  /// hard-deleted — its own push resolves it.
  Future<void> reconcileFromServer(
    String listId,
    List<ShoppingListItem> serverItems,
  ) {
    return _lockFor(listId).synchronized(() async {
      final resident = _cache.containsKey(listId);
      final locals = resident
          ? _cache[listId]!.values.toList()
          : await _dao.readItems(listId);
      final localByServerId = {
        for (final item in locals)
          if (item.serverId != null) item.serverId!: item,
      };

      final updatedByLocalId = <String, LocalShoppingListItem>{};
      final deletedLocalIds = <String>{};

      for (final s in serverItems) {
        final local = localByServerId[s.id];
        if (local == null) {
          final inserted = LocalShoppingListItem(
            localId: _uuid.v4(),
            serverId: s.id,
            listId: listId,
            name: s.name,
            quantity: s.quantity,
            unit: s.unit,
            checked: s.checked,
            position: s.position,
            lastAckedVersion: s.version,
            dirty: false,
            failed: false,
            pendingDelete: false,
          );
          updatedByLocalId[inserted.localId] = inserted;
        } else if (!local.dirty && s.version >= local.lastAckedVersion!) {
          final adopted = local.copyWith(
            name: s.name,
            quantity: s.quantity,
            unit: s.unit,
            checked: s.checked,
            position: s.position,
            lastAckedVersion: s.version,
            pendingDelete: false,
          );
          updatedByLocalId[adopted.localId] = adopted;
        }
        // else: dirty (or a stale response) -> keep local untouched.
      }

      final serverIds = {for (final s in serverItems) s.id};
      for (final local in locals) {
        if (local.serverId != null &&
            !serverIds.contains(local.serverId) &&
            !local.dirty) {
          deletedLocalIds.add(local.localId);
        }
      }

      await _dao.writeServerDiff(
        upserts: updatedByLocalId.values.toList(),
        deletedLocalIds: deletedLocalIds,
      );

      if (resident) {
        final listCache = _cache[listId]!;
        updatedByLocalId.forEach((localId, item) => listCache[localId] = item);
        for (final localId in deletedLocalIds) {
          listCache.remove(localId);
        }
        _notifiers[listId]!.value = _visibleItems(listId);
      }
    });
  }

  /// Accept (create/update ack): adopt [winner]'s id/version, drop the acked
  /// entry, clear `dirty` iff no entries remain for the item. Fields are NOT
  /// overwritten — later queued edits have already advanced them.
  Future<void> reconcileAck(
    String listId,
    String localId,
    ShoppingListItem winner,
    int ackedSeq,
  ) {
    return _lockFor(listId).synchronized(() async {
      final resident = _cache.containsKey(listId);
      final current = resident
          ? _cache[listId]![localId]
          : await _dao.readItem(localId);
      if (current == null) return;

      final updated = await _dao.writeItemDroppingEntry(
        current,
        serverId: winner.id,
        version: winner.version,
        ackedSeq: ackedSeq,
      );

      if (resident) {
        _cache[listId]![localId] = updated;
        _notifiers[listId]!.value = _visibleItems(listId);
      }
    });
  }

  /// 204/404 delete ack: hard-remove the row and drop the delete entry.
  Future<void> reconcileDeleteAck(String listId, String localId, int ackedSeq) {
    return _lockFor(listId).synchronized(() async {
      final resident = _cache.containsKey(listId);
      await _dao.deleteItemDroppingEntry(localId, ackedSeq);

      if (resident) {
        _cache[listId]!.remove(localId);
        _notifiers[listId]!.value = _visibleItems(listId);
      }
    });
  }

  /// 412: overwrite the local item with [winner] (un-tombstoning a rejected
  /// delete), adopt the winner's serverId/version, and drop EVERY queued
  /// entry for the item.
  Future<void> cascadeDiscard(
    String listId,
    String localId,
    ShoppingListItem winner,
  ) {
    return _lockFor(listId).synchronized(() async {
      final resident = _cache.containsKey(listId);
      final current = resident
          ? _cache[listId]![localId]
          : await _dao.readItem(localId);
      if (current == null) return;

      final updated = current.copyWith(
        name: winner.name,
        quantity: winner.quantity,
        unit: winner.unit,
        checked: winner.checked,
        position: winner.position,
        serverId: winner.id,
        lastAckedVersion: winner.version,
        pendingDelete: false,
        dirty: false,
      );
      await _dao.writeItemClearingOutbox(updated, localId);

      if (resident) {
        _cache[listId]![localId] = updated;
        _notifiers[listId]!.value = _visibleItems(listId);
      }
    });
  }

  /// Gone (404 create/update) / rejected (400/403): hard-remove the row and
  /// drop EVERY queued entry for the item. No winner to roll back to.
  Future<void> discardItem(String listId, String localId) {
    return _lockFor(listId).synchronized(() async {
      final resident = _cache.containsKey(listId);
      await _dao.deleteItemClearingOutbox(localId);

      if (resident) {
        _cache[listId]!.remove(localId);
        _notifiers[listId]!.value = _visibleItems(listId);
      }
    });
  }

  void dispose() {
    for (final notifier in _notifiers.values) {
      notifier.dispose();
    }
  }

  // ── private unlocked cores (run only inside _lockFor(listId)) ──

  Future<void> _createItem(
    String listId,
    String name,
    double? quantity,
    String? unit,
    String? afterLocalId,
  ) async {
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
      position = visible.isEmpty ? 1.0 : visible.last.position + 1.0;
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
    await _dao.writeItemAppendingOutbox(item, OutboxKind.create, {
      'name': item.name,
      'quantity': item.quantity,
      'unit': item.unit,
      'checked': item.checked,
      'position': item.position,
    });
  }

  Future<void> _editItem(
    String listId,
    String localId,
    String name,
    double? quantity,
    String? unit,
  ) async {
    final item = _cache[listId]![localId]!;
    final updated = item.copyWith(
      name: name,
      quantity: quantity,
      unit: unit,
      dirty: true,
    );
    _cache[listId]![localId] = updated;
    _notifiers[listId]!.value = _visibleItems(listId);
    await _dao.writeItemAppendingOutbox(
      updated,
      OutboxKind.update,
      _snapshotPayload(updated),
    );
  }

  Future<void> _checkItem(String listId, String localId, bool checked) async {
    final item = _cache[listId]![localId]!;
    final updated = item.copyWith(checked: checked, dirty: true);
    _cache[listId]![localId] = updated;
    _notifiers[listId]!.value = _visibleItems(listId);
    await _dao.writeItemAppendingOutbox(
      updated,
      OutboxKind.update,
      _snapshotPayload(updated),
    );
  }

  Future<void> _reorderItem(
    String listId,
    String localId,
    double newPosition,
  ) async {
    final item = _cache[listId]![localId]!;
    final updated = item.copyWith(position: newPosition, dirty: true);
    _cache[listId]![localId] = updated;
    _notifiers[listId]!.value = _visibleItems(listId);
    await _dao.writeItemAppendingOutbox(
      updated,
      OutboxKind.update,
      _snapshotPayload(updated),
    );
  }

  Future<void> _deleteItem(String listId, String localId) async {
    final item = _cache[listId]![localId]!;
    final updated = item.copyWith(pendingDelete: true, dirty: true);
    _cache[listId]![localId] = updated;
    _notifiers[listId]!.value = _visibleItems(listId);
    await _dao.writeItemAppendingOutbox(updated, OutboxKind.delete, {});
  }

  /// The full mutable field set of [item], stored as the outbox entry's
  /// snapshot so a queued push can never leak values from a later edit.
  Map<String, dynamic> _snapshotPayload(LocalShoppingListItem item) {
    return {
      'name': item.name,
      'quantity': item.quantity,
      'unit': item.unit,
      'checked': item.checked,
      'position': item.position,
    };
  }

  List<LocalShoppingListItem> _visibleItems(String listId) {
    return _cache[listId]!.values.where((item) => !item.pendingDelete).toList();
  }
}
