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
