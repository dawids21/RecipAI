import '../../shared/user_role.dart';

class ResourcePermission {
  final String email;
  final UserRole role;
  final bool pending;

  const ResourcePermission({
    required this.email,
    required this.role,
    required this.pending,
  });

  factory ResourcePermission.fromJson(Map<String, dynamic> json) {
    return ResourcePermission(
      email: json['email'] as String,
      role: UserRole.fromApiString(json['role'] as String),
      pending: json['pending'] as bool,
    );
  }
}
