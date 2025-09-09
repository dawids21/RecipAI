import 'package:flutter/material.dart';

abstract class AuthService extends ChangeNotifier {
  bool get isAuthenticated;

  Future<String?> get idToken;

  Future<void> signIn();

  Future<void> signOut();
  @override
  void dispose();
}

class InheritedAuthService extends InheritedNotifier<AuthService> {
  const InheritedAuthService({
    super.key,
    required super.notifier,
    required super.child,
  });

  static AuthService of(BuildContext context) {
    final result = context
        .dependOnInheritedWidgetOfExactType<InheritedAuthService>();
    assert(result != null, 'No InheritedAuthService found in context');
    return result!.notifier!;
  }
}
