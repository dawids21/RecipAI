# Planning — Codebase Structure

```
mobile/lib/features/planning/
├── meal_plan.dart                              # Meal plan data model
├── meal_plan_calendar_entry.dart               # Meal plan entry data model
├── meal_plan_calendar_data.dart                # Calendar data model
├── meal_plan_permission.dart                   # Permission data model for sharing
├── meal_entry_form_result.dart                 # Result model for meal entry form dialog
├── shopping_list_generated_items.dart          # Data model for shopping list generation API response
├── meal_plan_repository.dart                   # Meal plan data access layer with create/update/fetch operations
├── meal_plan_list_service.dart                 # Plan list business logic with create/update methods and ValueNotifier
├── meal_plan_sharing_service.dart              # Meal plan sharing service with ValueNotifier, depends on MealPlanListService
├── meal_plan_visibility_service.dart           # Visibility toggles with local persistence
├── meal_plan_calendar_service.dart             # Calendar business logic with week navigation and entry CRUD
├── shopping_list_generation_service.dart       # Service for generating shopping lists from meal plan entries
├── shopping_list_generation_calendar_service.dart # Service for loading calendar data for date selection step
├── meal_plan_setup.dart                        # Dependency injection setup for planning module
├── meal_plan_calendar_screen.dart              # Agenda view screen
├── meal_plan_calendar_fab.dart                 # FAB widget for adding meal entries
├── meal_plan_drawer.dart                       # Side drawer for plan management with create/edit handlers
├── meal_plan_sharing_dialog.dart               # Meal plan sharing dialog wrapper
├── plan_form_dialog.dart                       # Unified create/edit dialog with PlanFormResult model
├── meal_entry_form_dialog.dart                 # Modal dialog for creating/editing meal entries with recipe/placeholder modes
├── plan_color_picker.dart                      # Reusable color picker widget with 12 predefined colors
├── plan_list_tile.dart                         # Plan list item widget with checkbox and menu
├── week_strip.dart                             # Week navigation header widget with tappable label to jump to today
├── day_section.dart                            # Day section widget with entry list
├── meal_entry_calendar_card.dart               # Individual meal entry card widget
├── shopping_list_generation_screen.dart        # Multi-step wizard screen for shopping list generation
├── shopping_list_generation_select_plan_step.dart  # Step 1: plan selection widget
├── shopping_list_generation_select_dates_step.dart # Step 2: date selection widget with MonthCalendarWidget
├── shopping_list_generation_review_step.dart   # Step 3: review and add generated items widget
└── month_calendar_widget.dart                  # Reusable month grid calendar with locale-aware day layout and entry dots
```
