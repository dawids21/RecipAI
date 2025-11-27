# UI Architecture for Recipe Collections

## 1. Feature Overview

- **Feature Name**: Recipe Collections
- **Purpose**: Allows users to organize recipes into folders, filter their library by these folders, and share entire
  collections for collaborative meal planning.
- **Target Users**: Users with growing recipe libraries and groups/families who plan meals together.
- **Integration Points**:
    - **Main Screen (Recipes Tab)**: Adds filtering capabilities.
    - **App Bar**: Adds entry point for management.
    - **Recipe Forms**: Adds assignment logic during creation/editing.

## 2. Views

### View 1: Main Screen (Recipes Tab Updates)

- **Path**: `/` (AppRoute.main - Tab 0)
- **Purpose**: Displays the list of recipes with the ability to filter by collection.
- **Trigger/Entry Point**: App launch or Bottom Navigation "Recipes" tab.
- **Key Data Displayed**:
    - Horizontal scrollable list of chips: "All Recipes", "Unassigned", [User Collections...].
    - Filtered list of recipes.
- **Primary Actions**:
    - **Select Filter**: Tapping a chip updates the recipe list.
    - **Manage Collections**: Accessed via AppBar overflow menu (Three dots).
    - **Create Recipe**: Standard FAB (context-aware of selected filter).
- **Navigation**:
    - Tap Recipe -> Detail Screen.
    - Overflow Menu -> Manage Collections Screen.
- **Components Required**:
    - `CollectionFilterBar` (New).
    - `RecipeList` (Existing - Updated to accept filter params).
- **API Endpoints Used**:
    - `GET /collections` (To populate filter bar).
    - `GET /recipes` (With `collectionId` or `unassigned` query params).
- **Edge Cases**:
    - If the active collection filter is deleted/left via the Manage screen, the view resets to "All Recipes" upon
      return.

### View 2: Manage Collections Screen

- **Path**: `/collections`
- **Purpose**: Central hub for creating, renaming, deleting, and sharing collections.
- **Trigger/Entry Point**: Main Screen AppBar Overflow Menu -> "Manage Collections".
- **Key Data Displayed**:
    - Alphabetically sorted list of user-created collections (excluding "Unassigned").
    - Loading shimmer or "No collections found" empty state.
- **Primary Actions**:
    - **Create**: FAB opens `CreateCollectionDialog`.
    - **Row Actions**: Three-dot menu on each tile:
        - **Rename**: Opens `RenameCollectionDialog` (Owner/Editor).
        - **Share**: Opens `SharingDialog` (Owner/Editor).
        - **Delete**: Opens confirmation dialog (Owner only).
        - **Leave**: Opens confirmation dialog (Editor only).
- **Navigation**:
    - Back button -> Returns to Main Screen.
- **Components Required**:
    - `CollectionListTile` (Standard ListTile with actions).
    - `ApiErrorWidget` (Existing).
- **API Endpoints Used**:
    - `GET /collections`
    - `POST /collections`
    - `DELETE /collections/{uuid}`
    - `POST /collections/{uuid}/unshare` (For leaving).

### View 3: Create/Edit Recipe Screen (Updated)

- **Path**: `/recipes/create` OR `/recipes/:id/edit`
- **Purpose**: Assign or re-assign a recipe to a collection.
- **Trigger/Entry Point**: FAB on Main Screen or "Edit" on Detail Screen.
- **Key Data Displayed**:
    - Standard recipe form fields.
    - **New**: Dropdown menu "Collection" (Default: "No Collection").
- **Primary Actions**:
    - **Select Collection**: Choose from list of available collections.
    - **Save**: Persists recipe with `collectionId`.
- **Components Required**:
    - `RecipeFormWidget` (Updated with Dropdown).
- **API Endpoints Used**:
    - `GET /collections` (To populate dropdown).
    - `POST /recipes` (With `collectionId`).
    - `PUT /recipes/{uuid}` (With `collectionId`).

### View 4: Generic Sharing Dialog

- **Path**: Modal Dialog (Overlay)
- **Purpose**: Manage access to a specific collection.
- **Trigger/Entry Point**: "Share" action from Manage Collections screen.
- **Key Data Displayed**:
    - Title: "Share [Collection Name]".
    - List of current users (Email + Role).
- **Primary Actions**:
    - **Add User**: Text input for email + "Add" button.
    - **Remove User**: "X" or Trash icon next to existing users (cannot remove self).
- **Components Required**:
    - `SharingDialog` (Refactored generic component).
- **API Endpoints Used**:
    - `GET /collections/{uuid}/shared_users`
    - `POST /collections/{uuid}/share`
    - `POST /collections/{uuid}/unshare`

## 3. User Journey

### Primary Flow: Organization & Filtering

1. **Create Collection**: User goes to `Main` -> Overflow Menu -> `Manage Collections` -> FAB -> Enters "Dinner
   Ideas" -> Saves.
2. **Assign Recipe**: User taps `Back` -> Taps `Create Recipe` -> Fills details -> Selects "Dinner Ideas" in dropdown ->
   Saves.
3. **Filter View**: User returns to `Main` -> Taps "Dinner Ideas" chip -> List updates to show only the new recipe.

### Alternative Flows

- **Sharing**: User in `Manage Collections` -> Taps Menu on "Dinner Ideas" -> `Share` -> Enters spouse's email. Spouse
  opens app -> Sees "Dinner Ideas" in their filter bar.
- **Contextual Creation**: User selects "Keto" filter on Main Screen -> Taps `Create Recipe` -> Form opens with "Keto"
  already selected in the dropdown.
- **Leaving**: Editor goes to `Manage Collections` -> Taps Menu on "Shared List" -> `Leave` -> Confirms. Collection
  disappears from their list.

### Exit Points

- **Logout**: Moved to Main Screen Overflow Menu.
- **Back Navigation**: Standard top-left back arrow from `/collections` returns to `/`.

## 4. Navigation Structure

- **Entry Points**:
    - **Main App Bar Overflow**: "Manage Collections" -> `/collections`.
- **Internal Navigation**:
    - `/collections` is a terminal screen (stack push). It does not navigate deeper except for Dialog overlays.
- **Exit Points**:
    - `Pop` stack (Back button) returns to Main Screen.
- **Breadcrumbs/Back Navigation**:
    - Standard Material `AppBar` back button.

## 5. Key Components

### New Components

1. **CollectionFilterBar**
    - **Purpose**: Horizontal list of chips for filtering.
    - **Used In**: `MainScreen` (Recipes Tab).
    - **Key Props**: `List<Collection>`, `selectedId`, `onSelected(id)`.
2. **SharingDialog** (Generic Refactor)
    - **Purpose**: Reusable dialog for managing ACLs (Recipes, Collections, Shopping Lists).
    - **Used In**: `ManageCollectionsScreen`, `RecipeDetailScreen`.
    - **Key Props**: `entityName`, `List<SharedUser>`, `onAddUser(email)`, `onRemoveUser(email)`.

### Reused Components

1. **RecipeList**
    - **Source**: Existing `recipe_list.dart`.
    - **Used In**: `MainScreen`.
    - **Adaptations Needed**: Accept optional `collectionId` and `bool showUnassigned` to pass to the service/API.
2. **RecipeFormWidget**
    - **Source**: Existing `recipe_form_widget.dart`.
    - **Used In**: `CreateRecipeScreen`, `EditRecipeScreen`.
    - **Adaptations Needed**: Add `DropdownButtonFormField` for collection selection.

## 6. State Management

- **Local State**:
    - `CollectionFilterBar`: Scroll position.
    - `RecipeFormWidget`: Selected value in Collection dropdown.
- **Shared State**:
    - `RecipeListService`: Holds `List<Collection>`, `currentFilterId`, and `List<Recipe>`.
    - **Syncing**: The `ManageCollectionsScreen` and `MainScreen` observe the same service. Creating a collection in the
      Manager updates the list in the Service, which automatically updates the Filter Bar on the Main Screen.
- **Persistence**:
    - Active filter selection is transient (resets on app restart).

## 7. UX Considerations

- **Accessibility**:
    - Filter chips must be large enough for touch targets.
    - Dropdowns must be labeled clearly.
- **Responsive Design**:
    - Filter bar uses `ListView.horizontal` to handle any number of collections without overflowing.
- **Performance**:
    - Use `Shimmer` loading states when switching filters.
    - Optimistic UI updates for Renaming (update local state immediately while waiting for API).
- **Error Handling**:
    - "Last Save Wins" logic for naming conflicts.
    - Toast notifications for "Permission Denied" if a user tries to access a collection they were removed from.

## 8. Security Considerations

- **Authentication**: All `/collections` endpoints require valid Bearer token.
- **Authorization**:
    - UI hides "Delete" option for Editors.
    - Frontend should handle 403 Forbidden gracefully if a user navigates to a collection they no longer have access to.
- **Data Protection**:
    - Sharing is strictly "Push" (email match). No public link sharing.

## 9. Requirements Mapping

- **US-COL-001 (Create)** - `ManageCollectionsScreen` FAB - Opens creation dialog, calls API, updates list.
- **US-COL-002 (Filter)** - `CollectionFilterBar` - Chips trigger service filter method.
- **US-COL-003 (Assign Create)** - `RecipeFormWidget` - Dropdown allows selection, defaults to active filter.
- **US-COL-004 (Move)** - `RecipeFormWidget` - Changing dropdown updates `collectionId`.
- **US-COL-005 (Share)** - `SharingDialog` - Input field calls `share` endpoint.
- **US-COL-008 (Rename)** - `ManageCollectionsScreen` - Tile menu option updates name globally.
- **US-COL-009/010 (Delete/Leave)** - `ManageCollectionsScreen` - Context-aware menu option (Owner=Delete,
  Editor=Leave).

## 10. Open Questions

- None.