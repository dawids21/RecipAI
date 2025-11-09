import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../core/app_config.dart';
import 'optimistic_lock_exception.dart';
import 'shopping_list.dart';
import 'shopping_list_detail.dart';

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
      throw Exception('Failed to load shopping lists: ${response.statusCode}');
    }
  }

  Future<ShoppingList> createShoppingList(String name, String? idToken) async {
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
      throw Exception('Failed to create shopping list: ${response.statusCode}');
    }
  }

  Future<ShoppingListDetail> fetchShoppingListDetail(
    String id,
    String? idToken,
  ) async {
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
  }

  Future<ShoppingList> updateShoppingList(
    String id,
    String name,
    int version,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    headers['If-Match'] = '"$version"';
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
    } else if (response.statusCode == 412) {
      throw OptimisticLockException('This list was modified by someone else.');
    } else {
      throw Exception('Failed to update shopping list: ${response.statusCode}');
    }
  }

  Future<void> deleteShoppingList(
    String id,
    int version,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    headers['If-Match'] = '"$version"';
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
    } else if (response.statusCode == 412) {
      throw OptimisticLockException('This list was modified by someone else');
    } else {
      throw Exception('Failed to delete shopping list: ${response.statusCode}');
    }
  }
}
