import 'package:flutter/material.dart';

import '../../core/api_service.dart';
import 'recipe.dart';

class RecipeListModel extends ChangeNotifier {
  final ApiService _apiService;
  late Future<List<Recipe>> _recipes = _initRecipes();

  RecipeListModel(this._apiService);

  Future<List<Recipe>> get recipes => _recipes;

  Future<List<Recipe>> _initRecipes() {
    return _apiService.fetchRecipes();
  }

  void refresh() {
    _recipes = _apiService.fetchRecipes();
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
