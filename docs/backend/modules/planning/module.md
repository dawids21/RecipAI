# Planning Module

Manages meal plans with user-based permission control (CRUD with role-based access, sharing), meal
plan entries with recipe or placeholder support, automatic conversion of recipe entries to
placeholders on `RecipeDeleted` event, calendar view grouped by date, and shopping list generation
with serving size scaling and inaccessible recipe warnings.

## Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── planning/
    ├── MealPlan.java                               # Meal plan entity
    ├── MealPlanEntry.java                          # Meal plan entry entity
    ├── MealPlanPermission.java                     # Meal plan permission association entity
    ├── MealPlanPermissionId.java                   # Composite key for meal plan permissions
    ├── UserRole.java                               # Enum for OWNER/EDITOR roles
    ├── MealPlanRepository.java                     # Meal plan data access
    ├── MealPlanEntryRepository.java                # Meal plan entry data access with calendar view query (JPQL projection)
    ├── MealPlanCalendarEntryProjection.java        # Projection interface for calendar entries
    ├── MealPlanPermissionRepository.java           # Permission queries repository
    ├── MealPlanService.java                        # Meal plan business logic with entries, permissions and sharing; MEAL_PLAN_RESOURCE reserved on create, released on delete
    ├── MealPlanCalendarService.java                # Calendar view service with date range validation and grouping by date
    ├── MealPlanController.java                     # Meal plan REST endpoints with JWT authentication, sharing, and calendar endpoints
    ├── PlanningExceptionHandler.java               # Exception handling with ProblemDetail
    ├── dto/
    │   ├── GenerateShoppingListItemsRequest.java   # Request DTO with planIds and selectedDates
    │   ├── GeneratedShoppingListItemDto.java       # Shopping list item DTO (name, quantity, unit, source)
    │   └── GeneratedShoppingListResponse.java      # Response DTO with items list and warnings list
    └── exception/                                  # Meal planning custom exceptions
```

## Limits

Creating a meal plan consumes one unit of the owner's `MEAL_PLAN` budget, reserved before anything is
written and keyed by the `email` claim of the JWT. Deleting one returns the unit. It is a stock quota:
a refusal does not resolve itself by waiting, and only creation is blocked — reading, editing and
sharing keep working while the owner is over the quota. Sharing never charges the recipient. See
`docs/backend/modules/limits/` for how the quota is configured and changed.
