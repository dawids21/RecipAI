import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/auth_service.dart';
import '../features/recipe/collection/recipes_collection_list_service.dart';
import '../features/recipe/recipe_list.dart';
import '../features/recipe/recipe_list_fab.dart';
import '../features/recipe/recipe_list_service.dart';
import '../features/shopping_list/shopping_list_list.dart';
import '../features/shopping_list/shopping_list_list_fab.dart';
import '../features/shopping_list/shopping_list_list_service.dart';
import 'get_it.dart';
import 'routes.dart';
import 'theme.dart';

class MainScreen extends StatefulWidget {
  final RecipeListService recipeListService;
  final RecipesCollectionListService recipesCollectionListService;
  final ShoppingListListService shoppingListListService;
  final AuthService authService;

  const MainScreen({
    super.key,
    required this.recipeListService,
    required this.recipesCollectionListService,
    required this.shoppingListListService,
    required this.authService,
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
      body: IndexedStack(
        index: _selectedIndex,
        children: [
          RecipeList(
            recipeListService: widget.recipeListService,
            recipesCollectionListService: widget.recipesCollectionListService,
          ),
          ShoppingListList(
            shoppingListListService: widget.shoppingListListService,
          ),
        ],
      ),
      floatingActionButton: _selectedIndex == 0
          ? RecipeListFab(recipeListService: widget.recipeListService)
          : ShoppingListListFab(
              shoppingListListService: widget.shoppingListListService,
            ),
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
        ],
      ),
    );
  }
}
