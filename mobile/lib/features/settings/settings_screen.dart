import 'package:flutter/material.dart';

import '../../core/logging/share_logs.dart';
import '../../core/theme.dart';
import '../auth/auth_service.dart';

class SettingsScreen extends StatefulWidget {
  final AuthService authService;

  const SettingsScreen({super.key, required this.authService});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
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
      body: ListView(
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
              ],
            ),
          ),
        ],
      ),
    );
  }
}
