import 'package:flutter/material.dart';
import 'package:recipai/services/api_service.dart';

import 'config/app_config.dart';
import 'recipe/recipe_list_screen.dart';

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
    return MaterialApp(
      title: 'RecipAI',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepOrange),
        useMaterial3: true,
      ),
      home: const RecipeListScreen(),
    );
  }
}
