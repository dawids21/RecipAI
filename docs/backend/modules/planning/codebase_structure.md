# Planning Module — Codebase Structure

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
    ├── MealPlanService.java                        # Meal plan business logic with entries, permissions and sharing
    ├── MealPlanCalendarService.java                # Calendar view service with date range validation and grouping by date
    ├── MealPlanController.java                     # Meal plan REST endpoints with JWT authentication, sharing, and calendar endpoints
    ├── MealPlanConfig.java                         # Configuration for meal plan limits
    ├── MealPlanProperties.java                     # Configuration properties
    ├── PlanningExceptionHandler.java               # Exception handling with ProblemDetail
    ├── dto/
    │   ├── GenerateShoppingListItemsRequest.java   # Request DTO with planIds and selectedDates
    │   ├── GeneratedShoppingListItemDto.java       # Shopping list item DTO (name, quantity, unit, source)
    │   └── GeneratedShoppingListResponse.java      # Response DTO with items list and warnings list
    └── exception/                                  # Meal planning custom exceptions
```
