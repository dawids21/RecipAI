import 'package:flutter/material.dart';

import '../../core/theme.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import '../shopping_list/shopping_list_list_service.dart';
import '../shopping_list/shopping_list_review_widget.dart';
import '../shopping_list/shopping_list_sync_service.dart';
import 'shopping_list_generation_service.dart';

class ShoppingListGenerationReviewStep extends StatelessWidget {
  final ShoppingListGenerationService generationService;
  final ShoppingListListService shoppingListListService;
  final ShoppingListSyncService shoppingListSyncService;
  final VoidCallback onRetry;

  const ShoppingListGenerationReviewStep({
    super.key,
    required this.generationService,
    required this.shoppingListListService,
    required this.shoppingListSyncService,
    required this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return ValueListenableBuilder(
      valueListenable: generationService.generatedItems,
      builder: (context, itemsAsync, _) {
        return itemsAsync.when(
          loading: () => const LoadingWidget(),
          error: (error) =>
              ApiErrorWidget(errorMessage: error.toString(), onRetry: onRetry),
          data: (data) => Column(
            children: [
              if (data.warnings.isNotEmpty)
                _buildWarningsBanner(theme, data.warnings),
              Expanded(
                child: ShoppingListReviewWidget(
                  items: data.items,
                  shoppingListListService: shoppingListListService,
                  shoppingListSyncService: shoppingListSyncService,
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildWarningsBanner(ThemeData theme, List<String> warnings) {
    return Container(
      margin: AppSpacing.cardMargin,
      padding: AppSpacing.listTilePadding,
      decoration: BoxDecoration(
        color: theme.colorScheme.errorContainer,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                Icons.warning_amber_rounded,
                color: theme.colorScheme.onErrorContainer,
                size: 18,
              ),
              const SizedBox(width: AppSpacing.extraSmall),
              Text(
                'Some meals were skipped:',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onErrorContainer,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.extraSmall),
          ...warnings.map(
            (w) => Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(
                '• $w',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onErrorContainer,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
