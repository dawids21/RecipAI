# Limits Module

The client half of the backend's `limits` module: holds the signed-in user's quotas and provides the
`used / limit` counter every limited surface renders, so each surface can disable its action at the quota. It is a
display and a pre-emptive block only; the server remains the only enforcement.

## Boundary

- **Backend endpoints**: `GET /limits` (`docs/backend/modules/limits/api.md`). The per-resource balance reads
  (`GET /recipes/balance` and the like) belong to the features that own each resource.
- **Uses**: `auth` (loads and clears quotas on the sign-in state).
- **Used by**: `recipe` (recipes, collections), `shopping_list` (lists, items), `planning` (meal plans) and
  `extraction`. Each owns the count for its resource and holds it as the shared `LimitBalance` model; the table below
  gives each surface's sources (what each blocks is in `ui.md` → Limited Surfaces). Shopping-list items are the
  exception on both halves: the count is the length of the local item store and the quota comes from
  `ShoppingListDetailService`, fetched per list because an item quota is configured against the list's *owner*
  (`docs/backend/modules/limits/module.md` → Behaviour).

| Surface                                | Balance from                                     | Quota from                            |
|----------------------------------------|--------------------------------------------------|---------------------------------------|
| Create Recipe Screen                   | `RecipeListService.recipeBalance`                | `RECIPE`                              |
| Recipes Collection Create Dialog       | `RecipesCollectionListService.collectionBalance` | `RECIPES_COLLECTION`                  |
| Shopping List Create Dialog            | `ShoppingListListService.listBalance`            | `SHOPPING_LIST`                       |
| Plan Form Dialog (create mode only)    | `MealPlanListService.planBalance`                | `MEAL_PLAN`                           |
| URL and Image Extraction Screens       | `ExtractionService.extractionBalance`            | `EXTRACTION`                          |
| Shopping List Detail Screen (add item) | length of the local item store                   | `ShoppingListDetailService.itemQuota` |

## File Map

```
mobile/lib/features/limits/
├── limits_repository.dart  # GET /limits, decoded to a map keyed by resource
├── limits_service.dart     # The session's quotas (see Services and State)
├── limits_setup.dart
├── limit_quota.dart        # LimitQuota model, LimitKind enum, and the LimitResources key constants
├── limit_balance.dart      # LimitBalance — the fields (used, resetsInSeconds) read off every /balance response
├── limit_counter.dart      # LimitCounter widget ("used / limit noun") and formatResetIn
└── limit_gate.dart         # LimitGate — rebuilds a limited surface when its balance or its quota changes
```

## Services and State

- **Quotas, once per session.** `LimitsService` is an app-lifetime singleton that listens to
  `AuthService.isAuthenticated`: it loads `GET /limits` on the flip to signed-in and clears every quota on sign-out,
  so signing in as another account gets that account's quotas. Nothing else refreshes it — quotas are near-static
  configuration, and an operator's change is picked up at the next app start. `quotaFor(resource)` hands back that
  one resource's own listenable, which holds null until the quotas land and returns to null on sign-out. It holds
  only the quotas that are the caller's own (`LimitResources.perUser`); the per-list item quota is not among them,
  and any other resource gets a listenable that stays null rather than an error.
- **Counts, on open.** Each limited surface calls its own service's balance load when it opens, so the count is a
  snapshot taken when the user arrived; the surface closes after the one action it gates. Because the value is the
  subject's *recorded* balance — the same number a reserve compares against — a disabled button and a refusal agree
  by construction.
- **Surfaces rebuild on either value.** The count and the quota resolve independently, so `LimitGate` rebuilds a
  surface when either changes; one built before the quota lands would otherwise never show its counter. The surface
  resolves its own quota and passes just that listenable in, and `LimitCounter` takes both numbers as parameters,
  because every surface has a different count source.
- **Every surface fails open.** A quota still loading, no quotas at all (limits disabled on the backend), or a
  failed balance fetch leaves both the counter hidden and the action enabled, with the server as the only thing that
  says no. Disabling can only ever block; it never admits an operation the server would refuse.
