# Limits — Codebase Structure

```
mobile/lib/features/limits/
├── limits_repository.dart  # API communication layer — GET /limits, decoded to a map keyed by resource
├── limits_service.dart     # Holds the session's quotas; loads on the auth flip to signed-in, clears on sign-out, and exposes one listenable per per-user quota
├── limits_setup.dart       # Dependency injection setup for the limits module
├── limit_quota.dart        # LimitQuota model (resource, kind, limit), LimitKind enum, and the LimitResources key constants
├── limit_balance.dart      # LimitBalance model — the two fields (used, resetsInSeconds) the client reads off every module's /balance response
├── limit_counter.dart      # LimitCounter widget rendering "used / limit noun", plus formatResetIn
└── limit_gate.dart         # LimitGate — rebuilds a limited surface when either its balance or its one quota changes
```

The per-resource **counts** do not live here. Each limited feature's own repository and service own the
balance read for their resource (`RecipeListService.recipeBalance`,
`RecipesCollectionListService.collectionBalance`, `ShoppingListListService.listBalance`,
`MealPlanListService.planBalance`, `ExtractionService.extractionBalance`), all holding the shared
`LimitBalance` model. Shopping-list items are the exception on both halves: the count is the length of
the local item store and the quota is `ShoppingListDetailService.itemQuota`, fetched per list because an
item quota is configured against the list's *owner*.
