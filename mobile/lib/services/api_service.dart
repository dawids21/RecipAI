import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../recipe/recipe.dart';
import '../recipe/recipe_detail.dart';

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

  static void dispose() {
    _client.close();
  }
}
