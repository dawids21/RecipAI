import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:image_picker/image_picker.dart';
import 'package:mime/mime.dart';

import '../features/auth/auth_service.dart';
import '../features/extraction/extracted_recipe.dart';
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

  Future<ExtractedRecipe> extractRecipeFromText(String htmlContent) async {
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

  Future<ExtractedRecipe> extractRecipeFromImage(XFile imageFile) async {
    try {
      final request = http.MultipartRequest(
        'POST',
        Uri.parse('$_baseUrl/extract/image'),
      );

      // Add auth headers
      final token = await _authService.idToken;
      if (token != null) {
        request.headers['Authorization'] = 'Bearer $token';
      }

      // Add image file with proper MIME type
      final mimeType = lookupMimeType(imageFile.path);
      request.files.add(
        await http.MultipartFile.fromPath(
          'file',
          imageFile.path,
          contentType: mimeType != null ? MediaType.parse(mimeType) : null,
        ),
      );

      final response = await request.send();
      final responseBody = await http.Response.fromStream(response);

      if (response.statusCode == 200) {
        final Map<String, dynamic> json = jsonDecode(responseBody.body);
        return ExtractedRecipe.fromJson(json);
      } else {
        throw Exception(
          'Failed to extract recipe from image: ${response.statusCode}',
        );
      }
    } catch (e) {
      throw Exception('Network error while extracting recipe from image: $e');
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
