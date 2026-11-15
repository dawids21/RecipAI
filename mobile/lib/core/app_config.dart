import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:logging/logging.dart';

class AppConfig {
  static const String _defaultApiBaseUrl = 'http://10.0.2.2:8080';
  static const String _apiBaseUrlKey = 'API_BASE_URL';
  static const String _defaultPrivacyPolicyUrl =
      'https://recipai.stasiak.xyz/privacy-policy.html';
  static const String _privacyPolicyUrlKey = 'PRIVACY_POLICY_URL';

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

  static String get apiBaseUrl => _resolve(
    _apiBaseUrlKey,
    const String.fromEnvironment(_apiBaseUrlKey),
    _defaultApiBaseUrl,
  );

  static String get privacyPolicyUrl => _resolve(
    _privacyPolicyUrlKey,
    const String.fromEnvironment(_privacyPolicyUrlKey),
    _defaultPrivacyPolicyUrl,
  );

  /// Resolves a value from, in order: the `--dart-define` value [envValue],
  /// the loaded config file, then [defaultValue].
  static String _resolve(String key, String envValue, String defaultValue) {
    if (envValue.isNotEmpty) {
      return envValue;
    }
    final configValue = _config?[key];
    if (configValue is String) {
      return configValue;
    }
    return defaultValue;
  }
}
