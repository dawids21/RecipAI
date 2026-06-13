import 'package:recipai_mobile/core/get_it.dart';

import '../auth/auth_service.dart';
import 'shopping_list_detail_service.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_repository.dart';

// TODO(shopping-list-items): register the dependency that drives item syncing
// here and wire it into ShoppingListDetailService once it's designed.

void setupShoppingList({ShoppingListRepository? shoppingListRepository}) {
  final repository = shoppingListRepository ?? ShoppingListRepository();
  getIt.registerSingleton<ShoppingListRepository>(repository);
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
      // TODO(shopping-list-items): pass the item-sync dependency here.
    ),
    dispose: (service) => service.dispose(),
  );
}
