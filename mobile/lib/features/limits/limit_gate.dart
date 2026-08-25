import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../core/async_value.dart';
import 'limit_balance.dart';
import 'limit_quota.dart';

/// Rebuilds a limited surface whenever *either* of the two numbers behind it
/// changes: the surface's own balance notifier and the quota for its resource,
/// which the session loads independently on the auth flip. Listening to the
/// balance alone would leave the counter hidden and the action enabled for the
/// whole visit whenever the quota lands after the first build.
///
/// Both are handed to [builder] as nullables, so every caller keeps the
/// fail-open rule: while either is missing, show no counter and block nothing.
class LimitGate extends StatelessWidget {
  final ValueListenable<AsyncValue<LimitBalance>> balance;
  final ValueListenable<LimitQuota?>? quota;
  final Widget Function(
    BuildContext context,
    LimitBalance? balance,
    LimitQuota? quota,
  )
  builder;

  const LimitGate({
    super.key,
    required this.balance,
    required this.quota,
    required this.builder,
  });

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([balance, quota]),
      builder: (context, _) =>
          builder(context, balance.value.valueOrNull, quota?.value),
    );
  }
}
