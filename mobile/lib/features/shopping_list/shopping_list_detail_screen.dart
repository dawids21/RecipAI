import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/get_it.dart';
import '../../core/routes.dart';
import '../../core/theme.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import '../../shared/user_role.dart';
import 'local_shopping_list_item.dart';
import 'shopping_list_detail_service.dart';
import 'shopping_list_item_add_widget.dart';
import 'shopping_list_item_widget.dart';
import 'shopping_list_rename_dialog.dart';
import 'shopping_list_sharing_dialog.dart';
import 'shopping_list_sync_service.dart';

typedef ShoppingListItems = ({
  List<LocalShoppingListItem> active,
  List<LocalShoppingListItem> done,
});

class ShoppingListDetailScreen extends StatefulWidget {
  final String shoppingListId;

  /// The list name carried over from the previous screen (the list of lists),
  /// avoiding a network fetch just to render the header. Null when the route is
  /// entered without it (e.g. app-restart route restoration); a generic title
  /// is shown until known.
  final String? shoppingListName;
  final ShoppingListDetailService shoppingListDetailService;

  const ShoppingListDetailScreen({
    super.key,
    required this.shoppingListId,
    this.shoppingListName,
    required this.shoppingListDetailService,
  });

  @override
  State<ShoppingListDetailScreen> createState() =>
      _ShoppingListDetailScreenState();
}

class _ShoppingListDetailScreenState extends State<ShoppingListDetailScreen> {
  /// Index into the active items list after which the ephemeral input row is
  /// anchored, or `null` when no ephemeral row is open. The row is placed (both
  /// visually and position-wise) immediately after this item.
  int? _ephemeralAfterIndex;
  StreamSubscription<RejectionEvent>? _rejectionSubscription;

  /// The header list name. Seeded from the nav argument and kept live locally
  /// (e.g. updated after a successful rename), since it is no longer sourced
  /// from a detail fetch.
  late String? _listName = widget.shoppingListName;

  ShoppingListDetailService get service => widget.shoppingListDetailService;

  @override
  void initState() {
    super.initState();
    service.loadSharedUsers(widget.shoppingListId);
    service.openShoppingList(widget.shoppingListId);
    _rejectionSubscription = service.rejections.listen(_showRejectionToast);
  }

  @override
  void dispose() {
    _rejectionSubscription?.cancel();
    if (getIt.isRegistered<ShoppingListDetailService>()) {
      getIt.resetLazySingleton<ShoppingListDetailService>();
    }
    super.dispose();
  }

  void _showRejectionToast(RejectionEvent event) {
    if (!mounted) return;
    final message = switch (event.outcome) {
      RejectionOutcome.conflict =>
        '"${event.itemName}" was changed elsewhere and rolled back',
      RejectionOutcome.gone => '"${event.itemName}" no longer exists',
      RejectionOutcome.rejected => '"${event.itemName}" could not be synced',
    };
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Widget _buildSyncIndicator() {
    final theme = Theme.of(context);
    const size = 28.0;
    return ValueListenableBuilder(
      valueListenable: service.syncStatus,
      builder: (context, status, _) {
        final Widget indicator = switch (status) {
          SyncStatus.syncing => SizedBox(
            width: size,
            height: size,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          SyncStatus.notSyncing => Icon(
            Icons.check_circle,
            size: size,
            color: theme.colorScheme.primary,
          ),
          SyncStatus.offline => Icon(
            Icons.cloud_off,
            size: size,
            color: theme.colorScheme.onSurfaceVariant,
          ),
          SyncStatus.failure => Icon(
            Icons.priority_high,
            size: size,
            color: theme.colorScheme.error,
          ),
        };
        return SizedBox(width: size, height: size, child: indicator);
      },
    );
  }

  Widget _buildSyncFailureBanner() {
    final theme = Theme.of(context);
    return ValueListenableBuilder(
      valueListenable: service.syncStatus,
      builder: (context, status, _) {
        if (status != SyncStatus.failure) return const SizedBox.shrink();
        return Material(
          color: theme.colorScheme.errorContainer,
          child: SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.medium,
                vertical: AppSpacing.small,
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      'Some changes failed to sync',
                      style: TextStyle(
                        color: theme.colorScheme.onErrorContainer,
                      ),
                    ),
                  ),
                  TextButton(
                    onPressed: service.retrySync,
                    child: const Text('Retry'),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
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
      await service.renameShoppingList(listId, newName);

      if (mounted) {
        setState(() => _listName = newName);
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
      await service.deleteShoppingList(listId);

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
      builder: (context) =>
          ShoppingListSharingDialog(shoppingListDetailService: service),
    );
  }

  void _createEphemeralItemAfter(int index) {
    setState(() {
      _ephemeralAfterIndex = index;
    });
  }

  /// Saves the typed ephemeral item after the active item at [afterLocalId].
  /// Fire-and-forget: the store applies the create synchronously (in-memory +
  /// notify) before its DB write-through, so the new item is already visible by
  /// the next build — chaining (re-anchoring below) is handled synchronously by
  /// the row's `onSubmitted`.
  void _commitEphemeralItem(ItemChanged result, String afterLocalId) {
    service.addItem(result, afterLocalId: afterLocalId);
    setState(() {
      _ephemeralAfterIndex = null;
    });
  }

  void _discardEphemeralItem() {
    setState(() {
      _ephemeralAfterIndex = null;
    });
  }

  /// Splits the flat item list into active and done sections, each sorted by
  /// position. Pure presentation concern: the service exposes a single flat
  /// list and the screen derives the displayed sections.
  ShoppingListItems _splitSections(List<LocalShoppingListItem> flat) {
    final active = flat.where((i) => !i.checked).toList()
      ..sort((a, b) => a.compareTo(b));
    final done = flat.where((i) => i.checked).toList()
      ..sort((a, b) => a.compareTo(b));
    return (active: active, done: done);
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

  ({List<Widget> unchecked, List<Widget> checked}) _buildSplitItemWidgets(
    List<LocalShoppingListItem> activeItems,
    List<LocalShoppingListItem> doneItems,
  ) {
    final uncheckedWidgets = <Widget>[];
    final checkedWidgets = <Widget>[];

    for (int i = 0; i < activeItems.length; i++) {
      final item = activeItems[i];

      uncheckedWidgets.add(
        ShoppingListItemWidget(
          key: ValueKey(item.localId),
          item: ItemDisplayData(
            name: item.name,
            quantity: item.quantity,
            unit: item.unit,
            checked: item.checked,
          ),
          index: i,
          showDragHandle: true,
          onEdit: (result) => service.editItem(item.localId, result),
          onDelete: () => service.deleteItem(item.localId),
          onCheckChanged: (checked) =>
              service.toggleChecked(item.localId, checked),
          onSubmitted: () => _createEphemeralItemAfter(i),
        ),
      );

      if (_ephemeralAfterIndex == i) {
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
                _commitEphemeralItem(result, item.localId);
              }
            },
            onDelete: _discardEphemeralItem,
            onCheckChanged: (_) {},
            onSubmitted: () => _createEphemeralItemAfter(i + 1),
          ),
        );
      }
    }

    for (int i = 0; i < doneItems.length; i++) {
      final item = doneItems[i];

      checkedWidgets.add(
        ShoppingListItemWidget(
          key: ValueKey(item.localId),
          item: ItemDisplayData(
            name: item.name,
            quantity: item.quantity,
            unit: item.unit,
            checked: item.checked,
          ),
          index: i,
          showDragHandle: true,
          onEdit: (result) => service.editItem(item.localId, result),
          onDelete: () => service.deleteItem(item.localId),
          onCheckChanged: (checked) =>
              service.toggleChecked(item.localId, checked),
          onSubmitted: () {},
        ),
      );
    }

    return (unchecked: uncheckedWidgets, checked: checkedWidgets);
  }

  /// Builds the popup menu. Rebuilt each time the menu opens, so the owner-only
  /// "Delete List" item appears as soon as the role request
  /// ([ShoppingListDetailService.loadSharedUsers]) has resolved for an owner.
  List<PopupMenuItem<String>> _buildMenuItems() {
    final menuItems = <PopupMenuItem<String>>[
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
      const PopupMenuItem<String>(
        value: 'delete_checked',
        child: Row(
          children: [
            Icon(Icons.delete_sweep),
            SizedBox(width: AppSpacing.small),
            Text('Delete All Checked'),
          ],
        ),
      ),
      const PopupMenuItem<String>(
        value: 'uncheck_all',
        child: Row(
          children: [
            Icon(Icons.check_box_outline_blank),
            SizedBox(width: AppSpacing.small),
            Text('Uncheck All'),
          ],
        ),
      ),
    ];

    if (service.currentUserRole.value == UserRole.owner) {
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

    return menuItems;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final listName = _listName ?? 'Shopping List';

    return Scaffold(
      appBar: AppBar(
        title: const Text('Shopping List Details'),
        backgroundColor: theme.colorScheme.inversePrimary,
        actions: [
          PopupMenuButton<String>(
            onSelected: (value) {
              if (value == 'rename') {
                _renameShoppingList(widget.shoppingListId, listName);
              } else if (value == 'share') {
                _showSharingDialog();
              } else if (value == 'delete') {
                _deleteShoppingList(widget.shoppingListId, listName);
              } else if (value == 'delete_checked') {
                service.deleteAllChecked();
              } else if (value == 'uncheck_all') {
                service.uncheckAll();
              }
            },
            itemBuilder: (context) => _buildMenuItems(),
          ),
        ],
      ),
      bottomNavigationBar: _buildSyncFailureBanner(),
      body: SafeArea(
        top: false,
        child: SingleChildScrollView(
          padding: AppSpacing.screenPadding,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Expanded(
                    child: Text(
                      listName,
                      style: theme.textTheme.headlineMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.small),
                  _buildSyncIndicator(),
                ],
              ),
              const SizedBox(height: AppSpacing.medium),
              Card(
                child: Padding(
                  padding: AppSpacing.cardMargin,
                  child: ValueListenableBuilder(
                    valueListenable: service.items,
                    builder: (context, asyncItems, _) {
                      return asyncItems.when(
                        loading: () => const LoadingWidget(),
                        error: (e) => ApiErrorWidget(
                          errorMessage: 'Error loading items: $e',
                          onRetry: () =>
                              service.openShoppingList(widget.shoppingListId),
                        ),
                        data: (flatItems) {
                          final itemSections = _splitSections(flatItems);
                          final splitWidgets = _buildSplitItemWidgets(
                            itemSections.active,
                            itemSections.done,
                          );
                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
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
                                        onReorderItem: (oldIndex, newIndex) =>
                                            service.reorderItem(
                                              itemSections.active,
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

                              ShoppingListItemAddWidget(
                                key: const ValueKey('add-item'),
                                onAdd: (result) => service.addItem(result),
                              ),

                              AnimatedCrossFade(
                                duration: AppAnimations.sectionTransition,
                                crossFadeState: splitWidgets.checked.isNotEmpty
                                    ? CrossFadeState.showFirst
                                    : CrossFadeState.showSecond,
                                firstChild: _buildDoneSectionHeader(),
                                secondChild: const SizedBox.shrink(),
                              ),

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
                                        onReorderItem: (oldIndex, newIndex) =>
                                            service.reorderItem(
                                              itemSections.done,
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
  }
}
