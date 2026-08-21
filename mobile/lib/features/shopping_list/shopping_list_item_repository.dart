import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';

import '../../core/app_config.dart';
import 'shopping_list_item.dart';

class ShoppingListItemRepository {
  static final _log = Logger('recipai.shopping_list.repository');

  final http.Client _client;
  final String _baseUrl = AppConfig.apiBaseUrl;

  ShoppingListItemRepository({http.Client? client})
    : _client = client ?? http.Client();

  Map<String, String> _getAuthHeaders(String? idToken) {
    return {
      'Content-Type': 'application/json',
      if (idToken != null) 'Authorization': 'Bearer $idToken',
    };
  }

  // ── HTTP: item read (pull) ──

  /// GET /shopping-lists/{listId} -> parses the `items[]` array. Only a 2xx
  /// with a parseable body is a result; every other outcome (network/timeout,
  /// 401/403/404/5xx) throws — the poll caller leaves the store untouched
  /// rather than risk mistaking a permission/lifecycle error for "the list is
  /// now empty".
  Future<List<ShoppingListItem>> fetchServerItems(
    String listId,
    String? idToken,
  ) async {
    final url = '$_baseUrl/shopping-lists/$listId';
    final sw = Stopwatch()..start();
    final http.Response response;
    try {
      response = await _client.get(
        Uri.parse(url),
        headers: _getAuthHeaders(idToken),
      );
    } catch (e) {
      _log.warning('GET $url failed (${sw.elapsedMilliseconds} ms)', e);
      throw ShoppingListNetworkException();
    }
    _log.info(
      'GET $url -> ${response.statusCode} (${sw.elapsedMilliseconds} ms)',
    );

    if (response.statusCode != 200) {
      throw Exception(
        'Failed to fetch shopping list items: ${response.statusCode}',
      );
    }
    final body = json.decode(response.body) as Map<String, dynamic>;
    final items = body['items'] as List;
    return items
        .map((item) => ShoppingListItem.fromJson(item as Map<String, dynamic>))
        .toList();
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
          'checked': snapshot.checked,
          'position': snapshot.position,
        }),
      );
    } catch (e) {
      _log.warning('POST $url failed (${sw.elapsedMilliseconds} ms)', e);
      throw ShoppingListNetworkException();
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
      case 429:
        throw ItemDiscardedException(DiscardReason.limitReached);
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
      throw ShoppingListNetworkException();
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
      throw ShoppingListNetworkException();
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

  void dispose() {
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

/// Thrown for a caught network/timeout failure on any item HTTP call (read or
/// write) — distinguishes "offline" from a non-2xx server response so the
/// sync service can map it to [SyncStatus.offline] specifically.
class ShoppingListNetworkException implements Exception {
  const ShoppingListNetworkException();
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

  /// 429 on create — the list is at its item cap; waiting cannot resolve a stock cap.
  limitReached,
}

/// Thrown for a permanent, non-conflict push failure (§Response
/// classification). The entry and item are discarded, never retried.
class ItemDiscardedException implements Exception {
  final DiscardReason reason;

  const ItemDiscardedException(this.reason);
}
