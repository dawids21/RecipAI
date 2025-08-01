import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:logging/logging.dart';

class AppConfig {
  static const String _defaultApiBaseUrl = 'http://10.0.2.2:8080';
  static const String _apiBaseUrlKey = 'API_BASE_URL';

  static final Logger _logger = Logger('AppConfig');

  static Map<String, dynamic>? _config;

  // Load configuration from JSON asset file
  static Future<void> loadConfig() async {
    try {
      final String configString = await rootBundle.loadString(
        'assets/config/app_config.json',
      );
      _config = json.decode(configString);
    } catch (e) {
      _logger.warning('Could not load app_config.json, using defaults: $e');
    }
  }

  static String get apiBaseUrl {
    // 1. Try environment variable first (for runtime configuration)
    const String envUrl = String.fromEnvironment(_apiBaseUrlKey);
    if (envUrl.isNotEmpty) {
      return envUrl;
    }

    // 2. Try loaded config file
    if (_config != null && _config![_apiBaseUrlKey] != null) {
      return _config![_apiBaseUrlKey] as String;
    }

    // 3. Fall back to default
    return _defaultApiBaseUrl;
  }
}
