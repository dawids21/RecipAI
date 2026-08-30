import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:mime/mime.dart';
import 'package:recipai_mobile/features/recipe/recipe_image_input.dart';

import '../../core/app_config.dart';
import '../limits/limit_balance.dart';
import '../sharing/resource_permission.dart';
import '../sharing/share_refused_exception.dart';
import 'recipe.dart';
import 'recipe_detail.dart';

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

  Future<LimitBalance> fetchRecipeBalance(String? idToken) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.get(
      Uri.parse('$_baseUrl/recipes/balance'),
      headers: headers,
    );

    if (response.statusCode == 200) {
      return LimitBalance.fromJson(json.decode(response.body));
    } else {
      throw Exception('Failed to load recipe balance: ${response.statusCode}');
    }
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
    RecipeRequest recipeRequest,
    String? idToken,
  ) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.post(
        Uri.parse('$_baseUrl/recipes'),
        headers: headers,
        body: json.encode(recipeRequest.toJson()),
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
    RecipeRequest recipeRequest,
    String? idToken,
  ) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.put(
        Uri.parse('$_baseUrl/recipes/$id'),
        headers: headers,
        body: json.encode(recipeRequest.toJson()),
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

  Future<List<ResourcePermission>> fetchPermissions(
    String recipeId,
    String? idToken,
  ) async {
    try {
      final headers = _getAuthHeaders(idToken);
      final response = await _client.get(
        Uri.parse('$_baseUrl/recipes/$recipeId/permissions'),
        headers: headers,
      );

      if (response.statusCode == 200) {
        final List<dynamic> jsonList = json.decode(response.body);
        return jsonList
            .map(
              (json) =>
                  ResourcePermission.fromJson(json as Map<String, dynamic>),
            )
            .toList();
      } else if (response.statusCode == 404) {
        throw Exception('Recipe not found');
      } else {
        throw Exception('Failed to load permissions: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Network error while fetching permissions: $e');
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
        body: json.encode({'email': email, 'role': 'EDITOR'}),
      );

      if (response.statusCode == 204) {
        return;
      } else if (response.statusCode == 409) {
        final refusal = ShareRefusedException.fromResponseBody(
          response.body,
          email,
        );
        if (refusal != null) throw refusal;
        throw Exception('Failed to share recipe: ${response.statusCode}');
      } else if (response.statusCode == 404) {
        throw Exception('Recipe not found');
      } else {
        throw Exception('Failed to share recipe: ${response.statusCode}');
      }
    } on ShareRefusedException {
      rethrow;
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

      if (response.statusCode == 204) {
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

  Future<RecipeDetail> createRecipeMultipart(
    RecipeRequest recipeRequest,
    List<RecipeImageInput> images,
    String? idToken,
  ) async {
    final request = http.MultipartRequest(
      'POST',
      Uri.parse('$_baseUrl/recipes'),
    );

    if (idToken != null) {
      request.headers['Authorization'] = 'Bearer $idToken';
    }

    request.files.add(
      http.MultipartFile.fromString(
        'data',
        json.encode(recipeRequest.toJson()),
        contentType: MediaType('application', 'json'),
      ),
    );

    for (final image in images) {
      if (image.isExistingImage) {
        continue;
      }
      final imageFile = image.file!;
      final mimeType = lookupMimeType(imageFile.path);
      final extension = imageFile.path.split('.').last.toLowerCase();
      request.files.add(
        await http.MultipartFile.fromPath(
          'images',
          imageFile.path,
          filename: '${image.uuid}.$extension',
          contentType: mimeType != null ? MediaType.parse(mimeType) : null,
        ),
      );
    }

    final response = await request.send();
    final responseBody = await http.Response.fromStream(response);

    if (responseBody.statusCode == 201) {
      return RecipeDetail.fromJson(jsonDecode(responseBody.body));
    } else if (responseBody.statusCode == 400) {
      throw Exception(
        'Invalid request. Check file size (max 5MB) and format (JPEG/PNG).',
      );
    } else {
      throw Exception('Failed to create recipe: ${responseBody.statusCode}');
    }
  }

  Future<RecipeDetail> updateRecipeMultipart(
    String id,
    RecipeRequest recipeRequest,
    List<RecipeImageInput> images,
    String? idToken,
  ) async {
    final request = http.MultipartRequest(
      'PUT',
      Uri.parse('$_baseUrl/recipes/$id'),
    );

    if (idToken != null) {
      request.headers['Authorization'] = 'Bearer $idToken';
    }

    // Add JSON data as multipart file with correct content type
    request.files.add(
      http.MultipartFile.fromString(
        'data',
        json.encode(recipeRequest.toJson()),
        contentType: MediaType('application', 'json'),
      ),
    );

    for (final image in images) {
      if (image.isExistingImage) {
        continue;
      }
      final imageFile = image.file!;
      final mimeType = lookupMimeType(imageFile.path);
      final extension = imageFile.path.split('.').last.toLowerCase();
      request.files.add(
        await http.MultipartFile.fromPath(
          'images',
          imageFile.path,
          filename: '${image.uuid}.$extension',
          contentType: mimeType != null ? MediaType.parse(mimeType) : null,
        ),
      );
    }

    final response = await request.send();
    final responseBody = await http.Response.fromStream(response);

    if (responseBody.statusCode == 200) {
      return RecipeDetail.fromJson(jsonDecode(responseBody.body));
    } else if (responseBody.statusCode == 400) {
      throw Exception(
        'Invalid request. Check file size (max 5MB) and format (JPEG/PNG).',
      );
    } else if (responseBody.statusCode == 404) {
      throw Exception('Recipe not found');
    } else {
      throw Exception('Failed to update recipe: ${responseBody.statusCode}');
    }
  }
}
