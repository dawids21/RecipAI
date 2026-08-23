import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';

import '../auth/auth_service.dart';
import 'limit_cap.dart';
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
  /// and never replaced, so [capFor] is a pure read a widget may call from
  /// `build()`. Their lifetime is the service's.
  final Map<String, ValueNotifier<LimitCap?>> _caps = {
    for (final resource in LimitResources.perUser)
      resource: ValueNotifier<LimitCap?>(null),
  };

  /// Handed to [capFor] for a resource this service holds no cap for, so an
  /// unknown key fails open like an unloaded one instead of throwing in
  /// `build()`. Never written to.
  final ValueNotifier<LimitCap?> _noCap = ValueNotifier<LimitCap?>(null);

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
      _apply(await _limitsRepository.fetchCaps(token));
    } catch (error, stackTrace) {
      _log.warning(
        'Failed to load limit caps; every capped surface falls back to no cap',
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
  /// value every surface reads as "no cap known" and so fails open on.
  void _apply(Map<String, LimitCap> loaded) {
    for (final entry in _caps.entries) {
      entry.value.value = loaded[entry.key];
    }
  }

  /// The cap for one resource, as its own listenable, so a widget can depend on
  /// the single number it gates on instead of on the whole service. A resource
  /// outside [LimitResources.perUser] gets a listenable that stays null, which
  /// every surface fails open on.
  ValueListenable<LimitCap?> capFor(String resource) =>
      _caps[resource] ?? _noCap;

  void dispose() {
    if (_isDisposed) return;
    _isDisposed = true;
    _authService.isAuthenticated.removeListener(_onAuthChanged);
    for (final cap in _caps.values) {
      cap.dispose();
    }
    _noCap.dispose();
  }
}
