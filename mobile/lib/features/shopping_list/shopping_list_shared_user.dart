import '../../shared/user_role.dart';
import 'shopping_list_permission.dart';

class ShoppingListSharedUser {
  final ShoppingListPermission permission;
  final bool isCurrentUser;

  const ShoppingListSharedUser({
    required this.permission,
    required this.isCurrentUser,
  });

  String get email => permission.email;

  UserRole get role => permission.role;
}
