import 'package:flutter/foundation.dart';

import '../../../core/async_value.dart';
import '../../auth/auth_service.dart';
import '../../limits/limit_balance.dart';
import '../../sharing/resource_permission.dart';
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

  final ValueNotifier<AsyncValue<List<ResourcePermission>>> _permissions =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<ResourcePermission>>> get permissions =>
      _permissions;

  final ValueNotifier<AsyncValue<LimitBalance>> _collectionBalance =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<LimitBalance>> get collectionBalance =>
      _collectionBalance;

  bool _isLoadRecipesCollectionsRunning = false;
  bool _isLoadPermissionsRunning = false;
  bool _isShareRunning = false;
  bool _isUnshareRunning = false;
  bool _isLoadCollectionBalanceRunning = false;

  Future<void> loadCollectionBalance() async {
    if (_isLoadCollectionBalanceRunning) return;
    _isLoadCollectionBalanceRunning = true;
    _collectionBalance.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _recipesCollectionRepository.fetchCollectionBalance(token);
    });
    _isLoadCollectionBalanceRunning = false;
  }

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

  Future<void> loadPermissions(String collectionId) async {
    if (_isLoadPermissionsRunning) return;
    _isLoadPermissionsRunning = true;

    _permissions.value = const AsyncValue.loading();

    _permissions.value = await AsyncValue.guardAsync(() async {
      final idToken = await _authService.idToken;
      return _recipesCollectionRepository.fetchPermissions(
        collectionId,
        idToken,
      );
    });
    _isLoadPermissionsRunning = false;
  }

  Future<void> shareCollection(String collectionId, String email) async {
    if (_isShareRunning) {
      throw Exception('Share already in progress');
    }
    _isShareRunning = true;

    final result = await AsyncValue.guardAsync(() async {
      final idToken = await _authService.idToken;
      return _recipesCollectionRepository.shareCollection(
        collectionId,
        email,
        idToken,
      );
    });

    if (result is AsyncData) {
      await loadPermissions(collectionId);
    }

    _isShareRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  Future<void> unshareCollection(String collectionId, String email) async {
    if (_isUnshareRunning) {
      throw Exception('Unshare already in progress');
    }
    _isUnshareRunning = true;

    final result = await AsyncValue.guardAsync(() async {
      final idToken = await _authService.idToken;
      return _recipesCollectionRepository.unshareCollection(
        collectionId,
        email,
        idToken,
      );
    });

    if (result is AsyncData) {
      await loadPermissions(collectionId);
      await loadRecipesCollections(); // Refresh list in case user unshared themselves
    }

    _isUnshareRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  void dispose() {
    _recipesCollections.dispose();
    _permissions.dispose();
    _collectionBalance.dispose();
  }
}
