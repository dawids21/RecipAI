# Core — UI

## Screens

### Main Screen

- Main Screen (`main_screen.dart`) - Main application screen with embedded bottom navigation, managing recipe,
  planning, and shopping list tabs. Displays RecipeGrid, MealPlanCalendarScreen, or ShoppingListList widgets based on
  selected tab, with corresponding FABs (RecipeListFab, MealPlanCalendarFab, or ShoppingListListFab). Features
  PopupMenuButton in AppBar with "Recipes collections", "Generate shopping list", and logout options. When Planning tab
  is active and meal planning feature flag is enabled, shows "Manage Plans" IconButton before the overflow menu.

## Shared Widgets

- **Loading Widget** (`loading_widget.dart`) - Reusable loading indicator for async operations
- **API Error Widget** (`api_error_widget.dart`) - Reusable error display with retry functionality for API failures
- **Error Icon** (`error_icon.dart`) - Standardized error icon (64px, theme-based color)
- **Serving Size Input** (`serving_size_input.dart`) - Reusable spinner-style control for serving size input with
  increment/decrement buttons. Minimum value is 1 (decrement disabled at 1). Uses IconButton.outlined with Material
  icons, displays value using headlineMedium text style. Used in RecipeFormWidget and MealEntryFormDialog.

### Generic Sharing Dialog

- **Implementation**: `lib/core/widgets/sharing_dialog.dart`
- **Purpose**: Reusable dialog for managing ACLs (Recipes, Recipes Collections, Shopping Lists)
- **SharedUser DTO**: Simple UI model with email (String), role (String displayName), isCurrentUser (bool)
- **Usage Pattern**:
    1. Service loads feature-specific Permission models (RecipePermission, RecipesCollectionPermission,
       ShoppingListPermission)
    2. Service wraps Permissions with isCurrentUser flag (RecipeSharedUser, etc.)
    3. Screen creates ValueNotifier<AsyncValue<List<SharedUser>>> for mapped data
    4. Screen sets up listener to map from SharedUser wrapper to SharedUser DTO
    5. Screen passes mapped notifier and callbacks to SharingDialog
    6. Dialog handles UI, validation, and user interactions
    7. Screen handles success/error SnackBar feedback
    8. Screen cleans up listener and disposes mapped notifier on dialog close

## Navigation

### Route Structure

The app uses a simple GoRoute structure with embedded bottom navigation in MainScreen:

#### Authentication Routes

- `/login` - Authentication screen with Google Sign-In (only accessible when unauthenticated)

#### Main App Routes

- `/` - Main screen with embedded bottom navigation (AppRoute.main)
  - Tab 1: Recipes (default) - Displays RecipeGrid widget
  - Tab 2: Planning - Displays MealPlanCalendarScreen widget with right-side drawer for plan management
  - Tab 3: Shopping - Displays ShoppingListList widget
- `/recipes/url-extraction` - URL extraction screen (nested route)
- `/recipes/image-extraction` - Image extraction screen (nested route)
- `/recipes/create` - Recipe creation screen (nested route)
- `/recipes/:id` - Recipe detail screen with dynamic ID parameter (nested route)
- `/recipes/:id/edit` - Recipe edit screen with dynamic ID parameter (nested route)
- `/recipes/:id/to-shopping-list` - Add ingredients to shopping list screen (nested route)
- `/recipes/picker` - Recipe picker screen for selecting a recipe (AppRoute.recipePicker, nested route)
- `/recipes-collections` - Recipe collections list screen (AppRoute.recipesCollections, shown when feature flag enabled)
- `/shopping-list-generation` - Shopping list generation wizard screen (AppRoute.shoppingListGeneration)
- `/shopping-lists/:id` - Shopping list detail screen with dynamic ID parameter (AppRoute.shoppingListDetail)

### Bottom Navigation Bar

The app features an embedded bottom navigation bar (part of MainScreen) with tabs:

- **Recipes** (restaurant_menu icon) - Access recipe management features
- **Planning** (calendar_today icon) - Access meal planning agenda view
- **Shopping** (shopping_cart icon) - Access shopping list features

Tab state is ephemeral and not preserved across app restarts. Navigation to sub-routes (like recipe detail) pushes a new
screen on top of MainScreen.

### Authentication & Route Protection

All routes except `/login` require user authentication. The app automatically redirects:

- Unauthenticated users → `/login`
- Authenticated users on `/login` → `/` (main screen)

### Flow

#### Authentication Flow

1. **App Launch** → Authentication check:
    - **If unauthenticated** → Login Screen (`/login`)
    - **If authenticated** → Main Screen (`/`) showing Recipes tab
2. **Login Screen → Google Sign-In Tap** → Authentication process → Main Screen (`/`)
3. **Main Screen → Logout Tap** → Confirmation dialog → Sign out → Login Screen (`/login`)
