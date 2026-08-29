import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:logging/logging.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import '../planning/meal_plan_list_service.dart';
import '../recipe/collection/recipes_collection_list_service.dart';
import '../recipe/recipe_list_service.dart';
import '../shopping_list/shopping_list_list_service.dart';
import 'invite.dart';
import 'invite_resource_type.dart';
import 'invites_repository.dart';

class InvitesService with WidgetsBindingObserver {
  static final _log = Logger('recipai.invites.service');

  final InvitesRepository _invitesRepository;
  final AuthService _authService;
  final RecipeListService _recipeListService;
  final RecipesCollectionListService _recipesCollectionListService;
  final ShoppingListListService _shoppingListListService;
  final MealPlanListService _mealPlanListService;

  InvitesService({
    required InvitesRepository invitesRepository,
    required AuthService authService,
    required RecipeListService recipeListService,
    required RecipesCollectionListService recipesCollectionListService,
    required ShoppingListListService shoppingListListService,
    required MealPlanListService mealPlanListService,
  }) : _invitesRepository = invitesRepository,
       _authService = authService,
       _recipeListService = recipeListService,
       _recipesCollectionListService = recipesCollectionListService,
       _shoppingListListService = shoppingListListService,
       _mealPlanListService = mealPlanListService {
    WidgetsBinding.instance.addObserver(this);
  }

  final ValueNotifier<AsyncValue<List<Invite>>> _invites = ValueNotifier(
    const AsyncValue.loading(),
  );

  ValueListenable<AsyncValue<List<Invite>>> get invites => _invites;

  bool _isLoadInvitesRunning = false;

  Future<void> loadInvites() async {
    if (_isLoadInvitesRunning) return;
    _isLoadInvitesRunning = true;
    try {
      // A reload keeps the rows and the badge count on screen; the badge reads a
      // loading value as zero, so blanking it here blinks the dot off on every
      // app resume. An error holds nothing worth keeping, and Retry needs the
      // spinner as its feedback.
      if (_invites.value is AsyncError) {
        _invites.value = const AsyncValue.loading();
      }
      final result = await AsyncValue.guardAsync(() async {
        final token = await _authService.idToken;
        return _invitesRepository.fetchInvites(token);
      });
      _invites.value = result;
      if (result is AsyncError<List<Invite>>) {
        _log.warning('loadInvites failed', result.error);
      }
    } finally {
      _isLoadInvitesRunning = false;
    }
  }

  Future<void> acceptInvite(Invite invite) async {
    final token = await _authService.idToken;
    try {
      await _invitesRepository.acceptInvite(invite.id, token);
    } on InviteGoneException {
      _removeFromNotifier(invite.id);
      rethrow;
    }
    _removeFromNotifier(invite.id);
    await _reloadListsFor(invite.resourceType);
  }

  Future<void> declineInvite(Invite invite) async {
    final token = await _authService.idToken;
    try {
      await _invitesRepository.declineInvite(invite.id, token);
    } on InviteGoneException {
      _removeFromNotifier(invite.id);
      return;
    }
    _removeFromNotifier(invite.id);
  }

  void _removeFromNotifier(String inviteId) {
    final current = _invites.value.valueOrNull;
    if (current == null) return;
    _invites.value = AsyncValue.data(
      current.where((invite) => invite.id != inviteId).toList(),
    );
  }

  Future<void> _reloadListsFor(InviteResourceType resourceType) {
    switch (resourceType) {
      case InviteResourceType.recipe:
        return _recipeListService.loadRecipes();
      case InviteResourceType.recipesCollection:
        return Future.wait([
          _recipesCollectionListService.loadRecipesCollections(),
          _recipeListService.loadRecipes(),
        ]);
      case InviteResourceType.shoppingList:
        return _shoppingListListService.loadShoppingLists();
      case InviteResourceType.mealPlan:
        return _mealPlanListService.loadMealPlans();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      loadInvites();
    }
  }

  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _invites.dispose();
  }
}
