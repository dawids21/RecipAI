import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../core/app_config.dart';
import 'shopping_list.dart';

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
}
