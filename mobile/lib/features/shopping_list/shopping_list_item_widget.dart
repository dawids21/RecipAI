import 'package:flutter/material.dart';

import '../../core/theme.dart';
import 'shopping_list_item_parser.dart';

class ItemDisplayData {
  final String name;
  final double? quantity;
  final String? unit;
  final bool checked;
  final bool strikethrough;

  const ItemDisplayData({
    required this.name,
    this.quantity,
    this.unit,
    this.checked = false,
    bool? strikethrough,
  }) : strikethrough = strikethrough ?? checked;

  /// Compares the fields a remote change can affect — used by
  /// `didUpdateWidget` to distinguish a real content change from an ordinary
  /// rebuild (a fresh [ItemDisplayData] instance with the same values).
  bool hasSameContentAs(ItemDisplayData other) =>
      name == other.name &&
      quantity == other.quantity &&
      unit == other.unit &&
      checked == other.checked;
}

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
  final ItemDisplayData item;
  final ValueChanged<ItemChanged> onEdit;
  final VoidCallback? onDelete;
  final ValueChanged<bool> onCheckChanged;
  final bool showDragHandle;
  final bool showDeleteButton;
  final int? index;
  final VoidCallback? onSubmitted;
  final bool autoFocus;
  final bool allowEmpty;
  final String? subtitle;

  const ShoppingListItemWidget({
    super.key,
    required this.item,
    required this.onEdit,
    this.onDelete,
    required this.onCheckChanged,
    this.showDragHandle = false,
    this.showDeleteButton = true,
    this.index,
    this.onSubmitted,
    this.autoFocus = false,
    this.allowEmpty = false,
    this.subtitle,
  });

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

    // Add focus listener to save changes when field loses focus
    _focusNode.addListener(_onFocusChange);

    if (widget.autoFocus) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _focusNode.requestFocus();
      });
    }
  }

  void _onFocusChange() {
    if (!_focusNode.hasFocus) {
      // Field lost focus - save changes
      _parseAndSave();
    }
  }

  @override
  void didUpdateWidget(ShoppingListItemWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!widget.item.hasSameContentAs(oldWidget.item)) {
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

  void _parseAndSave() {
    final text = _controller.text.trim();
    if (text.isEmpty) {
      if (widget.allowEmpty) {
        widget.onEdit(const ItemChanged(name: '', quantity: null, unit: null));
        return;
      }
      _controller.text = _formatItem();
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

    if (widget.autoFocus) {
      _controller.clear();
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final textStyle = widget.item.strikethrough
        ? theme.textTheme.bodyLarge?.copyWith(
            decoration: TextDecoration.lineThrough,
            color: theme.textTheme.bodyLarge?.color?.withValues(alpha: 0.6),
          )
        : theme.textTheme.bodyLarge;

    final leadingWidget = Checkbox(
      value: widget.item.checked,
      onChanged: (bool? value) {
        if (value != null) {
          widget.onCheckChanged(value);
        }
      },
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
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextField(
                    controller: _controller,
                    focusNode: _focusNode,
                    style: textStyle,
                    maxLines: null,
                    keyboardType: TextInputType.text,
                    textInputAction: TextInputAction.done,
                    decoration: const InputDecoration(
                      border: InputBorder.none,
                      isDense: true,
                      contentPadding: EdgeInsets.zero,
                    ),
                    onSubmitted: (text) {
                      if (text.isNotEmpty) {
                        _parseAndSave();
                        widget.onSubmitted?.call();
                      }
                      // Re-focus this row's own (reused) node after every
                      // submit so the keyboard stays up across consecutive
                      // entries — the ephemeral row keeps the same State
                      // (constant ValueKey), so autoFocus only fires once.
                      _focusNode.requestFocus();
                    },
                    onTapOutside: (_) {
                      _focusNode.unfocus();
                    },
                  ),
                  if (widget.subtitle != null && widget.subtitle!.isNotEmpty)
                    Text(
                      widget.subtitle!,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                ],
              ),
            ),
            if (widget.showDeleteButton)
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
