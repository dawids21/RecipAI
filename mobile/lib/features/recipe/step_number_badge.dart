import 'package:flutter/material.dart';

/// Circular badge displaying step numbers in recipe instructions
class StepNumberBadge extends StatelessWidget {
  final int stepNumber;

  const StepNumberBadge({super.key, required this.stepNumber});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: 24.0,
      height: 24.0,
      decoration: BoxDecoration(
        color: theme.primaryColor,
        shape: BoxShape.circle,
      ),
      child: Center(
        child: Text(
          '$stepNumber',
          style: const TextStyle(
            color: Colors.white,
            fontSize: 12,
            fontWeight: FontWeight.bold,
          ),
        ),
      ),
    );
  }
}
