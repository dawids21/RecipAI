import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import 'shopping_list.dart';
import 'shopping_list_repository.dart';

class ShoppingListListService {
  final ShoppingListRepository _shoppingListRepository;
  final AuthService _authService;

  ShoppingListListService({
    required ShoppingListRepository shoppingListRepository,
    required AuthService authService,
  }) : _shoppingListRepository = shoppingListRepository,
       _authService = authService;

  final ValueNotifier<AsyncValue<List<ShoppingList>>> _shoppingLists =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<ShoppingList>>> get shoppingLists =>
      _shoppingLists;

  bool _isLoadShoppingListsRunning = false;
  bool _isCreateShoppingListRunning = false;

  Future<void> loadShoppingLists() async {
    if (_isLoadShoppingListsRunning) return;
    _isLoadShoppingListsRunning = true;
    _shoppingLists.value = const AsyncValue.loading();
    _shoppingLists.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _shoppingListRepository.fetchShoppingLists(token);
    });
    _isLoadShoppingListsRunning = false;
  }

  Future<void> createShoppingList(String name) async {
    if (_isCreateShoppingListRunning) return;
    _isCreateShoppingListRunning = true;

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _shoppingListRepository.createShoppingList(name, token);
    });

    if (result is AsyncData) {
      await loadShoppingLists();
    }

    _isCreateShoppingListRunning = false;

    if (result is AsyncError<ShoppingList>) {
      throw result.error;
    }
  }
}
