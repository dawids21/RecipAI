import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme.dart';
import '../limits/limit_cap.dart';
import '../limits/limit_counter.dart';
import '../limits/limit_gate.dart';
import '../limits/limits_service.dart';
import 'shopping_list_list_service.dart';

class ShoppingListCreateDialog extends StatefulWidget {
  final ShoppingListListService shoppingListListService;
  final LimitsService limitsService;

  const ShoppingListCreateDialog({
    super.key,
    required this.shoppingListListService,
    required this.limitsService,
  });

  @override
  State<ShoppingListCreateDialog> createState() =>
      _ShoppingListCreateDialogState();
}

class _ShoppingListCreateDialogState extends State<ShoppingListCreateDialog> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
    widget.shoppingListListService.loadListUsage();
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
      title: const Text('Create Shopping List'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextField(
            controller: _controller,
            decoration: const InputDecoration(
              labelText: 'List Name',
              hintText: 'Enter list name',
            ),
            autofocus: true,
          ),
          LimitGate(
            usage: widget.shoppingListListService.listUsage,
            cap: widget.limitsService.capFor(LimitResources.shoppingList),
            builder: (context, usage, cap) {
              if (usage == null || cap == null) return const SizedBox.shrink();
              return Padding(
                padding: const EdgeInsets.only(top: AppSpacing.small),
                child: LimitCounter(
                  used: usage.used,
                  limit: cap.limit,
                  resetsInSeconds: usage.resetsInSeconds,
                  noun: 'lists',
                ),
              );
            },
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => context.pop(null),
          child: const Text('Cancel'),
        ),
        LimitGate(
          usage: widget.shoppingListListService.listUsage,
          cap: widget.limitsService.capFor(LimitResources.shoppingList),
          builder: (context, usage, cap) {
            final blocked =
                usage != null && cap != null && usage.used >= cap.limit;
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
