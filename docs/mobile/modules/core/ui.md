# Core — UI

## Screens

### Main Screen

- Main Screen (`main_screen.dart`) - The signed-in shell, titled "RecipAI":
  - *Tabs*: Recipes, Planning and Shopping (see Bottom Navigation Bar), each with its own FAB.
  - *App bar*: on the Planning tab, a "Manage Plans" button opens the plan drawer
    (`docs/mobile/modules/planning/ui.md`). The overflow menu holds "Invites" (with the pending-invite indicator —
    `docs/mobile/modules/invites/ui.md` → Indicator), "Recipes collections", "Generate shopping list", "Logout" and
    "Send logs", which shares the current log file (Android only).
  - *Logout*: asks "Are you sure you want to logout?" before signing out.

## Shared Widgets

- **Loading Widget** (`loading_widget.dart`) - Loading indicator for async operations.
- **API Error Widget** (`api_error_widget.dart`) - Error display with a Retry button for failed API calls.
- **Error Icon** (`error_icon.dart`) - The app's standard error icon.
- **Serving Size Input** (`serving_size_input.dart`) - Serving-size spinner with increment and decrement buttons;
  minimum 1, so decrement is disabled at 1. Used in the recipe form and the meal entry dialog.

## Navigation

### Route Structure

#### Authentication Routes

- `/login` - Login Screen (`docs/mobile/modules/auth/ui.md`); reachable only when signed out

#### Main App Routes

- `/` - Main Screen
  - Tab 1: Recipes (default)
  - Tab 2: Planning, with the plan drawer on the right
  - Tab 3: Shopping
- `/recipes/url-extraction` - URL extraction screen (nested route)
- `/recipes/image-extraction` - Image extraction screen (nested route)
- `/recipes/create` - Recipe creation screen (nested route)
- `/recipes/:id` - Recipe detail screen (nested route)
- `/recipes/:id/edit` - Recipe edit screen (nested route)
- `/recipes/:id/to-shopping-list` - Add ingredients to shopping list screen (nested route)
- `/recipes/picker` - Recipe picker screen (nested route)
- `/recipes-collections` - Recipe collections list screen
- `/invites` - Pending invites list screen
- `/shopping-list-generation` - Shopping list generation wizard screen
- `/shopping-lists/:id` - Shopping list detail screen

### Bottom Navigation Bar

The Main Screen's bottom navigation bar has three tabs:

- **Recipes** - recipe management
- **Planning** - meal planning agenda
- **Shopping** - shopping lists

The selected tab is not kept across app restarts. Sub-routes (like recipe detail) open on top of the Main Screen.

### Route Protection

Every route except `/login` needs a signed-in user; the redirect rule is in `module.md` → Services and State. The
sign-in and sign-out flow is in `docs/mobile/modules/auth/ui.md` → Flow.
