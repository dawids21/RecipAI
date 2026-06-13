import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';

import '../../core/async_value.dart';
import '../../core/widgets/sharing_dialog.dart';
import '../auth/auth_service.dart';
import 'shopping_list_detail.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_repository.dart';

class ShoppingListDetailService {
  static final _log = Logger('recipai.shopping_list.detail');

  final ShoppingListRepository _shoppingListRepository;
  final AuthService _authService;
  final ShoppingListListService _shoppingListListService;

  // TODO(shopping-list-items): inject whatever dependency drives item syncing once
  // the new item write/sync path is designed.

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

  final ValueNotifier<AsyncValue<List<SharedUser>>> _sharedUsers =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<SharedUser>>> get sharedUsers => _sharedUsers;

  bool _isLoadShoppingListDetailRunning = false;
  bool _isRenameRunning = false;
  bool _isDeleteRunning = false;
  bool _isLoadSharedUsersRunning = false;
  bool _isShareShoppingListRunning = false;
  bool _isUnshareShoppingListRunning = false;

  Future<void> loadShoppingListDetail(String id) async {
    if (_isLoadShoppingListDetailRunning) return;
    _isLoadShoppingListDetailRunning = true;

    _log.fine('loadShoppingListDetail start (listId=$id)');
    _shoppingListDetail.value = const AsyncValue.loading();
    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _shoppingListRepository.fetchShoppingListDetail(id, token);
    });
    _shoppingListDetail.value = result;

    switch (result) {
      case AsyncData(:final value):
        _log.info(
          'loadShoppingListDetail loaded (listId=$id, items=${value.items.length})',
        );
      case AsyncError(:final error):
        _log.warning('loadShoppingListDetail failed (listId=$id)', error);
      case AsyncLoading():
        break;
    }

    _isLoadShoppingListDetailRunning = false;
  }

  Future<void> renameShoppingList(String id, String newName) async {
    if (_isRenameRunning) return;
    _isRenameRunning = true;

    _log.info('renameShoppingList start (listId=$id)');
    try {
      final token = await _authService.idToken;
      await _shoppingListRepository.updateShoppingList(id, newName, token);
      await loadShoppingListDetail(id);
      await _shoppingListListService.loadShoppingLists();
    } catch (e) {
      _log.warning('renameShoppingList failed (listId=$id)', e);
      rethrow;
    } finally {
      _isRenameRunning = false;
    }
  }

  Future<void> deleteShoppingList(String id) async {
    if (_isDeleteRunning) return;
    _isDeleteRunning = true;

    _log.info('deleteShoppingList start (listId=$id)');
    try {
      final token = await _authService.idToken;
      await _shoppingListRepository.deleteShoppingList(id, token);
      await _shoppingListListService.loadShoppingLists();
    } catch (e) {
      _log.warning('deleteShoppingList failed (listId=$id)', e);
      rethrow;
    } finally {
      _isDeleteRunning = false;
    }
  }

  // TODO(shopping-list-items): this service should expose the item write/sync API
  // for the detail screen — adding, editing, deleting, checking/unchecking, and
  // reordering items; bulk "delete all checked" / "uncheck all"

  Future<void> loadSharedUsers(String id) async {
    if (_isLoadSharedUsersRunning) return;
    _isLoadSharedUsersRunning = true;

    _sharedUsers.value = const AsyncValue.loading();

    _sharedUsers.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      final permissions = await _shoppingListRepository.fetchSharedUsers(
        id,
        token,
      );
      final currentUserEmail = _authService.email;
      return permissions.map((permission) {
        return SharedUser(
          email: permission.email,
          role: permission.role.displayName,
          isCurrentUser: permission.email == currentUserEmail,
        );
      }).toList();
    });

    _isLoadSharedUsersRunning = false;
  }

  Future<void> shareShoppingList(String email) async {
    if (_isShareShoppingListRunning) return;
    _isShareShoppingListRunning = true;

    // Get shoppingListId from current state
    final shoppingListDetail = _shoppingListDetail.value;
    if (shoppingListDetail is! AsyncData<ShoppingListDetail>) {
      _isShareShoppingListRunning = false;
      throw Exception('Shopping list not loaded');
    }
    final shoppingListId = shoppingListDetail.value.id;

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _shoppingListRepository.shareShoppingList(
        shoppingListId,
        email,
        token,
      );
    });

    if (result is AsyncData) {
      await loadSharedUsers(shoppingListId); // Refresh list on success
      await _shoppingListListService.loadShoppingLists();
    }

    _isShareShoppingListRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  Future<void> unshareShoppingList(String email) async {
    if (_isUnshareShoppingListRunning) return;
    _isUnshareShoppingListRunning = true;

    // Get shoppingListId from current state
    final shoppingListDetail = _shoppingListDetail.value;
    if (shoppingListDetail is! AsyncData<ShoppingListDetail>) {
      _isUnshareShoppingListRunning = false;
      throw Exception('Shopping list not loaded');
    }
    final shoppingListId = shoppingListDetail.value.id;

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _shoppingListRepository.unshareShoppingList(
        shoppingListId,
        email,
        token,
      );
    });

    if (result is AsyncData) {
      await loadSharedUsers(shoppingListId); // Refresh list on success
      await _shoppingListListService.loadShoppingLists();
    }

    _isUnshareShoppingListRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  void dispose() {
    _shoppingListDetail.dispose();
    _sharedUsers.dispose();
  }
}
