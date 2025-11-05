import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/get_it.dart';
import '../../core/routes.dart';
import '../../core/theme.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import '../../shared/user_role.dart';
import 'shopping_list_detail_service.dart';
import 'shopping_list_rename_dialog.dart';

class ShoppingListDetailScreen extends StatefulWidget {
  final String shoppingListId;
  final ShoppingListDetailService shoppingListDetailService;

  const ShoppingListDetailScreen({
    super.key,
    required this.shoppingListId,
    required this.shoppingListDetailService,
  });

  @override
  State<ShoppingListDetailScreen> createState() =>
      _ShoppingListDetailScreenState();
}

class _ShoppingListDetailScreenState extends State<ShoppingListDetailScreen> {
  @override
  void initState() {
    super.initState();
    widget.shoppingListDetailService.loadShoppingListDetail(
      widget.shoppingListId,
    );
  }

  @override
  void dispose() {
    if (getIt.isRegistered<ShoppingListDetailService>()) {
      getIt.resetLazySingleton<ShoppingListDetailService>();
    }
    super.dispose();
  }

  Future<String?> _showRenameDialog(String currentName) async {
    return showDialog<String>(
      context: context,
      builder: (context) => ShoppingListRenameDialog(currentName: currentName),
    );
  }

  Future<bool> _showDeleteConfirmation(String listName) async {
    final theme = Theme.of(context);

    final shouldDelete = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Shopping List'),
        content: Text(
          'Are you sure you want to delete \'$listName\'? This action cannot be undone.',
        ),
        actions: [
          TextButton(
            child: const Text('Cancel'),
            onPressed: () => context.pop(false),
          ),
          TextButton(
            style: TextButton.styleFrom(
              foregroundColor: theme.colorScheme.error,
            ),
            child: const Text('Delete'),
            onPressed: () => context.pop(true),
          ),
        ],
      ),
    );

    return shouldDelete ?? false;
  }

  Future<void> _renameShoppingList(String listId, String currentName) async {
    final newName = await _showRenameDialog(currentName);
    if (newName == null) return;

    try {
      await widget.shoppingListDetailService.renameShoppingList(
        listId,
        newName,
      );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Shopping list renamed successfully')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Failed to rename: $e')));
      }
    }
  }

  Future<void> _deleteShoppingList(String listId, String listName) async {
    final shouldDelete = await _showDeleteConfirmation(listName);
    if (!shouldDelete) return;

    try {
      await widget.shoppingListDetailService.deleteShoppingList(listId);

      if (mounted) {
        context.goNamed(AppRoute.main.name);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Failed to delete: $e')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return ValueListenableBuilder(
      valueListenable: widget.shoppingListDetailService.shoppingListDetail,
      builder: (context, asyncValueDetail, child) {
        return asyncValueDetail.when(
          loading: () => Scaffold(
            appBar: AppBar(
              title: const Text('Shopping List Details'),
              backgroundColor: theme.colorScheme.inversePrimary,
            ),
            body: const LoadingWidget(),
          ),
          error: (error) => Scaffold(
            appBar: AppBar(
              title: const Text('Shopping List Details'),
              backgroundColor: theme.colorScheme.inversePrimary,
            ),
            body: ApiErrorWidget(
              errorMessage: 'Error: $error',
              onRetry: () => widget.shoppingListDetailService
                  .loadShoppingListDetail(widget.shoppingListId),
            ),
          ),
          data: (detail) {
            final menuItems = <PopupMenuItem<String>>[];

            menuItems.add(
              const PopupMenuItem<String>(
                value: 'rename',
                child: Row(
                  children: [
                    Icon(Icons.edit),
                    SizedBox(width: AppSpacing.small),
                    Text('Rename List'),
                  ],
                ),
              ),
            );

            if (detail.role == UserRole.owner) {
              menuItems.add(
                const PopupMenuItem<String>(
                  value: 'delete',
                  child: Row(
                    children: [
                      Icon(Icons.delete),
                      SizedBox(width: AppSpacing.small),
                      Text('Delete List'),
                    ],
                  ),
                ),
              );
            }

            return Scaffold(
              appBar: AppBar(
                title: const Text('Shopping List Details'),
                backgroundColor: theme.colorScheme.inversePrimary,
                actions: [
                  PopupMenuButton<String>(
                    onSelected: (value) {
                      if (value == 'rename') {
                        _renameShoppingList(detail.id, detail.name);
                      } else if (value == 'delete') {
                        _deleteShoppingList(detail.id, detail.name);
                      }
                    },
                    itemBuilder: (context) => menuItems,
                  ),
                ],
              ),
              body: SingleChildScrollView(
                padding: AppSpacing.screenPadding,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      detail.name,
                      style: theme.textTheme.headlineMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: AppSpacing.medium),
                    if (detail.items.isEmpty)
                      Text(
                        'No items in this list',
                        style: theme.textTheme.bodyMedium,
                      )
                    else
                      Card(
                        child: Padding(
                          padding: AppSpacing.cardMargin,
                          child: Column(
                            children: detail.items.map((item) {
                              String itemText = item.name;
                              if (item.quantity != null) {
                                final quantity = item.quantity!;
                                final quantityStr = quantity == quantity.toInt()
                                    ? quantity.toInt().toString()
                                    : quantity.toString();

                                if (item.unit != null) {
                                  itemText =
                                      '$quantityStr ${item.unit} ${item.name}';
                                } else {
                                  itemText = '$quantityStr ${item.name}';
                                }
                              }

                              return Padding(
                                padding: AppSpacing.smallVertical,
                                child: Row(
                                  children: [
                                    Icon(
                                      item.checked
                                          ? Icons.check_box
                                          : Icons.check_box_outline_blank,
                                    ),
                                    const SizedBox(width: AppSpacing.small),
                                    Expanded(child: Text(itemText)),
                                  ],
                                ),
                              );
                            }).toList(),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }
}
