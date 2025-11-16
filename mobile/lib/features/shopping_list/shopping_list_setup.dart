import 'package:recipai_mobile/core/get_it.dart';

import '../auth/auth_service.dart';
import 'shopping_list_detail_service.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_repository.dart';
import 'shopping_list_sync_service.dart';

void setupShoppingList({ShoppingListRepository? shoppingListRepository}) {
  final repository = shoppingListRepository ?? ShoppingListRepository();
  getIt.registerSingleton<ShoppingListRepository>(repository);
  getIt.registerSingleton<ShoppingListSyncService>(
    ShoppingListSyncService(
      repository: getIt<ShoppingListRepository>(),
      authService: getIt<AuthService>(),
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
      syncService: getIt<ShoppingListSyncService>(),
    ),
    dispose: (service) => service.dispose(),
  );
}
