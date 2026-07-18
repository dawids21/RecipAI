import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:recipai_mobile/core/theme.dart';

import '../../core/routes.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import 'shopping_list.dart';
import 'shopping_list_list_service.dart';

class ShoppingListList extends StatefulWidget {
  final ShoppingListListService shoppingListListService;

  const ShoppingListList({super.key, required this.shoppingListListService});

  @override
  State<ShoppingListList> createState() => _ShoppingListListState();
}

class _ShoppingListListState extends State<ShoppingListList> {
  Future<void> _handleRefresh() async {
    await widget.shoppingListListService.loadShoppingLists();
  }

  void _onShoppingListTap(BuildContext context, ShoppingList shoppingList) {
    context.goNamed(
      AppRoute.shoppingListDetail.name,
      pathParameters: {'id': shoppingList.id},
      extra: shoppingList.name,
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return RefreshIndicator(
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
                  return Card(
                    margin: AppSpacing.cardMargin,
                    child: ListTile(
                      title: Text(
                        shoppingList.name,
                        style: theme.textTheme.titleMedium,
                      ),
                      trailing: const Icon(Icons.arrow_forward_ios),
                      onTap: () => _onShoppingListTap(context, shoppingList),
                      contentPadding: AppSpacing.listTilePadding,
                    ),
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
    );
  }
}
