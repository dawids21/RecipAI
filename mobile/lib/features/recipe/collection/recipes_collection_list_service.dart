import 'package:flutter/foundation.dart';

import '../../../core/async_value.dart';
import '../../auth/auth_service.dart';
import 'recipes_collection.dart';
import 'recipes_collection_repository.dart';

class RecipesCollectionListService {
  final RecipesCollectionRepository _recipesCollectionRepository;
  final AuthService _authService;

  RecipesCollectionListService({
    required RecipesCollectionRepository recipesCollectionRepository,
    required AuthService authService,
  }) : _recipesCollectionRepository = recipesCollectionRepository,
       _authService = authService;

  final ValueNotifier<AsyncValue<List<RecipesCollection>>> _recipesCollections =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<RecipesCollection>>> get recipesCollections =>
      _recipesCollections;

  bool _isLoadRecipesCollectionsRunning = false;

  Future<void> loadRecipesCollections() async {
    if (_isLoadRecipesCollectionsRunning) return;
    _isLoadRecipesCollectionsRunning = true;
    _recipesCollections.value = const AsyncValue.loading();
    _recipesCollections.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _recipesCollectionRepository.fetchRecipesCollections(token);
    });
    _isLoadRecipesCollectionsRunning = false;
  }

  Future<void> createRecipesCollection(String name) async {
    final token = await _authService.idToken;
    await _recipesCollectionRepository.createRecipesCollection(name, token);
    await loadRecipesCollections();
  }

  Future<void> updateRecipesCollection(String id, String name) async {
    final token = await _authService.idToken;
    await _recipesCollectionRepository.updateRecipesCollection(id, name, token);
    await loadRecipesCollections();
  }

  Future<void> deleteRecipesCollection(String id) async {
    final token = await _authService.idToken;
    await _recipesCollectionRepository.deleteRecipesCollection(id, token);
    await loadRecipesCollections();
  }
}
