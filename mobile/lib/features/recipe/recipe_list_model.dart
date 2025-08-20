import 'package:flutter/material.dart';

import '../../core/api_service.dart';
import 'recipe.dart';

class RecipeListModel extends ChangeNotifier {
  late Future<List<Recipe>> _recipes = _initRecipes();

  Future<List<Recipe>> get recipes => _recipes;

  Future<List<Recipe>> _initRecipes() {
    return ApiService.fetchRecipes();
  }

  void refresh() {
    _recipes = ApiService.fetchRecipes();
    notifyListeners();
  }
}

class InheritedRecipeListModel extends InheritedNotifier<RecipeListModel> {
  const InheritedRecipeListModel({
    super.key,
    required super.notifier,
    required super.child,
  });

  static RecipeListModel of(BuildContext context) {
    final result = context
        .dependOnInheritedWidgetOfExactType<InheritedRecipeListModel>();
    assert(result != null, 'No InheritedRecipeListModel found in context');
    return result!.notifier!;
  }
}
