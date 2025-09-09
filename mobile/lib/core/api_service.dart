import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import '../features/auth/auth_service.dart';
import '../features/extraction/extracted_recipe.dart';
import '../features/recipe/recipe.dart';
import '../features/recipe/recipe_detail.dart';
import 'app_config.dart';

class ApiService {
  final AuthService _authService;
  final http.Client _client = http.Client();
  final String _baseUrl = AppConfig.apiBaseUrl;

  ApiService(this._authService);

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

  Future<ExtractedRecipe> extractRecipeFromText(
    String htmlContent,
  ) async {
    try {
      final headers = await _getAuthHeaders();
      final response = await _client.post(
        Uri.parse('$_baseUrl/extract/text'),
        headers: headers,
        body: json.encode({'text': htmlContent}),
      );

      if (response.statusCode == 200) {
        final Map<String, dynamic> json = jsonDecode(response.body);
        return ExtractedRecipe.fromJson(json);
      } else {
        throw Exception('Failed to extract recipe: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Network error while extracting recipe: $e');
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
        final Map<String, dynamic> json = jsonDecode(response.body);
        return RecipeDetail.fromJson(json);
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
  ) async {
    try {
      final headers = await _getAuthHeaders();
      final response = await _client.put(
        Uri.parse('$_baseUrl/recipes/$id'),
        headers: headers,
        body: json.encode(recipe.toJson()),
      );

      if (response.statusCode == 200) {
        final Map<String, dynamic> json = jsonDecode(response.body);
        return RecipeDetail.fromJson(json);
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

  void dispose() {
    _client.close();
  }
}

class InheritedApiService extends InheritedWidget {
  final ApiService apiService;

  const InheritedApiService({
    super.key,
    required this.apiService,
    required super.child,
  });

  static ApiService of(BuildContext context) {
    final result = context
        .dependOnInheritedWidgetOfExactType<InheritedApiService>();
    assert(result != null, 'No InheritedApiService found in context');
    return result!.apiService;
  }

  @override
  bool updateShouldNotify(InheritedApiService oldWidget) {
    return apiService != oldWidget.apiService;
  }
}
