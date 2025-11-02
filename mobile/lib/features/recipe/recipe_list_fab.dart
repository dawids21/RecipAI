import 'package:flutter/material.dart';
import 'package:flutter_speed_dial/flutter_speed_dial.dart';
import 'package:go_router/go_router.dart';

import '../../core/routes.dart';
import '../../core/theme.dart';
import '../extraction/extraction_dialog.dart';
import 'recipe_list_service.dart';

class RecipeListFab extends StatelessWidget {
  final RecipeListService recipeListService;

  const RecipeListFab({super.key, required this.recipeListService});

  Future<void> _onExtractionTap(BuildContext context) async {
    final method = await showDialog<ExtractionMethod>(
      context: context,
      builder: (context) => const ExtractionDialog(),
    );

    if (method == ExtractionMethod.url) {
      if (context.mounted) {
        context.goNamed(AppRoute.urlExtraction.name);
      }
    } else if (method == ExtractionMethod.image) {
      if (context.mounted) {
        context.goNamed(AppRoute.imageExtraction.name);
      }
    }
  }

  void _onCreateTap(BuildContext context) {
    context.goNamed(AppRoute.recipeCreate.name);
  }

  @override
  Widget build(BuildContext context) {
    return SpeedDial(
      spaceBetweenChildren: AppSpacing.medium,
      icon: Icons.add,
      activeIcon: Icons.menu,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(16.0)),
      ),
      children: [
        SpeedDialChild(
          child: const Icon(Icons.download),
          label: 'Extract Recipe',
          onTap: () => _onExtractionTap(context),
        ),
        SpeedDialChild(
          child: const Icon(Icons.edit),
          label: 'Create Recipe',
          onTap: () => _onCreateTap(context),
        ),
      ],
    );
  }
}
