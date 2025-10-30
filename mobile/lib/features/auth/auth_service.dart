import 'dart:async';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';

import 'auth_repository.dart';

class AuthService {
  final AuthRepository _authRepository;
  late final StreamSubscription<User?> _authStateSubscription;

  AuthService({required AuthRepository authRepository})
    : _authRepository = authRepository {
    _initializeAuthState();
  }

  // State management with ValueNotifier
  final ValueNotifier<bool> _isAuthenticated = ValueNotifier(false);
  final ValueNotifier<String> _email = ValueNotifier('');

  // Public interface
  ValueListenable<bool> get isAuthenticated => _isAuthenticated;

  String get email => _email.value;

  Future<String?> get idToken => _authRepository.getIdToken();

  // Concurrent operation prevention
  bool _isSignInRunning = false;
  bool _isSignOutRunning = false;

  /// Initialize auth state by setting up stream listener
  void _initializeAuthState() {
    // Listen to auth state changes
    _authStateSubscription = _authRepository.watchAuthState().listen(
      _updateAuthState,
      onError: (error) => debugPrint('Auth state error: $error'),
    );
  }

  /// Update state when auth changes
  void _updateAuthState(User? user) {
    _isAuthenticated.value = user != null;
    _email.value = user?.email ?? '';
  }

  /// Sign in with Google
  Future<void> signIn() async {
    if (_isSignInRunning) return;
    _isSignInRunning = true;

    try {
      await _authRepository.signInWithGoogle();
      // State updates via stream listener
    } catch (e) {
      _isSignInRunning = false;
      rethrow;
    }

    _isSignInRunning = false;
  }

  /// Sign out
  Future<void> signOut() async {
    if (_isSignOutRunning) return;
    _isSignOutRunning = true;

    try {
      await _authRepository.signOut();
      // State updates via stream listener
    } catch (e) {
      _isSignOutRunning = false;
      rethrow;
    }

    _isSignOutRunning = false;
  }

  /// Dispose resources
  void dispose() {
    _authStateSubscription.cancel();
    _isAuthenticated.dispose();
    _email.dispose();
  }
}
