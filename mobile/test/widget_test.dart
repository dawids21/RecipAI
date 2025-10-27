// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:recipai_mobile/core/api_service.dart';
import 'package:recipai_mobile/core/get_it.dart';
import 'package:recipai_mobile/core/routes.dart';
import 'package:recipai_mobile/features/auth/auth_service.dart';
import 'package:recipai_mobile/features/recipe/recipe_setup.dart';
import 'package:recipai_mobile/main.dart';

class MockAuthService extends ChangeNotifier implements AuthService {
  bool _isAuthenticated;

  MockAuthService({bool isAuthenticated = false})
    : _isAuthenticated = isAuthenticated;

  @override
  bool get isAuthenticated => _isAuthenticated;

  @override
  String get email => _isAuthenticated ? 'test@example.com' : '';

  @override
  Future<String?> get idToken async {
    return _isAuthenticated ? 'mock-token' : null;
  }

  @override
  Future<void> signIn() async {
    _isAuthenticated = true;
    notifyListeners();
  }

  @override
  Future<void> signOut() async {
    _isAuthenticated = false;
    notifyListeners();
  }

  void setAuthenticated(bool value) {
    _isAuthenticated = value;
    notifyListeners();
  }
}

void main() {
  testWidgets('RecipAI app smoke test', (WidgetTester tester) async {
    final mockAuthService = MockAuthService(isAuthenticated: true);
    final mockApiService = ApiService(mockAuthService);
    final mockAppRouter = createAppRouter(mockAuthService);

    getIt.registerSingleton<AuthService>(mockAuthService);
    setupRecipe();

    // Build our app and trigger a frame.
    await tester.pumpWidget(
      RecipAIApp(
        authService: mockAuthService,
        apiService: mockApiService,
        appRouter: mockAppRouter,
      ),
    );

    // Wait for the app to settle, including any pending timers
    await tester.pumpAndSettle();

    // Verify that the app title is displayed.
    expect(find.text('RecipAI'), findsOneWidget);
  });
}
