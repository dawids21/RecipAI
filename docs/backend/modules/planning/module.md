# Planning Module

Manages meal plans with role-based access (CRUD, sharing via invite), meal plan entries with recipe or
placeholder support, automatic conversion of recipe entries to placeholders on `RecipeDeleted` event,
calendar view grouped by date, and shopping list generation with serving size scaling and inaccessible
recipe warnings.

Access control and sharing are owned by the `permissions` module
(`docs/backend/modules/permissions/`); this module asks `PermissionsFacade` for every access question
on a meal plan. Recipe access for the calendar's `hasRecipeAccess` flag is resolved
through `RecipeFacade`, which hands back one already-composed set of accessible recipe ids —
`planning` never names a `RECIPE` or `RECIPES_COLLECTION` resource key of its own.

## Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── planning/
    ├── MealPlan.java                               # Meal plan entity
    ├── MealPlanEntry.java                          # Meal plan entry entity
    ├── MealPlanRepository.java                     # Meal plan data access
    ├── MealPlanEntryRepository.java                # Meal plan entry data access, including the calendar view projection query
    ├── MealPlanCalendarEntryProjection.java        # Projection interface for calendar entries
    ├── MealPlanService.java                        # Meal plan and entry logic and sharing; owns the MEAL_PLAN resource key (see Limits)
    ├── PlanningFacade.java                         # Public facade — the account-deletion purge (see settings/module.md)
    ├── MealPlanCalendarService.java                # Calendar view over the caller's accessible plans, grouped by date
    ├── MealPlanController.java                     # Meal plan REST endpoints, including sharing and the calendar view
    ├── PlanningExceptionHandler.java               # Exception handling with ProblemDetail
    ├── dto/
    │   ├── MealPlanDto.java                        # Meal plan response DTO with the caller's ResourceRole
    │   ├── CreateMealPlanRequest.java              # Create meal plan request DTO
    │   ├── UpdateMealPlanRequest.java              # Update meal plan request DTO
    │   ├── CreateMealPlanEntryRequest.java         # Create meal plan entry request DTO
    │   ├── UpdateMealPlanEntryRequest.java         # Update meal plan entry request DTO
    │   ├── MealPlanEntryDto.java                   # Meal plan entry response DTO
    │   ├── MealPlanCalendarViewDto.java            # Calendar view response DTO
    │   ├── GenerateShoppingListItemsRequest.java   # Request DTO with planIds and selectedDates
    │   ├── GeneratedShoppingListItemDto.java       # Shopping list item DTO (name, quantity, unit, source)
    │   └── GeneratedShoppingListResponse.java      # Response DTO with items list and warnings list
    └── exception/
        ├── MealPlanNotFoundException.java
        ├── MealPlanEntryNotFoundException.java
        ├── InvalidMealPlanEntryException.java
        └── InvalidDateRangeException.java
```

Sharing types (`ResourceRole`, `PermissionDto`, `ShareRequest`, `UnshareRequest`) and the access-denied
exception live in `permissions` — see `docs/backend/modules/permissions/module.md`.

```
backend/src/test/java/xyz/stasiak/recipai/
└── planning/
    └── MealPlanIntegrationTest.java   # Plans, entries, sharing, calendar view, list generation and the MEAL_PLAN quota
```

## Limits

Creating a meal plan consumes one unit of the owner's `MEAL_PLAN` budget, reserved before anything is
written and keyed by the `email` claim of the JWT. Deleting one returns the unit. It is a stock quota:
a refusal does not resolve itself by waiting, and only creation is blocked — reading, editing and
sharing keep working while the owner is over the quota. Sharing never charges the recipient, including
one who has merely been invited but not yet accepted. See `docs/backend/modules/limits/` for how the
quota is configured and changed.
