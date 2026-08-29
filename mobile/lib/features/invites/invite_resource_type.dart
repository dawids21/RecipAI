enum InviteResourceType {
  recipe,
  recipesCollection,
  shoppingList,
  mealPlan;

  static InviteResourceType fromApiString(String apiString) {
    switch (apiString.toUpperCase()) {
      case 'RECIPE':
        return InviteResourceType.recipe;
      case 'RECIPES_COLLECTION':
        return InviteResourceType.recipesCollection;
      case 'SHOPPING_LIST':
        return InviteResourceType.shoppingList;
      case 'MEAL_PLAN':
        return InviteResourceType.mealPlan;
      default:
        throw ArgumentError('Unknown resource type: $apiString');
    }
  }

  String get displayName {
    switch (this) {
      case InviteResourceType.recipe:
        return 'Recipe';
      case InviteResourceType.recipesCollection:
        return 'Collection';
      case InviteResourceType.shoppingList:
        return 'Shopping list';
      case InviteResourceType.mealPlan:
        return 'Meal plan';
    }
  }
}
