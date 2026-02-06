# Mobile App Overview - RecipAI

## Features

- `recipe` - Contains recipe data models, UI screens, and widgets for displaying recipe lists and details. Includes
  horizontal scrollable chip-based filtering to view recipes by collection: "All Recipes" (default), "Unassigned", or
  specific collection. Features client-side fuzzy search bar for filtering recipes by name.
- `recipe/collection` - Recipe collections management for organizing recipes into collections. Uses
  Repository-Service-View architecture with RecipesCollectionRepository and RecipesCollectionListService
- `extraction` - Recipe extraction functionality supporting both URL extraction via WebView and image extraction via
  camera/gallery. URL extraction screen intelligently detects domain patterns and performs Google searches for
  non-URL inputs. Uses Repository-Service-View architecture with ExtractionRepository and ExtractionService
- `auth` - User authentication using Firebase Authentication with Google Sign-In
- `shopping_list` - Shopping list management with list creation, display, item management, and optimistic UI updates.
  Uses Repository-Service-View architecture with ShoppingListRepository, ShoppingListListService,
  ShoppingListDetailService (with bulk operations: deleteAllCheckedItems, uncheckAllItems), and
  ShoppingListSyncService for background syncing and conflict resolution
- `planning` - Meal planning calendar management with weekly agenda view for viewing meal plan entries across multiple
  plans. Includes plan management drawer with create/edit/delete functionality via unified PlanFormDialog, role-based
  actions (delete requires OWNER role), and local visibility toggles for filtering calendar display. Features meal entry
  management supporting recipe entries (with serving size) and placeholder entries (text-only), and full CRUD
  operations (create, edit, delete) with calendar refresh.

## Data Models

### Recipe module

- Recipe (`recipe.dart`) - Basic recipe data model with id, name, and optional thumbnailUrl
- Recipe Detail (`recipe_detail.dart`) - Complex nested structure for detailed recipe information including ingredients,
  instructions, source url, serving size, UserRole enum (owner/editor), and optional images (RecipeImage class with id,
  url, and thumbnailUrl fields), collectionId and collectionName fields for recipe-to-collection assignment
- Recipe Image Input (`recipe_image_input.dart`) - Data model for managing recipe images during creation/editing.
- Initial Recipe Form Data (`initial_recipe_form_data.dart`) - Wrapper class for passing recipe data for recipe form
  widget prefilling
- Shared User (`shared_user.dart`) - Data model for recipe sharing API responses containing email and UserRole enum
- User Role (`user_role.dart`) - Enum defining user roles (owner, editor) with API conversion methods for
  uppercase/lowercase handling
- Recipes Collection (`collection/recipes_collection.dart`) - Data model for recipe collections with id and name fields

### Shopping List module

- Shopping List (`shopping_list.dart`) - Basic shopping list data model with id and name fields
- Shopping List Item (`shopping_list_item.dart`) - Item data model with id, name, quantity, unit, checked, position,
  and version fields for optimistic concurrency control
- Shopping List Operation (`shopping_list_operation.dart`) - Sealed class hierarchy for operation queue (
  AddItemOperation, DeleteItemOperation, MoveItemOperation, CheckItemOperation, UncheckItemOperation,
  UpdateItemOperation) with UUID-based temporary IDs

### Meal Planning module

- Meal Plan (`planning/meal_plan.dart`) - Data model for meal plans with id, name, color (Flutter Color type), role
  (UserRole enum), and createdAt fields.
- Meal Plan Permission (`planning/meal_plan_permission.dart`) - Permission data model for meal plan sharing with email
  and UserRole enum fields
- Meal Plan Calendar Entry (`planning/meal_plan_calendar_entry.dart`) - Data model for individual calendar entries with
  plan metadata, recipe information, or placeholder text
- Meal Plan Calendar Data (`planning/meal_plan_calendar_data.dart`) - Data model for grouped calendar entries organized
  by date

### Shared Utilities

- Color Extension (`shared/extensions.dart`) - Extension methods on Flutter Color class for API hex string conversion:
  `toHexString()` converts Color to "#RRGGBB" format, `fromHexString()` creates Color from hex string.

## Codebase Structure

```
mobile/
├── lib/
│   ├── main.dart                       # RecipAI app entry point with DI setup
│   ├── core/                           # Core services and configuration
│   │   ├── routes.dart                 # Go router configuration with AppRoute enum and simple GoRoute structure
│   │   ├── main_screen.dart            # Main screen with embedded bottom navigation
│   │   ├── app_config.dart            # Application configuration
│   │   ├── async_value.dart           # AsyncValue sealed class (Loading/Data/Error)
│   │   ├── get_it.dart                # Global GetIt instance
│   │   ├── feature_flags.dart         # Feature flags configuration using environment variables
│   │   ├── preferences_service.dart   # SharedPreferences wrapper for local storage (recipe filter, plan visibility)
│   │   ├── theme.dart                 # App theme and spacing constants
│   │   └── widgets/                    # Reusable widgets shared across features
│   │       └── sharing_dialog.dart    # Generic sharing dialog with SharedUser DTO
│   ├── shared/                         # Shared/reusable widgets and utilities
│   │   ├── loading_widget.dart        # Loading indicator widget
│   │   ├── api_error_widget.dart      # API error display widget
│   │   ├── error_message_widget.dart  # General error message widget
│   │   ├── error_icon.dart           # Error icon widget
│   │   ├── serving_size_input.dart   # Reusable serving size input widget with increment/decrement controls
│   │   ├── extensions.dart           # Extension methods (IsoDateFormat for DateTime, ColorExtension for Color)
│   │   └── user_role.dart            # UserRole enum with API conversion methods
│   └── features/                       # Feature modules
│       ├── auth/                       # "authentication" feature
│       │   ├── auth_repository.dart    # Abstract auth repository interface with FirebaseAuthRepository implementation
│       │   ├── auth_service.dart       # Auth business logic with ValueNotifier for state management
│       │   ├── auth_setup.dart         # Dependency injection setup for auth module
│       │   └── login_screen.dart       # Login UI with constructor injection
│       ├── recipe/                     # "recipe" feature
│       │   ├── recipe_repository.dart  # Recipe data access layer with sharing operations
│       │   ├── recipe_list_service.dart # Recipe list business logic with ValueNotifier
│       │   ├── recipe_filter_bar.dart  # Horizontal chip-based filter widget for collections
│       │   ├── recipe_search_bar.dart  # Search bar widget with fuzzy matching
│       │   ├── recipe_detail_service.dart # Recipe detail and sharing business logic with ValueNotifier
│       │   ├── recipe_setup.dart       # Dependency injection setup for recipe module
│       │   ├── recipe_list.dart        # Reusable recipe list body widget with filter bar and search. Requires onRecipeTap callback, uses local search state
│       │   ├── recipe_list_fab.dart    # Reusable recipe list FAB widget
│       │   ├── recipe_list_item.dart   # Recipe list item widget with thumbnail support
│       │   ├── recipe_picker_dialog.dart # Dialog for selecting recipes using RecipeList component
│       │   ├── recipe_image_carousel.dart # Image carousel widget with PageView, pagination, and tap-to-zoom
│       │   ├── recipe_image_fullscreen_viewer.dart # Fullscreen zoomable image viewer using photo_view package
│       │   ├── recipe_image_input.dart # Data model for managing recipe images (new/existing)
│       │   ├── recipe_image_manager.dart # Widget for image upload, reordering, and removal
│       │   ├── initial_recipe_form_data.dart # Wrapper for passing extracted recipe data with images and source URL
│       │   ├── source_link_widget.dart # Clickable source URL widget with url_launcher integration
│       │   ├── recipe_to_shopping_list_screen.dart # Add ingredients to shopping list screen
│       │   ├── recipe_to_shopping_list_service.dart # Service for adding ingredients to shopping list
│       │   ├── collection/             # Recipe collections sub-feature
│       │   │   ├── recipes_collection.dart # Recipes collection data model
│       │   │   ├── recipes_collection_repository.dart # Collections data access layer
│       │   │   ├── recipes_collection_list_service.dart # Collections list business logic with ValueNotifier
│       │   │   ├── recipes_collection_setup.dart # Dependency injection setup for collections
│       │   │   ├── recipes_collection_list_screen.dart # Collections list screen with CRUD operations
│       │   │   ├── recipes_collection_list_item.dart # Reusable collection list item widget
│       │   │   └── recipes_collection_rename_dialog.dart # Dialog for renaming collections
│       │   └── ...                     # Other screens, models, and widgets
│       ├── shopping_list/              # "shopping list" feature
│       │   ├── shopping_list.dart      # Shopping list data model
│       │   ├── shopping_list_item.dart # Shopping list item data model
│       │   ├── shopping_list_repository.dart # Shopping list data access layer
│       │   ├── shopping_list_list_service.dart # Shopping list list business logic with ValueNotifier
│       │   ├── shopping_list_detail_service.dart # Shopping list detail business logic with optimistic updates
│       │   ├── shopping_list_sync_service.dart # Background sync service with operation queue and conflict handling
│       │   ├── shopping_list_operation.dart # Operation models for optimistic UI updates
│       │   ├── shopping_list_setup.dart # Dependency injection setup for shopping list module
│       │   ├── shopping_list_list.dart # Reusable shopping list body widget
│       │   ├── shopping_list_list_fab.dart # Reusable shopping list FAB widget
│       │   ├── shopping_list_item_widget.dart # Reusable inline-editable item widget for existing items
│       │   ├── shopping_list_item_add_widget.dart # Dedicated widget for adding new items
│       │   └── shopping_list_detail_screen.dart # Shopping list detail screen
│       ├── planning/                    # "meal planning" feature
│       │   ├── meal_plan.dart           # Meal plan data model
│       │   ├── meal_plan_calendar_entry.dart # Meal plan entry data model
│       │   ├── meal_plan_calendar_data.dart # Calendar data model
│       │   ├── meal_entry_form_result.dart # Result model for meal entry form dialog
│       │   ├── meal_plan_sharing_service.dart # Meal plan sharing service with ValueNotifier, depends on MealPlanListService
│       │   ├── meal_plan_permission.dart # Permission data model for sharing
│       │   ├── meal_plan_repository.dart # Meal plan data access layer with create/update/fetch operations
│       │   ├── meal_plan_list_service.dart # Plan list business logic with create/update methods and ValueNotifier
│       │   ├── meal_plan_sharing_dialog.dart # Meal plan sharing dialog wrapper
│       │   ├── meal_plan_visibility_service.dart # Visibility toggles with local persistence
│       │   ├── meal_plan_calendar_service.dart # Calendar business logic with week navigation and entry CRUD
│       │   ├── meal_plan_setup.dart     # Dependency injection setup for planning module
│       │   ├── meal_plan_calendar_screen.dart # Agenda view screen
│       │   ├── meal_plan_calendar_fab.dart # FAB widget for adding meal entries
│       │   ├── meal_plan_drawer.dart    # Side drawer for plan management with create/edit handlers
│       │   ├── plan_form_dialog.dart    # Unified create/edit dialog with PlanFormResult model
│       │   ├── meal_entry_form_dialog.dart # Modal dialog for creating/editing meal entries with recipe/placeholder modes
│       │   ├── plan_color_picker.dart   # Reusable color picker widget with 12 predefined colors
│       │   ├── plan_list_tile.dart      # Plan list item widget with checkbox and menu
│       │   ├── week_strip.dart          # Week navigation header widget with tappable label to jump to today
│       │   ├── day_section.dart         # Day section widget with entry list
│       │   └── meal_entry_calendar_card.dart # Individual meal entry card widget
│       └── extraction/                 # "extraction" feature
│           ├── extraction_repository.dart # API communication layer for extraction endpoints
│           ├── extraction_service.dart # Business logic layer for extraction operations
│           ├── extraction_setup.dart   # Dependency injection setup for extraction module
│           ├── extracted_recipe.dart   # Data models (ExtractedRecipe, ExtractedIngredient, ExtractedInstruction)
│           ├── url_extraction_screen.dart # WebView-based URL extraction UI
│           ├── image_extraction_screen.dart # Camera/Gallery image extraction UI
│           ├── extraction_dialog.dart  # Simple dialog widget
│           └── web_recipe_extractor.dart # Utility for HTML extraction
├── assets/
│   └── config/
│       └── app_config.json            # App configuration file
├── android/                            # Android-specific configuration and native code
├── test/
│   └── widget_test.dart               # Smoke test
├── pubspec.yaml                       # Flutter dependencies and project configuration
└── analysis_options.yaml             # Dart/Flutter linting rules
```

## Usage Patterns

### Using Preferences Service

`PreferencesService` provides a type-safe wrapper around SharedPreferences for local data persistence. It's registered
as
a singleton in `main.dart` and available via GetIt dependency injection.

**Current supported preferences:**

- Recipe filter collection ID - Persists the selected collection filter across app restarts
- Plan visibility toggles - Persists which meal plans are visible on calendar (Map<String, bool> stored as JSON)

**Usage example in services:**

```dart
class MyService {
  final PreferencesService _preferencesService;

  MyService({required PreferencesService preferencesService})
          : _preferencesService = preferencesService;

  void loadSavedState() {
    // Read preference (synchronous - SharedPreferences caches values)
    final savedFilter = _preferencesService.getRecipeFilterCollectionId();
  }

  Future<void> saveState(String? filterId) async {
    // Write preference (asynchronous)
    await _preferencesService.setRecipeFilterCollectionId(filterId);
  }
}
```

**Adding new preferences:**

1. Add a constant key to `PreferencesService` (e.g., `static const String _myKey = 'my_preference_key';`)
2. Add getter/setter methods following the existing pattern
3. Inject `PreferencesService` into services that need the preference

### Using Feature Flags

Feature flags are defined in `core/feature_flags.dart` using `bool.fromEnvironment` to allow runtime configuration.

Available feature flags:

- None

```dart
import 'package:mobile/core/feature_flags.dart';

class _MyScreenState extends State<MyScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Column(
        children: [
          // Always visible content
          Text('Main content'),

          // Conditionally rendered based on feature flag
          if (FeatureFlags.featureEnabled)
            Container(
              padding: EdgeInsets.all(16),
              child: Text('Feature is enabled!'),
            ),
        ],
      ),
    );
  }
}
```