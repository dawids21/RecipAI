import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';

import 'local_shopping_list_item.dart';

class ShoppingListItemDao {
  final Database _db;

  ShoppingListItemDao(this._db);

  static Future<Database> openShoppingListDatabase() async {
    final dir = await getApplicationDocumentsDirectory();
    final path = '${dir.path}${Platform.pathSeparator}shopping_list_items.db';
    return openDatabase(path, version: 1, onCreate: _onCreate);
  }

  static Future<void> createSchema(Database db) => _onCreate(db, 1);

  static Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE items (
        local_id        TEXT PRIMARY KEY,
        server_id       TEXT,
        list_id         TEXT NOT NULL,
        name            TEXT NOT NULL,
        quantity        REAL,
        unit            TEXT,
        checked         INTEGER NOT NULL DEFAULT 0,
        position        REAL NOT NULL,
        last_acked_version INTEGER,
        dirty           INTEGER NOT NULL DEFAULT 0,
        failed          INTEGER NOT NULL DEFAULT 0,
        pending_delete  INTEGER NOT NULL DEFAULT 0
      )
    ''');
    await db.execute('CREATE INDEX idx_items_list_id ON items(list_id)');

    await db.execute('''
      CREATE TABLE outbox (
        seq             INTEGER PRIMARY KEY AUTOINCREMENT,
        item_local_id   TEXT NOT NULL,
        list_id         TEXT NOT NULL,
        kind            TEXT NOT NULL,
        payload         TEXT NOT NULL
      )
    ''');
    await db.execute(
      'CREATE INDEX idx_outbox_item_local_id ON outbox(item_local_id)',
    );
  }

  Future<List<LocalShoppingListItem>> readItems(String listId) async {
    final rows = await _db.query(
      'items',
      where: 'list_id = ?',
      whereArgs: [listId],
    );
    return rows.map(LocalShoppingListItem.fromMap).toList();
  }

  Future<void> upsertItem(LocalShoppingListItem item) async {
    await _db.insert(
      'items',
      item.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> appendOutbox(
    String localId,
    String listId,
    OutboxKind kind,
    Map<String, dynamic> payload,
  ) async {
    await _db.insert('outbox', {
      'item_local_id': localId,
      'list_id': listId,
      'kind': kind.wire,
      'payload': jsonEncode(payload),
    });
  }

  Future<T> transaction<T>(Future<T> Function(Transaction txn) fn) {
    return _db.transaction(fn);
  }

  Future<void> upsertItemTxn(Transaction txn, LocalShoppingListItem item) {
    return txn.insert(
      'items',
      item.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> appendOutboxTxn(
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

  Future<void> deleteOutboxEntryTxn(Transaction txn, int seq) {
    return txn.delete('outbox', where: 'seq = ?', whereArgs: [seq]);
  }

  Future<void> deleteOutboxForItemTxn(Transaction txn, String localId) {
    return txn.delete(
      'outbox',
      where: 'item_local_id = ?',
      whereArgs: [localId],
    );
  }

  Future<int> countOutboxForItemTxn(Transaction txn, String localId) async {
    final result = await txn.rawQuery(
      'SELECT COUNT(*) AS c FROM outbox WHERE item_local_id = ?',
      [localId],
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<void> deleteItemRowTxn(Transaction txn, String localId) {
    return txn.delete('items', where: 'local_id = ?', whereArgs: [localId]);
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
