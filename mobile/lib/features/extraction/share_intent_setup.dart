import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/get_it.dart';
import '../auth/auth_service.dart';
import 'share_intent_service.dart';

void setupShareIntent({
  required GoRouter router,
  required AuthService authService,
  required GlobalKey<ScaffoldMessengerState> scaffoldMessengerKey,
}) {
  getIt.registerSingleton(
    ShareIntentService(
      authService: authService,
      router: router,
      scaffoldMessengerKey: scaffoldMessengerKey,
    ),
    dispose: (service) => service.dispose(),
  );
}
