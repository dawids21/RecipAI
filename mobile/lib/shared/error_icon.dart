import 'package:flutter/material.dart';

/// Standardized error icon widget with consistent size and theme-based color
class ErrorIcon extends StatelessWidget {
  const ErrorIcon({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Icon(
      Icons.error_outline,
      size: 64.0,
      color: theme.colorScheme.error,
    );
  }
}
