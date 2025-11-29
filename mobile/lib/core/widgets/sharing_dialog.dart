import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../async_value.dart';
import '../theme.dart';

class SharedUser {
  final String email;
  final String role;
  final bool isCurrentUser;

  const SharedUser({
    required this.email,
    required this.role,
    required this.isCurrentUser,
  });
}

class SharingDialog extends StatefulWidget {
  final String title;
  final ValueListenable<AsyncValue<List<SharedUser>>> sharedUsers;
  final Future<void> Function(String email) onShare;
  final Future<void> Function(String email) onUnshare;

  const SharingDialog({
    super.key,
    required this.title,
    required this.sharedUsers,
    required this.onShare,
    required this.onUnshare,
  });

  @override
  State<SharingDialog> createState() => _SharingDialogState();
}

class _SharingDialogState extends State<SharingDialog> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  bool _isSharing = false;

  @override
  void dispose() {
    _emailController.dispose();
    super.dispose();
  }

  String? _validateEmail(String? value) {
    if (value == null || value.isEmpty) {
      return 'Please enter an email address';
    }

    final emailRegex = RegExp(
      r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$',
    );
    if (!emailRegex.hasMatch(value)) {
      return 'Please enter a valid email address';
    }

    return null;
  }

  Future<void> _handleShare() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSharing = true);
    try {
      await widget.onShare(_emailController.text.trim());
      _emailController.clear();
    } catch (e) {
      // Error feedback handled by caller
      rethrow;
    } finally {
      if (mounted) {
        setState(() => _isSharing = false);
      }
    }
  }

  Future<void> _handleUnshare(String email) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Confirm Unshare'),
        content: Text(
          'Remove access for $email? They will no longer be able to view or edit this item.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: TextButton.styleFrom(
              foregroundColor: Theme.of(context).colorScheme.error,
            ),
            child: const Text('Unshare'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    try {
      await widget.onUnshare(email);
    } catch (e) {
      // Error feedback handled by caller
      rethrow;
    }
  }

  /// Builds the email input form with share button
  Widget _buildEmailInput() {
    return Form(
      key: _formKey,
      child: Row(
        children: [
          Expanded(
            child: TextFormField(
              controller: _emailController,
              validator: _validateEmail,
              decoration: const InputDecoration(
                labelText: 'Email address',
                hintText: 'user@example.com',
                border: OutlineInputBorder(),
              ),
              keyboardType: TextInputType.emailAddress,
              enabled: !_isSharing,
            ),
          ),
          const SizedBox(width: AppSpacing.small),
          ElevatedButton(
            onPressed: _isSharing ? null : _handleShare,
            child: _isSharing
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Share'),
          ),
        ],
      ),
    );
  }

  Widget _buildSharedUsersList(List<SharedUser> users) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Shared with', style: theme.textTheme.titleSmall),
        const SizedBox(height: AppSpacing.small),
        ...users.map(
          (user) => ListTile(
            title: Text(user.email),
            subtitle: Text(user.role),
            trailing: user.isCurrentUser
                ? null
                : IconButton(
                    icon: const Icon(Icons.remove_circle_outline),
                    onPressed: () => _handleUnshare(user.email),
                    tooltip: 'Remove access',
                  ),
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return AlertDialog(
      title: Text(widget.title),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxHeight: 400),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildEmailInput(),
            const SizedBox(height: AppSpacing.medium),
            Flexible(
              child: SingleChildScrollView(
                child: ValueListenableBuilder<AsyncValue<List<SharedUser>>>(
                  valueListenable: widget.sharedUsers,
                  builder: (context, asyncValue, child) {
                    return asyncValue.when(
                      data: (users) => users.isEmpty
                          ? const Center(
                              child: Text('Not shared with anyone yet'),
                            )
                          : _buildSharedUsersList(users),
                      loading: () => const SizedBox(
                        height: 100,
                        child: Center(child: CircularProgressIndicator()),
                      ),
                      error: (error) => Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              Icons.error_outline,
                              color: theme.colorScheme.error,
                              size: 48,
                            ),
                            const SizedBox(height: AppSpacing.small),
                            Text(
                              'Error: $error',
                              style: TextStyle(color: theme.colorScheme.error),
                              textAlign: TextAlign.center,
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Close'),
        ),
      ],
    );
  }
}
