import '../../../core/get_it.dart';
import '../../auth/auth_service.dart';
import 'recipes_collection_list_service.dart';
import 'recipes_collection_repository.dart';

void setupRecipesCollection({
  RecipesCollectionRepository? recipesCollectionRepository,
}) {
  final repository =
      recipesCollectionRepository ?? RecipesCollectionRepository();
  getIt.registerSingleton<RecipesCollectionRepository>(repository);
  getIt.registerLazySingleton(
    () => RecipesCollectionListService(
      recipesCollectionRepository: getIt<RecipesCollectionRepository>(),
      authService: getIt<AuthService>(),
    ),
    dispose: (service) => service.dispose(),
  );
}
