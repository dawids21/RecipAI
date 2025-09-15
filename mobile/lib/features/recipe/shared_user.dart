class SharedUser {
  final String email;
  final String role;

  const SharedUser({required this.email, required this.role});

  factory SharedUser.fromJson(Map<String, dynamic> json) {
    return SharedUser(
      email: json['email'] as String,
      role: json['role'] as String,
    );
  }

  Map<String, dynamic> toJson() {
    return {'email': email, 'role': role};
  }
}
