import 'package:flutter/material.dart';
import 'package:recipai_mobile/core/api_service.dart';

import 'core/app_config.dart';
import 'core/routes.dart';
import 'core/theme.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppConfig.loadConfig();
  runApp(const RecipAIApp());
}

class RecipAIApp extends StatefulWidget {
  const RecipAIApp({super.key});

  @override
  State<RecipAIApp> createState() => _RecipAIAppState();
}

class _RecipAIAppState extends State<RecipAIApp> {
  @override
  void dispose() {
    ApiService.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'RecipAI',
      theme: AppTheme.theme,
      routerConfig: appRouter,
    );
  }
}
