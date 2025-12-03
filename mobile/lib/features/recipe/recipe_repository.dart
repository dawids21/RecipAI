import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../core/app_config.dart';
import 'recipe.dart';
import 'recipe_detail.dart';
import 'recipe_permission.dart';

class RecipeRepository {
  final http.Client _client = http.Client();
  final String _baseUrl = AppConfig.apiBaseUrl;

  RecipeRepository();

  Map<String, String> _getAuthHeaders(String? idToken) {
    return {
      'Content-Type': 'application/json',
      if (idToken != null) 'Authorization': 'Bearer $idToken',
    };
  }

  Future<List<Recipe>> fetchRecipes(String? idToken) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.get(
      Uri.parse('$_baseUrl/recipes'),
      headers: headers,
    );

    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList
          .map((json) => Recipe.fromJson(json as Map<String, dynamic>))
          .toList();
    } else {
      throw Exception('Failed to load recipes: ${response.statusCode}');
    }
  }

  Future<List<Recipe>> fetchRecipesByCollectionId(
    String collectionId,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    final uri = Uri.parse(
      '$_baseUrl/recipes',
    ).replace(queryParameters: {'collectionId': collectionId});
    final response = await _client.get(uri, headers: headers);

    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList
          .map((json) => Recipe.fromJson(json as Map<String, dynamic>))
          .toList();
    } else {
      throw Exception('Failed to load recipes: ${response.statusCode}');
    }
  }

  Future<List<Recipe>> fetchUnassignedRecipes(String? idToken) async {
    final headers = _getAuthHeaders(idToken);
    final uri = Uri.parse(
      '$_baseUrl/recipes',
    ).replace(queryParameters: {'unassigned': 'true'});
    final response = await _client.get(uri, headers: headers);

    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList
          .map((json) => Recipe.fromJson(json as Map<String, dynamic>))
          .toList();
    } else {
      throw Exception('Failed to load recipes: ${response.statusCode}');
    }
  }

  Future<RecipeDetail> fetchRecipeDetail(String id, String? idToken) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.get(
        Uri.parse('$_baseUrl/recipes/$id'),
        headers: headers,
      );

      if (response.statusCode == 200) {
        final Map<String, dynamic> json = jsonDecode(response.body);
        return RecipeDetail.fromJson(json);
      } else if (response.statusCode == 404) {
        throw Exception('Recipe not found');
      } else {
        throw Exception('Failed to load recipe detail: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Network error while fetching recipe detail: $e');
    }
  }

  Future<RecipeDetail> createRecipe(
    RecipeDetail recipe,
    String? idToken,
  ) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.post(
        Uri.parse('$_baseUrl/recipes'),
        headers: headers,
        body: json.encode(recipe.toJson()),
      );

      if (response.statusCode == 201) {
        final Map<String, dynamic> jsonMap = jsonDecode(response.body);
        return RecipeDetail.fromJson(jsonMap);
      } else {
        throw Exception('Failed to create recipe: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Network error while creating recipe: $e');
    }
  }

  Future<RecipeDetail> updateRecipe(
    String id,
    RecipeDetail recipe,
    String? idToken,
  ) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.put(
        Uri.parse('$_baseUrl/recipes/$id'),
        headers: headers,
        body: json.encode(recipe.toJson()),
      );

      if (response.statusCode == 200) {
        final Map<String, dynamic> jsonMap = jsonDecode(response.body);
        return RecipeDetail.fromJson(jsonMap);
      } else if (response.statusCode == 404) {
        throw Exception('Recipe not found');
      } else {
        throw Exception('Failed to update recipe: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Network error while updating recipe: $e');
    }
  }

  Future<void> deleteRecipe(String id, String? idToken) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.delete(
        Uri.parse('$_baseUrl/recipes/$id'),
        headers: headers,
      );

      if (response.statusCode == 204) {
        return;
      } else if (response.statusCode == 404) {
        throw Exception('Recipe not found');
      } else {
        throw Exception('Failed to delete recipe: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Network error while deleting recipe: $e');
    }
  }

  Future<List<RecipePermission>> fetchSharedUsers(
    String recipeId,
    String? idToken,
  ) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.get(
        Uri.parse('$_baseUrl/recipes/$recipeId/shared_users'),
        headers: headers,
      );

      if (response.statusCode == 200) {
        final List<dynamic> jsonList = json.decode(response.body);
        return jsonList
            .map(
              (json) => RecipePermission.fromJson(json as Map<String, dynamic>),
            )
            .toList();
      } else if (response.statusCode == 404) {
        throw Exception('Recipe not found');
      } else {
        throw Exception('Failed to load shared users: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Network error while fetching shared users: $e');
    }
  }

  Future<void> shareRecipe(
    String recipeId,
    String email,
    String? idToken,
  ) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.post(
        Uri.parse('$_baseUrl/recipes/$recipeId/share'),
        headers: headers,
        body: json.encode({'email': email}),
      );

      if (response.statusCode == 200) {
        return;
      } else if (response.statusCode == 404) {
        throw Exception('Recipe not found');
      } else {
        throw Exception('Failed to share recipe: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Network error while sharing recipe: $e');
    }
  }

  Future<void> unshareRecipe(
    String recipeId,
    String email,
    String? idToken,
  ) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.post(
        Uri.parse('$_baseUrl/recipes/$recipeId/unshare'),
        headers: headers,
        body: json.encode({'email': email}),
      );

      if (response.statusCode == 200) {
        return;
      } else if (response.statusCode == 404) {
        throw Exception('Recipe not found');
      } else {
        throw Exception('Failed to unshare recipe: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Network error while unsharing recipe: $e');
    }
  }
}
