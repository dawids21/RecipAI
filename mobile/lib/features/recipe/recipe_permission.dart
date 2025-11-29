import '../../shared/user_role.dart';

class RecipePermission {
  final String email;
  final UserRole role;

  const RecipePermission({required this.email, required this.role});

  factory RecipePermission.fromJson(Map<String, dynamic> json) {
    return RecipePermission(
      email: json['email'] as String,
      role: UserRole.fromApiString(json['role'] as String),
    );
  }

  Map<String, dynamic> toJson() {
    return {'email': email, 'role': role.toApiString()};
  }
}
