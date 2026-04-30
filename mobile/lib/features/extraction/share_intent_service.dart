import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import '../../core/routes.dart';
import '../auth/auth_service.dart';
import 'share_payload.dart';
import 'share_route_extras.dart';

// consumeInitialShare() must be called from exactly one site (post-first-frame
// in _RecipAIAppState). A second call returns null and silently drops the share.
class ShareIntentService {
  final AuthService _authService;
  final GoRouter _router;
  final GlobalKey<ScaffoldMessengerState> _scaffoldMessengerKey;
  final MethodChannel _methodChannel;
  final EventChannel _eventChannel;

  late final StreamSubscription<dynamic> _eventSubscription;

  bool _consumeCalled = false;

  ShareIntentService({
    required AuthService authService,
    required GoRouter router,
    required GlobalKey<ScaffoldMessengerState> scaffoldMessengerKey,
    MethodChannel? methodChannel,
    EventChannel? eventChannel,
  }) : _authService = authService,
       _router = router,
       _scaffoldMessengerKey = scaffoldMessengerKey,
       _methodChannel = methodChannel ?? const MethodChannel('recipai/share'),
       _eventChannel =
           eventChannel ?? const EventChannel('recipai/share/events') {
    _eventSubscription = _eventChannel.receiveBroadcastStream().listen(
      _onEventReceived,
    );
  }

  Future<void> consumeInitialShare() async {
    assert(!_consumeCalled, 'consumeInitialShare() must only be called once');
    _consumeCalled = true;

    final result = await _methodChannel.invokeMethod<Map<Object?, Object?>>(
      'consumeInitialShare',
    );
    if (result == null) return;
    final map = result.cast<String, Object>();
    final payload = SharePayload.fromMap(map);
    if (payload == null) return;
    _classifyAndRoute(payload);
  }

  void _onEventReceived(dynamic event) {
    if (event is! Map) return;
    final map = Map<String, Object>.from(event);
    final payload = SharePayload.fromMap(map);
    if (payload == null) return;
    _classifyAndRoute(payload);
  }

  void _classifyAndRoute(SharePayload payload) {
    if (!_authService.isAuthenticated.value) return;

    switch (payload) {
      case UrlSharePayload(:final url):
        _router.goNamed(AppRoute.urlExtraction.name, extra: UrlPrefill(url));
      case NonUrlTextSharePayload():
        _router.goNamed(AppRoute.main.name);
        _scaffoldMessengerKey.currentState?.showSnackBar(
          const SnackBar(
            content: Text(
              'RecipAI can only extract recipes from URLs or images.',
            ),
            duration: Duration(seconds: 3),
          ),
        );
      case ImageSharePayload(:final file):
        _router.goNamed(
          AppRoute.imageExtraction.name,
          extra: ImagePrefill(file),
        );
    }
  }

  void dispose() {
    _eventSubscription.cancel();
  }
}
