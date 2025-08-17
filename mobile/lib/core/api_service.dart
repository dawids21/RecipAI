import 'dart:convert';

import 'package:http/http.dart' as http;

import '../features/recipe/recipe.dart';
import '../features/recipe/recipe_detail.dart';
import 'app_config.dart';

class ApiService {
  static final http.Client _client = http.Client();
  static final String _baseUrl = AppConfig.apiBaseUrl;

  static Future<List<Recipe>> fetchRecipes() async {
    try {
      final response = await _client.get(
        Uri.parse('$_baseUrl/recipes'),
        headers: {'Content-Type': 'application/json'},
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

  static Future<RecipeDetail> fetchRecipeDetail(String id) async {
    try {
      final response = await _client.get(
        Uri.parse('$_baseUrl/recipes/$id'),
        headers: {'Content-Type': 'application/json'},
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

  static Future<RecipeDetail> extractRecipeFromText(String htmlContent) async {
    try {
      final response = await _client.post(
        Uri.parse('$_baseUrl/extract/text'),
        headers: {'Content-Type': 'application/json'},
        body: json.encode({'text': htmlContent}),
      );

      if (response.statusCode == 200) {
        final Map<String, dynamic> json = jsonDecode(response.body);
        return RecipeDetail.fromJson(json);
      } else {
        throw Exception('Failed to extract recipe: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Network error while extracting recipe: $e');
    }
  }

  static Future<RecipeDetail> createRecipe(RecipeDetail recipe) async {
    try {
      final response = await _client.post(
        Uri.parse('$_baseUrl/recipes'),
        headers: {'Content-Type': 'application/json'},
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

  static Future<RecipeDetail> updateRecipe(String id,
      RecipeDetail recipe) async {
    try {
      final response = await _client.put(
        Uri.parse('$_baseUrl/recipes/$id'),
        headers: {'Content-Type': 'application/json'},
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

  static Future<void> deleteRecipe(String id) async {
    try {
      final response = await _client.delete(
        Uri.parse('$_baseUrl/recipes/$id'),
        headers: {'Content-Type': 'application/json'},
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

  static void dispose() {
    _client.close();
  }
}
