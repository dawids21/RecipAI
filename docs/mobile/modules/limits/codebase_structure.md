# Limits — Codebase Structure

```
mobile/lib/features/limits/
├── limits_repository.dart  # API communication layer — GET /limits, decoded to a map keyed by resource
├── limits_service.dart     # Holds the session's caps; loads on the auth flip to signed-in, clears on sign-out, and exposes one listenable per per-user cap
├── limits_setup.dart       # Dependency injection setup for the limits module
├── limit_cap.dart          # LimitCap model (resource, kind, limit), LimitKind enum, and the LimitResources key constants
├── limit_usage.dart        # LimitUsage model — the two fields (used, resetsInSeconds) the client reads off every module's /usage response
├── limit_counter.dart      # LimitCounter widget rendering "used / limit noun", plus formatResetIn
└── limit_gate.dart         # LimitGate — rebuilds a capped surface when either its usage or its one cap changes
```

The per-resource **counts** do not live here. Each capped feature's own repository and service own the
usage read for their resource (`RecipeListService.recipeUsage`,
`RecipesCollectionListService.collectionUsage`, `ShoppingListListService.listUsage`,
`MealPlanListService.planUsage`, `ExtractionService.extractionUsage`), all holding the shared
`LimitUsage` model. Shopping-list items are the exception on both halves: the count is the length of
the local item store and the cap is `ShoppingListDetailService.itemCap`, fetched per list because an
item cap is configured against the list's *owner*.
