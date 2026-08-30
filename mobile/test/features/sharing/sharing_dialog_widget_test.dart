import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:recipai_mobile/core/async_value.dart';
import 'package:recipai_mobile/features/sharing/resource_permission.dart';
import 'package:recipai_mobile/features/sharing/sharing_dialog.dart';
import 'package:recipai_mobile/shared/user_role.dart';

const ownerPermission = ResourcePermission(
  email: 'owner@example.com',
  role: UserRole.owner,
  pending: false,
);
const editorPermission = ResourcePermission(
  email: 'editor@example.com',
  role: UserRole.editor,
  pending: false,
);
const pendingPermission = ResourcePermission(
  email: 'invitee@example.com',
  role: UserRole.editor,
  pending: true,
);

Future<void> pumpDialog(
  WidgetTester tester, {
  required ValueListenable<AsyncValue<List<ResourcePermission>>> permissions,
  String currentUserEmail = 'owner@example.com',
  Future<void> Function(String)? onShare,
  Future<void> Function(String)? onUnshare,
}) => tester.pumpWidget(
  MaterialApp(
    home: Scaffold(
      body: SharingDialog(
        title: 'Share List',
        permissions: permissions,
        currentUserEmail: currentUserEmail,
        onShare: onShare ?? (_) async {},
        onUnshare: onUnshare ?? (_) async {},
      ),
    ),
  ),
);

void main() {
  testWidgets('renders a granted OWNER row with no Pending chip', (
    tester,
  ) async {
    await pumpDialog(
      tester,
      permissions: ValueNotifier(const AsyncValue.data([ownerPermission])),
    );

    expect(find.text('owner@example.com'), findsOneWidget);
    expect(find.text('Owner'), findsOneWidget);
    expect(find.widgetWithText(Chip, 'Pending'), findsNothing);
  });

  testWidgets('renders a granted EDITOR row', (tester) async {
    await pumpDialog(
      tester,
      permissions: ValueNotifier(const AsyncValue.data([editorPermission])),
    );

    expect(find.text('editor@example.com'), findsOneWidget);
    expect(find.text('Editor'), findsOneWidget);
  });

  testWidgets('renders a pending row with a chip and Invited as subtitle', (
    tester,
  ) async {
    await pumpDialog(
      tester,
      permissions: ValueNotifier(const AsyncValue.data([pendingPermission])),
    );

    expect(find.text('invitee@example.com'), findsOneWidget);
    expect(find.widgetWithText(Chip, 'Pending'), findsOneWidget);
    expect(find.text('Invited as Editor'), findsOneWidget);
  });

  testWidgets('renders rows in the notifier order without sorting', (
    tester,
  ) async {
    await pumpDialog(
      tester,
      permissions: ValueNotifier(
        const AsyncValue.data([
          ownerPermission,
          editorPermission,
          pendingPermission,
        ]),
      ),
    );

    final tiles = tester.widgetList<ListTile>(find.byType(ListTile)).toList();
    expect(tiles, hasLength(3));
    expect(
      find.descendant(
        of: find.byWidget(tiles[0]),
        matching: find.text('owner@example.com'),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: find.byWidget(tiles[1]),
        matching: find.text('editor@example.com'),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: find.byWidget(tiles[2]),
        matching: find.text('invitee@example.com'),
      ),
      findsOneWidget,
    );
  });

  testWidgets('shows no remove icon on the current user\'s row', (
    tester,
  ) async {
    await pumpDialog(
      tester,
      permissions: ValueNotifier(
        const AsyncValue.data([ownerPermission, editorPermission]),
      ),
      currentUserEmail: 'owner@example.com',
    );

    expect(find.byType(IconButton), findsOneWidget);
  });

  testWidgets('shows the remove icon on every other row, pending included', (
    tester,
  ) async {
    await pumpDialog(
      tester,
      permissions: ValueNotifier(
        const AsyncValue.data([
          ownerPermission,
          editorPermission,
          pendingPermission,
        ]),
      ),
      currentUserEmail: 'owner@example.com',
    );

    expect(find.byType(IconButton), findsNWidgets(2));
  });

  testWidgets(
    'Remove access is the tooltip on a granted row, Cancel invitation on a pending one',
    (tester) async {
      await pumpDialog(
        tester,
        permissions: ValueNotifier(
          const AsyncValue.data([editorPermission, pendingPermission]),
        ),
      );

      final grantedButton = tester.widget<IconButton>(
        find.widgetWithIcon(IconButton, Icons.remove_circle_outline).first,
      );
      expect(grantedButton.tooltip, 'Remove access');

      final pendingButton = tester.widget<IconButton>(
        find.widgetWithIcon(IconButton, Icons.remove_circle_outline).last,
      );
      expect(pendingButton.tooltip, 'Cancel invitation');
    },
  );

  testWidgets(
    'tapping the icon on a pending row confirms Cancel Invitation and calls onUnshare',
    (tester) async {
      final calls = <String>[];
      await pumpDialog(
        tester,
        permissions: ValueNotifier(const AsyncValue.data([pendingPermission])),
        onUnshare: (email) async => calls.add(email),
      );

      await tester.tap(find.byIcon(Icons.remove_circle_outline));
      await tester.pumpAndSettle();

      expect(find.text('Cancel Invitation'), findsOneWidget);
      expect(
        find.text(
          'Cancel the invitation for invitee@example.com? They will not be able to accept it any more.',
        ),
        findsOneWidget,
      );

      await tester.tap(
        find.descendant(
          of: find.byType(AlertDialog),
          matching: find.widgetWithText(TextButton, 'Confirm'),
        ),
      );
      await tester.pumpAndSettle();

      expect(calls, ['invitee@example.com']);
    },
  );

  testWidgets(
    'tapping the icon on a granted row confirms Confirm Unshare and calls onUnshare',
    (tester) async {
      final calls = <String>[];
      await pumpDialog(
        tester,
        permissions: ValueNotifier(const AsyncValue.data([editorPermission])),
        onUnshare: (email) async => calls.add(email),
      );

      await tester.tap(find.byIcon(Icons.remove_circle_outline));
      await tester.pumpAndSettle();

      expect(find.text('Confirm Unshare'), findsOneWidget);
      expect(
        find.text(
          'Remove access for editor@example.com? They will no longer be able to view or edit this item.',
        ),
        findsOneWidget,
      );

      await tester.tap(
        find.descendant(
          of: find.byType(AlertDialog),
          matching: find.widgetWithText(TextButton, 'Unshare'),
        ),
      );
      await tester.pumpAndSettle();

      expect(calls, ['editor@example.com']);
    },
  );

  testWidgets(
    'dismissing the confirmation with Cancel does not call onUnshare',
    (tester) async {
      final calls = <String>[];
      await pumpDialog(
        tester,
        permissions: ValueNotifier(const AsyncValue.data([editorPermission])),
        onUnshare: (email) async => calls.add(email),
      );

      await tester.tap(find.byIcon(Icons.remove_circle_outline));
      await tester.pumpAndSettle();

      await tester.tap(
        find.descendant(
          of: find.byType(AlertDialog),
          matching: find.widgetWithText(TextButton, 'Cancel'),
        ),
      );
      await tester.pumpAndSettle();

      expect(calls, isEmpty);
    },
  );

  testWidgets('a valid email plus Share calls onShare and clears the field', (
    tester,
  ) async {
    final calls = <String>[];
    await pumpDialog(
      tester,
      permissions: ValueNotifier(const AsyncValue.data(<ResourcePermission>[])),
      onShare: (email) async => calls.add(email),
    );

    await tester.enterText(find.byType(TextFormField), 'bob@example.com');
    await tester.tap(find.widgetWithText(ElevatedButton, 'Share'));
    await tester.pumpAndSettle();

    expect(calls, ['bob@example.com']);
    expect(
      tester.widget<TextFormField>(find.byType(TextFormField)).controller!.text,
      isEmpty,
    );
  });

  testWidgets(
    'an invalid email shows the validation message and does not share',
    (tester) async {
      final calls = <String>[];
      await pumpDialog(
        tester,
        permissions: ValueNotifier(
          const AsyncValue.data(<ResourcePermission>[]),
        ),
        onShare: (email) async => calls.add(email),
      );

      await tester.enterText(find.byType(TextFormField), 'not-an-email');
      await tester.tap(find.widgetWithText(ElevatedButton, 'Share'));
      await tester.pumpAndSettle();

      expect(find.text('Please enter a valid email address'), findsOneWidget);
      expect(calls, isEmpty);
    },
  );

  testWidgets('an empty field shows the required message and does not share', (
    tester,
  ) async {
    final calls = <String>[];
    await pumpDialog(
      tester,
      permissions: ValueNotifier(const AsyncValue.data(<ResourcePermission>[])),
      onShare: (email) async => calls.add(email),
    );

    await tester.tap(find.widgetWithText(ElevatedButton, 'Share'));
    await tester.pumpAndSettle();

    expect(find.text('Please enter an email address'), findsOneWidget);
    expect(calls, isEmpty);
  });

  testWidgets('the Share button disables and shows a spinner while in flight', (
    tester,
  ) async {
    final completer = Completer<void>();
    await pumpDialog(
      tester,
      permissions: ValueNotifier(const AsyncValue.data(<ResourcePermission>[])),
      onShare: (_) => completer.future,
    );

    await tester.enterText(find.byType(TextFormField), 'bob@example.com');
    await tester.tap(find.widgetWithText(ElevatedButton, 'Share'));
    await tester.pump();

    final button = tester.widget<ElevatedButton>(find.byType(ElevatedButton));
    expect(button.onPressed, isNull);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    completer.complete();
    await tester.pumpAndSettle();
  });

  testWidgets('AsyncValue.loading() renders a CircularProgressIndicator', (
    tester,
  ) async {
    await pumpDialog(
      tester,
      permissions: ValueNotifier(const AsyncValue.loading()),
    );

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('AsyncValue.error(...) renders the Error text', (tester) async {
    await pumpDialog(
      tester,
      permissions: ValueNotifier(AsyncValue.error(Exception('boom'))),
    );

    expect(find.textContaining('Error:'), findsOneWidget);
  });

  testWidgets('AsyncValue.data([]) renders the empty state', (tester) async {
    await pumpDialog(
      tester,
      permissions: ValueNotifier(const AsyncValue.data(<ResourcePermission>[])),
    );

    expect(find.text('Not shared with anyone yet'), findsOneWidget);
  });
}
