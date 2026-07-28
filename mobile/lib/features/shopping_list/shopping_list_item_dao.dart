import 'dart:convert';

import 'package:sqflite/sqflite.dart';

import 'local_shopping_list_item.dart';

class ShoppingListItemDao {
  final Database _db;

  ShoppingListItemDao(this._db);

  Future<List<LocalShoppingListItem>> readItems(String listId) async {
    final rows = await _db.query(
      'items',
      where: 'list_id = ?',
      whereArgs: [listId],
    );
    return rows.map(LocalShoppingListItem.fromMap).toList();
  }

  /// Upserts [item] and appends one outbox entry for it, atomically. Covers
  /// create/edit/check/reorder/delete, which differ only in [kind]/[payload].
  Future<void> writeItemAppendingOutbox(
    LocalShoppingListItem item,
    OutboxKind kind,
    Map<String, dynamic> payload,
  ) {
    return _db.transaction((txn) async {
      await _upsertItem(txn, item);
      await _appendOutbox(txn, item.localId, item.listId, kind, payload);
    });
  }

  /// Persists a pull's diff atomically: batch-upserts [upserts] and
  /// batch-deletes [deletedLocalIds]. The diff itself (version gating, dirty
  /// checks, uuid minting) is computed by the caller; this only writes it.
  Future<void> writeServerDiff({
    required List<LocalShoppingListItem> upserts,
    required Set<String> deletedLocalIds,
  }) {
    return _db.transaction((txn) async {
      for (final item in upserts) {
        await _upsertItem(txn, item);
      }
      for (final localId in deletedLocalIds) {
        await _deleteItemRow(txn, localId);
      }
    });
  }

  /// Drops the acked outbox entry, counts what remains for the item, and
  /// upserts [current] adopting [serverId]/[version] with `dirty` set to
  /// whether entries remain — all atomically. Returns the updated row.
  Future<LocalShoppingListItem> writeItemDroppingEntry(
    LocalShoppingListItem current, {
    required String? serverId,
    required int? version,
    required int ackedSeq,
  }) {
    return _db.transaction((txn) async {
      await _deleteOutboxEntry(txn, ackedSeq);
      final remaining = await _countOutboxForItem(txn, current.localId);
      final updated = current.copyWith(
        serverId: serverId,
        lastAckedVersion: version,
        dirty: remaining > 0,
      );
      await _upsertItem(txn, updated);
      return updated;
    });
  }

  /// Hard-removes the item row and drops its acked outbox entry, atomically.
  Future<void> deleteItemDroppingEntry(String localId, int ackedSeq) {
    return _db.transaction((txn) async {
      await _deleteItemRow(txn, localId);
      await _deleteOutboxEntry(txn, ackedSeq);
    });
  }

  /// Upserts [item] and drops every queued outbox entry for [localId],
  /// atomically.
  Future<void> writeItemClearingOutbox(
    LocalShoppingListItem item,
    String localId,
  ) {
    return _db.transaction((txn) async {
      await _upsertItem(txn, item);
      await _deleteOutboxForItem(txn, localId);
    });
  }

  /// Hard-removes the item row and drops every queued outbox entry for
  /// [localId], atomically.
  Future<void> deleteItemClearingOutbox(String localId) {
    return _db.transaction((txn) async {
      await _deleteItemRow(txn, localId);
      await _deleteOutboxForItem(txn, localId);
    });
  }

  Future<void> _upsertItem(Transaction txn, LocalShoppingListItem item) {
    return txn.insert(
      'items',
      item.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> _appendOutbox(
    Transaction txn,
    String localId,
    String listId,
    OutboxKind kind,
    Map<String, dynamic> payload,
  ) {
    return txn.insert('outbox', {
      'item_local_id': localId,
      'list_id': listId,
      'kind': kind.wire,
      'payload': jsonEncode(payload),
    });
  }

  Future<void> _deleteOutboxEntry(Transaction txn, int seq) {
    return txn.delete('outbox', where: 'seq = ?', whereArgs: [seq]);
  }

  Future<void> _deleteOutboxForItem(Transaction txn, String localId) {
    return txn.delete(
      'outbox',
      where: 'item_local_id = ?',
      whereArgs: [localId],
    );
  }

  Future<int> _countOutboxForItem(Transaction txn, String localId) async {
    final result = await txn.rawQuery(
      'SELECT COUNT(*) AS c FROM outbox WHERE item_local_id = ?',
      [localId],
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<void> _deleteItemRow(Transaction txn, String localId) {
    return txn.delete('items', where: 'local_id = ?', whereArgs: [localId]);
  }

  /// The oldest queued entry for [listId] (lowest `seq`), or `null` if empty.
  Future<OutboxEntry?> nextOutboxEntry(String listId) async {
    final rows = await _db.query(
      'outbox',
      where: 'list_id = ?',
      whereArgs: [listId],
      orderBy: 'seq ASC',
      limit: 1,
    );
    if (rows.isEmpty) return null;
    return OutboxEntry.fromMap(rows.first);
  }

  /// Distinct list ids with at least one pending outbox entry.
  Future<List<String>> listIdsWithOutbox() async {
    final rows = await _db.rawQuery('SELECT DISTINCT list_id FROM outbox');
    return rows.map((row) => row['list_id'] as String).toList();
  }

  /// Reads a single item row by [localId], or `null` if it no longer exists.
  Future<LocalShoppingListItem?> readItem(String localId) async {
    final rows = await _db.query(
      'items',
      where: 'local_id = ?',
      whereArgs: [localId],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    return LocalShoppingListItem.fromMap(rows.first);
  }

}

enum OutboxKind {
  create,
  update,
  delete;

  /// The value persisted to the `outbox.kind` column.
  String get wire => name;

  /// Parses a persisted [wire] value. Throws if it does not match a known kind.
  static OutboxKind fromWire(String wire) => OutboxKind.values.byName(wire);
}

/// A single queued outbox row, decoded for push.
class OutboxEntry {
  final int seq;
  final String itemLocalId;
  final String listId;
  final OutboxKind kind;
  final Map<String, dynamic> payload;

  const OutboxEntry({
    required this.seq,
    required this.itemLocalId,
    required this.listId,
    required this.kind,
    required this.payload,
  });

  factory OutboxEntry.fromMap(Map<String, dynamic> map) {
    return OutboxEntry(
      seq: map['seq'] as int,
      itemLocalId: map['item_local_id'] as String,
      listId: map['list_id'] as String,
      kind: OutboxKind.fromWire(map['kind'] as String),
      payload: jsonDecode(map['payload'] as String) as Map<String, dynamic>,
    );
  }
}
