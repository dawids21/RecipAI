import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';

import '../../core/app_config.dart';

class AccountRepository {
  static final _log = Logger('recipai.settings.repository');

  final http.Client _client = http.Client();
  final String _baseUrl = AppConfig.apiBaseUrl;

  AccountRepository();

  Map<String, String> _getAuthHeaders(String? idToken) {
    return {
      'Content-Type': 'application/json',
      if (idToken != null) 'Authorization': 'Bearer $idToken',
    };
  }

  /// DELETE /settings/account — purges every resource the caller owns.
  /// 204 is success; anything else throws.
  Future<void> deleteAccount(String? idToken) async {
    final url = '$_baseUrl/settings/account';
    final sw = Stopwatch()..start();
    final http.Response response;
    try {
      response = await _client.delete(
        Uri.parse(url),
        headers: _getAuthHeaders(idToken),
      );
    } catch (e) {
      _log.warning('DELETE $url failed (${sw.elapsedMilliseconds} ms)', e);
      rethrow;
    }
    _log.info(
      'DELETE $url -> ${response.statusCode} (${sw.elapsedMilliseconds} ms)',
    );

    if (response.statusCode == 204) {
      return;
    } else {
      throw Exception('Failed to delete account: ${response.statusCode}');
    }
  }
}
