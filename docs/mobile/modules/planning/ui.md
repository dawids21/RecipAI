# Planning — UI

## Screens and Widgets

- Meal Plan Calendar Screen (`meal_plan_calendar_screen.dart`) - Agenda view displaying weekly meal plan entries with
  week navigation strip, 7 vertical day sections (locale-aware first day of week), pull-to-refresh, and entry tap
  handling. Features right-side drawer for plan management accessed via "Manage Plans" button (calendar icon) when
  Planning tab is active. All dates formatted according to user locale.
- Meal Plan Drawer (`meal_plan_drawer.dart`) - Side drawer for managing meal plans with unified list of all plans
  (personal and shared), visibility checkboxes for filtering calendar display, and three-dot menu per plan for
  Edit/Share/Delete actions (Delete only visible to owners, requires confirmation dialog). Share action opens
  MealPlanSharingDialog with email input, shared users list, and unshare functionality. Includes "Create New Plan"
  button with integrated form dialog, pull-to-refresh functionality, and comprehensive error handling with user-friendly
  messages including special handling for plan limit exceeded (409 Conflict).
- Plan Form Dialog (`plan_form_dialog.dart`) - Unified modal dialog for creating and editing meal plans with name input
  field (autofocus, validation) and color picker.
- Plan Color Picker (`plan_color_picker.dart`) - Reusable grid-based color picker widget with 12 predefined Material
  Design 3 colors. Selected color displays checkmark icon and thicker border.
- Plan List Tile (`plan_list_tile.dart`) - Card widget for individual plans with color indicator (CircleAvatar), plan
  name, visibility checkbox, and PopupMenuButton with role-based menu items (Edit, Share for all; Delete for owners
  only).
- Meal Plan Sharing Dialog (`meal_plan_sharing_dialog.dart`) - Modal dialog for sharing meal plans with other users,
  featuring email input with validation, shared users list with UserRole display, and unshare functionality with
  Material Design 3 styling. Prevents users from unsharing themselves by hiding the unshare button for the current
  user. Uses MealPlanSharingService for sharing operations, which automatically refreshes the meal plans list after
  successful share/unshare actions.
- Meal Entry Form Dialog (`meal_entry_form_dialog.dart`) - Modal dialog for creating and editing meal entries with plan
  dropdown (OWNER/EDITOR only), date picker, recipe/note mode toggle (segmented button with icons), recipe selection
  button (navigates to RecipePickerScreen), serving size input using ServingSizeInput widget (for recipes), and note
  text input. Form content is scrollable. Supports both create mode (with defaultDate from FAB) and edit mode (with
  pre-filled existingEntry data). Supports preselected recipe mode (when preselectedRecipe parameter is provided):
  hides mode toggle and recipe selection UI, pre-fills serving size from recipe's default, only shows plan dropdown,
  date picker, and serving size input. Validation ensures plan selection, recipe selection with positive serving size
  for recipe mode, or non-empty text for note mode.
- Week Strip (`week_strip.dart`) - Week navigation header widget showing current week range with previous/next week
  navigation buttons and tappable week label to jump to today. Date range formatted according to user locale.
- Day Section (`day_section.dart`) - Day container widget showing date header (highlighted for today) and list of meal
  entries, or "No meals planned" empty state. Day header formatted according to user locale.
- Meal Entry Calendar Card (`meal_entry_calendar_card.dart`) - Card widget for individual meal entries with background
  color from plan color, recipe name or placeholder text, serving size, and placeholder overflow menu for edit/delete
  actions. Text color adapts to background luminance for readability.
- Shopping List Generation Screen (`shopping_list_generation_screen.dart`) - Multi-step wizard screen for generating
  shopping lists from meal plan entries. Uses a PageView with 3 steps: Select Plans, Select Dates, Review Items.
  Features an animated step indicator bar at the top. Navigation between steps is controlled programmatically (swipe
  disabled). Back button shown in AppBar from step 2 onwards. Lazy singleton services are reset on dispose.
- Shopping List Generation Select Plan Step (`shopping_list_generation_select_plan_step.dart`) - First step widget
  displaying a list of meal plans with CheckboxListTile items (color indicator + name). "Next" button enabled only when
  at least one plan is selected.
- Shopping List Generation Select Dates Step (`shopping_list_generation_select_dates_step.dart`) - Second step widget
  with a scrollable MonthCalendarWidget for date selection. Supports month navigation (previous/next). Loads calendar
  data for the displayed month from ShoppingListGenerationCalendarService. "Generate Shopping List" button enabled only
  when at least one date is selected. Shows selected date count.
- Shopping List Generation Review Step (`shopping_list_generation_review_step.dart`) - Third step widget showing
  generated items via ShoppingListReviewWidget. Displays a collapsible warnings banner (errorContainer styled) when
  some meals were skipped due to inaccessible recipes, listing the inaccessible recipe names. Shows LoadingWidget
  during generation and ApiErrorWidget on failure with retry support.
- Shopping List Review Widget (`shopping_list_review_widget.dart`) - Widget for reviewing and selecting generated
  shopping list items before adding them to a list. Items are displayed using ShoppingListItemWidget with drag-and-drop
  reordering (ReorderableListView with auto-scroll), inline editing, and checkbox selection. Unchecked items appear
  with strikethrough. Source recipe name is shown as a subtitle below each item text. "Select All / Deselect All"
  toggle in the header. Only checked items are submitted when tapping "Add to Shopping List". Items are a mutable
  local copy — all modifications are client-side only.
- Month Calendar Widget (`month_calendar_widget.dart`) - Reusable month grid calendar widget with previous/next month
  navigation, locale-aware weekday labels (respects first day of week), and tappable day cells. Each day cell shows a
  dot indicator when the date has meal plan entries. Selected dates are highlighted with a filled circle using primary
  color. Accepts calendarData (MealPlanCalendarData) for entry indicators.

## Flows

#### Meal Planning Management Flow

1. **Bottom Navigation → Planning Tab** → MainScreen switches to Planning tab view with calendar
2. **Manage Plans Button Tap** (calendar icon in AppBar) → Opens MealPlanDrawer overlay from right side
3. **Pull to Refresh** (in drawer) → Meal plans reloaded from API
4. **Visibility Checkbox Tap** (in drawer) → Toggle plan visibility → Calendar refreshes automatically to show/hide
   plan entries
5. **Plan Menu → Delete** (owners only) → Confirmation dialog "Are you sure you want to delete...?" → Tap "Delete" →
   Plan deleted (with all entries) → SnackBar "Plan deleted successfully" → Drawer list refreshes → Calendar refreshes
   to remove deleted plan entries
6. **Plan Menu → Edit** → PlanFormDialog opens with pre-filled name and color → Edit fields → Tap "Save" → Plan
   updated → SnackBar "Plan updated successfully" → Drawer list refreshes
7. **Plan Menu → Share** → MealPlanSharingDialog opens → Enter email and tap "Share" → Email validated → Shared user
   added to list → SnackBar "Meal plan shared successfully!" → Can unshare users (except self) via remove icon →
   SnackBar "Meal plan unshared successfully!" → Close dialog → Plan list refreshes
8. **Create New Plan Button** (bottom of drawer) → PlanFormDialog opens → Enter name and select color → Tap "Create" →
   Plan created → SnackBar "Plan created successfully" → New plan appears in drawer list
9. **Create/Edit Validation Errors** → Name required, color required → Error messages displayed inline
10. **Create/Edit/Delete API Errors** → Network failures, plan limit exceeded (409), permission errors (403 for
    delete), plan not found (404) → User-friendly error messages in SnackBars
11. **Close Drawer** → Swipe left or tap outside drawer → Returns to calendar view
12. **Switch Tabs** → Drawer closes automatically
13. **Add Meal Entry (FAB)** → Tap FAB on calendar screen → MealEntryFormDialog opens with date defaulting to week
    start → Select plan from dropdown → Choose Recipe or Note mode → For Recipe: tap "Select Recipe" button →
    RecipePickerScreen opens with full-screen navigation → Search/filter and tap recipe → Navigate back with selected
    recipe → Enter serving size → Tap "Create" → Entry added to calendar → SnackBar "Meal entry added" → Calendar
    refreshes
14. **Add Note Entry** → Same as step 13 but select "Note" mode → Enter text description → Tap "Create" → Note added
    to calendar
15. **Edit Meal Entry** → Tap three-dot menu on entry card → Select "Edit" → MealEntryFormDialog opens with pre-filled
    data → Modify fields (plan, date, recipe, serving size, or text) → Tap "Save" → Entry updated → SnackBar "Meal
    entry updated" → Calendar refreshes
16. **Delete Meal Entry** → Tap three-dot menu on entry card → Select "Delete" → Confirmation dialog "Are you sure you
    want to delete...?" → Tap "Delete" → Entry deleted → SnackBar "Meal entry deleted" → Calendar refreshes
17. **Navigate to Recipe from Entry** → Tap on recipe entry card (not menu) → If hasRecipeAccess, navigates to Recipe
    Detail Screen → If no access, shows SnackBar "Recipe details not shared"

#### Shopping List Generation Flow

1. **AppBar Menu → Generate shopping list** → Shopping List Generation Screen (`/shopping-list-generation`)
2. **Step 1 - Select Plans** → Displays all available meal plans with checkboxes → Select one or more → Tap "Next"
3. **Step 2 - Select Dates** → Month calendar loaded for selected plans → Navigate months with previous/next → Tap
   dates to toggle selection (dots show days with meal entries) → Tap "Generate Shopping List"
4. **Step 3 - Review Items** → Loading indicator during generation → Generated items displayed via
   ShoppingListReviewWidget → Warnings banner shown for skipped inaccessible recipes → Select shopping list and add
   items → Navigate back on success
5. **Back Button** (steps 2 and 3) → Returns to previous step
