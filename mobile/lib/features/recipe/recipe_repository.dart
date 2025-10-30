import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../core/app_config.dart';
import '../auth/auth_service.dart';
import 'recipe.dart';
import 'recipe_detail.dart';
import 'shared_user.dart';

class RecipeRepository {
  final AuthService _authService;
  final http.Client _client = http.Client();
  final String _baseUrl = AppConfig.apiBaseUrl;

  RecipeRepository(this._authService);

  Future<Map<String, String>> _getAuthHeaders() async {
    final token = await _authService.idToken;
    return {
      'Content-Type': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  Future<List<Recipe>> fetchRecipes() async {
    try {
      final headers = await _getAuthHeaders();
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
    } catch (e) {
      throw Exception('Network error while fetching recipes: $e');
    }
  }

  Future<RecipeDetail> fetchRecipeDetail(String id) async {
    try {
      final headers = await _getAuthHeaders();
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

  Future<RecipeDetail> createRecipe(RecipeDetail recipe) async {
    try {
      final headers = await _getAuthHeaders();
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

  Future<RecipeDetail> updateRecipe(String id, RecipeDetail recipe) async {
    try {
      final headers = await _getAuthHeaders();
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

  Future<void> deleteRecipe(String id) async {
    try {
      final headers = await _getAuthHeaders();
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

  Future<List<SharedUser>> fetchSharedUsers(String recipeId) async {
    try {
      final headers = await _getAuthHeaders();
      final response = await _client.get(
        Uri.parse('$_baseUrl/recipes/$recipeId/shared_users'),
        headers: headers,
      );

      if (response.statusCode == 200) {
        final List<dynamic> jsonList = json.decode(response.body);
        return jsonList
            .map((json) => SharedUser.fromJson(json as Map<String, dynamic>))
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

  Future<void> shareRecipe(String recipeId, String email) async {
    try {
      final headers = await _getAuthHeaders();
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

  Future<void> unshareRecipe(String recipeId, String email) async {
    try {
      final headers = await _getAuthHeaders();
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
