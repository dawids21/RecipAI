import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get_it/get_it.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';
import 'package:recipai_mobile/core/preferences_service.dart';
import 'package:recipai_mobile/core/routes.dart';
import 'package:recipai_mobile/features/auth/auth_service.dart';
import 'package:recipai_mobile/features/auth/auth_setup.dart';
import 'package:recipai_mobile/features/auth/auth_user.dart';
import 'package:recipai_mobile/features/settings/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/mocks.dart';

void main() {
  late MockAuthRepository authRepository;
  late Widget app;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await GetIt.I.reset();

    authRepository = MockAuthRepository();

    when(
      () => authRepository.watchAuthState(),
    ).thenAnswer((_) => const Stream<AuthUser?>.empty());
    when(
      () => authRepository.getIdToken(),
    ).thenAnswer((_) async => 'fake-token');

    final prefs = await SharedPreferences.getInstance();
    GetIt.I.registerSingleton(PreferencesService(prefs));

    setupAuth(authRepository: authRepository);

    final router = GoRouter(
      initialLocation: '/${AppRoute.settings.path}',
      routes: [
        GoRoute(
          path: '/${AppRoute.settings.path}',
          name: AppRoute.settings.name,
          builder: (context, state) =>
              SettingsScreen(authService: GetIt.I<AuthService>()),
        ),
      ],
    );
    app = MaterialApp.router(routerConfig: router);
  });

  tearDown(() => GetIt.I.reset());

  Finder dialogButton(String label) => find.descendant(
    of: find.byType(AlertDialog),
    matching: find.widgetWithText(TextButton, label),
  );

  Future<void> openLogoutDialog(WidgetTester tester) async {
    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ListTile, 'Logout'));
    await tester.pumpAndSettle();
  }

  testWidgets('renders Send logs above Logout', (tester) async {
    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    final sendLogs = find.widgetWithText(ListTile, 'Send logs');
    final logout = find.widgetWithText(ListTile, 'Logout');
    expect(sendLogs, findsOneWidget);
    expect(logout, findsOneWidget);
    expect(
      tester.getTopLeft(sendLogs).dy,
      lessThan(tester.getTopLeft(logout).dy),
    );
  });

  testWidgets('tapping Logout shows the confirmation dialog', (tester) async {
    await openLogoutDialog(tester);

    expect(find.byType(AlertDialog), findsOneWidget);
    expect(find.text('Are you sure you want to logout?'), findsOneWidget);
  });

  testWidgets('Cancel closes the dialog without calling signOut', (
    tester,
  ) async {
    await openLogoutDialog(tester);

    await tester.tap(dialogButton('Cancel'));
    await tester.pumpAndSettle();

    verifyNever(() => authRepository.signOut());
    expect(find.byType(AlertDialog), findsNothing);
    expect(find.byType(SettingsScreen), findsOneWidget);
  });

  testWidgets('confirming calls signOut once', (tester) async {
    when(() => authRepository.signOut()).thenAnswer((_) async {});

    await openLogoutDialog(tester);

    await tester.tap(dialogButton('Logout'));
    await tester.pumpAndSettle();

    verify(() => authRepository.signOut()).called(1);
  });

  testWidgets('a failed signOut shows the Failed to logout snackbar', (
    tester,
  ) async {
    when(() => authRepository.signOut()).thenThrow(Exception('boom'));

    await openLogoutDialog(tester);

    await tester.tap(dialogButton('Logout'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Failed to logout'), findsOneWidget);
  });
}
