import '../../shared/user_role.dart';

class ShoppingListPermission {
  final String email;
  final UserRole role;

  const ShoppingListPermission({required this.email, required this.role});

  factory ShoppingListPermission.fromJson(Map<String, dynamic> json) {
    return ShoppingListPermission(
      email: json['email'] as String,
      role: UserRole.fromApiString(json['role'] as String),
    );
  }

  Map<String, dynamic> toJson() {
    return {'email': email, 'role': role.toApiString()};
  }
}
