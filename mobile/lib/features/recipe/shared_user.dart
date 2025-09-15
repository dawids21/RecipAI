import '../../shared/user_role.dart';

class SharedUser {
  final String email;
  final UserRole role;

  const SharedUser({required this.email, required this.role});

  factory SharedUser.fromJson(Map<String, dynamic> json) {
    return SharedUser(
      email: json['email'] as String,
      role: UserRole.fromApiString(json['role'] as String),
    );
  }

  Map<String, dynamic> toJson() {
    return {'email': email, 'role': role.toApiString()};
  }
}
