import 'package:flutter/material.dart';

import '../../core/logging/share_logs.dart';
import '../../core/theme.dart';
import '../../shared/loading_widget.dart';
import '../auth/auth_service.dart';
import 'account_deletion_service.dart';

class SettingsScreen extends StatefulWidget {
  final AuthService authService;
  final AccountDeletionService accountDeletionService;

  const SettingsScreen({
    super.key,
    required this.authService,
    required this.accountDeletionService,
  });

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _isDeleting = false;

  Future<void> _onLogoutTap(BuildContext context) async {
    final shouldLogout = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Logout'),
          content: const Text('Are you sure you want to logout?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('Logout'),
            ),
          ],
        );
      },
    );

    if (shouldLogout == true) {
      try {
        await widget.authService.signOut();
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('Failed to logout: $e')));
        }
      }
    }
  }

  Future<void> _onDeleteAccountTap() async {
    final shouldDelete = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Delete account'),
          content: const Text(
            'All your recipes, collections, shopping lists and meal plans '
            'will be deleted immediately. This cannot be undone.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('Delete'),
            ),
          ],
        );
      },
    );
    if (shouldDelete != true || !mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    setState(() => _isDeleting = true);
    final outcome = await widget.accountDeletionService.deleteAccount();
    // On success the auth redirect has already disposed this screen.
    if (!mounted) return;
    setState(() => _isDeleting = false);

    switch (outcome) {
      case AccountDeletionOutcome.deleted:
      case AccountDeletionOutcome.cancelled:
        break;
      case AccountDeletionOutcome.reauthFailed:
        messenger.showSnackBar(
          const SnackBar(
            content: Text(
              'Could not confirm your identity. Nothing was deleted.',
            ),
          ),
        );
      case AccountDeletionOutcome.purgeFailed:
        messenger.showSnackBar(
          const SnackBar(
            content: Text('Failed to delete your account. Please try again.'),
          ),
        );
      case AccountDeletionOutcome.identityDeletionFailed:
        await showDialog<void>(
          context: context,
          barrierDismissible: false,
          builder: (BuildContext context) {
            return AlertDialog(
              title: const Text('Something went wrong'),
              content: const Text(
                'Your data was deleted, but your account could not be '
                'removed. Please contact support.',
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('OK'),
                ),
              ],
            );
          },
        );
        await widget.accountDeletionService.signOut();
    }
  }

  Future<void> _onSendLogsTap() async {
    await shareLogs();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: PopScope(
        canPop: !_isDeleting,
        child: _isDeleting
            ? const LoadingWidget(message: 'Deleting your account...')
            : ListView(
                padding: AppSpacing.mediumVertical,
                children: [
                  Card(
                    margin: AppSpacing.cardMargin,
                    clipBehavior: Clip.antiAlias,
                    child: Column(
                      children: [
                        ListTile(
                          contentPadding: AppSpacing.listTilePadding,
                          leading: const Icon(Icons.bug_report),
                          title: const Text('Send logs'),
                          onTap: _onSendLogsTap,
                        ),
                        const Divider(height: 1),
                        ListTile(
                          contentPadding: AppSpacing.listTilePadding,
                          leading: const Icon(Icons.logout),
                          title: const Text('Logout'),
                          onTap: () => _onLogoutTap(context),
                        ),
                        const Divider(height: 1),
                        ListTile(
                          contentPadding: AppSpacing.listTilePadding,
                          leading: Icon(
                            Icons.delete_forever,
                            color: theme.colorScheme.error,
                          ),
                          title: Text(
                            'Delete account',
                            style: TextStyle(color: theme.colorScheme.error),
                          ),
                          onTap: _onDeleteAccountTap,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}
