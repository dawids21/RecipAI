import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';
import 'package:uuid/uuid.dart';

import '../../core/app_config.dart';
import 'local_shopping_list_item.dart';
import 'shopping_list_item.dart';
import 'shopping_list_item_dao.dart';

class ShoppingListItemRepository {
  static final _log = Logger('recipai.shopping_list.repository');

  final ShoppingListItemDao _dao;
  final http.Client _client;
  final String _baseUrl = AppConfig.apiBaseUrl;

  ShoppingListItemRepository({
    required ShoppingListItemDao dao,
    http.Client? client,
  }) : _dao = dao,
       _client = client ?? http.Client();

  /// Opens the on-device database and builds the production repository.
  /// Called from `main()` so feature setup can stay synchronous.
  static Future<ShoppingListItemRepository> open() async {
    final db = await ShoppingListItemDao.openShoppingListDatabase();
    return ShoppingListItemRepository(dao: ShoppingListItemDao(db));
  }

  final _cache = <String, Map<String, LocalShoppingListItem>>{};
  final _notifiers = <String, ValueNotifier<List<LocalShoppingListItem>>>{};

  static const _uuid = Uuid();

  Map<String, String> _getAuthHeaders(String? idToken) {
    return {
      'Content-Type': 'application/json',
      if (idToken != null) 'Authorization': 'Bearer $idToken',
    };
  }

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
      await _dao.appendOutboxTxn(
        txn,
        localId,
        listId,
        OutboxKind.update,
        _snapshotPayload(updated),
      );
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
      await _dao.appendOutboxTxn(
        txn,
        localId,
        listId,
        OutboxKind.update,
        _snapshotPayload(updated),
      );
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
      await _dao.appendOutboxTxn(
        txn,
        localId,
        listId,
        OutboxKind.update,
        _snapshotPayload(updated),
      );
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
      await _dao.appendOutboxTxn(txn, localId, listId, OutboxKind.delete, {});
    });
  }

  // ── HTTP: item write endpoints (§Response classification in T3 design) ──

  /// POST /shopping-lists/{listId}/items -> 201 ShoppingListItem (version 0).
  Future<ShoppingListItem> createItem(
    String listId,
    OutboxPayload snapshot,
    String? idToken,
  ) async {
    final url = '$_baseUrl/shopping-lists/$listId/items';
    final sw = Stopwatch()..start();
    final http.Response response;
    try {
      response = await _client.post(
        Uri.parse(url),
        headers: _getAuthHeaders(idToken),
        body: json.encode({
          'name': snapshot.name,
          'quantity': snapshot.quantity,
          'unit': snapshot.unit,
          'position': snapshot.position,
        }),
      );
    } catch (e) {
      _log.warning('POST $url failed (${sw.elapsedMilliseconds} ms)', e);
      throw Exception('Network error while creating item: $e');
    }
    _log.info(
      'POST $url -> ${response.statusCode} (${sw.elapsedMilliseconds} ms)',
    );

    switch (response.statusCode) {
      case 201:
        return ShoppingListItem.fromJson(
          json.decode(response.body) as Map<String, dynamic>,
        );
      case 404:
        throw ItemDiscardedException(DiscardReason.gone);
      case 400:
      case 403:
        throw ItemDiscardedException(DiscardReason.rejected);
      default:
        throw Exception('Failed to create item: ${response.statusCode}');
    }
  }

  /// PUT /shopping-lists/{listId}/items/{itemId} -> 200 | 412 | 404 | 400/403.
  Future<ShoppingListItem> updateItem(
    String listId,
    String itemId, {
    required int baseVersion,
    required OutboxPayload snapshot,
    required String? idToken,
  }) async {
    final url = '$_baseUrl/shopping-lists/$listId/items/$itemId';
    final sw = Stopwatch()..start();
    final http.Response response;
    try {
      response = await _client.put(
        Uri.parse(url),
        headers: _getAuthHeaders(idToken),
        body: json.encode({
          'baseVersion': baseVersion,
          'name': snapshot.name,
          'quantity': snapshot.quantity,
          'unit': snapshot.unit,
          'checked': snapshot.checked,
          'position': snapshot.position,
        }),
      );
    } catch (e) {
      _log.warning('PUT $url failed (${sw.elapsedMilliseconds} ms)', e);
      throw Exception('Network error while updating item: $e');
    }
    _log.info(
      'PUT $url -> ${response.statusCode} (${sw.elapsedMilliseconds} ms)',
    );

    switch (response.statusCode) {
      case 200:
        return ShoppingListItem.fromJson(
          json.decode(response.body) as Map<String, dynamic>,
        );
      case 412:
        throw ItemVersionConflictException(
          ShoppingListItem.fromJson(
            json.decode(response.body) as Map<String, dynamic>,
          ),
        );
      case 404:
        throw ItemDiscardedException(DiscardReason.gone);
      case 400:
      case 403:
        throw ItemDiscardedException(DiscardReason.rejected);
      default:
        throw Exception('Failed to update item: ${response.statusCode}');
    }
  }

  /// DELETE /shopping-lists/{listId}/items/{itemId}?baseVersion=n -> 204 | 404 | 412 | 400/403.
  Future<void> deleteItem(
    String listId,
    String itemId,
    int baseVersion,
    String? idToken,
  ) async {
    final url =
        '$_baseUrl/shopping-lists/$listId/items/$itemId?baseVersion=$baseVersion';
    final sw = Stopwatch()..start();
    final http.Response response;
    try {
      response = await _client.delete(
        Uri.parse(url),
        headers: _getAuthHeaders(idToken),
      );
    } catch (e) {
      _log.warning('DELETE $url failed (${sw.elapsedMilliseconds} ms)', e);
      throw Exception('Network error while deleting item: $e');
    }
    _log.info(
      'DELETE $url -> ${response.statusCode} (${sw.elapsedMilliseconds} ms)',
    );

    switch (response.statusCode) {
      case 204:
      case 404:
        return;
      case 412:
        throw ItemVersionConflictException(
          ShoppingListItem.fromJson(
            json.decode(response.body) as Map<String, dynamic>,
          ),
        );
      case 400:
      case 403:
        throw ItemDiscardedException(DiscardReason.rejected);
      default:
        throw Exception('Failed to delete item: ${response.statusCode}');
    }
  }

  // ── Store reads for the sync service (plain DAO passthroughs) ──

  /// The oldest queued entry for [listId], or `null` if its queue is empty.
  Future<OutboxEntry?> nextOutboxEntry(String listId) =>
      _dao.nextOutboxEntry(listId);

  /// Distinct list ids with at least one pending outbox entry (start/resume fan-out).
  Future<List<String>> listIdsWithOutbox() => _dao.listIdsWithOutbox();

  /// The item's current local row (serverId + lastAckedVersion), read live at
  /// push time — never frozen into the outbox entry.
  Future<LocalShoppingListItem?> readItem(String localId) =>
      _dao.readItem(localId);

  // ── Store mutations: reconcile a push outcome (DB + cache coherent) ──

  /// Accept (create/update ack): adopt [winner]'s id/version, drop the acked
  /// entry, clear `dirty` iff no entries remain for the item. Fields are NOT
  /// overwritten — later queued edits have already advanced them.
  Future<void> reconcileAck(
    String localId,
    ShoppingListItem winner,
    int ackedSeq,
  ) async {
    final residentListId = _residentListId(localId);
    final current = residentListId != null
        ? _cache[residentListId]![localId]!
        : await _dao.readItem(localId);
    if (current == null) return;

    late final LocalShoppingListItem updated;
    await _dao.transaction((txn) async {
      await _dao.deleteOutboxEntryTxn(txn, ackedSeq);
      final remaining = await _dao.countOutboxForItemTxn(txn, localId);
      updated = current.copyWith(
        serverId: winner.id,
        lastAckedVersion: winner.version,
        dirty: remaining > 0,
      );
      await _dao.upsertItemTxn(txn, updated);
    });

    if (residentListId != null) {
      _cache[residentListId]![localId] = updated;
      _notifiers[residentListId]!.value = _visibleItems(residentListId);
    }
  }

  /// 204/404 delete ack: hard-remove the row and drop the delete entry.
  Future<void> reconcileDeleteAck(String localId, int ackedSeq) async {
    final residentListId = _residentListId(localId);
    await _dao.transaction((txn) async {
      await _dao.deleteItemRowTxn(txn, localId);
      await _dao.deleteOutboxEntryTxn(txn, ackedSeq);
    });

    if (residentListId != null) {
      _cache[residentListId]!.remove(localId);
      _notifiers[residentListId]!.value = _visibleItems(residentListId);
    }
  }

  /// 412: overwrite the local item with [winner] (un-tombstoning a rejected
  /// delete), adopt the winner's serverId/version, and drop EVERY queued
  /// entry for the item.
  Future<void> cascadeDiscard(String localId, ShoppingListItem winner) async {
    final residentListId = _residentListId(localId);
    final current = residentListId != null
        ? _cache[residentListId]![localId]!
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
    await _dao.transaction((txn) async {
      await _dao.upsertItemTxn(txn, updated);
      await _dao.deleteOutboxForItemTxn(txn, localId);
    });

    if (residentListId != null) {
      _cache[residentListId]![localId] = updated;
      _notifiers[residentListId]!.value = _visibleItems(residentListId);
    }
  }

  /// Gone (404 create/update) / rejected (400/403): hard-remove the row and
  /// drop EVERY queued entry for the item. No winner to roll back to.
  Future<void> discardItem(String localId) async {
    final residentListId = _residentListId(localId);
    await _dao.transaction((txn) async {
      await _dao.deleteItemRowTxn(txn, localId);
      await _dao.deleteOutboxForItemTxn(txn, localId);
    });

    if (residentListId != null) {
      _cache[residentListId]!.remove(localId);
      _notifiers[residentListId]!.value = _visibleItems(residentListId);
    }
  }

  /// The listId the item is currently resident under (list open in memory),
  /// or `null` if the list isn't loaded — reconcile mutations then go DB-only.
  String? _residentListId(String localId) {
    for (final entry in _cache.entries) {
      if (entry.value.containsKey(localId)) return entry.key;
    }
    return null;
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

  void dispose() {
    for (final notifier in _notifiers.values) {
      notifier.dispose();
    }
    _client.close();
  }
}

/// The full mutable field set of a queued create/update outbox entry.
class OutboxPayload {
  final String name;
  final double? quantity;
  final String? unit;
  final bool checked;
  final double position;

  const OutboxPayload({
    required this.name,
    required this.quantity,
    required this.unit,
    required this.checked,
    required this.position,
  });

  factory OutboxPayload.fromMap(Map<String, dynamic> map) {
    return OutboxPayload(
      name: map['name'] as String,
      quantity: map['quantity'] as double?,
      unit: map['unit'] as String?,
      checked: map['checked'] as bool,
      position: map['position'] as double,
    );
  }
}

/// Thrown when a push is rejected with 412: the server's current winning
/// value the local item and outbox queue must roll back to.
class ItemVersionConflictException implements Exception {
  final ShoppingListItem winner;

  const ItemVersionConflictException(this.winner);
}

/// Why a push outcome discarded its item with no winner to roll back to.
enum DiscardReason {
  /// 404 on create/update — a delete already won and removed the row.
  gone,

  /// 400/403 — validation error or lost editor access; can never succeed.
  rejected,
}

/// Thrown for a permanent, non-conflict push failure (§Response
/// classification). The entry and item are discarded, never retried.
class ItemDiscardedException implements Exception {
  final DiscardReason reason;

  const ItemDiscardedException(this.reason);
}
