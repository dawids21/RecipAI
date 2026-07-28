import 'dart:async';

import 'package:recipai_mobile/core/get_it.dart';

import '../../core/scheduler.dart';
import '../auth/auth_service.dart';
import 'shopping_list_detail_service.dart';
import 'shopping_list_item_import_service.dart';
import 'shopping_list_item_repository.dart';
import 'shopping_list_item_store_service.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_repository.dart';
import 'shopping_list_sync_service.dart';

/// Registers the shopping-list feature. The database-backed
/// [ShoppingListItemStoreService] is opened by the caller (see `main()`) and
/// injected here so setup stays synchronous; tests inject a mock instead.
void setupShoppingList({
  required ShoppingListItemStoreService store,
  ShoppingListItemRepository? itemRepository,
  ShoppingListRepository? shoppingListRepository,
  Scheduler? scheduler,
}) {
  final repository = shoppingListRepository ?? ShoppingListRepository();
  getIt.registerSingleton<ShoppingListRepository>(repository);

  getIt.registerSingleton<ShoppingListItemStoreService>(
    store,
    dispose: (s) => s.dispose(),
  );

  getIt.registerSingleton<ShoppingListItemRepository>(
    itemRepository ?? ShoppingListItemRepository(),
    dispose: (r) => r.dispose(),
  );

  getIt.registerSingleton<ShoppingListSyncService>(
    ShoppingListSyncService(
      itemRepository: getIt<ShoppingListItemRepository>(),
      store: getIt<ShoppingListItemStoreService>(),
      authService: getIt<AuthService>(),
      scheduler: scheduler ?? RealScheduler(),
    ),
    dispose: (s) => s.dispose(),
  );
  unawaited(getIt<ShoppingListSyncService>().start());

  getIt.registerLazySingleton(
    () => ShoppingListItemImportService(
      store: getIt<ShoppingListItemStoreService>(),
      syncService: getIt<ShoppingListSyncService>(),
    ),
  );

  getIt.registerLazySingleton(
    () => ShoppingListListService(
      shoppingListRepository: getIt<ShoppingListRepository>(),
      authService: getIt<AuthService>(),
    ),
  );
  getIt.registerLazySingleton(
    () => ShoppingListDetailService(
      shoppingListRepository: getIt<ShoppingListRepository>(),
      authService: getIt<AuthService>(),
      shoppingListListService: getIt<ShoppingListListService>(),
      store: getIt<ShoppingListItemStoreService>(),
      syncService: getIt<ShoppingListSyncService>(),
    ),
    dispose: (service) => service.dispose(),
  );
}
