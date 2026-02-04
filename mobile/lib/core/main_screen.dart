import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/auth_service.dart';
import '../features/planning/meal_plan_calendar_screen.dart';
import '../features/planning/meal_plan_calendar_service.dart';
import '../features/planning/meal_plan_drawer.dart';
import '../features/planning/meal_plan_list_service.dart';
import '../features/planning/meal_plan_visibility_service.dart';
import '../features/recipe/collection/recipes_collection_list_service.dart';
import '../features/recipe/recipe_list.dart';
import '../features/recipe/recipe_list_fab.dart';
import '../features/recipe/recipe_list_service.dart';
import '../features/shopping_list/shopping_list_list.dart';
import '../features/shopping_list/shopping_list_list_fab.dart';
import '../features/shopping_list/shopping_list_list_service.dart';
import 'feature_flags.dart';
import 'get_it.dart';
import 'routes.dart';
import 'theme.dart';

class MainScreen extends StatefulWidget {
  final RecipeListService recipeListService;
  final RecipesCollectionListService recipesCollectionListService;
  final ShoppingListListService shoppingListListService;
  final AuthService authService;
  final MealPlanCalendarService? mealPlanCalendarService;
  final MealPlanListService? mealPlanListService;
  final MealPlanVisibilityService? mealPlanVisibilityService;

  const MainScreen({
    super.key,
    required this.recipeListService,
    required this.recipesCollectionListService,
    required this.shoppingListListService,
    required this.authService,
    required this.mealPlanCalendarService,
    required this.mealPlanListService,
    required this.mealPlanVisibilityService,
  });

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int _selectedIndex = 0;

  @override
  void initState() {
    super.initState();
    widget.recipeListService.loadRecipes();
    widget.recipesCollectionListService.loadRecipesCollections();
    widget.shoppingListListService.loadShoppingLists();

    if (FeatureFlags.mealPlanningEnabled) {
      widget.mealPlanCalendarService?.loadCalendar();
      widget.mealPlanListService?.loadMealPlans();
    }
  }

  @override
  void dispose() {
    // Reset both services on dispose
    if (getIt.isRegistered<RecipeListService>()) {
      getIt.resetLazySingleton<RecipeListService>();
    }
    if (getIt.isRegistered<RecipesCollectionListService>()) {
      getIt.resetLazySingleton<RecipesCollectionListService>();
    }
    if (getIt.isRegistered<ShoppingListListService>()) {
      getIt.resetLazySingleton<ShoppingListListService>();
    }
    if (FeatureFlags.mealPlanningEnabled) {
      if (getIt.isRegistered<MealPlanCalendarService>()) {
        getIt.resetLazySingleton<MealPlanCalendarService>();
      }
      if (getIt.isRegistered<MealPlanListService>()) {
        getIt.resetLazySingleton<MealPlanListService>();
      }
      if (getIt.isRegistered<MealPlanListService>()) {
        getIt.resetLazySingleton<MealPlanVisibilityService>();
      }
    }
    super.dispose();
  }

  Future<void> _onLogoutTap(BuildContext context) async {
    final shouldLogout = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Logout'),
          content: const Text('Are you sure you want to logout?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('Logout'),
            ),
          ],
        );
      },
    );

    if (shouldLogout == true) {
      try {
        await widget.authService.signOut();
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('Failed to logout: $e')));
        }
      }
    }
  }

  void _onBottomNavTap(int index) {
    setState(() {
      _selectedIndex = index;
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('RecipAI'),
        backgroundColor: theme.colorScheme.inversePrimary,
        actions: [
          if (FeatureFlags.mealPlanningEnabled && _selectedIndex == 2)
            Builder(
              builder: (context) => IconButton(
                icon: const Icon(Icons.calendar_month),
                tooltip: 'Manage Plans',
                onPressed: () {
                  Scaffold.of(context).openEndDrawer();
                },
              ),
            ),
          PopupMenuButton<String>(
            onSelected: (value) {
              if (value == 'recipes_collections') {
                context.goNamed(AppRoute.recipesCollections.name);
              } else if (value == 'logout') {
                _onLogoutTap(context);
              }
            },
            itemBuilder: (context) {
              final menuItems = <PopupMenuItem<String>>[];

              menuItems.add(
                const PopupMenuItem<String>(
                  value: 'recipes_collections',
                  child: Row(
                    children: [
                      Icon(Icons.folder),
                      SizedBox(width: AppSpacing.small),
                      Text('Recipes collections'),
                    ],
                  ),
                ),
              );

              menuItems.add(
                const PopupMenuItem<String>(
                  value: 'logout',
                  child: Row(
                    children: [
                      Icon(Icons.logout),
                      SizedBox(width: AppSpacing.small),
                      Text('Logout'),
                    ],
                  ),
                ),
              );

              return menuItems;
            },
          ),
        ],
      ),
      endDrawer: FeatureFlags.mealPlanningEnabled && _selectedIndex == 2
          ? MealPlanDrawer(
              mealPlanListService: widget.mealPlanListService!,
              visibilityService: widget.mealPlanVisibilityService!,
            )
          : null,
      body: IndexedStack(
        index: _selectedIndex,
        children: [
          RecipeList(
            recipeListService: widget.recipeListService,
            recipesCollectionListService: widget.recipesCollectionListService,
            onRecipeTap: (context, recipe) {
              context.goNamed(
                AppRoute.recipeDetail.name,
                pathParameters: {'id': recipe.id},
              );
            },
          ),
          ShoppingListList(
            shoppingListListService: widget.shoppingListListService,
          ),
          if (FeatureFlags.mealPlanningEnabled &&
              widget.mealPlanCalendarService != null)
            MealPlanCalendarScreen(
              calendarService: widget.mealPlanCalendarService!,
            ),
        ],
      ),
      floatingActionButton: _selectedIndex == 0
          ? RecipeListFab(recipeListService: widget.recipeListService)
          : _selectedIndex == 1
          ? ShoppingListListFab(
              shoppingListListService: widget.shoppingListListService,
            )
          : FeatureFlags.mealPlanningEnabled && _selectedIndex == 2
          ? FloatingActionButton(
              onPressed: () {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Add meal feature coming soon')),
                );
              },
              tooltip: 'Add meal',
              child: const Icon(Icons.add),
            )
          : null,
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _selectedIndex,
        onTap: _onBottomNavTap,
        selectedItemColor: theme.colorScheme.primary,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.restaurant_menu),
            label: 'Recipes',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.shopping_cart),
            label: 'Shopping',
          ),
          if (FeatureFlags.mealPlanningEnabled)
            BottomNavigationBarItem(
              icon: Icon(Icons.calendar_today),
              label: 'Planning',
            ),
        ],
      ),
    );
  }
}
