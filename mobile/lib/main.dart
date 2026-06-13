import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:go_router/go_router.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:recipai_mobile/core/get_it.dart';
import 'package:recipai_mobile/firebase_options.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'core/app_config.dart';
import 'core/logging/logging_setup.dart';
import 'core/preferences_service.dart';
import 'core/routes.dart';
import 'core/theme.dart';
import 'features/auth/auth_service.dart';
import 'features/auth/auth_setup.dart';
import 'features/extraction/extraction_setup.dart';
import 'features/extraction/share_intent_service.dart';
import 'features/extraction/share_intent_setup.dart';
import 'features/planning/meal_plan_setup.dart';
import 'features/recipe/collection/recipes_collection_setup.dart';
import 'features/recipe/recipe_setup.dart';
import 'features/shopping_list/shopping_list_setup.dart';

final scaffoldMessengerKey = GlobalKey<ScaffoldMessengerState>();

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  await GoogleSignIn.instance.initialize();
  await AppConfig.loadConfig();
  await setupLogging();

  final prefs = await SharedPreferences.getInstance();
  getIt.registerSingleton(PreferencesService(prefs));

  // DI
  setupAuth();
  setupRecipe();
  setupRecipesCollection();
  setupMealPlan();
  setupShoppingList();
  setupExtraction();

  final appRouter = createAppRouter();
  setupShareIntent(
    router: appRouter,
    authService: getIt<AuthService>(),
    scaffoldMessengerKey: scaffoldMessengerKey,
  );

  runApp(RecipAIApp(appRouter: appRouter));
}

class RecipAIApp extends StatefulWidget {
  final GoRouter appRouter;

  const RecipAIApp({super.key, required this.appRouter});

  @override
  State<RecipAIApp> createState() => _RecipAIAppState();
}

class _RecipAIAppState extends State<RecipAIApp> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => getIt<ShareIntentService>().consumeInitialShare(),
    );
  }

  @override
  void dispose() {
    getIt<ShareIntentService>().dispose();
    getIt<AuthService>().dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'RecipAI',
      theme: AppTheme.theme,
      scaffoldMessengerKey: scaffoldMessengerKey,
      localizationsDelegates: [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      routerConfig: widget.appRouter,
      supportedLocales: [Locale('en', 'US'), Locale('en', 'GB'), Locale('pl')],
    );
  }
}
