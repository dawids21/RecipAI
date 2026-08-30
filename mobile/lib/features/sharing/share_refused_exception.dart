import 'dart:convert';

enum ShareRefusedReason {
  alreadyInvited,
  alreadyHasAccess;

  /// Null — not a throw — when the reason is missing or unrecognised, so an
  /// unexpected refusal degrades to the caller's generic exception.
  static ShareRefusedReason? fromApiString(String? apiString) {
    switch (apiString) {
      case 'ALREADY_INVITED':
        return ShareRefusedReason.alreadyInvited;
      case 'ALREADY_HAS_ACCESS':
        return ShareRefusedReason.alreadyHasAccess;
      default:
        return null;
    }
  }
}

/// Thrown when a `share` call gets a 409: the target already has a pending
/// invite or already holds access.
class ShareRefusedException implements Exception {
  final ShareRefusedReason reason;
  final String email;

  const ShareRefusedException(this.reason, this.email);

  /// Parses a 409 `ProblemDetail` body. Null when the body is not JSON or
  /// carries no recognised `reason`; the caller then throws its own generic
  /// exception. Never throws — a malformed body must not escape the parse.
  static ShareRefusedException? fromResponseBody(String body, String email) {
    try {
      final decoded = json.decode(body) as Map<String, dynamic>;
      final reason = ShareRefusedReason.fromApiString(
        decoded['reason'] as String?,
      );
      if (reason == null) return null;
      return ShareRefusedException(reason, email);
    } catch (_) {
      return null;
    }
  }
}
