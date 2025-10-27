import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../core/app_config.dart';
import '../auth/auth_service.dart';
import 'recipe.dart';

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
}
