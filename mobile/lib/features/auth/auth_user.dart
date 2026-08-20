/// Provider-agnostic authenticated user, decoupled from any auth SDK type.
class AuthUser {
  final String email;

  const AuthUser({required this.email});
}
