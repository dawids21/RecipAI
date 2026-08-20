import 'package:firebase_auth/firebase_auth.dart';
import 'package:google_sign_in/google_sign_in.dart';

import 'auth_user.dart';

/// Abstract interface for authentication repository
abstract class AuthRepository {
  /// Watch auth state changes (reactive stream)
  Stream<AuthUser?> watchAuthState();

  /// Get ID token for API authentication
  Future<String?> getIdToken();

  /// Sign in
  Future<void> signIn();

  /// Sign out
  Future<void> signOut();
}

/// Firebase implementation of AuthRepository
class FirebaseAuthRepository implements AuthRepository {
  final FirebaseAuth _firebaseAuth = FirebaseAuth.instance;
  final GoogleSignIn _googleSignIn = GoogleSignIn.instance;

  @override
  Stream<AuthUser?> watchAuthState() {
    return _firebaseAuth.userChanges().map(
      (user) => user == null ? null : AuthUser(email: user.email ?? ''),
    );
  }

  @override
  Future<String?> getIdToken() async {
    final user = _firebaseAuth.currentUser;
    if (user == null) return null;
    return await user.getIdToken();
  }

  @override
  Future<void> signIn() async {
    // 1. Trigger Google authentication
    final GoogleSignInAccount googleUser = await _googleSignIn.authenticate();

    // 2. Get authentication details
    final GoogleSignInAuthentication googleAuth = googleUser.authentication;

    // 3. Create Firebase credential
    final credential = GoogleAuthProvider.credential(
      idToken: googleAuth.idToken,
    );

    // 4. Sign in to Firebase (throws on error)
    await _firebaseAuth.signInWithCredential(credential);
  }

  @override
  Future<void> signOut() async {
    await Future.wait([_firebaseAuth.signOut(), _googleSignIn.signOut()]);
  }
}
