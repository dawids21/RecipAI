# Planning Module

Meal planning: a weekly agenda of entries across several meal plans (own and shared), a drawer to create, edit,
share, delete and hide plans, and entries that are either a recipe with a serving size or a free-text note. Also
drives the shopping list generation wizard: pick plans, pick dates, review the generated items, add them to a list.

## Boundary

- **Backend endpoints**: all of `/meal-plans` — plans, entries, `calendar`, `balance`, `generate-shopping-list` and
  the sharing endpoints (`docs/backend/modules/planning/api.md`).
- **Uses**: `auth`; `limits` (plan counter and quota in the create dialog); `sharing` (plan sharing dialog);
  `recipe` (the `Recipe` model, the recipe picker route to choose an entry's recipe, the recipe detail route from an
  entry); `shopping_list` (list picker, review widget and import service; see Cross-module Flows).
- **Used by**: `recipe` (the detail screen's Add to Meal Plan opens `MealEntryFormDialog` and creates the entry via
  `MealPlanCalendarService`), `invites` (reloads the plan list on accept), `core` (Planning tab, Manage Plans action,
  generation route).

## File Map

```
mobile/lib/features/planning/
├── meal_plan.dart                                  # MealPlan model (name, color, the caller's role)
├── meal_plan_calendar_entry.dart                   # One calendar entry: recipe or note, with recipe-access flag
├── meal_plan_calendar_data.dart                    # Calendar response for a date range
├── meal_entry_form_result.dart                     # Result of the meal entry dialog
├── shopping_list_generated_items.dart              # Generation response: items and inaccessible recipe names
├── meal_plan_repository.dart                       # /meal-plans endpoints
├── meal_plan_list_service.dart                     # Plans list and plan balance (see Services and State)
├── meal_plan_visibility_service.dart               # Per-plan show/hide on the calendar, persisted on the device
├── meal_plan_calendar_service.dart                 # The shown week's entries and entry create/update/delete
├── meal_plan_sharing_service.dart                  # One plan's permissions with share/unshare
├── shopping_list_generation_service.dart           # Generated items for the wizard's review step
├── shopping_list_generation_calendar_service.dart  # Month data for the wizard's date step
├── meal_plan_setup.dart
├── meal_plan_calendar_screen.dart                  # Planning tab body: the weekly agenda
├── meal_plan_calendar_fab.dart                     # Adds an entry for the shown week
├── meal_plan_drawer.dart                           # Plan management drawer; opens the sharing dialog
├── meal_plan_sharing_dialog.dart                   # Binds MealPlanSharingService to SharingDialog
├── plan_form_dialog.dart                           # Create/edit plan dialog; create mode is limited (see limits)
├── meal_entry_form_dialog.dart                     # Create/edit entry dialog, recipe or note mode
├── plan_color_picker.dart                          # Grid of 12 plan colours
├── plan_list_tile.dart                             # One plan in the drawer: visibility checkbox and menu
├── week_strip.dart                                 # Week header with previous/next and jump to today
├── day_section.dart                                # One day's entries or "No meals planned"
├── meal_entry_calendar_card.dart                   # One entry card in the plan's colour
├── shopping_list_generation_screen.dart            # Three-step generation wizard
├── shopping_list_generation_select_plan_step.dart  # Step 1: choose plans
├── shopping_list_generation_select_dates_step.dart # Step 2: choose dates on a month calendar
├── shopping_list_generation_review_step.dart       # Step 3: review and add generated items
└── month_calendar_widget.dart                      # Month grid with entry dots and selectable days
```

## Services and State

- **`MealPlanListService`** — session-scoped lazy singleton (see `docs/mobile/modules/core/module.md` → Services and
  State) holding the plan list and the plan balance. Every load marks plans it has not seen before as visible.
- **`MealPlanVisibilityService`** — session-scoped lazy singleton over a plan-id → visible map persisted in
  `PreferencesService`, so hidden plans stay hidden across restarts on this device; a plan missing from the map is
  visible. Hiding is local only and never reaches the server.
- **`MealPlanCalendarService`** — session-scoped lazy singleton holding the shown week's start and that week's
  entries for the visible plans only. It reloads on any change to the plan list or to visibility, on week navigation,
  and after each entry create, update or delete, so callers never reload it themselves.
- **`MealPlanSharingService`** — not registered in `get_it`: the drawer creates one per Share action for a single
  plan and disposes it when the dialog closes. A successful share or unshare reloads the permissions and the plan list.
- **`ShoppingListGenerationService`, `ShoppingListGenerationCalendarService`** — lazy singletons reset when the
  generation screen is disposed, so each run of the wizard starts clean. The wizard's plan and date selections are
  screen state, not service state.

## Cross-module Flows

### Shopping list generation

The wizard sends the chosen plans and dates to `POST /meal-plans/generate-shopping-list`, which returns the entries'
ingredients scaled to each entry's serving size, plus the names of recipes the caller cannot read (shown as a
warning). The review step uses
`shopping_list`'s review widget; the user edits and selects items, picks a list, and the selected items are written
to that list through `ShoppingListItemImportService` (see `docs/mobile/modules/shopping_list/module.md` → Services
and State), so they appear offline-first and sync like manual edits.
