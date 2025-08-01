import 'package:flutter/material.dart';

/// Small bullet point icon for ingredient lists
class IngredientBullet extends StatelessWidget {
  const IngredientBullet({super.key});

  @override
  Widget build(BuildContext context) {
    return const Icon(Icons.circle, size: 8.0);
  }
}
