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
    ├── MealPlanRepository.java                     # Meal plan data access — findByIdInOrderByCreatedAtAsc(ids), a derived query over the caller's accessible plan ids
    ├── MealPlanEntryRepository.java                # Meal plan entry data access with calendar view query (JPQL projection) — findEntriesWithRecipes and findCalendarEntries take accessible plan and recipe ids, no join to permissions or recipes.collections
    ├── MealPlanCalendarEntryProjection.java        # Projection interface for calendar entries
    ├── MealPlanService.java                        # Meal plan business logic with entries, permissions and sharing; owns the MEAL_PLAN_RESOURCE key, reserved on create, released on delete; shares via PermissionsFacade.invite, unshares via revoke
    ├── MealPlanCalendarService.java                # Calendar view service — intersects requested plan ids with the caller's accessible plans, resolves accessible recipe ids via RecipeFacade, then groups by date
    ├── MealPlanController.java                     # Meal plan REST endpoints with JWT authentication, sharing, and calendar endpoints
    ├── PlanningExceptionHandler.java               # Exception handling with ProblemDetail
    ├── dto/
    │   ├── MealPlanDto.java                        # Meal plan response DTO with the caller's ResourceRole
    │   ├── GenerateShoppingListItemsRequest.java   # Request DTO with planIds and selectedDates
    │   ├── GeneratedShoppingListItemDto.java       # Shopping list item DTO (name, quantity, unit, source)
    │   └── GeneratedShoppingListResponse.java      # Response DTO with items list and warnings list
    └── exception/                                  # Meal planning custom exceptions
```

Sharing types (`ResourceRole`, `PermissionDto`, `ShareRequest`, `UnshareRequest`) and the access-denied
exception live in `permissions` — see `docs/backend/modules/permissions/module.md`.

## Limits

Creating a meal plan consumes one unit of the owner's `MEAL_PLAN` budget, reserved before anything is
written and keyed by the `email` claim of the JWT. Deleting one returns the unit. It is a stock quota:
a refusal does not resolve itself by waiting, and only creation is blocked — reading, editing and
sharing keep working while the owner is over the quota. Sharing never charges the recipient, including
one who has merely been invited but not yet accepted. See `docs/backend/modules/limits/` for how the
quota is configured and changed.
