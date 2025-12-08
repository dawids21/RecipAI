import 'package:flutter/material.dart';

import '../../core/theme.dart';
import 'shopping_list_item_parser.dart';
import 'shopping_list_item_widget.dart';

class ShoppingListItemAddWidget extends StatefulWidget {
  final ValueChanged<ItemChanged> onAdd;

  const ShoppingListItemAddWidget({super.key, required this.onAdd});

  @override
  State<ShoppingListItemAddWidget> createState() =>
      _ShoppingListItemAddWidgetState();
}

class _ShoppingListItemAddWidgetState extends State<ShoppingListItemAddWidget> {
  late TextEditingController _controller;
  late FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
    _focusNode = FocusNode();
    _focusNode.addListener(_onFocusChange);
  }

  void _onFocusChange() {
    if (!_focusNode.hasFocus) {
      _parseAndSave();
    }
  }

  void _parseAndSave() {
    final text = _controller.text.trim();
    if (text.isEmpty) {
      return;
    }

    final parsed = ShoppingListItemParser.parse(text);
    widget.onAdd(
      ItemChanged(
        name: parsed.name,
        quantity: parsed.quantity,
        unit: parsed.unit,
      ),
    );

    // Clear field after adding item
    _controller.clear();
  }

  void _onSubmitted() {
    _parseAndSave();
    // Keep focus for quick consecutive entry
    _focusNode.requestFocus();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: AppSpacing.smallVertical,
      child: Container(
        constraints: const BoxConstraints(minHeight: 48),
        child: Row(
          children: [
            const Icon(Icons.add),
            const SizedBox(width: AppSpacing.small),
            Expanded(
              child: TextField(
                controller: _controller,
                focusNode: _focusNode,
                style: theme.textTheme.bodyLarge,
                maxLines: null,
                keyboardType: TextInputType.text,
                textInputAction: TextInputAction.done,
                decoration: const InputDecoration(
                  border: InputBorder.none,
                  isDense: true,
                  contentPadding: EdgeInsets.zero,
                  hintText: "Add item...",
                ),
                onSubmitted: (_) => _onSubmitted(),
                onTapOutside: (_) => _focusNode.unfocus(),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _focusNode.removeListener(_onFocusChange);
    _focusNode.dispose();
    _controller.dispose();
    super.dispose();
  }
}
