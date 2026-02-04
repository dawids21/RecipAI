import '../../shared/user_role.dart';

class MealPlanPermission {
  final String email;
  final UserRole role;

  const MealPlanPermission({required this.email, required this.role});

  factory MealPlanPermission.fromJson(Map<String, dynamic> json) {
    return MealPlanPermission(
      email: json['email'] as String,
      role: UserRole.fromApiString(json['role'] as String),
    );
  }

  Map<String, dynamic> toJson() {
    return {'email': email, 'role': role.toApiString()};
  }
}
