import 'dart:async';

import '../../core/preferences_service.dart';
import 'auth_repository.dart';
import 'auth_user.dart';

/// Dev-only [AuthRepository] backed by a name persisted in [PreferencesService].
///
/// The backend's `dev` profile accepts any RFC 6750-legal bearer token and derives
/// the caller as `<token>@local.test`. This repository mirrors that: the bare
/// persisted name is the bearer token, while the emitted [AuthUser.email] carries
/// the `@local.test` suffix. Every name that reaches the persisted value is
/// checked against that grammar here, since [getIdToken] hands it straight back
/// as the bearer token.
class DevAuthRepository implements AuthRepository {
  final PreferencesService _preferencesService;
  final _controller = StreamController<AuthUser?>.broadcast();
  AuthUser? _current;

  /// RFC 6750 bearer-token grammar. Anything outside this set (notably `@`) is
  /// rejected by Spring Security's `DefaultBearerTokenResolver` before the
  /// backend ever sees it.
  static final RegExp _legalName = RegExp(r'^[a-zA-Z0-9\-._~+/]+$');

  DevAuthRepository({required PreferencesService preferencesService})
    : _preferencesService = preferencesService {
    final name = _preferencesService.getDevAuthUserName();
    if (name == null) return;
    if (_legalName.hasMatch(name)) {
      _current = AuthUser(email: '$name@local.test');
    } else {
      unawaited(_preferencesService.setDevAuthUserName(null));
    }
  }

  @override
  Stream<AuthUser?> watchAuthState() async* {
    // Seed the current value so a listener that subscribes after construction
    // (e.g. AuthService, wired up a moment later) still replays a persisted
    // session instead of missing it — a plain broadcast controller drops
    // events added before anyone is listening.
    yield _current;
    yield* _controller.stream;
  }

  @override
  Future<String?> getIdToken() async {
    return _preferencesService.getDevAuthUserName();
  }

  /// Signs in as [name], persisting it as the bearer token. Not part of
  /// [AuthRepository] — only this implementation takes its identity from the
  /// caller. Throws [ArgumentError] if [name] is not a legal bearer token; the
  /// Login Screen turns that into user-facing copy.
  Future<void> signInAs(String name) async {
    if (!_legalName.hasMatch(name)) {
      throw ArgumentError.value(name, 'name', 'Not a legal bearer token');
    }
    await _preferencesService.setDevAuthUserName(name);
    _current = AuthUser(email: '$name@local.test');
    _controller.add(_current);
  }

  /// Not a live path: the dev identity comes from the caller, so there is
  /// nothing to sign in *as* here. The Login Screen goes through
  /// `DevAuthService.signIn(name)`, which reaches [signInAs]; a persisted
  /// session is restored by the constructor.
  @override
  Future<void> signIn() {
    throw UnsupportedError('Dev sign-in goes through signInAs(name).');
  }

  @override
  Future<void> signOut() async {
    await _preferencesService.setDevAuthUserName(null);
    _current = null;
    _controller.add(null);
  }
}
