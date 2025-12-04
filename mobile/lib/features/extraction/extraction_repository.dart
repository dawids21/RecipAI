import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:image_picker/image_picker.dart';
import 'package:mime/mime.dart';

import '../../core/app_config.dart';
import 'extracted_recipe.dart';

class ExtractionRepository {
  final http.Client _client = http.Client();
  final String _baseUrl = AppConfig.apiBaseUrl;

  ExtractionRepository();

  Map<String, String> _getAuthHeaders(String? idToken) {
    return {
      'Content-Type': 'application/json',
      if (idToken != null) 'Authorization': 'Bearer $idToken',
    };
  }

  Future<ExtractedRecipe> extractRecipeFromText(
    String htmlContent,
    String? idToken,
  ) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.post(
      Uri.parse('$_baseUrl/extract/text'),
      headers: headers,
      body: json.encode({'text': htmlContent}),
    );

    if (response.statusCode == 200) {
      final Map<String, dynamic> jsonMap = jsonDecode(response.body);
      return ExtractedRecipe.fromJson(jsonMap);
    } else {
      throw Exception(
        'Failed to extract recipe from text: ${response.statusCode}',
      );
    }
  }

  Future<ExtractedRecipe> extractRecipeFromImage(
    XFile imageFile,
    String? idToken,
  ) async {
    final request = http.MultipartRequest(
      'POST',
      Uri.parse('$_baseUrl/extract/image'),
    );

    // Add auth header
    if (idToken != null) {
      request.headers['Authorization'] = 'Bearer $idToken';
    }

    // Add file with MIME type
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

    if (responseBody.statusCode == 200) {
      final Map<String, dynamic> jsonMap = jsonDecode(responseBody.body);
      return ExtractedRecipe.fromJson(jsonMap);
    } else {
      throw Exception(
        'Failed to extract recipe from image: ${responseBody.statusCode}',
      );
    }
  }
}
