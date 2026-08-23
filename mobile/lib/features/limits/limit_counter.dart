import 'package:flutter/material.dart';

class LimitCounter extends StatelessWidget {
  final int used;
  final int limit;
  final int? resetsInSeconds;
  final String noun;

  const LimitCounter({
    super.key,
    required this.used,
    required this.limit,
    this.resetsInSeconds,
    required this.noun,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final resets = resetsInSeconds;
    final text = resets == null
        ? '$used / $limit $noun'
        : '$used / $limit $noun (resets in ${formatResetIn(resets)})';

    return Text(
      text,
      style: theme.textTheme.bodySmall?.copyWith(
        color: theme.colorScheme.onSurfaceVariant,
      ),
    );
  }
}

String formatResetIn(int seconds) {
  if (seconds < 60) return '${seconds}s';
  final minutes = seconds ~/ 60;
  if (minutes < 60) return '${minutes}m';
  final hours = minutes ~/ 60;
  if (hours < 24) return '${hours}h';
  final days = hours ~/ 24;
  return '${days}d';
}
