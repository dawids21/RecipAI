import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get_it/get_it.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';
import 'package:recipai_mobile/core/preferences_service.dart';
import 'package:recipai_mobile/core/routes.dart';
import 'package:recipai_mobile/features/auth/auth_repository.dart';
import 'package:recipai_mobile/features/auth/auth_service.dart';
import 'package:recipai_mobile/features/auth/auth_setup.dart';
import 'package:recipai_mobile/features/auth/auth_user.dart';
import 'package:recipai_mobile/features/settings/account_deletion_service.dart';
import 'package:recipai_mobile/features/settings/settings_screen.dart';
import 'package:recipai_mobile/features/settings/settings_setup.dart';
import 'package:recipai_mobile/shared/loading_widget.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/mocks.dart';

void main() {
  late MockAuthRepository authRepository;
  late MockAccountRepository accountRepository;
  late Widget app;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await GetIt.I.reset();

    authRepository = MockAuthRepository();
    accountRepository = MockAccountRepository();

    when(
      () => authRepository.watchAuthState(),
    ).thenAnswer((_) => const Stream<AuthUser?>.empty());
    when(
      () => authRepository.getIdToken(),
    ).thenAnswer((_) async => 'fake-token');

    final prefs = await SharedPreferences.getInstance();
    GetIt.I.registerSingleton(PreferencesService(prefs));

    setupAuth(authRepository: authRepository);
    setupSettings(accountRepository: accountRepository);

    final router = GoRouter(
      initialLocation: '/${AppRoute.settings.path}',
      routes: [
        GoRoute(
          path: '/${AppRoute.settings.path}',
          name: AppRoute.settings.name,
          builder: (context, state) => SettingsScreen(
            authService: GetIt.I<AuthService>(),
            accountDeletionService: GetIt.I<AccountDeletionService>(),
          ),
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

  Future<void> openDeleteDialog(WidgetTester tester) async {
    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ListTile, 'Delete account'));
    await tester.pumpAndSettle();
  }

  /// Stubs every step of the deletion flow to succeed.
  void stubSuccessfulDeletion() {
    when(() => authRepository.reauthenticate()).thenAnswer((_) async {});
    when(() => accountRepository.deleteAccount(any())).thenAnswer((_) async {});
    when(() => authRepository.deleteIdentity()).thenAnswer((_) async {});
    when(() => authRepository.signOut()).thenAnswer((_) async {});
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

  testWidgets('renders Delete account below Logout', (tester) async {
    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    final logout = find.widgetWithText(ListTile, 'Logout');
    final deleteAccount = find.widgetWithText(ListTile, 'Delete account');
    expect(deleteAccount, findsOneWidget);
    expect(
      tester.getTopLeft(logout).dy,
      lessThan(tester.getTopLeft(deleteAccount).dy),
    );
  });

  testWidgets('tapping Delete account shows the confirmation dialog', (
    tester,
  ) async {
    await openDeleteDialog(tester);

    expect(find.byType(AlertDialog), findsOneWidget);
    expect(find.textContaining('cannot be undone'), findsOneWidget);
  });

  testWidgets('Cancel on the confirmation dialog calls nothing', (
    tester,
  ) async {
    await openDeleteDialog(tester);

    await tester.tap(dialogButton('Cancel'));
    await tester.pumpAndSettle();

    verifyNever(() => authRepository.reauthenticate());
    verifyNever(() => accountRepository.deleteAccount(any()));
    verifyNever(() => authRepository.deleteIdentity());
    expect(find.byType(AlertDialog), findsNothing);
  });

  testWidgets(
    'confirming runs reauthenticate, the backend purge, deleteIdentity and signOut in that order',
    (tester) async {
      stubSuccessfulDeletion();

      await openDeleteDialog(tester);
      await tester.tap(dialogButton('Delete'));
      await tester.pumpAndSettle();

      verifyInOrder([
        () => authRepository.reauthenticate(),
        () => accountRepository.deleteAccount('fake-token'),
        () => authRepository.deleteIdentity(),
        () => authRepository.signOut(),
      ]);
      expect(find.byType(SnackBar), findsNothing);
      expect(find.byType(AlertDialog), findsNothing);
    },
  );

  testWidgets(
    'a cancelled re-authentication returns silently and deletes nothing',
    (tester) async {
      when(
        () => authRepository.reauthenticate(),
      ).thenThrow(const ReauthCancelledException());

      await openDeleteDialog(tester);
      await tester.tap(dialogButton('Delete'));
      await tester.pumpAndSettle();

      expect(find.byType(SnackBar), findsNothing);
      verifyNever(() => accountRepository.deleteAccount(any()));
      expect(find.widgetWithText(ListTile, 'Delete account'), findsOneWidget);
    },
  );

  testWidgets(
    'a failed re-authentication shows the identity snackbar and deletes nothing',
    (tester) async {
      when(() => authRepository.reauthenticate()).thenThrow(Exception('boom'));

      await openDeleteDialog(tester);
      await tester.tap(dialogButton('Delete'));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Could not confirm your identity'),
        findsOneWidget,
      );
      verifyNever(() => accountRepository.deleteAccount(any()));
      verifyNever(() => authRepository.signOut());
    },
  );

  testWidgets(
    'a failed backend purge shows the retry snackbar and keeps the user signed in',
    (tester) async {
      stubSuccessfulDeletion();
      when(
        () => accountRepository.deleteAccount(any()),
      ).thenThrow(Exception('boom'));

      await openDeleteDialog(tester);
      await tester.tap(dialogButton('Delete'));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Failed to delete your account'),
        findsOneWidget,
      );
      verifyNever(() => authRepository.deleteIdentity());
      verifyNever(() => authRepository.signOut());
    },
  );

  testWidgets(
    'a failed identity deletion shows the contact-support dialog and signs out after OK',
    (tester) async {
      stubSuccessfulDeletion();
      when(() => authRepository.deleteIdentity()).thenThrow(Exception('boom'));

      await openDeleteDialog(tester);
      await tester.tap(dialogButton('Delete'));
      await tester.pumpAndSettle();

      expect(find.textContaining('contact support'), findsOneWidget);
      verifyNever(() => authRepository.signOut());

      await tester.tap(dialogButton('OK'));
      await tester.pumpAndSettle();

      verify(() => authRepository.signOut()).called(1);
    },
  );

  testWidgets('shows the loading state while deletion is in progress', (
    tester,
  ) async {
    stubSuccessfulDeletion();
    final reauth = Completer<void>();
    when(
      () => authRepository.reauthenticate(),
    ).thenAnswer((_) => reauth.future);

    await openDeleteDialog(tester);
    await tester.tap(dialogButton('Delete'));
    await tester.pump();

    expect(find.byType(LoadingWidget), findsOneWidget);
    expect(find.widgetWithText(ListTile, 'Delete account'), findsNothing);

    reauth.complete();
    await tester.pumpAndSettle();
  });
}
