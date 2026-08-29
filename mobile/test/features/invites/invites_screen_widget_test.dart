import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get_it/get_it.dart';
import 'package:go_router/go_router.dart';
import 'package:mocktail/mocktail.dart';
import 'package:recipai_mobile/core/preferences_service.dart';
import 'package:recipai_mobile/core/routes.dart';
import 'package:recipai_mobile/features/auth/auth_setup.dart';
import 'package:recipai_mobile/features/auth/auth_user.dart';
import 'package:recipai_mobile/features/invites/invite.dart';
import 'package:recipai_mobile/features/invites/invite_list_item.dart';
import 'package:recipai_mobile/features/invites/invite_resource_type.dart';
import 'package:recipai_mobile/features/invites/invites_repository.dart';
import 'package:recipai_mobile/features/invites/invites_screen.dart';
import 'package:recipai_mobile/features/invites/invites_service.dart';
import 'package:recipai_mobile/features/invites/invites_setup.dart';
import 'package:recipai_mobile/features/limits/limits_setup.dart';
import 'package:recipai_mobile/features/planning/meal_plan_setup.dart';
import 'package:recipai_mobile/features/recipe/collection/recipes_collection_setup.dart';
import 'package:recipai_mobile/features/recipe/recipe_setup.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_item_store_service.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_setup.dart';
import 'package:recipai_mobile/shared/api_error_widget.dart';
import 'package:recipai_mobile/shared/loading_widget.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/mocks.dart';

const shoppingListInvite = Invite(
  id: 'invite-shopping-list',
  resourceType: InviteResourceType.shoppingList,
  label: 'Weekly Groceries',
  invitedBy: 'alice@example.com',
);
const recipeInvite = Invite(
  id: 'invite-recipe',
  resourceType: InviteResourceType.recipe,
  label: 'Pizza',
  invitedBy: 'alice@example.com',
);
const collectionInvite = Invite(
  id: 'invite-collection',
  resourceType: InviteResourceType.recipesCollection,
  label: 'Weeknight Dinners',
  invitedBy: 'alice@example.com',
);
const mealPlanInvite = Invite(
  id: 'invite-meal-plan',
  resourceType: InviteResourceType.mealPlan,
  label: 'August Plan',
  invitedBy: 'bob@example.com',
);

void main() {
  late MockAuthRepository authRepository;
  late MockRecipeRepository recipeRepository;
  late MockRecipesCollectionRepository recipesCollectionRepository;
  late MockShoppingListRepository shoppingListRepository;
  late MockShoppingListItemRepository shoppingListItemRepository;
  late MockShoppingListItemDao shoppingListItemDao;
  late MockMealPlanRepository mealPlanRepository;
  late MockLimitsRepository limitsRepository;
  late MockInvitesRepository invitesRepository;
  late Widget app;

  setUpAll(() {
    registerFallbackValue(Uri());
  });

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await GetIt.I.reset();

    authRepository = MockAuthRepository();
    recipeRepository = MockRecipeRepository();
    recipesCollectionRepository = MockRecipesCollectionRepository();
    shoppingListRepository = MockShoppingListRepository();
    shoppingListItemRepository = MockShoppingListItemRepository();
    shoppingListItemDao = MockShoppingListItemDao();
    mealPlanRepository = MockMealPlanRepository();
    limitsRepository = MockLimitsRepository();
    invitesRepository = MockInvitesRepository();

    when(() => limitsRepository.fetchQuotas(any())).thenAnswer((_) async => {});
    when(
      () => authRepository.watchAuthState(),
    ).thenAnswer((_) => const Stream<AuthUser?>.empty());
    when(
      () => authRepository.getIdToken(),
    ).thenAnswer((_) async => 'fake-token');
    when(
      () => shoppingListItemDao.listIdsWithOutbox(),
    ).thenAnswer((_) async => const []);

    final prefs = await SharedPreferences.getInstance();
    GetIt.I.registerSingleton(PreferencesService(prefs));

    setupAuth(authRepository: authRepository);
    setupLimits(limitsRepository: limitsRepository);
    setupRecipesCollection(
      recipesCollectionRepository: recipesCollectionRepository,
    );
    setupRecipe(recipeRepository: recipeRepository);
    setupShoppingList(
      shoppingListRepository: shoppingListRepository,
      itemRepository: shoppingListItemRepository,
      store: ShoppingListItemStoreService(dao: shoppingListItemDao),
    );
    setupMealPlan(mealPlanRepository: mealPlanRepository);
    setupInvites(invitesRepository: invitesRepository);

    final router = GoRouter(
      initialLocation: '/${AppRoute.invites.path}',
      routes: [
        GoRoute(
          path: '/${AppRoute.invites.path}',
          name: AppRoute.invites.name,
          builder: (context, state) =>
              InvitesScreen(invitesService: GetIt.I<InvitesService>()),
        ),
        GoRoute(
          path: AppRoute.main.path,
          name: AppRoute.main.name,
          builder: (context, state) => const Scaffold(body: Text('Main')),
        ),
      ],
    );
    app = MaterialApp.router(routerConfig: router);
  });

  tearDown(() => GetIt.I.reset());

  testWidgets('renders one InviteListItem per pending invite', (tester) async {
    when(
      () => invitesRepository.fetchInvites(any()),
    ).thenAnswer((_) async => [shoppingListInvite, recipeInvite]);

    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    expect(find.byType(InviteListItem), findsNWidgets(2));
    expect(find.text('Weekly Groceries'), findsOneWidget);
    expect(
      find.text('Shopping list · Shared by alice@example.com'),
      findsOneWidget,
    );
    expect(find.text('Pizza'), findsOneWidget);
    expect(find.text('Recipe · Shared by alice@example.com'), findsOneWidget);
  });

  testWidgets(
    'renders a row for each of the four resource types with its own icon',
    (tester) async {
      when(() => invitesRepository.fetchInvites(any())).thenAnswer(
        (_) async => [
          recipeInvite,
          collectionInvite,
          shoppingListInvite,
          mealPlanInvite,
        ],
      );

      await tester.pumpWidget(app);
      await tester.pumpAndSettle();

      expect(find.byType(InviteListItem), findsNWidgets(4));
      expect(find.byIcon(Icons.restaurant_menu), findsOneWidget);
      expect(find.byIcon(Icons.folder), findsOneWidget);
      expect(find.byIcon(Icons.shopping_cart), findsOneWidget);
      expect(find.byIcon(Icons.calendar_today), findsOneWidget);
    },
  );

  testWidgets('fetches invites when the screen opens', (tester) async {
    when(
      () => invitesRepository.fetchInvites(any()),
    ).thenAnswer((_) async => <Invite>[]);

    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    verify(() => invitesRepository.fetchInvites(any())).called(1);
  });

  testWidgets(
    'renders No pending invites when the fetch returns an empty list',
    (tester) async {
      when(
        () => invitesRepository.fetchInvites(any()),
      ).thenAnswer((_) async => <Invite>[]);

      await tester.pumpWidget(app);
      await tester.pumpAndSettle();

      expect(find.text('No pending invites'), findsOneWidget);
    },
  );

  testWidgets('renders ApiErrorWidget when fetch throws, re-fetches on Retry', (
    tester,
  ) async {
    var callCount = 0;
    when(() => invitesRepository.fetchInvites(any())).thenAnswer((_) async {
      callCount++;
      if (callCount == 1) throw Exception('network down');
      return <Invite>[];
    });

    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    expect(find.byType(ApiErrorWidget), findsOneWidget);

    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();

    verify(() => invitesRepository.fetchInvites(any())).called(2);
    expect(find.text('No pending invites'), findsOneWidget);
  });

  testWidgets(
    'fires fetchInvites again on a pull-to-refresh over the empty state',
    (tester) async {
      when(
        () => invitesRepository.fetchInvites(any()),
      ).thenAnswer((_) async => <Invite>[]);

      await tester.pumpWidget(app);
      await tester.pumpAndSettle();

      await tester.fling(find.byType(ListView), const Offset(0, 300), 1000);
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();

      verify(() => invitesRepository.fetchInvites(any())).called(2);
    },
  );

  testWidgets('keeps existing rows on screen while a reload is in flight', (
    tester,
  ) async {
    var callCount = 0;
    final completer = Completer<List<Invite>>();
    when(() => invitesRepository.fetchInvites(any())).thenAnswer((_) async {
      callCount++;
      if (callCount == 1) return [shoppingListInvite];
      return completer.future;
    });

    await tester.pumpWidget(app);
    await tester.pumpAndSettle();
    expect(find.byType(InviteListItem), findsOneWidget);

    await tester.fling(find.byType(ListView), const Offset(0, 300), 1000);
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    expect(find.byType(InviteListItem), findsOneWidget);
    expect(find.byType(LoadingWidget), findsNothing);

    completer.complete([shoppingListInvite]);
    await tester.pumpAndSettle();
  });

  testWidgets('shows LoadingWidget when Retry is tapped from the error state', (
    tester,
  ) async {
    var callCount = 0;
    final completer = Completer<List<Invite>>();
    when(() => invitesRepository.fetchInvites(any())).thenAnswer((_) async {
      callCount++;
      if (callCount == 1) throw Exception('network down');
      return completer.future;
    });

    await tester.pumpWidget(app);
    await tester.pumpAndSettle();
    expect(find.byType(ApiErrorWidget), findsOneWidget);

    await tester.tap(find.text('Retry'));
    await tester.pump();

    expect(find.byType(LoadingWidget), findsOneWidget);

    completer.complete(<Invite>[]);
    await tester.pumpAndSettle();
  });

  testWidgets(
    'accept calls acceptInvite with that invite\'s id and removes the row',
    (tester) async {
      when(
        () => invitesRepository.fetchInvites(any()),
      ).thenAnswer((_) async => [shoppingListInvite]);
      when(
        () => invitesRepository.acceptInvite(any(), any()),
      ).thenAnswer((_) async {});
      when(
        () => shoppingListRepository.fetchShoppingLists(any()),
      ).thenAnswer((_) async => []);

      await tester.pumpWidget(app);
      await tester.pumpAndSettle();

      await tester.tap(find.widgetWithText(FilledButton, 'Accept'));
      await tester.pumpAndSettle();

      verify(
        () => invitesRepository.acceptInvite(shoppingListInvite.id, any()),
      ).called(1);
      expect(find.byType(InviteListItem), findsNothing);
    },
  );

  testWidgets('accept reloads the matching list service', (tester) async {
    when(
      () => invitesRepository.fetchInvites(any()),
    ).thenAnswer((_) async => [shoppingListInvite]);
    when(
      () => invitesRepository.acceptInvite(any(), any()),
    ).thenAnswer((_) async {});
    when(
      () => shoppingListRepository.fetchShoppingLists(any()),
    ).thenAnswer((_) async => []);

    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Accept'));
    await tester.pumpAndSettle();

    verify(() => shoppingListRepository.fetchShoppingLists(any())).called(1);
  });

  testWidgets(
    'accept surfaces "That invite is no longer available" and still drops the row on InviteGoneException',
    (tester) async {
      when(
        () => invitesRepository.fetchInvites(any()),
      ).thenAnswer((_) async => [shoppingListInvite]);
      when(
        () => invitesRepository.acceptInvite(any(), any()),
      ).thenThrow(InviteGoneException(shoppingListInvite.id));

      await tester.pumpWidget(app);
      await tester.pumpAndSettle();

      await tester.tap(find.widgetWithText(FilledButton, 'Accept'));
      await tester.pumpAndSettle();

      expect(find.text('That invite is no longer available'), findsOneWidget);
      expect(find.byType(InviteListItem), findsNothing);
    },
  );

  testWidgets(
    'decline shows the confirmation dialog and does not call declineInvite when cancelled',
    (tester) async {
      when(
        () => invitesRepository.fetchInvites(any()),
      ).thenAnswer((_) async => [shoppingListInvite]);

      await tester.pumpWidget(app);
      await tester.pumpAndSettle();

      await tester.tap(find.widgetWithText(TextButton, 'Decline'));
      await tester.pumpAndSettle();

      expect(find.text('Decline invite'), findsOneWidget);

      await tester.tap(
        find.descendant(
          of: find.byType(AlertDialog),
          matching: find.widgetWithText(TextButton, 'Cancel'),
        ),
      );
      await tester.pumpAndSettle();

      verifyNever(() => invitesRepository.declineInvite(any(), any()));
      expect(find.byType(InviteListItem), findsOneWidget);
    },
  );

  testWidgets('decline calls declineInvite and drops the row when confirmed', (
    tester,
  ) async {
    when(
      () => invitesRepository.fetchInvites(any()),
    ).thenAnswer((_) async => [shoppingListInvite]);
    when(
      () => invitesRepository.declineInvite(any(), any()),
    ).thenAnswer((_) async {});

    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(TextButton, 'Decline'));
    await tester.pumpAndSettle();

    await tester.tap(
      find.descendant(
        of: find.byType(AlertDialog),
        matching: find.widgetWithText(TextButton, 'Decline'),
      ),
    );
    await tester.pumpAndSettle();

    verify(
      () => invitesRepository.declineInvite(shoppingListInvite.id, any()),
    ).called(1);
    expect(find.byType(InviteListItem), findsNothing);
  });

  testWidgets('decline swallows InviteGoneException', (tester) async {
    when(
      () => invitesRepository.fetchInvites(any()),
    ).thenAnswer((_) async => [shoppingListInvite]);
    when(
      () => invitesRepository.declineInvite(any(), any()),
    ).thenThrow(InviteGoneException(shoppingListInvite.id));

    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(TextButton, 'Decline'));
    await tester.pumpAndSettle();

    await tester.tap(
      find.descendant(
        of: find.byType(AlertDialog),
        matching: find.widgetWithText(TextButton, 'Decline'),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Invite declined'), findsOneWidget);
    expect(find.byType(InviteListItem), findsNothing);
  });

  testWidgets('both buttons are disabled while a call is in flight', (
    tester,
  ) async {
    when(
      () => invitesRepository.fetchInvites(any()),
    ).thenAnswer((_) async => [shoppingListInvite]);
    final completer = Completer<void>();
    when(
      () => invitesRepository.acceptInvite(any(), any()),
    ).thenAnswer((_) => completer.future);
    when(
      () => shoppingListRepository.fetchShoppingLists(any()),
    ).thenAnswer((_) async => []);

    await tester.pumpWidget(app);
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Accept'));
    await tester.pump();

    final acceptButton = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Accept'),
    );
    final declineButton = tester.widget<TextButton>(
      find.widgetWithText(TextButton, 'Decline'),
    );
    expect(acceptButton.onPressed, isNull);
    expect(declineButton.onPressed, isNull);

    completer.complete();
    await tester.pumpAndSettle();
  });

  testWidgets(
    'the empty state renders in place when the last invite is answered',
    (tester) async {
      when(
        () => invitesRepository.fetchInvites(any()),
      ).thenAnswer((_) async => [shoppingListInvite]);
      when(
        () => invitesRepository.acceptInvite(any(), any()),
      ).thenAnswer((_) async {});
      when(
        () => shoppingListRepository.fetchShoppingLists(any()),
      ).thenAnswer((_) async => []);

      await tester.pumpWidget(app);
      await tester.pumpAndSettle();

      await tester.tap(find.widgetWithText(FilledButton, 'Accept'));
      await tester.pumpAndSettle();

      expect(find.text('No pending invites'), findsOneWidget);
      expect(find.text('Main'), findsNothing);
    },
  );
}
