import 'package:flutter/material.dart';

import '../../core/theme.dart';
import '../../shared/error_message_widget.dart';
import '../../shared/loading_widget.dart';
import 'auth_service.dart';
import 'dev_auth_service.dart';

class LoginScreen extends StatefulWidget {
  final AuthService authService;

  /// Present only in a dev-auth build; drives the dev sign-in controls.
  final DevAuthService? devAuthService;

  const LoginScreen({
    super.key,
    required this.authService,
    this.devAuthService,
  });

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  bool _isLoading = false;
  String? _errorMessage;
  final _devUserNameController = TextEditingController();

  @override
  void dispose() {
    _devUserNameController.dispose();
    super.dispose();
  }

  Future<void> _handleGoogleSignIn() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      await widget.authService.signIn();
    } catch (e) {
      setState(() {
        _errorMessage = 'Failed to sign in with Google. Please try again.';
      });
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _handleDevSignIn() async {
    final name = _devUserNameController.text;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      await widget.devAuthService!.signIn(name);
    } catch (e) {
      setState(() {
        _errorMessage = 'Failed to sign in as $name. Please try again.';
      });
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Welcome to RecipAI'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: _isLoading
          ? const LoadingWidget(message: 'Signing you in...')
          : Padding(
              padding: AppSpacing.screenPadding,
              child: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.restaurant_menu,
                      size: 80,
                      color: theme.colorScheme.primary,
                    ),
                    const SizedBox(height: AppSpacing.large),
                    Text(
                      'RecipAI',
                      style: theme.textTheme.headlineLarge?.copyWith(
                        color: theme.colorScheme.primary,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: AppSpacing.medium),
                    Text(
                      'Your personal recipe assistant',
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: theme.colorScheme.onSurface.withValues(
                          alpha: 0.7,
                        ),
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: AppSpacing.extraLarge),
                    if (_errorMessage != null) ...[
                      ErrorMessageWidget(message: _errorMessage!),
                      const SizedBox(height: AppSpacing.medium),
                    ],
                    if (widget.devAuthService != null) ...[
                      TextFormField(
                        controller: _devUserNameController,
                        autocorrect: false,
                        decoration: const InputDecoration(
                          labelText: 'Dev user name',
                          border: OutlineInputBorder(),
                        ),
                        onChanged: (_) => setState(() {}),
                      ),
                      const SizedBox(height: AppSpacing.medium),
                      SizedBox(
                        width: double.infinity,
                        height: 56,
                        child: ElevatedButton(
                          onPressed:
                              _isLoading || _devUserNameController.text.isEmpty
                              ? null
                              : _handleDevSignIn,
                          child: const Text('Login'),
                        ),
                      ),
                    ],
                    if (widget.devAuthService == null)
                      SizedBox(
                        width: double.infinity,
                        height: 56,
                        child: ElevatedButton.icon(
                          onPressed: _isLoading ? null : _handleGoogleSignIn,
                          icon: const Icon(Icons.login),
                          label: const Text('Sign in with Google'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: theme.colorScheme.surface,
                            foregroundColor: theme.colorScheme.onSurface,
                            side: BorderSide(color: theme.colorScheme.outline),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(8),
                            ),
                          ),
                        ),
                      ),
                    const SizedBox(height: AppSpacing.large),
                    Text(
                      'Sign in to access your recipes and start cooking!',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurface.withValues(
                          alpha: 0.6,
                        ),
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            ),
    );
  }
}
