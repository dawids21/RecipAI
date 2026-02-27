import 'package:flutter/material.dart';

import '../../core/theme.dart';

class IngredientInput {
  final String name;
  final String quantityText;
  final String? comment;

  const IngredientInput({
    required this.name,
    required this.quantityText,
    this.comment,
  });
}

class IngredientInputWidget extends StatefulWidget {
  final ValueChanged<IngredientInput?> onIngredientChanged;
  final IngredientInput? initialIngredient;

  const IngredientInputWidget({
    super.key,
    required this.onIngredientChanged,
    this.initialIngredient,
  });

  @override
  State<IngredientInputWidget> createState() => _IngredientInputWidgetState();
}

class _IngredientInputWidgetState extends State<IngredientInputWidget> {
  late TextEditingController _nameController;
  late TextEditingController _quantityController;
  late TextEditingController _commentController;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(
      text: widget.initialIngredient?.name ?? '',
    );
    _quantityController = TextEditingController(
      text: widget.initialIngredient?.quantityText ?? '',
    );
    _commentController = TextEditingController(
      text: widget.initialIngredient?.comment ?? '',
    );

    _nameController.addListener(_notifyParent);
    _quantityController.addListener(_notifyParent);
    _commentController.addListener(_notifyParent);
  }

  @override
  void dispose() {
    _nameController.dispose();
    _quantityController.dispose();
    _commentController.dispose();
    super.dispose();
  }

  void _notifyParent() {
    final name = _nameController.text.trim();
    final quantity = _quantityController.text.trim();
    final comment = _commentController.text.trim();

    if (name.isNotEmpty) {
      final input = IngredientInput(
        name: name,
        quantityText: quantity,
        comment: comment.isNotEmpty ? comment : null,
      );
      widget.onIngredientChanged(input);
    } else {
      widget.onIngredientChanged(null);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              flex: 2,
              child: TextFormField(
                controller: _nameController,
                decoration: const InputDecoration(
                  labelText: 'Ingredient name',
                  hintText: 'e.g., flour, sugar',
                ),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return 'Ingredient name is required';
                  }
                  return null;
                },
              ),
            ),
            const SizedBox(width: AppSpacing.small),
            Expanded(
              flex: 1,
              child: TextFormField(
                controller: _quantityController,
                decoration: const InputDecoration(
                  labelText: 'Quantity',
                  hintText: '300g, 2 cups',
                ),
                validator: (value) {
                  return null;
                },
              ),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.extraSmall),
        TextFormField(
          controller: _commentController,
          decoration: const InputDecoration(
            labelText: 'Comment (optional)',
            hintText: 'e.g., to taste, fresh',
          ),
        ),
      ],
    );
  }
}
