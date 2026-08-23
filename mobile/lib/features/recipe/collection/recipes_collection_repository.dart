import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../core/app_config.dart';
import '../../limits/limit_usage.dart';
import 'recipes_collection.dart';
import 'recipes_collection_permission.dart';

class RecipesCollectionRepository {
  final http.Client _client = http.Client();
  final String _baseUrl = AppConfig.apiBaseUrl;

  RecipesCollectionRepository();

  Map<String, String> _getAuthHeaders(String? idToken) {
    return {
      'Content-Type': 'application/json',
      if (idToken != null) 'Authorization': 'Bearer $idToken',
    };
  }

  Future<LimitUsage> fetchCollectionUsage(String? idToken) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.get(
      Uri.parse('$_baseUrl/collections/usage'),
      headers: headers,
    );

    if (response.statusCode == 200) {
      return LimitUsage.fromJson(json.decode(response.body));
    } else {
      throw Exception(
        'Failed to load recipes collection usage: ${response.statusCode}',
      );
    }
  }

  Future<List<RecipesCollection>> fetchRecipesCollections(
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.get(
      Uri.parse('$_baseUrl/collections'),
      headers: headers,
    );

    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList
          .map(
            (json) => RecipesCollection.fromJson(json as Map<String, dynamic>),
          )
          .toList();
    } else {
      throw Exception(
        'Failed to load recipes collections: ${response.statusCode}',
      );
    }
  }

  Future<RecipesCollection> createRecipesCollection(
    String name,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.post(
      Uri.parse('$_baseUrl/collections'),
      headers: headers,
      body: json.encode({'name': name}),
    );

    if (response.statusCode == 201) {
      final Map<String, dynamic> jsonMap = json.decode(response.body);
      return RecipesCollection.fromJson(jsonMap);
    } else {
      throw Exception(
        'Failed to create recipes collection: ${response.statusCode}',
      );
    }
  }

  Future<RecipesCollection> updateRecipesCollection(
    String id,
    String name,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.put(
      Uri.parse('$_baseUrl/collections/$id'),
      headers: headers,
      body: json.encode({'name': name}),
    );

    if (response.statusCode == 200) {
      final Map<String, dynamic> jsonMap = json.decode(response.body);
      return RecipesCollection.fromJson(jsonMap);
    } else if (response.statusCode == 403) {
      throw Exception(
        'You do not have permission to rename this recipes collection',
      );
    } else if (response.statusCode == 404) {
      throw Exception('Recipes collection not found');
    } else {
      throw Exception(
        'Failed to update recipes collection: ${response.statusCode}',
      );
    }
  }

  Future<void> deleteRecipesCollection(String id, String? idToken) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.delete(
      Uri.parse('$_baseUrl/collections/$id'),
      headers: headers,
    );

    if (response.statusCode == 204) {
      return;
    } else if (response.statusCode == 403) {
      throw Exception(
        'You do not have permission to delete this recipes collection',
      );
    } else if (response.statusCode == 404) {
      throw Exception('Recipes collection not found');
    } else {
      throw Exception(
        'Failed to delete recipes collection: ${response.statusCode}',
      );
    }
  }

  Future<List<RecipesCollectionPermission>> fetchSharedUsers(
    String collectionId,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.get(
      Uri.parse('$_baseUrl/collections/$collectionId/users'),
      headers: headers,
    );

    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList
          .map(
            (json) => RecipesCollectionPermission.fromJson(
              json as Map<String, dynamic>,
            ),
          )
          .toList();
    } else if (response.statusCode == 404) {
      throw Exception('Recipes collection not found');
    } else {
      throw Exception('Failed to load shared users: ${response.statusCode}');
    }
  }

  Future<void> shareCollection(
    String collectionId,
    String email,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.post(
      Uri.parse('$_baseUrl/collections/$collectionId/share'),
      headers: headers,
      body: json.encode({'email': email}),
    );

    if (response.statusCode == 204) {
      return;
    } else if (response.statusCode == 404) {
      throw Exception('Recipes collection not found');
    } else if (response.statusCode == 403) {
      throw Exception(
        'You do not have permission to share this recipes collection',
      );
    } else {
      throw Exception(
        'Failed to share recipes collection: ${response.statusCode}',
      );
    }
  }

  Future<void> unshareCollection(
    String collectionId,
    String email,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.post(
      Uri.parse('$_baseUrl/collections/$collectionId/unshare'),
      headers: headers,
      body: json.encode({'email': email}),
    );

    if (response.statusCode == 204) {
      return;
    } else if (response.statusCode == 404) {
      throw Exception('Recipes collection not found');
    } else if (response.statusCode == 403) {
      throw Exception(
        'You do not have permission to unshare this recipes collection',
      );
    } else {
      throw Exception(
        'Failed to unshare recipes collection: ${response.statusCode}',
      );
    }
  }
}
