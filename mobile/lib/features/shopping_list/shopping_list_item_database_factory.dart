import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';

/// Owns shopping-list item DB creation: [open] opens the on-device database,
/// and [createSchema] holds the single-sourced DDL. The test subclass
/// overrides only [open] to return an in-memory ffi database, reusing this
/// schema so test and production DDL can never drift.
class ShoppingListItemDatabaseFactory {
  const ShoppingListItemDatabaseFactory();

  Future<Database> open() async {
    final dir = await getApplicationDocumentsDirectory();
    final path = '${dir.path}${Platform.pathSeparator}shopping_list_items.db';
    return openDatabase(path, version: 1, onCreate: createSchema);
  }

  @protected
  Future<void> createSchema(Database db, int version) async {
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
}
