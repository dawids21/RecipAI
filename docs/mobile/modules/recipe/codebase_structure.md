# Recipe — Codebase Structure

```
mobile/lib/features/recipe/
├── recipe_repository.dart              # Recipe data access layer with sharing operations
├── recipe_list_service.dart            # Recipe list business logic with ValueNotifier
├── recipe_detail_service.dart          # Recipe detail and sharing business logic with ValueNotifier
├── recipe_setup.dart                   # Dependency injection setup for recipe module
├── recipe_filter_bar.dart              # Horizontal chip-based filter widget for collections
├── recipe_search_bar.dart              # Search bar widget with fuzzy matching
├── recipe_grid.dart                    # Reusable recipe grid body widget with filter bar and search. Requires onRecipeTap callback, uses local search state
├── recipe_grid_item.dart               # Recipe grid item card widget with full-width image and title
├── recipe_list_fab.dart                # Reusable recipe list FAB widget
├── recipe_picker_dialog.dart           # Dialog for selecting recipes using RecipeGrid component
├── recipe_image_carousel.dart          # Image carousel widget with PageView, pagination, and tap-to-zoom
├── recipe_image_fullscreen_viewer.dart # Fullscreen zoomable image viewer using photo_view package
├── recipe_image_input.dart             # Data model for managing recipe images (new/existing)
├── recipe_image_manager.dart           # Widget for image upload, reordering, and removal
├── initial_recipe_form_data.dart       # Wrapper for passing extracted recipe data with images and source URL
├── source_link_widget.dart             # Clickable source URL widget with url_launcher integration
├── recipe_to_shopping_list_screen.dart # Add ingredients to shopping list screen
├── recipe_to_shopping_list_service.dart # Service for adding ingredients to shopping list
├── collection/                         # Recipe collections sub-feature
│   ├── recipes_collection.dart         # Recipes collection data model
│   ├── recipes_collection_repository.dart # Collections data access layer
│   ├── recipes_collection_list_service.dart # Collections list business logic with ValueNotifier
│   ├── recipes_collection_setup.dart   # Dependency injection setup for collections
│   ├── recipes_collection_list_screen.dart # Collections list screen with CRUD operations
│   ├── recipes_collection_list_item.dart # Reusable collection list item widget
│   └── recipes_collection_rename_dialog.dart # Dialog for renaming collections
└── ...                                 # Other screens, models, and widgets
```

```
mobile/test/features/recipe/
└── main_screen_recipes_tab_widget_test.dart # Widget tests for the Recipes tab on MainScreen
```
