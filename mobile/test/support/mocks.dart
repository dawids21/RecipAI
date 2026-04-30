import 'package:mocktail/mocktail.dart';
import 'package:recipai_mobile/features/auth/auth_repository.dart';
import 'package:recipai_mobile/features/extraction/extraction_repository.dart';
import 'package:recipai_mobile/features/planning/meal_plan_repository.dart';
import 'package:recipai_mobile/features/recipe/collection/recipes_collection_repository.dart';
import 'package:recipai_mobile/features/recipe/recipe_repository.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_repository.dart';

class MockAuthRepository extends Mock implements AuthRepository {}

class MockExtractionRepository extends Mock implements ExtractionRepository {}

class MockRecipeRepository extends Mock implements RecipeRepository {}

class MockRecipesCollectionRepository extends Mock
    implements RecipesCollectionRepository {}

class MockShoppingListRepository extends Mock
    implements ShoppingListRepository {}

class MockMealPlanRepository extends Mock implements MealPlanRepository {}
