import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:recipai_mobile/core/api_service.dart';
import 'package:recipai_mobile/firebase_options.dart';

import 'core/app_config.dart';
import 'core/routes.dart';
import 'core/theme.dart';
import 'features/recipe/recipe_list_model.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  await AppConfig.loadConfig();
  runApp(const RecipAIApp());
}

class RecipAIApp extends StatefulWidget {
  const RecipAIApp({super.key});

  @override
  State<RecipAIApp> createState() => _RecipAIAppState();
}

class _RecipAIAppState extends State<RecipAIApp> {
  late final RecipeListModel _recipeListModel;

  @override
  void initState() {
    super.initState();
    _recipeListModel = RecipeListModel();
  }

  @override
  void dispose() {
    _recipeListModel.dispose();
    ApiService.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return InheritedRecipeListModel(
      notifier: _recipeListModel,
      child: MaterialApp.router(
        title: 'RecipAI',
        theme: AppTheme.theme,
        routerConfig: appRouter,
      ),
    );
  }
}
