import 'package:flutter/material.dart';

import '../../core/get_it.dart';
import '../../core/theme.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import 'shopping_list_detail_service.dart';

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

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Shopping List Details'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: ValueListenableBuilder(
        valueListenable: widget.shoppingListDetailService.shoppingListDetail,
        builder: (context, asyncValueDetail, child) {
          return asyncValueDetail.when(
            loading: () => const LoadingWidget(),
            error: (error) => ApiErrorWidget(
              errorMessage: 'Error: $error',
              onRetry: () => widget.shoppingListDetailService
                  .loadShoppingListDetail(widget.shoppingListId),
            ),
            data: (detail) => SingleChildScrollView(
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
      ),
    );
  }
}
