import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme.dart';
import '../../limits/limit_counter.dart';
import '../../limits/limit_gate.dart';
import '../../limits/limit_quota.dart';
import '../../limits/limits_service.dart';
import 'recipes_collection_list_service.dart';

class RecipesCollectionCreateDialog extends StatefulWidget {
  final RecipesCollectionListService recipesCollectionListService;
  final LimitsService limitsService;

  const RecipesCollectionCreateDialog({
    super.key,
    required this.recipesCollectionListService,
    required this.limitsService,
  });

  @override
  State<RecipesCollectionCreateDialog> createState() =>
      _RecipesCollectionCreateDialogState();
}

class _RecipesCollectionCreateDialogState
    extends State<RecipesCollectionCreateDialog> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
    widget.recipesCollectionListService.loadCollectionBalance();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _handleCreate() {
    final newName = _controller.text.trim();
    if (newName.isNotEmpty) {
      context.pop(newName);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Create recipes collection'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextField(
            controller: _controller,
            decoration: const InputDecoration(
              labelText: 'Recipes collection name',
            ),
            autofocus: true,
          ),
          LimitGate(
            balance: widget.recipesCollectionListService.collectionBalance,
            quota: widget.limitsService.quotaFor(
              LimitResources.recipesCollection,
            ),
            builder: (context, balance, quota) {
              if (balance == null || quota == null) {
                return const SizedBox.shrink();
              }
              return Padding(
                padding: const EdgeInsets.only(top: AppSpacing.small),
                child: LimitCounter(
                  used: balance.used,
                  limit: quota.limit,
                  resetsInSeconds: balance.resetsInSeconds,
                  noun: 'collections',
                ),
              );
            },
          ),
        ],
      ),
      actions: [
        TextButton(
          child: const Text('Cancel'),
          onPressed: () => context.pop(null),
        ),
        LimitGate(
          balance: widget.recipesCollectionListService.collectionBalance,
          quota: widget.limitsService.quotaFor(
            LimitResources.recipesCollection,
          ),
          builder: (context, balance, quota) {
            final blocked =
                balance != null && quota != null && balance.used >= quota.limit;
            return TextButton(
              onPressed: blocked ? null : _handleCreate,
              child: const Text('Create'),
            );
          },
        ),
      ],
    );
  }
}
