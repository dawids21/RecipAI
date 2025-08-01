import 'package:flutter/material.dart';

import '../core/theme.dart';
import 'error_icon.dart';

class ApiErrorWidget extends StatelessWidget {
  final String errorMessage;
  final VoidCallback onRetry;

  const ApiErrorWidget({
    super.key,
    required this.errorMessage,
    required this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const ErrorIcon(),
          const SizedBox(height: AppSpacing.medium),
          Text(
            errorMessage,
            style: theme.textTheme.bodyLarge,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.medium),
          ElevatedButton(onPressed: onRetry, child: const Text('Retry')),
        ],
      ),
    );
  }
}
