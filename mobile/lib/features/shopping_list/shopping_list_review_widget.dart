import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_review_item.dart';
import 'shopping_list_review_service.dart';
import 'shopping_list_sync_service.dart';

class ShoppingListReviewWidget extends StatefulWidget {
  final List<ShoppingListGeneratedItem> items;
  final ShoppingListListService shoppingListListService;
  final ShoppingListSyncService shoppingListSyncService;

  const ShoppingListReviewWidget({
    super.key,
    required this.items,
    required this.shoppingListListService,
    required this.shoppingListSyncService,
  });

  @override
  State<ShoppingListReviewWidget> createState() =>
      _ShoppingListReviewWidgetState();
}

class _ShoppingListReviewWidgetState extends State<ShoppingListReviewWidget> {
  late Set<int> _selectedIndices;
  late ShoppingListReviewService _reviewService;

  @override
  void initState() {
    super.initState();
    _selectedIndices = Set.from(List.generate(widget.items.length, (i) => i));
    _reviewService = ShoppingListReviewService(
      syncService: widget.shoppingListSyncService,
    );
  }

  void _onItemToggled(int index, bool selected) {
    setState(() {
      if (selected) {
        _selectedIndices.add(index);
      } else {
        _selectedIndices.remove(index);
      }
    });
  }

  void _onToggleSelectAll() {
    setState(() {
      if (_selectedIndices.length == widget.items.length) {
        _selectedIndices.clear();
      } else {
        _selectedIndices = Set.from(
          List.generate(widget.items.length, (i) => i),
        );
      }
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
    final selectedItems = _selectedIndices.map((i) => widget.items[i]).toList();
    if (selectedItems.isEmpty) return;

    final selectedListId = await _showListSelectionDialog();
    if (selectedListId == null || !mounted) return;

    _reviewService.addItemsToShoppingList(selectedListId, selectedItems);

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
                  _selectedIndices.length == widget.items.length
                      ? 'Deselect All'
                      : 'Select All',
                ),
              ),
            ],
          ),
        ),
        if (widget.items.isEmpty)
          Expanded(
            child: Center(
              child: Padding(
                padding: AppSpacing.screenPadding,
                child: Text(
                  'No items to add.',
                  style: theme.textTheme.bodyLarge,
                  textAlign: TextAlign.center,
                ),
              ),
            ),
          )
        else
          Expanded(
            child: ListView.builder(
              itemCount: widget.items.length,
              itemBuilder: (context, index) {
                final item = widget.items[index];
                final isSelected = _selectedIndices.contains(index);
                final subtitle = item.displaySubtitle;

                return CheckboxListTile(
                  value: isSelected,
                  onChanged: (checked) =>
                      _onItemToggled(index, checked == true),
                  title: Text(item.name),
                  subtitle: subtitle.isNotEmpty ? Text(subtitle) : null,
                );
              },
            ),
          ),
        Padding(
          padding: AppSpacing.screenPadding,
          child: ElevatedButton(
            onPressed: _selectedIndices.isNotEmpty ? _onAddButtonPressed : null,
            child: const Text('Add to Shopping List'),
          ),
        ),
      ],
    );
  }
}
