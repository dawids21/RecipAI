import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import 'shopping_list_detail.dart';
import 'shopping_list_repository.dart';

class ShoppingListDetailService {
  final ShoppingListRepository _shoppingListRepository;
  final AuthService _authService;

  ShoppingListDetailService({
    required ShoppingListRepository shoppingListRepository,
    required AuthService authService,
  }) : _shoppingListRepository = shoppingListRepository,
       _authService = authService;

  final ValueNotifier<AsyncValue<ShoppingListDetail>> _shoppingListDetail =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<ShoppingListDetail>> get shoppingListDetail =>
      _shoppingListDetail;

  bool _isLoadShoppingListDetailRunning = false;

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
}
