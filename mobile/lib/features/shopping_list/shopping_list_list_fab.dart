import 'package:flutter/material.dart';

import '../limits/limits_service.dart';
import 'shopping_list_create_dialog.dart';
import 'shopping_list_list_service.dart';

class ShoppingListListFab extends StatefulWidget {
  final ShoppingListListService shoppingListListService;
  final LimitsService limitsService;

  const ShoppingListListFab({
    super.key,
    required this.shoppingListListService,
    required this.limitsService,
  });

  @override
  State<ShoppingListListFab> createState() => _ShoppingListListFabState();
}

class _ShoppingListListFabState extends State<ShoppingListListFab> {
  Future<void> _handleCreateList(BuildContext context) async {
    final name = await showDialog<String>(
      context: context,
      builder: (context) => ShoppingListCreateDialog(
        shoppingListListService: widget.shoppingListListService,
        limitsService: widget.limitsService,
      ),
    );

    if (name != null) {
      try {
        await widget.shoppingListListService.createShoppingList(name);
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
