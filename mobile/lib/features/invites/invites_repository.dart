import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../core/app_config.dart';
import 'invite.dart';

class InvitesRepository {
  final http.Client _client = http.Client();
  final String _baseUrl = AppConfig.apiBaseUrl;

  InvitesRepository();

  Map<String, String> _getAuthHeaders(String? idToken) {
    return {
      'Content-Type': 'application/json',
      if (idToken != null) 'Authorization': 'Bearer $idToken',
    };
  }

  Future<List<Invite>> fetchInvites(String? idToken) async {
    final response = await _client.get(
      Uri.parse('$_baseUrl/invites'),
      headers: _getAuthHeaders(idToken),
    );

    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList
          .map((json) => Invite.fromJson(json as Map<String, dynamic>))
          .toList();
    } else {
      throw Exception('Failed to load invites: ${response.statusCode}');
    }
  }

  Future<void> acceptInvite(String inviteId, String? idToken) async {
    final response = await _client.post(
      Uri.parse('$_baseUrl/invites/$inviteId/accept'),
      headers: _getAuthHeaders(idToken),
    );

    if (response.statusCode == 204) {
      return;
    } else if (response.statusCode == 404) {
      throw InviteGoneException(inviteId);
    } else {
      throw Exception('Failed to accept invite: ${response.statusCode}');
    }
  }

  Future<void> declineInvite(String inviteId, String? idToken) async {
    final response = await _client.post(
      Uri.parse('$_baseUrl/invites/$inviteId/decline'),
      headers: _getAuthHeaders(idToken),
    );

    if (response.statusCode == 204) {
      return;
    } else if (response.statusCode == 404) {
      throw InviteGoneException(inviteId);
    } else {
      throw Exception('Failed to decline invite: ${response.statusCode}');
    }
  }
}

/// Thrown when accept/decline hits a 404: the invite no longer exists —
/// already answered elsewhere or cancelled by the sharer.
class InviteGoneException implements Exception {
  final String inviteId;

  const InviteGoneException(this.inviteId);
}
