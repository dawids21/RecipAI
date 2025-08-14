import 'package:flutter/material.dart';

import '../../core/theme.dart';
import 'recipe_detail.dart';

class IngredientInputWidget extends StatefulWidget {
  final ValueChanged<Ingredient?> onIngredientChanged;
  final Ingredient? initialIngredient;

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

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(
      text: widget.initialIngredient?.name ?? '',
    );
    _quantityController = TextEditingController(
      text: widget.initialIngredient?.quantity ?? '',
    );

    // Listen to changes and notify parent
    _nameController.addListener(_notifyParent);
    _quantityController.addListener(_notifyParent);
  }

  @override
  void dispose() {
    _nameController.dispose();
    _quantityController.dispose();
    super.dispose();
  }

  void _notifyParent() {
    final name = _nameController.text.trim();
    final quantity = _quantityController.text.trim();

    if (name.isNotEmpty) {
      final ingredient = Ingredient(
        name: name,
        quantity: quantity.isNotEmpty ? quantity : '',
        unit:
            null, // Unit will be parsed from quantity text in the parsing utility
      );
      widget.onIngredientChanged(ingredient);
    } else {
      widget.onIngredientChanged(null);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Row(
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
              // Quantity is optional, so no validation required
              return null;
            },
          ),
        ),
      ],
    );
  }
}
