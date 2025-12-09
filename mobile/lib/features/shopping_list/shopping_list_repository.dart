import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../core/app_config.dart';
import 'shopping_list.dart';
import 'shopping_list_detail.dart';
import 'shopping_list_exceptions.dart';
import 'shopping_list_item.dart';
import 'shopping_list_permission.dart';

class ShoppingListRepository {
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
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.get(
        Uri.parse('$_baseUrl/shopping-lists/$id'),
        headers: headers,
      );

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

  Future<ShoppingListItem> createItem(
    String listId,
    String name,
    double? quantity,
    String? unit,
    String? idToken,
    int? index,
  ) async {
    final headers = _getAuthHeaders(idToken);
    final body = <String, dynamic>{
      'name': name,
      if (quantity != null) 'quantity': quantity,
      if (unit != null) 'unit': unit,
      if (index != null) 'index': index,
    };

    final response = await _client.post(
      Uri.parse('$_baseUrl/shopping-lists/$listId/item'),
      headers: headers,
      body: json.encode(body),
    );

    if (response.statusCode == 400) {
      throw ShoppingListItemApiException('Invalid item data');
    } else if (response.statusCode == 401) {
      throw ShoppingListItemApiException('Unauthorized');
    } else if (response.statusCode == 403) {
      throw ShoppingListItemApiException(
        'You do not have permission to add items to this list',
      );
    } else if (response.statusCode == 404) {
      throw ShoppingListItemApiException('Shopping list not found');
    }

    final Map<String, dynamic> jsonMap = json.decode(response.body);
    return ShoppingListItem.fromJson(jsonMap);
  }

  Future<void> deleteItem(
    String listId,
    String itemId,
    int version,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    headers['If-Match'] = version.toString();

    final response = await _client.delete(
      Uri.parse('$_baseUrl/shopping-lists/$listId/item/$itemId'),
      headers: headers,
    );

    if (response.statusCode == 401) {
      throw ShoppingListItemApiException('Unauthorized');
    } else if (response.statusCode == 403) {
      throw ShoppingListItemApiException(
        'You do not have permission to delete items from this list',
      );
    } else if (response.statusCode == 404) {
      throw ShoppingListItemApiException('Item not found');
    } else if (response.statusCode == 412) {
      throw ShoppingListItemApiConflictException('412 Precondition Failed');
    }
  }

  Future<ShoppingListItem> moveItem(
    String listId,
    String itemId,
    int version,
    int targetIndex,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    headers['If-Match'] = version.toString();

    final response = await _client.post(
      Uri.parse('$_baseUrl/shopping-lists/$listId/item/$itemId/move'),
      headers: headers,
      body: json.encode({'index': targetIndex}),
    );

    if (response.statusCode == 400) {
      throw ShoppingListItemApiException('Invalid index');
    } else if (response.statusCode == 401) {
      throw ShoppingListItemApiException('Unauthorized');
    } else if (response.statusCode == 403) {
      throw ShoppingListItemApiException(
        'You do not have permission to move items in this list',
      );
    } else if (response.statusCode == 404) {
      throw ShoppingListItemApiException('Item not found');
    } else if (response.statusCode == 412) {
      throw ShoppingListItemApiConflictException('412 Precondition Failed');
    }

    final Map<String, dynamic> jsonMap = json.decode(response.body);
    return ShoppingListItem.fromJson(jsonMap);
  }

  Future<ShoppingListItem> checkItem(
    String listId,
    String itemId,
    int version,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    headers['If-Match'] = version.toString();

    final response = await _client.post(
      Uri.parse('$_baseUrl/shopping-lists/$listId/item/$itemId/check'),
      headers: headers,
    );

    if (response.statusCode == 401) {
      throw ShoppingListItemApiException('Unauthorized');
    } else if (response.statusCode == 403) {
      throw ShoppingListItemApiException(
        'You do not have permission to check items in this list',
      );
    } else if (response.statusCode == 404) {
      throw ShoppingListItemApiException('Item not found');
    } else if (response.statusCode == 412) {
      throw ShoppingListItemApiConflictException('412 Precondition Failed');
    }

    final Map<String, dynamic> jsonMap = json.decode(response.body);
    return ShoppingListItem.fromJson(jsonMap);
  }

  Future<ShoppingListItem> uncheckItem(
    String listId,
    String itemId,
    int version,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    headers['If-Match'] = version.toString();

    final response = await _client.post(
      Uri.parse('$_baseUrl/shopping-lists/$listId/item/$itemId/uncheck'),
      headers: headers,
    );

    if (response.statusCode == 401) {
      throw ShoppingListItemApiException('Unauthorized');
    } else if (response.statusCode == 403) {
      throw ShoppingListItemApiException(
        'You do not have permission to uncheck items in this list',
      );
    } else if (response.statusCode == 404) {
      throw ShoppingListItemApiException('Item not found');
    } else if (response.statusCode == 412) {
      throw ShoppingListItemApiConflictException('412 Precondition Failed');
    }

    final Map<String, dynamic> jsonMap = json.decode(response.body);
    return ShoppingListItem.fromJson(jsonMap);
  }

  Future<ShoppingListItem> updateItem(
    String listId,
    String itemId,
    int version,
    String name,
    double? quantity,
    String? unit,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    headers['If-Match'] = version.toString();

    final body = <String, dynamic>{
      'name': name,
      if (quantity != null) 'quantity': quantity,
      if (unit != null) 'unit': unit,
    };

    final response = await _client.put(
      Uri.parse('$_baseUrl/shopping-lists/$listId/item/$itemId'),
      headers: headers,
      body: json.encode(body),
    );

    if (response.statusCode == 400) {
      throw ShoppingListItemApiException('Invalid item data');
    } else if (response.statusCode == 401) {
      throw ShoppingListItemApiException('Unauthorized');
    } else if (response.statusCode == 403) {
      throw ShoppingListItemApiException(
        'You do not have permission to update items in this list',
      );
    } else if (response.statusCode == 404) {
      throw ShoppingListItemApiException('Item not found');
    } else if (response.statusCode == 412) {
      throw ShoppingListItemApiConflictException('412 Precondition Failed');
    }

    final Map<String, dynamic> jsonMap = json.decode(response.body);
    return ShoppingListItem.fromJson(jsonMap);
  }

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
