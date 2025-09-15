import 'dart:async';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';

import 'auth_service.dart';

class FirebaseAuthService extends AuthService {
  User? _currentUser;
  StreamSubscription<User?>? _userSubscription;

  FirebaseAuthService() {
    _userSubscription = FirebaseAuth.instance.userChanges().listen(
      _handleUserChanges,
      onError: _handleUserChangesError,
    );
  }

  @override
  bool get isAuthenticated => _currentUser != null;

  @override
  String get email => _currentUser?.email ?? '';

  @override
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

  @override
  Future<void> signIn() async {
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

  @override
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
