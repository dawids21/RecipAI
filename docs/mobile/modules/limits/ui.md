# Limits — UI

## Widgets

- Limit Counter (`limit_counter.dart`) - Small caption rendering `used / limit noun` (e.g. "3 / 5 recipes"),
  appending "(resets in 12h)" when the balance carries a countdown. Purely presentational: it takes both
  numbers as parameters and resolves nothing from `getIt`, because every surface has a different count
  source. `formatResetIn` renders the countdown as seconds, minutes, hours or days.

- Limit Gate (`limit_gate.dart`) - Not a visual widget: it wraps a limited surface's counter or action
  and hands its builder the two numbers behind them, the surface's `LimitBalance` and its `LimitQuota`, as
  nullables. It takes each as a listenable and rebuilds on *either* — they resolve independently, and a
  surface built before the quota lands would otherwise never show its counter. It knows nothing of
  `LimitsService`: the surface resolves its own resource's quota and passes just that listenable in.

## Behaviour

- **Quotas, once per session.** `LimitsService` subscribes to `AuthService.isAuthenticated` at
  construction: it loads `GET /limits` on the flip to signed-in and clears every quota on sign-out, so
  signing in as another account gets that account's quotas. Nothing else refreshes it — quotas are
  near-static configuration, and an operator's change is picked up at the next app start.
  `quotaFor(resource)` hands back that one resource's own listenable, which holds null until the quotas land
  and returns to null on sign-out. It holds only the quotas that are the caller's own
  (`LimitResources.perUser`); the per-list item quota is not among them, and any other resource gets a
  listenable that stays null rather than an error.
- **Counts, on open.** Each limited surface calls its own service's load method in `initState`, so the
  count is a snapshot taken when the user arrived; the surface closes after the one action it gates.
  Because the value is the subject's *recorded* balance — the same number a reserve compares against —
  a disabled button and a refusal agree by construction.
- **Every surface fails open.** A quota still loading, no quotas at all (limits disabled on the backend), or
  a failed balance fetch leaves both the counter hidden and the action enabled, with the server as the
  only thing that says no. Disabling can only ever block; it never admits an operation the server
  would refuse.

## Limited Surfaces

| Surface | Balance from | Quota from |
|---|---|---|
| Create Recipe Screen | `RecipeListService.recipeBalance` | `RECIPE` |
| Recipes Collection Create Dialog | `RecipesCollectionListService.collectionBalance` | `RECIPES_COLLECTION` |
| Shopping List Create Dialog | `ShoppingListListService.listBalance` | `SHOPPING_LIST` |
| Plan Form Dialog (create mode only) | `MealPlanListService.planBalance` | `MEAL_PLAN` |
| URL and Image Extraction Screens | `ExtractionService.extractionBalance` | `EXTRACTION` |
| Shopping List Detail Screen (add item) | length of the local item store | `ShoppingListDetailService.itemQuota` |

Each is described on its own feature's `ui.md`.
