# UI Architecture for Meal Planning

## 1. Feature Overview

- **Feature Name**: Meal Planning
- **Purpose**: Allows users to schedule meals across multiple contexts (e.g., Personal, Family), visualize them on a
  calendar, and generate shopping lists based on planned recipes.
- **Target Users**: Any user wishing to organize weekly meals or collaborate on meal schedules with family members.
- **Integration Points**:
    - **Main Navigation**: New 3rd tab "Planning".
    - **Recipe Module**: Selecting recipes for plans; linking from plan to recipe details.
    - **Shopping List Module**: Generating list items from planned meals; reusing item selection UI.

## 2. Views

### View 1: Meal Planning Screen (Main)

- **Path**: `/planning` (Tab 3 in `MainScreen`)
- **Purpose**: Primary interface for visualizing the weekly schedule in a vertical list format.
- **Trigger/Entry Point**: Tapping the "Planning" icon in the Bottom Navigation Bar.
- **Key Data Displayed**:
    - Horizontal "Week Strip" (to navigate between weeks).
    - Scrollable Vertical List containing 7 distinct Day Sections (Monday - Sunday) for the selected week.
    - Each Day Section contains a list of `MealEntry` cards.
    - Plan colors indicating context.
    - Lock icons for restricted recipes.
- **Primary Actions**:
    - **Switch Week**: Swipe/Tap week strip or use "Today" action.
    - **Manage Plans**: Open Side Drawer to toggle visibility or edit plans.
    - **Add Meal**: Floating Action Button (FAB).
    - **Behavior**: Tapping FAB opens the entry dialog with the **Start Date** of the currently visible week pre-filled.
    - **Interact with Entry**: Tap to view recipe (if allowed), tap "more" menu to Edit/Delete.
    - **Generate List**: AppBar action to start shopping list flow.
- **Navigation**:
    - Tap Recipe Card → `RecipeDetailScreen` (blocked if Restricted).
    - FAB → `MealEntryDialog`.
    - Generate Icon → `ShoppingListGenerationScreen`.
- **Components Required**: `WeekStripWidget`, `DaySectionWidget`, `MealEntryCard`, `PlanDrawer`.
- **API Endpoints Used**:
    - `GET /meal-plans` (for drawer).
  - `GET /meal-plans/calendar` (for entries, requesting range = selected week).
- **Edge Cases**:
    - No visible plans selected (Empty state prompt to open drawer).
    - Network error (Retry button, as this is online-first).

### View 2: Side Drawer (Plan Management)

- **Path**: Overlay on `MealPlanningScreen`.
- **Purpose**: Manage which plans are visible on the calendar and perform CRUD operations on plans.
- **Trigger/Entry Point**: Burger menu icon in AppBar of `MealPlanningScreen`.
- **Key Data Displayed**:
    - Unified List of all plans (Personal & Shared) with Checkboxes (visibility) and Name.
    - **Note:** Roles (Owner/Editor) are NOT displayed visually in the list items.
- **Primary Actions**:
    - **Toggle Visibility**: Checkbox (persisted locally).
    - **Create Plan**: "Create New Plan" button.
  - **Plan Options**: Three-dot menu per plan.
      - **Owners**: Edit, Share, Delete.
      - **Editors**: Edit, Share. (Delete option is hidden).
- **Navigation**:
    - Create/Edit → `PlanFormDialog`.
  - Share → `MealPlanSharingDialog` (uses generic `SharingDialog`).
- **Components Required**: `Drawer`, `PlanListTile`, `AlertDialog` (for delete confirmation).
- **API Endpoints Used**: `GET /meal-plans`, `DELETE /meal-plans/{id}`.

### View 3: Meal Entry Dialog

- **Path**: Dialog (Modal).
- **Purpose**: Add or Edit a specific meal entry.
- **Trigger/Entry Point**: FAB (Add) or "Edit" option on Meal Card.
- **Key Data Displayed**:
    - Target Plan (Dropdown).
  - Date (DatePicker - defaults to week start if creating via FAB).
    - Mode Switch (Recipe vs. Placeholder).
    - Recipe Selection (Name or "Select" button).
    - Serving Size (Input, visible only for Recipe).
    - Placeholder Text (Input, visible only for Placeholder).
- **Primary Actions**:
    - **Select Recipe**: Opens `RecipePickerDialog` (stacked on top of this dialog).
    - **Save**: Commits entry.
    - **Cancel**: Closes dialog.
- **Components Required**: `MealEntryForm`, `YieldInput`.
- **API Endpoints Used**: `POST/PUT /meal-plans/{planId}/entries`.
- **Edge Cases**:
    - User has no Editor rights on any plan (Dropdown empty/disabled).
  - Editing a Restricted Recipe: User **can** change date/yield or select a different recipe entirely. User cannot see
    current recipe details.

### View 4: Recipe Picker Dialog

- **Path**: Dialog (Stacked on top of `MealEntryDialog`).
- **Purpose**: Reuses the existing `RecipeList` component to select a recipe for a plan.
- **Trigger/Entry Point**: "Select Recipe" button in `MealEntryDialog`.
- **Key Data Displayed**:
    - Search Bar (Reused).
    - Filter Chips (Reused).
    - List of user's recipes (Reused).
- **Primary Actions**:
    - **Tap Recipe**: Selects the recipe, closes the dialog, and passes the selection back to `MealEntryDialog`.
- **Components Required**: `RecipeList`.
    - **Refactor Note**: COMPLETED. `RecipeList` now supports an optional `onRecipeTap` callback parameter. When
      provided, the callback is executed instead of navigating to RecipeDetailScreen. Search query is now local widget
      state, allowing each RecipeList instance to have independent search state for use in dialogs and pickers.
- **Edge Cases**: No recipes found (prompt to create recipe or use Placeholder).

### View 5: Shopping List Generation Flow

- **Path**: `/planning/generate` (and sub-steps).
- **Purpose**: Wizard to aggregate ingredients from plans into a shopping list.
- **Trigger/Entry Point**: "Generate Shopping List" in `MealPlanningScreen` overflow menu.
- **Steps**:
    1. **Select Plans**: Checkbox list of plans to include.
    2. **Select Dates**: Month-view calendar allowing multi-selection of days. Days with meals have visual indicators.
    3. **Review Items**: (Reused `AddItemsToShoppingListScreen`).
- **Key Data Displayed (Review Step)**:
    - **Warnings Banner**: "The following meals were skipped due to privacy..."
  - **Item List**: Calculated ingredients with checkboxes. **Sorted by Name.**
    - **Target List**: Dropdown to select Shopping List.
- **Primary Actions**: "Add to List".
- **Limitations**:
    - Users can only Check/Uncheck items in the Review Step. Editing item names or quantities is future scope.
- **API Endpoints Used**: `POST /meal-plans/generate-shopping-list`.
- **Edge Cases**: Selected range contains only restricted recipes (Warning banner only, empty item list).

## 3. User Journey

### Primary Flow: Planning a Meal

1. **View Calendar**: User opens "Planning" tab. Sees a list of Monday-Sunday with empty slots.
2. **Add Entry**: Taps FAB. `MealEntryDialog` opens with **Monday's date** (Week Start) pre-filled.
3. **Select Recipe**: Taps "Select Recipe".
4. **Browse**: `RecipePickerDialog` opens (stacked). User types "Lasagna" in the search bar.
5. **Pick**: Taps the "Lasagna" card. `RecipePickerDialog` closes. "Lasagna" is now selected in `MealEntryDialog`.
6. **Configure**: User sets Plan to "Family" and Serving Size to "4".
7. **Save**: Taps Save. Dialog closes. The "Lasagna" card appears under the Monday section of the list.

### Flow: Generating Shopping List

1. **Initiate**: User taps "Generate Shopping List" from AppBar menu.
2. **Scope**: Selects "Family" Plan and selects dates "Mon-Fri" on the multi-select calendar widget.
3. **Generate**: Taps "Next". System calls API.
4. **Review**: User sees list of ingredients sorted by name.
    - *Note:* "Salt" appears twice as separate lines because there is no aggregation. User unchecks one of them
      manually.
    - A banner warns: "Secret Sauce Recipe skipped (Restricted)".
5. **Confirm**: User selects target list "Weekly Groceries" and taps "Add Items".
6. **Result**: Navigated to Shopping List Detail view.

### Alternative Flows

- **Editing a Plan**: User opens Drawer. Sees list of plans. Taps "Edit" menu item on "Personal". Changes color to
  Blue → Saves. Calendar updates immediately.
- **Deleting a Plan**: User opens Drawer. Taps "Delete" menu item on owned plan. Confirmation dialog appears: "Are you
  sure you want to delete...?". User taps "Delete" → Plan and all entries are deleted. SnackBar confirms success.
  Drawer and calendar refresh.
- **Handling Restricted Access**: User taps a meal entry with a lock icon → Toast appears "Recipe details not shared".
  User opens "Edit" menu → Selects a new recipe they own → Saves. The entry is updated.

### Exit Points

- **Bottom Navigation**: Switching to "Recipes" or "Shopping" tabs.
- **Deep Link**: Tapping a Recipe Card navigates to `/recipes/{id}`.

## 4. Navigation Structure

- **Entry Points**:
    - `AppRoute.main` (Tab index 2).
- **Internal Navigation**:
    - `MealPlanningScreen` (Root)
        - Overlay: `SideDrawer`
      - Dialog: `MealEntryDialog`
          - Stacked Dialog: `RecipePickerDialog`
        - Push: `ShoppingListGenerationScreen`
- **Exit Points**:
    - `RecipeDetailScreen` (Push).
    - `ShoppingListDetailScreen` (Push, after generation).
- **Breadcrumbs/Back Navigation**:
    - Standard AppBar "Back" arrow for pushed screens.
    - "Cancel" buttons for Dialogs.

## 5. Key Components

### New Components

1. **WeekStripWidget**
    - **Purpose**: Horizontal calendar strip to navigate between weeks.
    - **Used In**: `MealPlanningScreen`.
    - **Behavior**: `PageView` controller. Displaying "Oct 23 - Oct 29".
2. **DaySectionWidget**
    - **Purpose**: Container for a single day in the vertical list.
    - **Used In**: `MealPlanningScreen`.
    - **Behavior**: Displays Date Header (e.g., "Monday, Oct 23") followed by a list of `MealEntryCard`s.
3. **MealEntryCard**
    - **Purpose**: Display a single scheduled meal.
   - **Used In**: `DaySectionWidget`.
    - **Key Props**: `MealPlanEntry` object, `backgroundColor` (from Plan), `onTap` callback.
    - **Behavior**: Shows lock icon if `!hasRecipeAccess`.
4. **PlanColorPicker**
    - **Purpose**: Select a color for a meal plan.
    - **Used In**: `PlanFormDialog`.
    - **Behavior**: Row of circular color swatches from `AppColors`.

### Reused Components

1. **RecipeList**
    - **Source**: `lib/features/recipes/widgets/recipe_list.dart`.
   - **Used In**: `RecipePickerDialog`.
   - **Adaptations Needed**: Add optional `Function(Recipe)? onRecipeTap` parameter.
       - If `onRecipeTap` is **null**: Perform standard navigation to `RecipeDetailScreen`.
       - If `onRecipeTap` is **provided**: Execute callback and do not navigate.
2. **AddItemsToShoppingListScreen**
    - **Source**: `lib/features/recipes/screens/recipe_to_shopping_list_screen.dart` (Conceptually similar).
    - **Used In**: `ShoppingListGenerationScreen` (Step 3).
    - **Adaptations Needed**: Refactor to generic `AddItemsScreen` that accepts `List<ShoppingListItem>` and optional
      `warningMessages` (List<String>) for the `MaterialBanner`. Support "Check/Uncheck" only (no editing).
3. **SharingDialog**
    - **Source**: `lib/core/widgets/sharing_dialog.dart`.
   - **Used In**: Plan Management (Drawer) via MealPlanSharingDialog wrapper.
   - **Adaptations Needed**: None, MealPlanPermission is mapped to SharedUser DTO in MealPlanSharingService.

## 6. State Management

- **Local State**:
    - `MealPlanningService` (Scoped/Singleton): Manages `selectedWeekStart`, `calendarCache` (Map<Week, Entries>), and
      `loadingState`.
    - `visiblePlanIds`: Read/Write via `PreferencesService`.
  - `MealPlanSharingService` (Created on-demand per dialog): Manages `sharedUsers` for a specific plan ID, created when
    Share is tapped, disposed when dialog closes. Depends on MealPlanListService to refresh the plans list after
    share/unshare operations.
- **Shared State**:
    - `RecipeRepository`: For recipe details.
    - `ShoppingListRepository`: For adding generated items.
- **Persistence**:
    - **Plan Visibility**: `SharedPreferences` (via `PreferencesService`).
  - **Calendar Data**: In-memory cache only (Online-first). Data is re-fetched on pull-to-refresh or week change.

## 7. UX Considerations

- **Accessibility**:
    - Color contrast for plan text against user-selected plan colors.
    - Screen readers should announce "Restricted" status for locked meals.
- **Responsive Design**:
    - **Mobile**: Agenda View (Week Strip + Vertical List).
- **Performance**:
    - Calendar entries fetched in weekly chunks to reduce API calls when swiping weeks.
    - Optimistic updates for Plan Visibility toggles.
- **Error Handling**:
    - `MaterialBanner` for Shopping List generation warnings (Partial success).
    - Toast notifications for "Access Denied" on restricted recipes.

## 8. Security Considerations

- **Authentication**: All views require authenticated user (JWT).
- **Authorization**:
    - **Plan Actions**: Hide "Delete" option in Drawer if user role is not OWNER.
    - **Entry Editing**: Validation logic checks Editor permissions before showing edit dialog.
- **Data Protection**:
    - UI strictly respects `hasRecipeAccess` flag. Never attempt to fetch Recipe Detail ID if flag is false.
    - Ingredients for unowned recipes are never displayed in the frontend during the generation flow.

## 9. Requirements Mapping

- **US-MP-001 (Create/Manage Plans)** - Side Drawer + `PlanFormDialog` - Allows creation, coloring, and deletion based
  on role.
- **US-MP-002 (View Calendar)** - `MealPlanningScreen` with `DaySectionWidget` list. - Shows colored entries; filters
  based on visibility prefs.
- **US-MP-003 (Recipe Entry + Yield)** - `MealEntryDialog` + `RecipePickerDialog` - Includes Recipe Picker and Serving
  Size integer input.
- **US-MP-004 (Placeholder)** - `MealEntryDialog` - Toggle switch handles mutual exclusivity of Recipe vs Text.
- **US-MP-005 (Sharing)** - `SharingDialog` (Triggered from Drawer) - Reuses existing secure sharing pattern.
- **US-MP-006 (Restricted Access)** - `MealEntryCard` - Lock icon + Tap blocker with Toast message.
- **US-MP-007 (Generate Shop List)** - `ShoppingListGenerationScreen` - Multi-step flow to select scope and target list.
- **US-MP-008 (Export Privacy)** - `AddItemsScreen` (Review Step) - Displays `MaterialBanner` with warnings from API.
- **US-MP-009 (Deleted Recipes)** - `MealEntryCard` - API converts these to Placeholders; UI renders `placeholderText`
  automatically.

## 10. Open Questions

- None. UI architecture aligns with all decisions made in the planning session.