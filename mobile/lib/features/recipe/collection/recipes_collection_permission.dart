import '../../../shared/user_role.dart';

class RecipesCollectionPermission {
  final String email;
  final UserRole role;

  const RecipesCollectionPermission({required this.email, required this.role});

  factory RecipesCollectionPermission.fromJson(Map<String, dynamic> json) {
    return RecipesCollectionPermission(
      email: json['email'] as String,
      role: UserRole.fromApiString(json['role'] as String),
    );
  }

  Map<String, dynamic> toJson() {
    return {'email': email, 'role': role.toApiString()};
  }
}
