import 'shared_user.dart';

class RecipeSharedUser {
  final SharedUser sharedUser;
  final bool isCurrentUser;

  const RecipeSharedUser({
    required this.sharedUser,
    required this.isCurrentUser,
  });

  String get email => sharedUser.email;

  get role => sharedUser.role;
}
