# Mobile App Overview - RecipAI

## Features

- `recipe` - Contains recipe data models, UI screens, and widgets for displaying recipe lists and details
- `extraction` - Recipe extraction functionality supporting both URL extraction via WebView and image extraction via
  camera/gallery. Uses Repository-Service-View architecture with ExtractionRepository and ExtractionService
- `auth` - User authentication using Firebase Authentication with Google Sign-In
- `shopping_list` - Shopping list management with list creation, display, item management, and optimistic UI updates.
  Uses Repository-Service-View architecture with ShoppingListRepository, ShoppingListListService,
  ShoppingListDetailService, and ShoppingListSyncService for background syncing and conflict resolution

## Data Models

### Recipe module

- Recipe (`recipe.dart`) - Basic recipe data model with id and name
- Recipe Detail (`recipe_detail.dart`) - Complex nested structure for detailed recipe information including UserRole
  enum (owner/editor)
- Shared User (`shared_user.dart`) - Data model for recipe sharing API responses containing email and UserRole enum
- User Role (`user_role.dart`) - Enum defining user roles (owner, editor) with API conversion methods for
  uppercase/lowercase handling

### Shopping List module

- Shopping List (`shopping_list.dart`) - Basic shopping list data model with id and name fields
- Shopping List Item (`shopping_list_item.dart`) - Item data model with id, name, quantity, unit, checked, position,
  and version fields for optimistic concurrency control
- Shopping List Operation (`shopping_list_operation.dart`) - Sealed class hierarchy for operation queue (
  AddItemOperation, DeleteItemOperation, MoveItemOperation, CheckItemOperation, UncheckItemOperation,
  UpdateItemOperation) with UUID-based temporary IDs

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
│   │   └── theme.dart                 # App theme and spacing constants
│   ├── shared/                         # Shared/reusable widgets and utilities
│   │   ├── loading_widget.dart        # Loading indicator widget
│   │   ├── api_error_widget.dart      # API error display widget
│   │   ├── error_message_widget.dart  # General error message widget
│   │   ├── error_icon.dart           # Error icon widget
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
│       │   ├── recipe_detail_service.dart # Recipe detail and sharing business logic with ValueNotifier
│       │   ├── recipe_setup.dart       # Dependency injection setup for recipe module
│       │   ├── recipe_list.dart        # Reusable recipe list body widget
│       │   ├── recipe_list_fab.dart    # Reusable recipe list FAB widget
│       │   ├── recipe_to_shopping_list_screen.dart # Add ingredients to shopping list screen
│       │   ├── recipe_to_shopping_list_service.dart # Service for adding ingredients to shopping list
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
│       │   ├── shopping_list_item_widget.dart # Reusable inline-editable item widget
│       │   └── shopping_list_detail_screen.dart # Shopping list detail screen
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

### Using Feature Flags

Feature flags are defined in `core/feature_flags.dart` using `bool.fromEnvironment` to allow runtime configuration.

Available feature flags:

- `shoppingListsEnabled` - Controls visibility of shopping lists feature in the app (environment variable:
  `SHOPPING_LISTS`, default: `false`)

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
          if (FeatureFlags.shoppingListsEnabled)
            Container(
              padding: EdgeInsets.all(16),
              child: Text('Shopping lists feature is enabled!'),
            ),
        ],
      ),
    );
  }
}
```