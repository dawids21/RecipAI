import 'package:flutter/foundation.dart';

import '../../../core/async_value.dart';
import '../../../core/widgets/sharing_dialog.dart';
import '../../auth/auth_service.dart';
import '../../limits/limit_usage.dart';
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

  final ValueNotifier<AsyncValue<List<SharedUser>>> _sharedUsers =
      ValueNotifier(const AsyncValue.loading());

  ValueNotifier<AsyncValue<List<SharedUser>>> get sharedUsers => _sharedUsers;

  final ValueNotifier<AsyncValue<LimitUsage>> _collectionUsage = ValueNotifier(
    const AsyncValue.loading(),
  );

  ValueListenable<AsyncValue<LimitUsage>> get collectionUsage =>
      _collectionUsage;

  bool _isLoadRecipesCollectionsRunning = false;
  bool _isLoadSharedUsersRunning = false;
  bool _isShareRunning = false;
  bool _isUnshareRunning = false;
  bool _isLoadCollectionUsageRunning = false;

  Future<void> loadCollectionUsage() async {
    if (_isLoadCollectionUsageRunning) return;
    _isLoadCollectionUsageRunning = true;
    _collectionUsage.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _recipesCollectionRepository.fetchCollectionUsage(token);
    });
    _isLoadCollectionUsageRunning = false;
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

  Future<void> loadSharedUsers(String collectionId) async {
    if (_isLoadSharedUsersRunning) return;
    _isLoadSharedUsersRunning = true;

    final idToken = await _authService.idToken;
    _sharedUsers.value = await AsyncValue.guardAsync(() async {
      final permissions = await _recipesCollectionRepository.fetchSharedUsers(
        collectionId,
        idToken,
      );
      final currentEmail = _authService.email;
      return permissions
          .map(
            (permission) => SharedUser(
              email: permission.email,
              role: permission.role.displayName,
              isCurrentUser: permission.email == currentEmail,
            ),
          )
          .toList();
    });
    _isLoadSharedUsersRunning = false;
  }

  Future<void> shareCollection(String collectionId, String email) async {
    if (_isShareRunning) {
      throw Exception('Share already in progress');
    }
    _isShareRunning = true;

    final idToken = await _authService.idToken;
    await _recipesCollectionRepository.shareCollection(
      collectionId,
      email,
      idToken,
    );
    await loadSharedUsers(collectionId);
    _isShareRunning = false;
  }

  Future<void> unshareCollection(String collectionId, String email) async {
    if (_isUnshareRunning) {
      throw Exception('Unshare already in progress');
    }
    _isUnshareRunning = true;

    final idToken = await _authService.idToken;
    await _recipesCollectionRepository.unshareCollection(
      collectionId,
      email,
      idToken,
    );
    await loadSharedUsers(collectionId);
    await loadRecipesCollections(); // Refresh list in case user unshared themselves
    _isUnshareRunning = false;
  }

  void dispose() {
    _recipesCollections.dispose();
    _sharedUsers.dispose();
    _collectionUsage.dispose();
  }
}
