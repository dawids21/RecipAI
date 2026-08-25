import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';

import '../auth/auth_service.dart';
import 'limit_quota.dart';
import 'limits_repository.dart';

class LimitsService {
  static final _log = Logger('recipai.limits.service');

  final LimitsRepository _limitsRepository;
  final AuthService _authService;

  LimitsService({
    required LimitsRepository limitsRepository,
    required AuthService authService,
  }) : _limitsRepository = limitsRepository,
       _authService = authService {
    _authService.isAuthenticated.addListener(_onAuthChanged);
  }

  /// One notifier per resource this client gates on, created with the service
  /// and never replaced, so [quotaFor] is a pure read a widget may call from
  /// `build()`. Their lifetime is the service's.
  final Map<String, ValueNotifier<LimitQuota?>> _quotas = {
    for (final resource in LimitResources.perUser)
      resource: ValueNotifier<LimitQuota?>(null),
  };

  /// Handed to [quotaFor] for a resource this service holds no quota for, so an
  /// unknown key fails open like an unloaded one instead of throwing in
  /// `build()`. Never written to.
  final ValueNotifier<LimitQuota?> _noQuota = ValueNotifier<LimitQuota?>(null);

  bool _isLoadRunning = false;

  bool _isDisposed = false;

  void _onAuthChanged() {
    if (_authService.isAuthenticated.value) {
      unawaited(_load());
    } else {
      _apply(const {});
    }
  }

  Future<void> _load() async {
    if (_isLoadRunning) return;
    _isLoadRunning = true;
    try {
      final token = await _authService.idToken;
      _apply(await _limitsRepository.fetchQuotas(token));
    } catch (error, stackTrace) {
      _log.warning(
        'Failed to load limit quotas; every limited surface falls back to no quota',
        error,
        stackTrace,
      );
      _apply(const {});
    } finally {
      _isLoadRunning = false;
    }
  }

  /// Writes the whole set at once, so a resource the response omitted — or
  /// every resource, on a failed load or a sign-out — falls back to null, the
  /// value every surface reads as "no quota known" and so fails open on.
  void _apply(Map<String, LimitQuota> loaded) {
    for (final entry in _quotas.entries) {
      entry.value.value = loaded[entry.key];
    }
  }

  /// The quota for one resource, as its own listenable, so a widget can depend on
  /// the single number it gates on instead of on the whole service. A resource
  /// outside [LimitResources.perUser] gets a listenable that stays null, which
  /// every surface fails open on.
  ValueListenable<LimitQuota?> quotaFor(String resource) =>
      _quotas[resource] ?? _noQuota;

  void dispose() {
    if (_isDisposed) return;
    _isDisposed = true;
    _authService.isAuthenticated.removeListener(_onAuthChanged);
    for (final quota in _quotas.values) {
      quota.dispose();
    }
    _noQuota.dispose();
  }
}
