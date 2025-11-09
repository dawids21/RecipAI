import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import 'optimistic_lock_exception.dart';
import 'shopping_list_detail.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_repository.dart';

class ShoppingListDetailService {
  final ShoppingListRepository _shoppingListRepository;
  final AuthService _authService;
  final ShoppingListListService _shoppingListListService;

  ShoppingListDetailService({
    required ShoppingListRepository shoppingListRepository,
    required AuthService authService,
    required ShoppingListListService shoppingListListService,
  }) : _shoppingListRepository = shoppingListRepository,
       _authService = authService,
       _shoppingListListService = shoppingListListService;

  final ValueNotifier<AsyncValue<ShoppingListDetail>> _shoppingListDetail =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<ShoppingListDetail>> get shoppingListDetail =>
      _shoppingListDetail;

  bool _isLoadShoppingListDetailRunning = false;
  bool _isRenameRunning = false;
  bool _isDeleteRunning = false;

  Future<void> loadShoppingListDetail(String id) async {
    if (_isLoadShoppingListDetailRunning) return;
    _isLoadShoppingListDetailRunning = true;

    _shoppingListDetail.value = const AsyncValue.loading();
    _shoppingListDetail.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _shoppingListRepository.fetchShoppingListDetail(id, token);
    });

    _isLoadShoppingListDetailRunning = false;
  }

  Future<void> renameShoppingList(String id, String newName) async {
    if (_isLoadShoppingListDetailRunning || _isRenameRunning) return;
    _isRenameRunning = true;

    try {
      final currentDetail = _shoppingListDetail.value.valueOrThrow;
      final version = currentDetail.version;
      final token = await _authService.idToken;
      await _shoppingListRepository.updateShoppingList(
        id,
        newName,
        version,
        token,
      );
      await loadShoppingListDetail(id);
      await _shoppingListListService.loadShoppingLists();
    } on OptimisticLockException {
      await loadShoppingListDetail(id);
      await _shoppingListListService.loadShoppingLists();
      rethrow;
    } finally {
      _isRenameRunning = false;
    }
  }

  Future<void> deleteShoppingList(String id) async {
    if (_isLoadShoppingListDetailRunning || _isDeleteRunning) return;
    _isDeleteRunning = true;

    try {
      final currentDetail = _shoppingListDetail.value.valueOrThrow;
      final version = currentDetail.version;
      final token = await _authService.idToken;
      await _shoppingListRepository.deleteShoppingList(id, version, token);
      await _shoppingListListService.loadShoppingLists();
    } on OptimisticLockException {
      await loadShoppingListDetail(id);
      await _shoppingListListService.loadShoppingLists();
      rethrow;
    } finally {
      _isDeleteRunning = false;
    }
  }
}
