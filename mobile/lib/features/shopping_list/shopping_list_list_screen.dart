import 'package:flutter/material.dart';
import 'package:recipai_mobile/core/get_it.dart';

import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import '../auth/auth_service.dart';
import 'shopping_list.dart';
import 'shopping_list_list_service.dart';

class ShoppingListListScreen extends StatefulWidget {
  final ShoppingListListService shoppingListListService;
  final AuthService authService;

  const ShoppingListListScreen({
    super.key,
    required this.shoppingListListService,
    required this.authService,
  });

  @override
  State<ShoppingListListScreen> createState() => _ShoppingListListScreenState();
}

class _ShoppingListListScreenState extends State<ShoppingListListScreen> {
  @override
  void initState() {
    super.initState();
    widget.shoppingListListService.loadShoppingLists();
  }

  @override
  void dispose() {
    if (getIt.isRegistered<ShoppingListListService>()) {
      getIt.resetLazySingleton<ShoppingListListService>();
    }
    super.dispose();
  }

  Future<void> _handleCreateList(BuildContext context) async {
    final nameController = TextEditingController();

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Create Shopping List'),
          content: TextField(
            controller: nameController,
            decoration: const InputDecoration(
              labelText: 'List Name',
              hintText: 'Enter list name',
            ),
            autofocus: true,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('Create'),
            ),
          ],
        );
      },
    );

    if (confirmed == true && nameController.text.isNotEmpty) {
      try {
        await widget.shoppingListListService.createShoppingList(
          nameController.text.trim(),
        );
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Shopping list created')),
          );
        }
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('Failed to create list: $e')));
        }
      }
    }
  }

  Future<void> _handleRefresh() async {
    await widget.shoppingListListService.loadShoppingLists();
  }

  void _onShoppingListTap(BuildContext context, ShoppingList shoppingList) {}

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

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('RecipAI'),
        backgroundColor: theme.colorScheme.inversePrimary,
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () => _onLogoutTap(context),
            tooltip: 'Logout',
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _handleRefresh,
        child: ValueListenableBuilder(
          valueListenable: widget.shoppingListListService.shoppingLists,
          builder: (context, asyncValueShoppingLists, child) {
            return asyncValueShoppingLists.when(
              loading: () => const LoadingWidget(),
              data: (shoppingLists) {
                if (shoppingLists.isEmpty) {
                  return Center(
                    child: Text(
                      'No shopping lists found',
                      style: theme.textTheme.labelMedium,
                    ),
                  );
                }
                return ListView.builder(
                  itemCount: shoppingLists.length,
                  itemBuilder: (context, index) {
                    final shoppingList = shoppingLists[index];
                    return ListTile(
                      title: Text(shoppingList.name),
                      onTap: () => _onShoppingListTap(context, shoppingList),
                    );
                  },
                );
              },
              error: (error) => ApiErrorWidget(
                errorMessage: 'Error: $error',
                onRetry: () {
                  widget.shoppingListListService.loadShoppingLists();
                },
              ),
            );
          },
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _handleCreateList(context),
        tooltip: 'Create Shopping List',
        child: const Icon(Icons.add),
      ),
    );
  }
}
