import 'package:flutter/material.dart';

import '../../core/theme.dart';
import 'shopping_list_item.dart';
import 'shopping_list_item_parser.dart';

class ItemChanged {
  final String name;
  final double? quantity;
  final String? unit;

  const ItemChanged({
    required this.name,
    required this.quantity,
    required this.unit,
  });
}

class ShoppingListItemWidget extends StatefulWidget {
  final ShoppingListItem item;
  final ValueChanged<ItemChanged> onEdit;
  final VoidCallback onDelete;
  final ValueChanged<bool>? onCheckChanged;
  final bool addMode;
  final bool showDragHandle;
  final int? index;

  const ShoppingListItemWidget({
    super.key,
    ShoppingListItem? item,
    required this.onEdit,
    required this.onDelete,
    this.onCheckChanged,
    this.addMode = false,
    this.showDragHandle = false,
    this.index,
  }) : item =
           item ??
           const ShoppingListItem(
             id: '',
             name: '',
             quantity: null,
             unit: null,
             checked: false,
             position: 0,
             version: 0,
           );

  @override
  State<ShoppingListItemWidget> createState() => _ShoppingListItemWidgetState();
}

class _ShoppingListItemWidgetState extends State<ShoppingListItemWidget> {
  late TextEditingController _controller;
  late FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: _formatItem());
    _focusNode = FocusNode();
    _focusNode.addListener(_onFocusChange);
  }

  @override
  void didUpdateWidget(ShoppingListItemWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Update controller text if item changed externally
    if (oldWidget.item != widget.item && !_focusNode.hasFocus) {
      _controller.text = _formatItem();
    }
  }

  String _formatItem() {
    if (widget.item.quantity != null) {
      final qtyStr = widget.item.quantity! == widget.item.quantity!.toInt()
          ? widget.item.quantity!.toInt().toString()
          : widget.item.quantity!.toString();
      if (widget.item.unit != null) {
        return '$qtyStr ${widget.item.unit} ${widget.item.name}';
      }
      return '$qtyStr ${widget.item.name}';
    }
    return widget.item.name;
  }

  void _onFocusChange() {
    if (!_focusNode.hasFocus) {
      _parseAndSave();
    }
  }

  void _parseAndSave() {
    final text = _controller.text.trim();
    if (text.isEmpty) {
      // If empty, restore original value (unless in clearAfterEdit mode)
      if (!widget.addMode) {
        _controller.text = _formatItem();
      }
      return;
    }

    final parsed = ShoppingListItemParser.parse(text);
    widget.onEdit(
      ItemChanged(
        name: parsed.name,
        quantity: parsed.quantity,
        unit: parsed.unit,
      ),
    );

    // Clear field after edit if requested
    if (widget.addMode) {
      _controller.clear();
    }
  }

  void _onSubmitted() {
    _parseAndSave();
    // Keep focus in add mode for quick consecutive entry
    if (widget.addMode) {
      _focusNode.requestFocus();
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final textStyle = widget.item.checked
        ? theme.textTheme.bodyLarge?.copyWith(
            decoration: TextDecoration.lineThrough,
            color: theme.textTheme.bodyLarge?.color?.withValues(alpha: 0.6),
          )
        : theme.textTheme.bodyLarge;

    final leadingWidget = widget.addMode
        ? const Icon(Icons.add)
        : Checkbox(
            value: widget.item.checked,
            onChanged: widget.onCheckChanged != null
                ? (bool? value) {
                    if (value != null) {
                      widget.onCheckChanged!(value);
                    }
                  }
                : null,
          );

    return Padding(
      padding: AppSpacing.smallVertical,
      child: Container(
        constraints: const BoxConstraints(minHeight: 48),
        child: Row(
          children: [
            if (widget.showDragHandle)
              ReorderableDragStartListener(
                index: widget.index!,
                child: Icon(
                  Icons.drag_handle,
                  color: theme.colorScheme.onSurface.withValues(alpha: 0.5),
                ),
              ),
            if (widget.showDragHandle) const SizedBox(width: AppSpacing.small),
            leadingWidget,
            const SizedBox(width: AppSpacing.small),
            Expanded(
              child: TextField(
                controller: _controller,
                focusNode: _focusNode,
                style: textStyle,
                maxLines: null,
                keyboardType: TextInputType.text,
                textInputAction: TextInputAction.done,
                decoration: InputDecoration(
                  border: InputBorder.none,
                  isDense: true,
                  contentPadding: EdgeInsets.zero,
                  hintText: widget.addMode ? "Add item..." : null,
                ),
                onSubmitted: (_) => _onSubmitted(),
                onTapOutside: (_) => _focusNode.unfocus(),
              ),
            ),
            if (!widget.addMode)
              IconButton(
                icon: const Icon(Icons.close),
                onPressed: widget.onDelete,
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
