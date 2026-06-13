import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme.dart';
import 'shopping_list_item_widget.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_review_item.dart';

// TODO(shopping-list-items): this widget needs a way to add the reviewed
// generated items into a chosen shopping list (see _onAddButtonPressed).

class ShoppingListReviewWidget extends StatefulWidget {
  final List<ShoppingListGeneratedItem> items;
  final ShoppingListListService shoppingListListService;

  const ShoppingListReviewWidget({
    super.key,
    required this.items,
    required this.shoppingListListService,
  });

  @override
  State<ShoppingListReviewWidget> createState() =>
      _ShoppingListReviewWidgetState();
}

class _ShoppingListReviewWidgetState extends State<ShoppingListReviewWidget> {
  late List<ShoppingListGeneratedItem> _items;
  late Set<ShoppingListGeneratedItem> _selectedItems;

  @override
  void initState() {
    super.initState();
    _items = List.of(widget.items)..sort((a, b) => a.name.compareTo(b.name));
    _selectedItems = Set.of(_items);
  }

  void _onItemToggled(ShoppingListGeneratedItem item, bool selected) {
    setState(() {
      if (selected) {
        _selectedItems.add(item);
      } else {
        _selectedItems.remove(item);
      }
    });
  }

  void _onToggleSelectAll() {
    setState(() {
      if (_selectedItems.length == _items.length) {
        _selectedItems.clear();
      } else {
        _selectedItems = Set.of(_items);
      }
    });
  }

  void _onItemEdited(ShoppingListGeneratedItem item, ItemChanged change) {
    setState(() {
      item.name = change.name;
      item.quantity = change.quantity;
      item.unit = change.unit;
    });
  }

  void _onReorder(int oldIndex, int newIndex) {
    setState(() {
      final item = _items.removeAt(oldIndex);
      _items.insert(newIndex, item);
    });
  }

  Future<String?> _showListSelectionDialog() async {
    final theme = Theme.of(context);

    return showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Select Shopping List'),
        content: ValueListenableBuilder(
          valueListenable: widget.shoppingListListService.shoppingLists,
          builder: (context, listsAsync, _) {
            return listsAsync.when(
              loading: () => const SizedBox(
                height: 100,
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (error) => Text(
                'Failed to load shopping lists',
                style: TextStyle(color: theme.colorScheme.error),
              ),
              data: (lists) {
                if (lists.isEmpty) {
                  return const Text(
                    'No shopping lists available. Create one first.',
                  );
                }

                return SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: lists
                        .map(
                          (list) => Card(
                            child: ListTile(
                              title: Text(
                                list.name,
                                style: theme.textTheme.titleMedium,
                              ),
                              trailing: const Icon(Icons.arrow_forward_ios),
                              onTap: () =>
                                  Navigator.of(dialogContext).pop(list.id),
                              contentPadding: AppSpacing.listTilePadding,
                            ),
                          ),
                        )
                        .toList(),
                  ),
                );
              },
            );
          },
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('Cancel'),
          ),
        ],
      ),
    );
  }

  Future<void> _onAddButtonPressed() async {
    final selectedItems = _items
        .where((item) => _selectedItems.contains(item))
        .toList();
    if (selectedItems.isEmpty) return;

    final selectedListId = await _showListSelectionDialog();
    if (selectedListId == null || !mounted) return;

    // TODO(shopping-list-items): add `selectedItems` to the shopping list
    // identified by `selectedListId`. Until then the success snackbar below is
    // shown without anything actually being persisted.

    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Added ${selectedItems.length} item(s) to shopping list'),
      ),
    );

    context.pop();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(
            AppSpacing.medium,
            AppSpacing.medium,
            AppSpacing.medium,
            0,
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Select items', style: theme.textTheme.titleMedium),
              TextButton(
                onPressed: _onToggleSelectAll,
                child: Text(
                  _selectedItems.length == _items.length
                      ? 'Deselect All'
                      : 'Select All',
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: _items.isEmpty
              ? Center(
                  child: Padding(
                    padding: AppSpacing.screenPadding,
                    child: Text(
                      'No items to add.',
                      style: theme.textTheme.bodyLarge,
                      textAlign: TextAlign.center,
                    ),
                  ),
                )
              : ReorderableListView(
                  padding: AppSpacing.screenPadding,
                  buildDefaultDragHandles: false,
                  onReorderItem: _onReorder,
                  proxyDecorator: (child, index, animation) => Material(
                    elevation: 8.0,
                    color: theme.colorScheme.surfaceContainerLow,
                    child: child,
                  ),
                  children: [
                    for (int i = 0; i < _items.length; i++)
                      ShoppingListItemWidget(
                        key: ValueKey(_items[i]),
                        item: ItemDisplayData(
                          name: _items[i].name,
                          quantity: _items[i].quantity,
                          unit: _items[i].unit,
                          checked: _selectedItems.contains(_items[i]),
                          strikethrough: !_selectedItems.contains(_items[i]),
                        ),
                        index: i,
                        showDragHandle: true,
                        showDeleteButton: false,
                        subtitle: _items[i].source,
                        onEdit: (change) => _onItemEdited(_items[i], change),
                        onCheckChanged: (checked) =>
                            _onItemToggled(_items[i], checked),
                      ),
                  ],
                ),
        ),
        Padding(
          padding: AppSpacing.screenPadding,
          child: ElevatedButton(
            onPressed: _selectedItems.isNotEmpty ? _onAddButtonPressed : null,
            child: const Text('Add to Shopping List'),
          ),
        ),
      ],
    );
  }
}
