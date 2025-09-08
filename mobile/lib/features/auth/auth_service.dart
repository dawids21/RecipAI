import 'dart:async';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';

class AuthService extends ChangeNotifier {
  User? _currentUser;
  StreamSubscription<User?>? _userSubscription;

  AuthService() {
    _userSubscription = FirebaseAuth.instance.userChanges().listen(
      _handleUserChanges,
      onError: _handleUserChangesError,
    );
  }

  bool get isAuthenticated => _currentUser != null;

  User? get currentUser => _currentUser;

  Future<String?> get idToken async {
    return await _currentUser?.getIdToken();
  }

  void _handleUserChanges(User? user) {
    _currentUser = user;
    notifyListeners();
  }

  void _handleUserChangesError(Object error) {
    debugPrint('Auth state error: $error');
  }

  Future<void> signInWithGoogle() async {
    try {
      final GoogleSignInAccount googleSignInAccount = await GoogleSignIn
          .instance
          .authenticate();
      final GoogleSignInAuthentication googleSignInAuthentication =
          googleSignInAccount.authentication;
      final OAuthCredential credential = GoogleAuthProvider.credential(
        idToken: googleSignInAuthentication.idToken,
      );
      await FirebaseAuth.instance.signInWithCredential(credential);
    } catch (e) {
      debugPrint('Google Sign-In error: $e');
      rethrow;
    }
  }

  Future<void> signOut() async {
    try {
      await Future.wait([
        FirebaseAuth.instance.signOut(),
        GoogleSignIn.instance.signOut(),
      ]);
    } catch (e) {
      debugPrint('Sign out error: $e');
      rethrow;
    }
  }

  @override
  void dispose() {
    _userSubscription?.cancel();
    super.dispose();
  }
}

// Global instance
final authService = AuthService();
