# UI Architecture for Shopping Lists Management

## 1. Feature Overview

- **Feature Name**: Shopping Lists Management
- **Purpose**: To provide a centralized, integrated, and collaborative solution for creating and managing shopping lists
  within the RecipAI app, with deep integration into the existing recipe system.
- **Target Users**: Registered RecipAI users who save recipes and want a more efficient way to plan their grocery
  shopping, including individuals and households that shop collaboratively.
- **Integration Points**:
    - A new "Shopping" tab in the main application's bottom navigation bar.
    - An "Add to Shopping List" entry point on the existing `Recipe Detail Screen`.
    - An "Add from Recipe" entry point on the new `Shopping List Detail Screen`.
    - The entire feature is conditionally available based on the `shoppingListsEnabled` feature flag, controlled by the
      app's router.

## 2. Views

### View 1: Shopping Lists Screen

- **Path**: `/shopping-lists`
- **Purpose**: To display all shopping lists created by or shared with the user, and to serve as the entry point for
  creating new lists.
- **Trigger/Entry Point**: Tapping the "Shopping" icon in the main bottom navigation bar.
- **Key Data Displayed**:
    - A list of shopping list names.
    - An empty state message ("No shopping lists") when no lists are available.
- **Primary Actions**:
    - Tapping a list to navigate to its detail view.
    - Tapping a Floating Action Button (FAB) to create a new list.
    - Pull-to-refresh to manually reload the list of shopping lists.
- **Navigation**:
    - Navigates to `Shopping List Detail Screen` (`/shopping-lists/:id`).
- **Components Required**:
    - `Scaffold` with `AppBar`
    - `ListView` to display list names
    - `FloatingActionButton`
    - Empty State Widget
    - `LoadingWidget`
    - `ApiErrorWidget`
- **API Endpoints Used**:
    - `[GET] /shopping-lists`: To fetch the list of shopping lists.
    - `[POST] /shopping-lists`: To create a new shopping list.
- **Edge Cases**:
    - **No Lists**: The view displays an informative empty state message and the FAB to encourage list creation.
    - **Network Error**: Displays the standard `ApiErrorWidget` with a "Retry" button.

### View 2: Shopping List Detail Screen

- **Path**: `/shopping-lists/:id`
- **Purpose**: To display and manage the items within a single shopping list.
- **Trigger/Entry Point**: Tapping a list item on the `Shopping Lists Screen`.
- **Key Data Displayed**:
    - The name of the shopping list in the `AppBar`.
    - A list of all items, showing their name, quantity, and unit.
    - A visual distinction for checked items (strikethrough and reduced opacity).
- **Primary Actions**:
    - Add a new item via a persistent `ItemInputWidget` at the bottom.
    - Edit an item in-place by tapping on it, which reveals an `ItemInputWidget`.
    - Mark an item as "checked" or "unchecked" using a checkbox.
    - Delete an item using a dedicated 'X' icon.
    - Access bulk actions ("Uncheck all", "Delete all checked"), sharing, and adding from recipes via a "more options" (
      three-dot) menu.
    - Delete the entire list (OWNER only).
- **Navigation**:
    - Opens the `Shopping List Sharing Dialog`.
    - Navigates to the `Recipe Selection Screen`.
    - Navigates back to the `Shopping Lists Screen` upon list deletion or back-press.
- **Components Required**:
    - `Scaffold` with `AppBar` and `PopupMenuButton` (three-dot menu).
    - `ListView` of `ShoppingListItemWidget`s.
    - A persistent `ItemInputWidget` (in "add" mode) at the bottom of the screen.
    - `ShoppingListSharingDialog` (Modal).
- **API Endpoints Used**:
    - `[GET] /shopping-lists/{id}`: For initial data load and periodic polling (every 10 seconds).
    - `[POST] /shopping-lists/{id}/operations`: To batch and sync all local changes (add, edit, delete, check/uncheck).
    - `[DELETE] /shopping-lists/{id}`: To delete the entire list.
- **Edge Cases**:
    - **Offline Operations**: User actions are queued locally in SharedPreferences and synced on the next successful
      poll.
    - **Sync Failure**: A `SnackBar` notifies the user of the sync failure, but the UI remains optimistic.
    - **Empty List**: The item list area is empty, but the `ItemInputWidget` for adding new items is still visible.

### View 3: Recipe Selection Screen

- **Path**: `/shopping-lists/:id/select-recipe`
- **Purpose**: To allow a user to select a recipe from which to add ingredients to the current shopping list.
- **Trigger/Entry Point**: Tapping "Add from Recipe" in the `Shopping List Detail Screen`'s "more options" menu.
- **Key Data Displayed**:
    - A list of the user's saved recipes.
- **Primary Actions**:
    - Tapping a recipe to proceed to ingredient selection.
- **Navigation**:
    - Navigates to the `Ingredient Selection Screen` for the chosen recipe.
- **Components Required**:
    - `Scaffold` with `AppBar`.
    - `ListView` of `RecipeListItem` widgets.
- **API Endpoints Used**: Uses the existing API to fetch the user's recipes.
- **Edge Cases**:
    - **User has no saved recipes**: An empty state message is displayed.

### View 4: Ingredient Selection Screen

- **Path**: `/recipe/:id/select-ingredients`
- **Purpose**: To allow a user to select which ingredients from a specific recipe they want to add to a shopping list.
- **Trigger/Entry Point**:
    1. Tapping "Add to Shopping List" on the `Recipe Detail Screen`.
    2. Tapping a recipe on the `Recipe Selection Screen`.
- **Key Data Displayed**:
    - A list of all ingredients from the selected recipe.
- **Primary Actions**:
    - Select/deselect individual ingredients via checkboxes.
    - "Select All" / "Deselect All".
    - Confirm selection and proceed to the next step.
- **Navigation**:
    - If entry point was from `Recipe Detail Screen`, it opens the `List Selection Dialog`.
    - If entry point was from `Recipe Selection Screen`, it returns to the `Shopping List Detail Screen`.
- **Components Required**:
    - `Scaffold` with `AppBar`.
    - `ListView` of ingredients with checkboxes.
    - A confirmation button (e.g., "Next" or "Add Ingredients").
- **API Endpoints Used**: None directly. Data is passed in from the previous screen.
- **Edge Cases**:
    - **Recipe has no ingredients**: Displays an empty state message.

## 3. User Journey

### Primary Flow

1. **User taps "Shopping" tab** → `Shopping Lists Screen` is displayed.
2. **User taps a list name** → `Shopping List Detail Screen` is displayed with items for that list.
3. **User types "2 eggs" in the bottom input and taps away** → A new "eggs" item with quantity "2" is optimistically
   added to the list, and an `ADD_ITEM` operation is queued.
4. **User taps the checkbox next to an item** → The item's appearance changes to checked, and a `CHECK_ITEM` operation
   is queued.

### Alternative Flows

- **Add from Recipe (from Recipe Detail)**: `Recipe Detail Screen` → Tap "Add to Shopping List" →
  `Ingredient Selection Screen` → Select ingredients & confirm → `List Selection Dialog` appears → Choose a list →
  Ingredients are added, and user is returned to `Recipe Detail Screen`.
- **Add from Recipe (from Shopping List)**: `Shopping List Detail Screen` → Tap "Add from Recipe" in menu →
  `Recipe Selection Screen` → Select a recipe → `Ingredient Selection Screen` → Select ingredients & confirm →
  Ingredients are added, and user is returned to the `Shopping List Detail Screen`.
- **Create New List**: `Shopping Lists Screen` → Tap FAB → A dialog prompts for a name → User confirms → The new list
  appears on the `Shopping Lists Screen`.
- **Sharing a List**: `Shopping List Detail Screen` → Tap three-dot menu → Tap "Share" → `Shopping List Sharing Dialog`
  opens → User enters an email and shares → User is returned to the `Shopping List Detail Screen`.

### Exit Points

- Users can leave the feature at any time by tapping another icon in the main bottom navigation bar (e.g., "Recipes").
- Standard back-button navigation will unwind the view stack (e.g., from Detail Screen back to List Screen).

## 4. Navigation Structure

- **Entry Points**:
    - Main Bottom Navigation Bar -> `/shopping-lists`
    - `Recipe Detail Screen` -> `/recipe/:id/select-ingredients`
- **Internal Navigation**:
    - `/shopping-lists` -> `/shopping-lists/:id`
    - `/shopping-lists/:id` -> `/shopping-lists/:id/select-recipe` -> `/recipe/:id/select-ingredients` ->
      `/shopping-lists/:id` (return)
- **Exit Points**:
    - Any view within the feature can exit to another primary feature via the bottom navigation bar.
    - The "Add from Recipe" flows conclude by navigating back to their respective starting points (`Recipe Detail` or
      `Shopping List Detail`).
- **Breadcrumbs/Back Navigation**: Standard "back" functionality is supported. The `AppBar` on nested screens will
  contain a back arrow.

## 5. Key Components

### New Components

1. **`ShoppingListItemWidget`**
    - **Purpose**: Displays a single item. Manages a view/edit state. In view state, it shows the item text, its state (
      checked/unchecked) and provides controls for checking, editing, and deleting. In edit state, it shows the
      `ItemInputWidget`.
    - **Used In**: `Shopping List Detail Screen`.
    - **Key Props/Behavior**: Tapping the item toggles its state between showing item details and showing the
      `ItemInputWidget` for editing. Tapping the 'X' icon triggers a delete callback. Tapping the checkbox triggers a
      check/uncheck callback.
2. **`ItemInputWidget`**
    - **Purpose**: A reusable text input for adding or editing a shopping list item. Handles client-side regex parsing
      of name, quantity, and unit.
    - **Used In**: `Shopping List Detail Screen` (for adding) and inside `ShoppingListItemWidget` (for editing).
    - **Key Props/Behavior**: Takes an optional initial `ShoppingListItem` object to populate the field for editing.
      Triggers an `onSave(String value)` callback on focus loss.
3. **`ListSelectionDialog`**
    - **Purpose**: A simple modal dialog to select a destination shopping list.
    - **Used In**: Part of the "Add from Recipe (from Recipe Detail)" flow.
    - **Key Props/Behavior**: Fetches and displays a list of shopping list names. Returns the ID of the selected list.
4. **`ShoppingListSharingDialog`**
    - **Purpose**: A modal dialog for inviting users and managing existing collaborators for a list.
    - **Used In**: `Shopping List Detail Screen`.
    - **Key Props/Behavior**: Functionally a clone of the existing `RecipeSharingDialog` but adapted for shopping list
      endpoints and data models. Conditionally renders "unshare" buttons based on user roles.

### Reused Components

1. **`LoadingWidget`**
    - **Source**: `shared/loading_widget.dart`
    - **Used In**: `Shopping Lists Screen`.
    - **Adaptations Needed**: None.
2. **`ApiErrorWidget`**
    - **Source**: `shared/api_error_widget.dart`
    - **Used In**: `Shopping Lists Screen`.
    - **Adaptations Needed**: None.
3. **`RecipeListItem`**
    - **Source**: `features/recipe/recipe_list_item.dart`
    - **Used In**: `Recipe Selection Screen`.
    - **Adaptations Needed**: None.

## 6. State Management

- **Local State**: The editing state of a `ShoppingListItemWidget` (i.e., whether it's currently a text input) is
  managed locally via `StatefulWidget`.
- **Shared State**: A new `ShoppingListDetailService` (lazy singleton) will manage the state of the currently viewed
  list using a `ValueNotifier`. This service will handle optimistic updates, background polling, and queuing/sending
  operations from SharedPreferences. The `ShoppingListsScreen` will use its own service.
- **Persistence**: An operation queue will be persisted in SharedPreferences to support offline changes. This queue is
  flushed to the API before each state refresh on the detail screen.

## 7. UX Considerations

- **Accessibility**: Initial implementation will defer advanced accessibility features. Standard Flutter widgets will
  provide baseline support.
- **Responsive Design**: The design is mobile-first and not intended for larger screens in this iteration.
- **Performance**: Optimistic UI updates ensure the app feels responsive even with network latency. Background polling
  keeps data fresh without requiring user action.
- **Error Handling**: Full-screen errors (e.g., initial load failure) will use the `ApiErrorWidget`. Non-blocking
  errors (e.g., background sync failure) will be communicated via a temporary `SnackBar`.

## 8. Security Considerations

- **Authentication**: All views and API interactions within this feature require the user to be authenticated. The
  router will enforce this.
- **Authorization**: UI elements for destructive or administrative actions (e.g., "Delete list" menu item, unsharing a
  user) will be conditionally rendered based on the user's role (OWNER/EDITOR) as returned by the API. This prevents
  users from seeing actions they are not permitted to perform.
- **Data Protection**: No sensitive data is handled beyond user email addresses in the sharing dialog, which is
  consistent with existing app patterns.

## 9. Requirements Mapping

- **US-SL-001 (Create a new shopping list)** - `Shopping Lists Screen` FAB -> Create dialog.
- **US-SL-002 (Manually add an item)** - `Shopping List Detail Screen` -> `ItemInputWidget` in "add" mode.
- **US-SL-003 (Add ingredients from a saved recipe)** - Addressed by two flows: one starting from
  `Recipe Detail Screen`, one from `Shopping List Detail Screen`.
- **US-SL-004 (Check and uncheck shopping list items)** - `ShoppingListItemWidget` checkbox.
- **US-SL-005 (Reorder items)** - Not implemented in this version, as per the API plan.
- **US-SL-006 (Share a shopping list)** - `Shopping List Detail Screen` three-dot menu -> `ShoppingListSharingDialog`.
- **US-SL-007 (Collaborate in near real-time)** - Addressed by the 10-second polling mechanism on the
  `Shopping List Detail Screen`.
- **US-SL-008 (Leave a shared shopping list)** - `ShoppingListSharingDialog` (unsharing self).
- **US-SL-009 (Delete a shopping list)** - `Shopping List Detail Screen` three-dot menu (OWNER only).
- **US-SL-010 (Manage list with bulk actions)** - `Shopping List Detail Screen` three-dot menu options.
- **US-SL-011 (Full offline list management)** - Addressed by the SharedPreferences operation queue.
- **US-SL-012 (Secure list access)** - Addressed by router authentication guards and conditional UI rendering based on
  OWNER/EDITOR roles.

## 10. Open Questions

- None. All planning questions have been resolved.
