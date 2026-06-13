import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';

import '../../core/app_config.dart';
import 'shopping_list.dart';
import 'shopping_list_detail.dart';
import 'shopping_list_permission.dart';

class ShoppingListRepository {
  static final _log = Logger('recipai.shopping_list.repository');

  final http.Client _client = http.Client();
  final String _baseUrl = AppConfig.apiBaseUrl;

  ShoppingListRepository();

  Map<String, String> _getAuthHeaders(String? idToken) {
    return {
      'Content-Type': 'application/json',
      if (idToken != null) 'Authorization': 'Bearer $idToken',
    };
  }

  Future<List<ShoppingList>> fetchShoppingLists(String? idToken) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.get(
        Uri.parse('$_baseUrl/shopping-lists'),
        headers: headers,
      );

      if (response.statusCode == 200) {
        final List<dynamic> jsonList = json.decode(response.body);
        return jsonList
            .map((json) => ShoppingList.fromJson(json as Map<String, dynamic>))
            .toList();
      } else {
        throw Exception(
          'Failed to load shopping lists: ${response.statusCode}',
        );
      }
    } catch (e) {
      throw Exception('Network error while fetching shopping lists: $e');
    }
  }

  Future<ShoppingList> createShoppingList(String name, String? idToken) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.post(
        Uri.parse('$_baseUrl/shopping-lists'),
        headers: headers,
        body: json.encode({'name': name}),
      );

      if (response.statusCode == 201) {
        final Map<String, dynamic> jsonMap = json.decode(response.body);
        return ShoppingList.fromJson(jsonMap);
      } else {
        throw Exception(
          'Failed to create shopping list: ${response.statusCode}',
        );
      }
    } catch (e) {
      throw Exception('Network error while creating shopping list: $e');
    }
  }

  Future<ShoppingListDetail> fetchShoppingListDetail(
    String id,
    String? idToken,
  ) async {
    final url = '$_baseUrl/shopping-lists/$id';
    final sw = Stopwatch()..start();
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.get(Uri.parse(url), headers: headers);
      _log.info('GET $url -> ${response.statusCode} (${sw.elapsedMilliseconds} ms)');

      if (response.statusCode == 200) {
        final Map<String, dynamic> jsonMap = json.decode(response.body);
        return ShoppingListDetail.fromJson(jsonMap);
      } else if (response.statusCode == 404) {
        throw Exception('Shopping list not found');
      } else {
        throw Exception(
          'Failed to load shopping list detail: ${response.statusCode}',
        );
      }
    } catch (e) {
      _log.warning('GET $url failed (${sw.elapsedMilliseconds} ms)', e);
      throw Exception('Network error while fetching shopping list detail: $e');
    }
  }

  Future<ShoppingList> updateShoppingList(
    String id,
    String name,
    String? idToken,
  ) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.put(
        Uri.parse('$_baseUrl/shopping-lists/$id'),
        headers: headers,
        body: json.encode({'name': name}),
      );

      if (response.statusCode == 200) {
        final Map<String, dynamic> jsonMap = json.decode(response.body);
        return ShoppingList.fromJson(jsonMap);
      } else if (response.statusCode == 403) {
        throw Exception('You do not have permission to rename this list');
      } else if (response.statusCode == 404) {
        throw Exception('Shopping list not found');
      } else {
        throw Exception(
          'Failed to update shopping list: ${response.statusCode}',
        );
      }
    } catch (e) {
      throw Exception('Network error: $e');
    }
  }

  Future<void> deleteShoppingList(String id, String? idToken) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.delete(
        Uri.parse('$_baseUrl/shopping-lists/$id'),
        headers: headers,
      );

      if (response.statusCode == 204) {
        return;
      } else if (response.statusCode == 403) {
        throw Exception('You do not have permission to delete this list');
      } else if (response.statusCode == 404) {
        throw Exception('Shopping list not found');
      } else {
        throw Exception(
          'Failed to delete shopping list: ${response.statusCode}',
        );
      }
    } catch (e) {
      throw Exception('Network error: $e');
    }
  }

  // TODO(shopping-list-items): add the item write endpoints this repository should
  // call — create, update, delete, move (reorder), check, and uncheck an item.

  Future<List<ShoppingListPermission>> fetchSharedUsers(
    String shoppingListId,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.get(
      Uri.parse('$_baseUrl/shopping-lists/$shoppingListId/users'),
      headers: headers,
    );

    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList
          .map(
            (json) =>
                ShoppingListPermission.fromJson(json as Map<String, dynamic>),
          )
          .toList();
    } else if (response.statusCode == 404) {
      throw Exception('Shopping list not found');
    } else {
      throw Exception('Failed to load shared users: ${response.statusCode}');
    }
  }

  Future<void> shareShoppingList(
    String shoppingListId,
    String email,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.post(
      Uri.parse('$_baseUrl/shopping-lists/$shoppingListId/share'),
      headers: headers,
      body: json.encode({'email': email}),
    );

    if (response.statusCode == 204) {
      return;
    } else if (response.statusCode == 404) {
      throw Exception('Shopping list not found');
    } else {
      throw Exception('Failed to share shopping list: ${response.statusCode}');
    }
  }

  Future<void> unshareShoppingList(
    String shoppingListId,
    String email,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.post(
      Uri.parse('$_baseUrl/shopping-lists/$shoppingListId/unshare'),
      headers: headers,
      body: json.encode({'email': email}),
    );

    if (response.statusCode == 204) {
      return;
    } else if (response.statusCode == 404) {
      throw Exception('Shopping list not found');
    } else {
      throw Exception(
        'Failed to unshare shopping list: ${response.statusCode}',
      );
    }
  }
}
