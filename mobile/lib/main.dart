import 'package:flutter/material.dart';

import 'config/app_config.dart';
import 'recipe/recipe_list_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppConfig.loadConfig();
  runApp(const RecipAIApp());
}

class RecipAIApp extends StatelessWidget {
  const RecipAIApp({super.key});

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
