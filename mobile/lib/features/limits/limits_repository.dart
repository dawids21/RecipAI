import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../core/app_config.dart';
import 'limit_cap.dart';

class LimitsRepository {
  final http.Client _client = http.Client();
  final String _baseUrl = AppConfig.apiBaseUrl;

  LimitsRepository();

  Map<String, String> _getAuthHeaders(String? idToken) {
    return {
      'Content-Type': 'application/json',
      if (idToken != null) 'Authorization': 'Bearer $idToken',
    };
  }

  Future<Map<String, LimitCap>> fetchCaps(String? idToken) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.get(
      Uri.parse('$_baseUrl/limits'),
      headers: headers,
    );

    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      final caps = jsonList
          .map((json) => LimitCap.fromJson(json as Map<String, dynamic>))
          .toList();
      return {for (final cap in caps) cap.resource: cap};
    } else {
      throw Exception('Failed to load limits: ${response.statusCode}');
    }
  }
}
