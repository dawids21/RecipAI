import 'package:flutter/material.dart';

import '../../core/api_service.dart';
import '../../core/theme.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import '../../shared/user_role.dart';
import '../auth/auth_service.dart';
import 'shared_user.dart';

class RecipeSharingDialog extends StatefulWidget {
  final String recipeId;

  const RecipeSharingDialog({super.key, required this.recipeId});

  @override
  State<RecipeSharingDialog> createState() => _RecipeSharingDialogState();
}

class _RecipeSharingDialogState extends State<RecipeSharingDialog> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  bool _isSharing = false;

  late ApiService _apiService;
  late AuthService _authService;
  late Future<List<SharedUser>> futureSharedUsers;
  bool _initSharedUsers = false;

  @override
  void didChangeDependencies() {
    _apiService = InheritedApiService.of(context);
    _authService = InheritedAuthService.of(context);
    if (!_initSharedUsers) {
      futureSharedUsers = _apiService.fetchSharedUsers(widget.recipeId);
      _initSharedUsers = true;
    }
    super.didChangeDependencies();
  }

  @override
  void dispose() {
    _emailController.dispose();
    super.dispose();
  }

  Future<void> _shareRecipe() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSharing = true);

    try {
      await _apiService.shareRecipe(widget.recipeId, _emailController.text);
      _emailController.clear();
      setState(() {
        futureSharedUsers = _apiService.fetchSharedUsers(widget.recipeId);
      });
      if (mounted) {
        _showSnackBar('Recipe shared successfully!');
      }
    } catch (e) {
      if (mounted) {
        _showSnackBar('Failed to share recipe: ${e.toString()}');
      }
    }

    setState(() => _isSharing = false);
  }

  Future<void> _unshareRecipe(String email) async {
    final shouldUnshare = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Unshare Recipe'),
        content: Text(
          'Remove access for $email? They will no longer be able to view or edit this recipe.',
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
      await _apiService.unshareRecipe(widget.recipeId, email);
      setState(() {
        futureSharedUsers = _apiService.fetchSharedUsers(widget.recipeId);
      });
      if (mounted) {
        _showSnackBar('Recipe unshared successfully!');
      }
    } catch (e) {
      if (mounted) {
        _showSnackBar('Failed to unshare recipe: ${e.toString()}');
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
            onPressed: _isSharing ? null : _shareRecipe,
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

  Widget _buildSharedUsersList(List<SharedUser> sharedUsers) {
    final theme = Theme.of(context);
    final currentUserEmail = _authService.email;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Shared with', style: theme.textTheme.titleSmall),
        const SizedBox(height: AppSpacing.small),
        ...(sharedUsers.map(
          (user) => ListTile(
            title: Text(user.email),
            subtitle: Text(user.role.displayName),
            trailing:
                (user.role == UserRole.editor && user.email != currentUserEmail)
                ? IconButton(
                    onPressed: () => _unshareRecipe(user.email),
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
      title: const Text('Share Recipe'),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxHeight: 400),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildEmailInput(),
            const SizedBox(height: AppSpacing.medium),
            Flexible(
              child: SingleChildScrollView(
                child: FutureBuilder<List<SharedUser>>(
                  future: futureSharedUsers,
                  builder: (context, snapshot) {
                    if (snapshot.connectionState == ConnectionState.waiting) {
                      return const SizedBox(
                        height: 100,
                        child: Center(child: LoadingWidget()),
                      );
                    } else if (snapshot.hasError) {
                      return ApiErrorWidget(
                        errorMessage: 'Error: ${snapshot.error}',
                        onRetry: () {
                          setState(() {
                            futureSharedUsers = _apiService.fetchSharedUsers(
                              widget.recipeId,
                            );
                          });
                        },
                      );
                    } else if (snapshot.hasData) {
                      return _buildSharedUsersList(snapshot.data!);
                    } else {
                      return const Text('No data available');
                    }
                  },
                ),
              ),
            ),
          ],
        ),
      ),
      // ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Close'),
        ),
      ],
    );
  }
}
