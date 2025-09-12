import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:recipai_mobile/firebase_options.dart';

import 'core/api_service.dart';
import 'core/app_config.dart';
import 'core/routes.dart';
import 'core/theme.dart';
import 'features/auth/auth_service.dart';
import 'features/auth/firebase_auth_service.dart';
import 'features/recipe/recipe_list_model.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  await GoogleSignIn.instance.initialize();
  await AppConfig.loadConfig();
  final authService = FirebaseAuthService();
  final apiService = ApiService(authService);
  runApp(RecipAIApp(authService: authService, apiService: apiService));
}

class RecipAIApp extends StatefulWidget {
  final AuthService authService;
  final ApiService apiService;

  const RecipAIApp({
    super.key,
    required this.authService,
    required this.apiService,
  });

  @override
  State<RecipAIApp> createState() => _RecipAIAppState();
}

class _RecipAIAppState extends State<RecipAIApp> {
  late final RecipeListModel _recipeListModel;

  @override
  void initState() {
    super.initState();
    _recipeListModel = RecipeListModel(widget.apiService);
  }

  @override
  void dispose() {
    _recipeListModel.dispose();
    widget.apiService.dispose();
    widget.authService.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return InheritedAuthService(
      notifier: widget.authService,
      child: InheritedApiService(
        apiService: widget.apiService,
        child: InheritedRecipeListModel(
          notifier: _recipeListModel,
          child: MaterialApp.router(
            title: 'RecipAI',
            theme: AppTheme.theme,
            routerConfig: createAppRouter(widget.authService),
          ),
        ),
      ),
    );
  }
}
