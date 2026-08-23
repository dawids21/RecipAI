import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../core/async_value.dart';
import 'limit_cap.dart';
import 'limit_usage.dart';

/// Rebuilds a capped surface whenever *either* of the two numbers behind it
/// changes: the surface's own usage notifier and the cap for its resource,
/// which the session loads independently on the auth flip. Listening to the
/// usage alone would leave the counter hidden and the action enabled for the
/// whole visit whenever the cap lands after the first build.
///
/// Both are handed to [builder] as nullables, so every caller keeps the
/// fail-open rule: while either is missing, show no counter and block nothing.
class LimitGate extends StatelessWidget {
  final ValueListenable<AsyncValue<LimitUsage>> usage;
  final ValueListenable<LimitCap?>? cap;
  final Widget Function(BuildContext context, LimitUsage? usage, LimitCap? cap)
  builder;

  const LimitGate({
    super.key,
    required this.usage,
    required this.cap,
    required this.builder,
  });

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([usage, cap]),
      builder: (context, _) =>
          builder(context, usage.value.valueOrNull, cap?.value),
    );
  }
}
