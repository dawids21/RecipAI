# Planning — UI

## Screens and Widgets

### Calendar

- Meal Plan Calendar Screen (`meal_plan_calendar_screen.dart`) - Planning tab body: a weekly agenda with the Week
  Strip on top, then one Day Section per day of the week, starting on the locale's first day. Pull-to-refresh
  reloads it. Only visible plans' entries show (see `module.md` → Services and State). All dates follow the user's
  locale.
- Week Strip (`week_strip.dart`) - The shown week's date range with previous/next week buttons; tapping the range
  jumps to the current week.
- Day Section (`day_section.dart`) - A day header (highlighted for today) and that day's entries, or "No meals
  planned".
- Meal Entry Calendar Card (`meal_entry_calendar_card.dart`) - One entry in its plan's colour, with text that stays
  readable on any plan colour: the recipe name and its servings, or the note text. Its menu holds Edit and Delete;
  tapping the card opens the recipe (see the flow below).

### Plan Management

- Meal Plan Drawer (`meal_plan_drawer.dart`) - Right-side drawer titled "My Meal Plans", opened by the Main Screen's
  "Manage Plans" button on the Planning tab:
  - *Plans*: one Plan List Tile per plan, own and shared together, or "No plans yet"; pull-to-refresh reloads them.
  - *Create*: a "Create New Plan" button at the bottom opens the Plan Form Dialog.
  - *Errors*: every failure is a snackbar; reaching the plan quota reads "Cannot create plan: You have reached the
    maximum number of plans".
- Plan List Tile (`plan_list_tile.dart`) - One plan: a dot in its colour, its name, a visibility checkbox that shows
  or hides its entries on the calendar, and a menu with Edit and Share, plus Delete for owners only.
- Plan Form Dialog (`plan_form_dialog.dart`) - Create or edit a plan: a name field (focused on open; "Please enter a
  plan name" when empty) and the Plan Color Picker. In create mode it shows the `used / limit` counter and disables
  Create at the quota (see `docs/mobile/modules/limits/module.md` → Services and State); edit mode shows neither,
  since editing spends nothing.
- Plan Color Picker (`plan_color_picker.dart`) - A grid of 12 preset colours; the selected one shows a check mark.
- Meal Plan Sharing Dialog (`meal_plan_sharing_dialog.dart`) - The sharing dialog for a plan
  (`docs/mobile/modules/sharing/ui.md`).

### Meal Entries

- Meal Entry Form Dialog (`meal_entry_form_dialog.dart`) - Create or edit one entry:
  - *Fields*: plan, date, a Recipe/Note toggle, then for a recipe a "Select Recipe" button (opens the Recipe Picker)
    and the serving size, or for a note its text.
  - *Create and edit*: create defaults the date to the shown week's start (from the calendar FAB); edit pre-fills
    every field from the entry.
  - *Preselected recipe* (from the recipe detail screen's Add to Meal Plan): only plan, date and serving size show,
    with the serving size pre-filled from the recipe.
  - *Validation*: a plan is required; a recipe entry needs a recipe and a positive serving size, a note needs text
    ("Please enter a description").
  - *No plans*: with no plan to add to, it shows "No Editable Plans" ("You need to create a plan with edit permissions
    first."); a failed plan load shows "Error while loading meal plans".

### Shopping List Generation

- Shopping List Generation Screen (`shopping_list_generation_screen.dart`) - "Generate Shopping List" wizard in three
  steps — Select Plans, Select Dates, Review Items — with a step indicator on top. Steps change only through the
  step buttons, not by swiping; from step 2 on, the app bar's Back returns to the previous step. Each visit starts
  clean (see `module.md` → Services and State).
- Shopping List Generation Select Plan Step (`shopping_list_generation_select_plan_step.dart`) - Step 1: every meal
  plan as a checkbox row with its colour and name. "Next" is enabled once at least one plan is selected.
- Shopping List Generation Select Dates Step (`shopping_list_generation_select_dates_step.dart`) - Step 2: the Month
  Calendar for the chosen plans, with the number of selected dates. "Generate Shopping List" is enabled once at least
  one date is selected.
- Shopping List Generation Review Step (`shopping_list_generation_review_step.dart`) - Step 3: a spinner while
  generating, then the Shopping List Review Widget (`docs/mobile/modules/shopping_list/ui.md`); an error with Retry
  on failure. When meals were skipped because their recipes are not shared with the user, a collapsible warning
  banner names those recipes.
- Month Calendar Widget (`month_calendar_widget.dart`) - A month grid with previous/next month buttons and weekday
  labels starting on the locale's first day. Days with entries carry a dot; tapping a day toggles it, and selected
  days are highlighted.

## Flows

#### Meal Planning Management Flow

1. **Bottom Navigation → Planning Tab** → Main Screen switches to Planning tab view with calendar
2. **Manage Plans Button Tap** (in the app bar) → Meal Plan Drawer opens from the right
3. **Pull to Refresh** (in drawer) → Meal plans reloaded from API
4. **Visibility Checkbox Tap** (in drawer) → Toggle plan visibility → Calendar refreshes automatically to show/hide
   plan entries
5. **Plan Menu → Delete** (owners only) → Confirmation dialog "Are you sure you want to delete...?" → Tap "Delete" →
   Plan deleted (with all entries) → snackbar "Plan deleted successfully" → Drawer list refreshes → Calendar refreshes
   to remove deleted plan entries
6. **Plan Menu → Edit** → Plan Form Dialog opens with pre-filled name and color → Edit fields → Tap "Save" → Plan
   updated → snackbar "Plan updated successfully" → Drawer list refreshes
7. **Plan Menu → Share** → Meal Plan Sharing Dialog → Share by email → snackbar "Invitation sent to {email}"; cancel
   an invite or remove a user → snackbar "Meal plan unshared successfully!" → Close dialog → Plan list refreshes
8. **Create New Plan Button** (bottom of drawer) → Plan Form Dialog opens → Enter name and select color → Tap
   "Create" → Plan created → snackbar "Plan created successfully" → New plan appears in drawer list
9. **Create/Edit Validation Errors** → Name required, color required → Error messages displayed inline
10. **Create/Edit/Delete API Errors** → Network failures, plan limit exceeded (429), permission errors (403 for
    delete), plan not found (404) → User-friendly error messages in snackbars
11. **Close Drawer** → Swipe left or tap outside drawer → Returns to calendar view
12. **Switch Tabs** → Drawer closes automatically
13. **Add Meal Entry (FAB)** → Tap FAB on calendar screen → Meal Entry Form Dialog opens with date defaulting to week
    start → Select plan → For a recipe: tap "Select Recipe" → Recipe Picker → tap a recipe → back with it → enter
    serving size; for a note: switch to "Note" and enter the text → Tap "Create" → Entry added to calendar → snackbar
    "Meal entry added" → Calendar refreshes
14. **Edit Meal Entry** → Tap the entry card's menu → Select "Edit" → Meal Entry Form Dialog opens with pre-filled
    data → Modify fields → Tap "Save" → Entry updated → snackbar "Meal entry updated" → Calendar refreshes
15. **Delete Meal Entry** → Tap the entry card's menu → Select "Delete" → Confirmation dialog "Are you sure you want
    to delete...?" → Tap "Delete" → Entry deleted → snackbar "Meal entry deleted" → Calendar refreshes
16. **Navigate to Recipe from Entry** → Tap a recipe entry card (not its menu) → Recipe Detail Screen if the user can
    read the recipe, otherwise snackbar "Recipe details not shared"

#### Shopping List Generation Flow

1. **App bar menu → Generate shopping list** → Shopping List Generation Screen (`/shopping-list-generation`)
2. **Step 1 - Select Plans** → Displays all available meal plans with checkboxes → Select one or more → Tap "Next"
3. **Step 2 - Select Dates** → Month calendar loaded for selected plans → Navigate months with previous/next → Tap
   dates to toggle selection (dots show days with meal entries) → Tap "Generate Shopping List"
4. **Step 3 - Review Items** → Loading indicator during generation → Generated items displayed in the review widget →
   Warnings banner shown for skipped inaccessible recipes → Select shopping list and add items → Navigate back on
   success
5. **Back Button** (steps 2 and 3) → Returns to previous step
