import 'package:flutter/material.dart';

import 'shopping_list_list_service.dart';

class ShoppingListListFab extends StatefulWidget {
  final ShoppingListListService shoppingListListService;

  const ShoppingListListFab({super.key, required this.shoppingListListService});

  @override
  State<ShoppingListListFab> createState() => _ShoppingListListFabState();
}

class _ShoppingListListFabState extends State<ShoppingListListFab> {
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

  @override
  Widget build(BuildContext context) {
    return FloatingActionButton(
      onPressed: () => _handleCreateList(context),
      tooltip: 'Create Shopping List',
      child: const Icon(Icons.add),
    );
  }
}
