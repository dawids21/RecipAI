import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/get_it.dart';
import '../../core/routes.dart';
import '../../core/theme.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import '../../shared/user_role.dart';
import 'shopping_list_detail.dart';
import 'shopping_list_detail_service.dart';
import 'shopping_list_item_add_widget.dart';
import 'shopping_list_item_widget.dart';
import 'shopping_list_rename_dialog.dart';
import 'shopping_list_sharing_dialog.dart';

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

class _ShoppingListDetailScreenState extends State<ShoppingListDetailScreen>
    with WidgetsBindingObserver {
  int? _ephemeralItemIndex;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.shoppingListDetailService.loadShoppingListDetail(
      widget.shoppingListId,
    );
    widget.shoppingListDetailService.loadSharedUsers(widget.shoppingListId);
    // TODO(shopping-list-items): start keeping this list's items in sync while the
    // screen is open, surfacing conflicts and errors to the user.
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    if (getIt.isRegistered<ShoppingListDetailService>()) {
      getIt.resetLazySingleton<ShoppingListDetailService>();
    }
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
  }

  // TODO(shopping-list-items): handle sync conflicts (server/local divergence) and
  // sync errors here, e.g. by reloading the list and notifying the user.

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

  Future<void> _showSharingDialog() async {
    await showDialog<void>(
      context: context,
      builder: (context) => ShoppingListSharingDialog(
        shoppingListDetailService: widget.shoppingListDetailService,
      ),
    );
  }

  void _createEphemeralItemAfter(int index) {
    setState(() {
      _ephemeralItemIndex = index;
    });
  }

  void _saveEphemeralItem(ItemChanged result) {
    if (_ephemeralItemIndex == null) return;

    setState(() {
      _ephemeralItemIndex = null;
    });

    // TODO(shopping-list-items): persist the newly entered item to the shopping
    // list (the ephemeral row the user just filled in) and reflect it in the list.
  }

  void _discardEphemeralItem() {
    setState(() {
      _ephemeralItemIndex = null;
    });
  }

  Widget _buildDoneSectionHeader() {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(
        vertical: AppSpacing.medium,
        horizontal: AppSpacing.small,
      ),
      child: Row(
        children: [
          Expanded(child: Divider(color: theme.colorScheme.outlineVariant)),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.small),
            child: Text(
              'Done',
              style: theme.textTheme.labelLarge?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          Expanded(child: Divider(color: theme.colorScheme.outlineVariant)),
        ],
      ),
    );
  }

  void _onReorderUnchecked(
    ShoppingListDetail detail,
    int oldIndex,
    int newIndex,
  ) {
    if (newIndex > oldIndex) {
      newIndex -= 1;
    }

    if (oldIndex == newIndex) {
      return;
    }

    // TODO(shopping-list-items): persist the new position of the reordered
    // unchecked item.
  }

  void _onReorderChecked(
    ShoppingListDetail detail,
    int oldIndex,
    int newIndex,
  ) {
    if (newIndex > oldIndex) {
      newIndex -= 1;
    }

    if (oldIndex == newIndex) {
      return;
    }

    // TODO(shopping-list-items): persist the new position of the reordered
    // checked item.
  }

  ({List<Widget> unchecked, List<Widget> checked}) _buildSplitItemWidgets(
    ShoppingListDetail detail,
  ) {
    // Filter items into unchecked and checked lists
    // Keep the order from detail.items (array order) to preserve optimistic updates
    final uncheckedItems = detail.items.where((item) => !item.checked).toList();
    final checkedItems = detail.items.where((item) => item.checked).toList();

    final uncheckedWidgets = <Widget>[];
    final checkedWidgets = <Widget>[];

    // Build unchecked section widgets
    for (int i = 0; i < uncheckedItems.length; i++) {
      final item = uncheckedItems[i];

      uncheckedWidgets.add(
        ShoppingListItemWidget(
          key: ValueKey(item.id),
          item: ItemDisplayData(
            name: item.name,
            quantity: item.quantity,
            unit: item.unit,
            checked: item.checked,
          ),
          index: i,
          showDragHandle: true,
          onEdit: (result) {
            // TODO(shopping-list-items): persist edits to this item's name,
            // quantity, and unit.
          },
          onDelete: () {
            // TODO(shopping-list-items): remove this item from the shopping list.
          },
          onCheckChanged: (checked) {
            // TODO(shopping-list-items): persist this item's checked/unchecked
            // state.
          },
          onSubmitted: () => _createEphemeralItemAfter(i),
        ),
      );

      // Handle ephemeral item insertion in unchecked section only
      if (_ephemeralItemIndex == i) {
        uncheckedWidgets.add(
          ShoppingListItemWidget(
            key: const ValueKey('ephemeral-item'),
            item: const ItemDisplayData(
              name: '',
              quantity: null,
              unit: null,
              checked: false,
            ),
            index: i + 1,
            showDragHandle: true,
            autoFocus: true,
            allowEmpty: true,
            onEdit: (result) {
              if (result.name.isEmpty) {
                _discardEphemeralItem();
              } else {
                _saveEphemeralItem(result);
              }
            },
            onDelete: _discardEphemeralItem,
            onCheckChanged: (_) {},
            onSubmitted: () => _createEphemeralItemAfter(i + 1),
          ),
        );
      }
    }

    // Build checked section widgets
    for (int i = 0; i < checkedItems.length; i++) {
      final item = checkedItems[i];

      checkedWidgets.add(
        ShoppingListItemWidget(
          key: ValueKey(item.id),
          item: ItemDisplayData(
            name: item.name,
            quantity: item.quantity,
            unit: item.unit,
            checked: item.checked,
          ),
          index: i,
          showDragHandle: true,
          onEdit: (result) {
            // TODO(shopping-list-items): persist edits to this item's name,
            // quantity, and unit.
          },
          onDelete: () {
            // TODO(shopping-list-items): remove this item from the shopping list.
          },
          onCheckChanged: (checked) {
            // TODO(shopping-list-items): persist this item's checked/unchecked
            // state.
          },
          onSubmitted: () => _createEphemeralItemAfter(i),
        ),
      );
    }

    return (unchecked: uncheckedWidgets, checked: checkedWidgets);
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
            body: const SafeArea(top: false, child: LoadingWidget()),
          ),
          error: (error) => Scaffold(
            appBar: AppBar(
              title: const Text('Shopping List Details'),
              backgroundColor: theme.colorScheme.inversePrimary,
            ),
            body: SafeArea(
              top: false,
              child: ApiErrorWidget(
                errorMessage: 'Error: $error',
                onRetry: () => widget.shoppingListDetailService
                    .loadShoppingListDetail(widget.shoppingListId),
              ),
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

            menuItems.add(
              const PopupMenuItem<String>(
                value: 'share',
                child: Row(
                  children: [
                    Icon(Icons.share),
                    SizedBox(width: AppSpacing.small),
                    Text('Share List'),
                  ],
                ),
              ),
            );

            // TODO(shopping-list-items): add the bulk "delete all checked items"
            // and "uncheck all items" menu actions.

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
                      } else if (value == 'share') {
                        _showSharingDialog();
                      } else if (value == 'delete') {
                        _deleteShoppingList(detail.id, detail.name);
                      }
                      // TODO(shopping-list-items): handle the "delete all checked
                      // items" and "uncheck all items" bulk actions here.
                    },
                    itemBuilder: (context) => menuItems,
                  ),
                ],
              ),
              body: SafeArea(
                top: false,
                child: SingleChildScrollView(
                  padding: AppSpacing.screenPadding,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // TODO(shopping-list-items): show a sync-status indicator
                      // (e.g. "syncing…") above the title; currently static.
                      Text(
                        detail.name,
                        style: theme.textTheme.headlineMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.medium),
                      Card(
                        child: Padding(
                          padding: AppSpacing.cardMargin,
                          child: Builder(
                            builder: (context) {
                              final splitWidgets = _buildSplitItemWidgets(
                                detail,
                              );
                              return Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  // Unchecked section with animation
                                  AnimatedSize(
                                    duration: AppAnimations.sectionTransition,
                                    curve: AppAnimations.sectionCurve,
                                    alignment: Alignment.topCenter,
                                    child: splitWidgets.unchecked.isNotEmpty
                                        ? ReorderableListView(
                                            shrinkWrap: true,
                                            physics:
                                                const NeverScrollableScrollPhysics(),
                                            buildDefaultDragHandles: false,
                                            onReorder: (oldIndex, newIndex) =>
                                                _onReorderUnchecked(
                                                  detail,
                                                  oldIndex,
                                                  newIndex,
                                                ),
                                            proxyDecorator:
                                                (child, index, animation) {
                                                  return Material(
                                                    elevation: 8.0,
                                                    color: theme
                                                        .colorScheme
                                                        .surfaceContainerLow,
                                                    child: child,
                                                  );
                                                },
                                            children: splitWidgets.unchecked,
                                          )
                                        : const SizedBox.shrink(),
                                  ),

                                  // Add item widget
                                  ShoppingListItemAddWidget(
                                    key: const ValueKey('add-item'),
                                    onAdd: (result) {
                                      // TODO(shopping-list-items): add the newly
                                      // entered item to the shopping list.
                                    },
                                  ),

                                  // Done section header with animation
                                  AnimatedCrossFade(
                                    duration: AppAnimations.sectionTransition,
                                    crossFadeState:
                                        splitWidgets.checked.isNotEmpty
                                        ? CrossFadeState.showFirst
                                        : CrossFadeState.showSecond,
                                    firstChild: _buildDoneSectionHeader(),
                                    secondChild: const SizedBox.shrink(),
                                  ),

                                  // Done section with animation
                                  AnimatedSize(
                                    duration: AppAnimations.sectionTransition,
                                    curve: AppAnimations.sectionCurve,
                                    alignment: Alignment.topCenter,
                                    child: splitWidgets.checked.isNotEmpty
                                        ? ReorderableListView(
                                            shrinkWrap: true,
                                            physics:
                                                const NeverScrollableScrollPhysics(),
                                            buildDefaultDragHandles: false,
                                            onReorder: (oldIndex, newIndex) =>
                                                _onReorderChecked(
                                                  detail,
                                                  oldIndex,
                                                  newIndex,
                                                ),
                                            proxyDecorator:
                                                (child, index, animation) {
                                                  return Material(
                                                    elevation: 8.0,
                                                    color: theme
                                                        .colorScheme
                                                        .surfaceContainerLow,
                                                    child: child,
                                                  );
                                                },
                                            children: splitWidgets.checked,
                                          )
                                        : const SizedBox.shrink(),
                                  ),
                                ],
                              );
                            },
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        );
      },
    );
  }
}
