class ShoppingListItemApiConflictException implements Exception {
  final String message;

  ShoppingListItemApiConflictException(this.message);

  @override
  String toString() => message;
}

class ShoppingListItemApiException implements Exception {
  final String message;

  ShoppingListItemApiException(this.message);

  @override
  String toString() => message;
}
