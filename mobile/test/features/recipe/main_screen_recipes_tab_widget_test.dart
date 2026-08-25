import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get_it/get_it.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';
import 'package:recipai_mobile/core/main_screen.dart';
import 'package:recipai_mobile/core/preferences_service.dart';
import 'package:recipai_mobile/core/routes.dart';
import 'package:recipai_mobile/features/auth/auth_setup.dart';
import 'package:recipai_mobile/features/auth/auth_user.dart';
import 'package:recipai_mobile/features/limits/limits_service.dart';
import 'package:recipai_mobile/features/limits/limits_setup.dart';
import 'package:recipai_mobile/features/planning/meal_plan_calendar_data.dart';
import 'package:recipai_mobile/features/planning/meal_plan_setup.dart';
import 'package:recipai_mobile/features/recipe/collection/recipes_collection_setup.dart';
import 'package:recipai_mobile/features/recipe/recipe.dart';
import 'package:recipai_mobile/features/recipe/recipe_grid_item.dart';
import 'package:recipai_mobile/features/recipe/recipe_list_service.dart';
import 'package:recipai_mobile/features/recipe/recipe_setup.dart';
import 'package:recipai_mobile/features/recipe/collection/recipes_collection_list_service.dart';
import 'package:recipai_mobile/features/planning/meal_plan_calendar_service.dart';
import 'package:recipai_mobile/features/planning/meal_plan_list_service.dart';
import 'package:recipai_mobile/features/planning/meal_plan_visibility_service.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_item_store_service.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_list_service.dart';
import 'package:recipai_mobile/features/auth/auth_service.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_setup.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/mocks.dart';

class _NavPushSpy extends NavigatorObserver {
  final List<Route<dynamic>> pushed = [];

  @override
  void didPush(Route route, Route? previousRoute) {
    pushed.add(route);
  }
}

void main() {
  late MockAuthRepository authRepository;
  late MockRecipeRepository recipeRepository;
  late MockRecipesCollectionRepository recipesCollectionRepository;
  late MockShoppingListRepository shoppingListRepository;
  late MockShoppingListItemRepository shoppingListItemRepository;
  late MockShoppingListItemDao shoppingListItemDao;
  late MockMealPlanRepository mealPlanRepository;
  late MockLimitsRepository limitsRepository;
  late _NavPushSpy navSpy;
  late Widget app;

  setUpAll(() {
    registerFallbackValue(Uri());
  });

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await GetIt.I.reset();

    authRepository = MockAuthRepository();
    recipeRepository = MockRecipeRepository();
    recipesCollectionRepository = MockRecipesCollectionRepository();
    shoppingListRepository = MockShoppingListRepository();
    shoppingListItemRepository = MockShoppingListItemRepository();
    shoppingListItemDao = MockShoppingListItemDao();
    mealPlanRepository = MockMealPlanRepository();
    limitsRepository = MockLimitsRepository();

    when(() => limitsRepository.fetchQuotas(any())).thenAnswer((_) async => {});
    when(
      () => authRepository.watchAuthState(),
    ).thenAnswer((_) => const Stream<AuthUser?>.empty());
    when(
      () => authRepository.getIdToken(),
    ).thenAnswer((_) async => 'fake-token');
    when(
      () => shoppingListItemDao.listIdsWithOutbox(),
    ).thenAnswer((_) async => const []);

    final prefs = await SharedPreferences.getInstance();
    GetIt.I.registerSingleton(PreferencesService(prefs));

    setupAuth(authRepository: authRepository);
    setupLimits(limitsRepository: limitsRepository);
    setupRecipesCollection(
      recipesCollectionRepository: recipesCollectionRepository,
    );
    setupRecipe(recipeRepository: recipeRepository);
    setupShoppingList(
      shoppingListRepository: shoppingListRepository,
      itemRepository: shoppingListItemRepository,
      store: ShoppingListItemStoreService(dao: shoppingListItemDao),
    );
    setupMealPlan(mealPlanRepository: mealPlanRepository);

    navSpy = _NavPushSpy();
    final router = GoRouter(
      observers: [navSpy],
      routes: [
        GoRoute(
          path: AppRoute.main.path,
          builder: (context, state) => MainScreen(
            recipeListService: GetIt.I<RecipeListService>(),
            recipesCollectionListService:
                GetIt.I<RecipesCollectionListService>(),
            shoppingListListService: GetIt.I<ShoppingListListService>(),
            authService: GetIt.I<AuthService>(),
            mealPlanCalendarService: GetIt.I<MealPlanCalendarService>(),
            mealPlanListService: GetIt.I<MealPlanListService>(),
            mealPlanVisibilityService: GetIt.I<MealPlanVisibilityService>(),
            limitsService: GetIt.I<LimitsService>(),
          ),
        ),
        GoRoute(
          path: '/${AppRoute.recipeDetail.path}',
          name: AppRoute.recipeDetail.name,
          builder: (context, state) =>
              const Scaffold(body: Text('Recipe Detail')),
        ),
      ],
    );
    app = MaterialApp.router(routerConfig: router);
  });

  tearDown(() => GetIt.I.reset());

  testWidgets('renders empty state when repository returns no recipes', (
    tester,
  ) async {
    when(
      () => recipeRepository.fetchRecipes(any()),
    ).thenAnswer((_) async => <Recipe>[]);
    when(
      () => recipesCollectionRepository.fetchRecipesCollections(any()),
    ).thenAnswer((_) async => []);
    when(
      () => shoppingListRepository.fetchShoppingLists(any()),
    ).thenAnswer((_) async => []);
    when(
      () => mealPlanRepository.fetchMealPlans(idToken: any(named: 'idToken')),
    ).thenAnswer((_) async => []);
    when(
      () => mealPlanRepository.fetchCalendar(
        startDate: any(named: 'startDate'),
        endDate: any(named: 'endDate'),
        planIds: any(named: 'planIds'),
        idToken: any(named: 'idToken'),
      ),
    ).thenAnswer((_) async => const MealPlanCalendarData(entriesByDate: {}));

    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    expect(find.text('No recipes found'), findsOneWidget);
  });

  testWidgets(
    'renders the grid with the expected items when the repository returns recipes',
    (tester) async {
      when(() => recipeRepository.fetchRecipes(any())).thenAnswer(
        (_) async => [
          const Recipe(id: '1', name: 'Pizza'),
          const Recipe(id: '2', name: 'Pasta'),
          const Recipe(id: '3', name: 'Salad'),
        ],
      );
      when(
        () => recipesCollectionRepository.fetchRecipesCollections(any()),
      ).thenAnswer((_) async => []);
      when(
        () => shoppingListRepository.fetchShoppingLists(any()),
      ).thenAnswer((_) async => []);
      when(
        () => mealPlanRepository.fetchMealPlans(idToken: any(named: 'idToken')),
      ).thenAnswer((_) async => []);
      when(
        () => mealPlanRepository.fetchCalendar(
          startDate: any(named: 'startDate'),
          endDate: any(named: 'endDate'),
          planIds: any(named: 'planIds'),
          idToken: any(named: 'idToken'),
        ),
      ).thenAnswer((_) async => const MealPlanCalendarData(entriesByDate: {}));

      await tester.pumpWidget(app);
      await tester.pumpAndSettle();

      expect(find.byType(RecipeGridItem), findsNWidgets(3));
    },
  );

  testWidgets('navigates to recipe detail when a recipe card is tapped', (
    tester,
  ) async {
    when(
      () => recipeRepository.fetchRecipes(any()),
    ).thenAnswer((_) async => [const Recipe(id: '1', name: 'Pizza')]);
    when(
      () => recipesCollectionRepository.fetchRecipesCollections(any()),
    ).thenAnswer((_) async => []);
    when(
      () => shoppingListRepository.fetchShoppingLists(any()),
    ).thenAnswer((_) async => []);
    when(
      () => mealPlanRepository.fetchMealPlans(idToken: any(named: 'idToken')),
    ).thenAnswer((_) async => []);
    when(
      () => mealPlanRepository.fetchCalendar(
        startDate: any(named: 'startDate'),
        endDate: any(named: 'endDate'),
        planIds: any(named: 'planIds'),
        idToken: any(named: 'idToken'),
      ),
    ).thenAnswer((_) async => const MealPlanCalendarData(entriesByDate: {}));

    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    await tester.tap(find.byType(RecipeGridItem).first);
    await tester.pumpAndSettle();

    expect(
      navSpy.pushed.any((r) => r.settings.name == AppRoute.recipeDetail.name),
      isTrue,
    );
  });
}
