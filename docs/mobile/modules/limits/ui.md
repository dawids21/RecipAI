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

How quotas and counts load, and the fail-open rule every surface follows, are in `module.md` → Services and
State.

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
