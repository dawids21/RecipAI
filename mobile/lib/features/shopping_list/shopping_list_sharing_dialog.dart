import 'package:flutter/material.dart';

import '../../core/theme.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import '../../shared/user_role.dart';
import 'shopping_list_detail_service.dart';
import 'shopping_list_shared_user.dart';

class ShoppingListSharingDialog extends StatefulWidget {
  final ShoppingListDetailService shoppingListDetailService;

  const ShoppingListSharingDialog({
    super.key,
    required this.shoppingListDetailService,
  });

  @override
  State<ShoppingListSharingDialog> createState() =>
      _ShoppingListSharingDialogState();
}

class _ShoppingListSharingDialogState extends State<ShoppingListSharingDialog> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  bool _isSharing = false;

  @override
  void initState() {
    super.initState();
    widget.shoppingListDetailService.loadSharedUsers();
  }

  @override
  void dispose() {
    _emailController.dispose();
    super.dispose();
  }

  Future<void> _shareShoppingList() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSharing = true);

    try {
      await widget.shoppingListDetailService.shareShoppingList(
        _emailController.text,
      );
      _emailController.clear();
      if (mounted) {
        _showSnackBar('Shopping list shared successfully!');
      }
    } catch (e) {
      if (mounted) {
        _showSnackBar('Failed to share shopping list: ${e.toString()}');
      }
    }

    if (mounted) {
      setState(() => _isSharing = false);
    }
  }

  Future<void> _unshareShoppingList(String email) async {
    final shouldUnshare = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Unshare Shopping List'),
        content: Text(
          'Remove access for $email? They will no longer be able to view or edit this shopping list.',
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

    if (shouldUnshare != true) return;

    try {
      await widget.shoppingListDetailService.unshareShoppingList(email);
      if (mounted) {
        _showSnackBar('Shopping list unshared successfully!');
      }
    } catch (e) {
      if (mounted) {
        _showSnackBar('Failed to unshare shopping list: ${e.toString()}');
      }
    }
  }

  void _showSnackBar(String message) {
    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    }
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
            onPressed: _isSharing ? null : _shareShoppingList,
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

  Widget _buildSharedUsersList(List<ShoppingListSharedUser> sharedUsers) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Shared with', style: theme.textTheme.titleSmall),
        const SizedBox(height: AppSpacing.small),
        ...(sharedUsers.map(
          (user) => ListTile(
            title: Text(user.email),
            subtitle: Text(user.role.displayName),
            trailing: (user.role == UserRole.editor && !user.isCurrentUser)
                ? IconButton(
                    onPressed: () => _unshareShoppingList(user.email),
                    icon: const Icon(Icons.remove_circle_outline),
                    tooltip: 'Remove access',
                  )
                : null,
          ),
        )),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Share Shopping List'),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxHeight: 400),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildEmailInput(),
            const SizedBox(height: AppSpacing.medium),
            Flexible(
              child: SingleChildScrollView(
                child: ValueListenableBuilder(
                  valueListenable: widget.shoppingListDetailService.sharedUsers,
                  builder: (context, asyncValueSharedUsers, child) {
                    return asyncValueSharedUsers.when(
                      loading: () => const SizedBox(
                        height: 100,
                        child: Center(child: LoadingWidget()),
                      ),
                      data: (sharedUsers) => _buildSharedUsersList(sharedUsers),
                      error: (error) => ApiErrorWidget(
                        errorMessage: 'Error: $error',
                        onRetry: () {
                          widget.shoppingListDetailService.loadSharedUsers();
                        },
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
