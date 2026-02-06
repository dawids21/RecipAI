import 'package:flutter/material.dart';

import '../core/theme.dart';

class ServingSizeInput extends StatelessWidget {
  final int servingSize;

  final ValueChanged<int> onChanged;

  const ServingSizeInput({
    super.key,
    required this.servingSize,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Serving Size',
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: AppSpacing.small),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            IconButton.outlined(
              onPressed: servingSize > 1
                  ? () => onChanged(servingSize - 1)
                  : null,
              icon: const Icon(Icons.remove),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.large),
              child: Text(
                '$servingSize',
                style: theme.textTheme.headlineMedium,
              ),
            ),
            IconButton.outlined(
              onPressed: () => onChanged(servingSize + 1),
              icon: const Icon(Icons.add),
            ),
          ],
        ),
      ],
    );
  }
}
