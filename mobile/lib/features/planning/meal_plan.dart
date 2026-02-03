import 'dart:ui';

import '../../shared/user_role.dart';

class MealPlan {
  final String id;
  final String name;
  final Color color;
  final UserRole role;
  final DateTime createdAt;

  const MealPlan({
    required this.id,
    required this.name,
    required this.color,
    required this.role,
    required this.createdAt,
  });

  factory MealPlan.fromJson(Map<String, dynamic> json) {
    final color = Color(
      int.parse((json['color'] as String).replaceFirst('#', '0xFF')),
    );
    return MealPlan(
      id: json['id'] as String,
      name: json['name'] as String,
      color: color,
      role: UserRole.fromApiString(json['role'] as String),
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}
