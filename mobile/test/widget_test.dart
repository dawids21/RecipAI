// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'dart:async';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:recipai_mobile/core/routes.dart';
import 'package:recipai_mobile/features/auth/auth_repository.dart';
import 'package:recipai_mobile/features/auth/auth_setup.dart';
import 'package:recipai_mobile/features/extraction/extraction_setup.dart';
import 'package:recipai_mobile/features/recipe/recipe_setup.dart';
import 'package:recipai_mobile/main.dart';

class MockAuthRepository implements AuthRepository {
  final StreamController<User?> _authStateController =
      StreamController<User?>.broadcast();
  User? _currentUser;

  MockAuthRepository({bool isAuthenticated = false}) {
    if (isAuthenticated) {
      _currentUser = MockUser(email: 'test@example.com');
      _authStateController.add(_currentUser);
    } else {
      _authStateController.add(null);
    }
  }

  @override
  Stream<User?> watchAuthState() {
    return _authStateController.stream;
  }

  @override
  Future<User?> getCurrentUser() async {
    return _currentUser;
  }

  @override
  Future<String?> getIdToken() async {
    return _currentUser != null ? 'mock-token' : null;
  }

  @override
  Future<void> signInWithGoogle() async {
    _currentUser = MockUser(email: 'test@example.com');
    _authStateController.add(_currentUser);
  }

  @override
  Future<void> signOut() async {
    _currentUser = null;
    _authStateController.add(null);
  }

  void dispose() {
    _authStateController.close();
  }
}

class MockUser implements User {
  @override
  final String? email;

  MockUser({this.email});

  @override
  dynamic noSuchMethod(Invocation invocation) => null;
}

void main() {
  testWidgets('RecipAI app smoke test', (WidgetTester tester) async {
    setupAuth(MockAuthRepository(isAuthenticated: true));
    setupRecipe();
    setupExtraction();

    final mockAppRouter = createAppRouter();

    // Build our app and trigger a frame.
    await tester.pumpWidget(RecipAIApp(appRouter: mockAppRouter));

    // Wait for the app to settle, including any pending timers
    await tester.pumpAndSettle();

    // Verify that the app title is displayed.
    expect(find.text('RecipAI'), findsOneWidget);
  });
}
