import 'package:recipai_mobile/core/get_it.dart';

import '../auth/auth_service.dart';
import 'shopping_list_detail_service.dart';
import 'shopping_list_item_repository.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_repository.dart';
import 'shopping_list_sync_service.dart';

/// Registers the shopping-list feature. The database-backed
/// [ShoppingListItemRepository] is opened by the caller (see `main()`) and
/// injected here so setup stays synchronous; tests inject a mock instead.
void setupShoppingList({
  required ShoppingListItemRepository itemRepository,
  ShoppingListRepository? shoppingListRepository,
}) {
  final repository = shoppingListRepository ?? ShoppingListRepository();
  getIt.registerSingleton<ShoppingListRepository>(repository);

  getIt.registerSingleton<ShoppingListItemRepository>(
    itemRepository,
    dispose: (r) => r.dispose(),
  );

  getIt.registerSingleton<ShoppingListSyncService>(
    ShoppingListSyncService(
      itemRepository: getIt<ShoppingListItemRepository>(),
      authService: getIt<AuthService>(),
    ),
    dispose: (s) => s.dispose(),
  );
  getIt<ShoppingListSyncService>().start();

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
      itemRepository: getIt<ShoppingListItemRepository>(),
      syncService: getIt<ShoppingListSyncService>(),
    ),
    dispose: (service) => service.dispose(),
  );
}
