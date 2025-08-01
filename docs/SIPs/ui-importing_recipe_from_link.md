# SIP: UI - Importing Recipe from Link

## Goal

- Build a complete recipe import feature allowing users to import recipes from web pages by entering URLs
- Create a new screen with URL input field, WebView to display the webpage, and FAB button to import recipe
- Add navigation from recipe list screen to import screen via FAB button
- Extract HTML content from the webpage and use the backend API `/extract/text` endpoint to process and save recipes
- After successful import, navigate back to recipe list screen with the new recipe added to the list
- Follow existing app patterns for UI, navigation, error handling, and API integration

## Context

### Documentation and References

- **Feature Request**: `docs/feature-requests/ui-importing_recipe_from_link.md` - Complete feature specification with
  WebView example
- **Mobile App Architecture**: `docs/mobile/mobile.md` - Feature-based modular architecture patterns
- **UI Components**: `docs/mobile/ui.md` - Existing screens, components, and theming system
- **API Documentation**: `docs/backend/api.md` - POST /extract/text endpoint for recipe extraction
- **WebView Flutter Documentation**: https://pub.dev/packages/webview_flutter - Official Flutter WebView plugin
- **WebView Codelab**: https://codelabs.developers.google.com/codelabs/flutter-webview - Implementation guidance
- **HTML Extraction**: https://medium.com/@janithsg/flutter-extract-web-page-html-content-from-webview-1c39e43e675d -
  Best practices for extracting HTML from WebView

### Current Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                       # App entry point
│   ├── core/
│   │   ├── api_service.dart           # API service for backend communication
│   │   ├── app_config.dart            # Application configuration
│   │   └── theme.dart                 # Material Design 3 theming
│   ├── features/
│   │   └── recipe/                    # Recipe feature module
│   │       ├── recipe.dart            # Basic recipe model
│   │       ├── recipe_detail.dart     # Detailed recipe models
│   │       ├── recipe_list_screen.dart # Main recipe list screen
│   │       ├── recipe_detail_screen.dart
│   │       ├── recipe_list_item.dart
│   │       ├── ingredient_bullet.dart
│   │       └── step_number_badge.dart
│   └── shared/
│       ├── api_error_widget.dart      # Reusable error handling
│       ├── error_icon.dart
│       └── loading_widget.dart        # Loading states
├── pubspec.yaml                       # Dependencies (missing webview_flutter)
```

### Desired Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                       # App entry point (unchanged)
│   ├── core/
│   │   ├── api_service.dart           # Updated with extractRecipeFromText method
│   │   ├── app_config.dart            # Application configuration (unchanged)
│   │   └── theme.dart                 # Material Design 3 theming (unchanged)
│   ├── features/
│   │   ├── recipe/                    # Recipe feature module (unchanged)
│   │   └── import/                    # NEW: Import feature module
│   │       ├── import_screen.dart     # Main import screen with WebView
│   │       └── web_recipe_extractor.dart # WebView HTML extraction logic
│   └── shared/
│       ├── api_error_widget.dart      # Reusable error handling (unchanged)
│       ├── error_icon.dart
│       └── loading_widget.dart        # Loading states (unchanged)
├── pubspec.yaml                       # Updated with webview_flutter dependency
```

### Known Gotchas of Our Codebase and Library Quirks

- **WebView Flutter**: Requires `webview_flutter: ^4.4.4` dependency addition to pubspec.yaml
- **JavaScript Mode**: Must set `JavaScriptMode.unrestricted` for HTML extraction to work
- **API Changes**: Modern WebView API uses `WebViewController.loadRequest()` instead of deprecated `loadUrl()`
- **HTML Extraction**: Use `runJavaScriptReturningResult()` with `document.documentElement.outerHTML` for complete HTML
- **Navigation**: Follow existing pattern using `MaterialPageRoute` and `Navigator.push()`
- **Error Handling**: Use existing `ApiErrorWidget` pattern for consistent error states
- **Theme Access**: Always use `final theme = Theme.of(context);` pattern for consistent theming
- **Feature Architecture**: Follow modular pattern - all import-related code in `features/import/` folder
- **API Integration**: Follow existing singleton `ApiService` pattern with try-catch error handling

## Implementation Plan

### Tasks

```
Task 1: Add WebView dependency
  Action: MODIFY
  File: mobile/pubspec.yaml
  Changes:
    - [ ] Add webview_flutter: ^4.4.4 to dependencies section
    - [ ] Run flutter pub get to install dependency

Task 2: Update API service with recipe extraction method
  Action: MODIFY
  File: mobile/lib/core/api_service.dart
  Changes:
    - [ ] Add extractRecipeFromText(String htmlContent) method
    - [ ] Follow existing error handling pattern with try-catch
    - [ ] Return RecipeDetail object on success
    - [ ] Use POST /extract/text endpoint from API documentation

Task 3: Create web recipe extractor utility
  Action: CREATE
  File: mobile/lib/features/import/web_recipe_extractor.dart
  Changes:
    - [ ] Create WebRecipeExtractor class for HTML extraction logic
    - [ ] Implement extractHtmlContent(WebViewController controller) method
    - [ ] Use runJavaScriptReturningResult with document.documentElement.outerHTML
    - [ ] Handle JavaScript execution errors gracefully
    - [ ] Return extracted HTML string

Task 4: Create import screen with WebView
  Action: CREATE
  File: mobile/lib/features/import/import_screen.dart
  Changes:
    - [ ] Create ImportScreen StatefulWidget following existing screen patterns
    - [ ] Add URL input TextField at top with proper validation
    - [ ] Implement WebView with WebViewController below input field
    - [ ] Add FAB for importing recipe (floating action button)
    - [ ] Handle WebView loading states with existing LoadingWidget
    - [ ] Implement error handling with existing ApiErrorWidget
    - [ ] Use AppSpacing constants for consistent spacing
    - [ ] Follow existing theme access pattern

Task 5: Add import functionality to ImportScreen
  Action: MODIFY
  File: mobile/lib/features/import/import_screen.dart
  Changes:
    - [ ] Implement _importRecipe() method in ImportScreen
    - [ ] Extract HTML using WebRecipeExtractor
    - [ ] Call ApiService.extractRecipeFromText() with extracted HTML
    - [ ] Handle loading states during import process
    - [ ] Show success/error messages using SnackBar
    - [ ] Navigate back to recipe list on successful import
    - [ ] Pass imported recipe back to recipe list for immediate display

Task 6: Add FAB to recipe list screen for navigation to import
  Action: MODIFY  
  File: mobile/lib/features/recipe/recipe_list_screen.dart
  Changes:
    - [ ] Add FloatingActionButton to Scaffold
    - [ ] Use Icons.add or Icons.import_export for button icon
    - [ ] Implement navigation to ImportScreen using MaterialPageRoute
    - [ ] Handle returned result from ImportScreen (newly imported recipe)
    - [ ] Refresh recipe list when returning from import screen
    - [ ] Follow existing navigation patterns in the app

Task 7: Update main app with import feature
  Action: MODIFY
  File: mobile/lib/main.dart  
  Changes:
    - [ ] Add import to include import screen (if needed for routing)
    - [ ] Ensure all necessary imports are present
    - [ ] No major changes expected as navigation uses push/pop pattern
```

### Per Task Pseudocode

```dart
// Task 2: API Service Extension
class ApiService {
  static Future<RecipeDetail> extractRecipeFromText(String htmlContent) async {
    try {
      final response = await _client.post(
        Uri.parse('$_baseUrl/extract/text'),
        headers: {'Content-Type': 'application/json'},
        body: json.encode({'text': htmlContent}),
      );
      
      if (response.statusCode == 200) {
        return RecipeDetail.fromJson(json.decode(response.body));
      } else {
        throw Exception('Failed to extract recipe: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Network error while extracting recipe: $e');
    }
  }
}

// Task 3: HTML Extraction Utility
class WebRecipeExtractor {
  static Future<String> extractHtmlContent(WebViewController controller) async {
    try {
      final result = await controller.runJavaScriptReturningResult(
        'document.documentElement.outerHTML'
      );
      return result.toString();
    } catch (e) {
      throw Exception('Failed to extract HTML content: $e');
    }
  }
}

// Task 4-5: Import Screen Structure
class ImportScreen extends StatefulWidget {
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Import Recipe')),
      body: Column(
        children: [
          // URL Input TextField
          TextField(controller: urlController, decoration: ...),
          // WebView
          Expanded(child: WebViewWidget(controller: webController)),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _importRecipe,
        child: Icon(Icons.download),
      ),
    );
  }
  
  void _importRecipe() async {
    setState(() => isImporting = true);
    try {
      final html = await WebRecipeExtractor.extractHtmlContent(webController);
      final recipe = await ApiService.extractRecipeFromText(html);
      Navigator.pop(context, recipe); // Return imported recipe
    } catch (e) {
      // Show error SnackBar
    } finally {
      setState(() => isImporting = false);
    }
  }
}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd mobile/
flutter analyze
dart format lib/ --set-exit-if-changed

# Expected: No errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
cd mobile/
flutter test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Integration Tests

```bash
# Manual testing required for WebView functionality:
cd mobile/
flutter run
# Test the complete import flow:
# 1. Navigate to import screen from recipe list
# 2. Enter a recipe URL (e.g., from food.com, allrecipes.com)
# 3. Verify WebView loads the page correctly
# 4. Import recipe and verify it appears in recipe list
```

## Integration Points

- **Frontend-Backend Integration**: Import screen communicates with backend via `POST /extract/text` endpoint
- **Navigation Integration**: Import screen integrates with existing navigation patterns using MaterialPageRoute
- **Data Integration**: Imported recipes integrate with existing Recipe/RecipeDetail models and API structure
- **UI Integration**: Import feature follows existing theming, spacing, and component patterns

## Documentation

- **Mobile UI Documentation**: Update `docs/mobile/ui.md` to include import screen and navigation flow
- **API Usage**: Import feature uses existing API patterns, no API documentation changes needed
- **Feature Documentation**: The import feature follows existing patterns documented in `docs/mobile/mobile.md`

## Final Validation Checklist

- [ ] Correct syntax (flutter analyze passes)
- [ ] Correct style (dart format passes)
- [ ] All tests pass
- [ ] Manual test successful (complete import flow works end-to-end)
- [ ] Error cases handled gracefully (network errors, invalid URLs, extraction failures)
- [ ] Logs are informative but not verbose
- [ ] Documentation updated in `docs/mobile/ui.md`
- [ ] WebView dependency added and working
- [ ] Import feature follows existing app patterns and conventions
- [ ] Recipe extraction and saving works correctly
- [ ] Navigation flow works correctly (list → import → list with new recipe)

## SIP Quality Score: 9/10

**Confidence Level for One-Pass Implementation:**
This SIP provides comprehensive context, follows existing patterns, includes specific implementation details, and covers
all integration points. The detailed pseudocode, external documentation links, and thorough validation steps should
enable successful one-pass implementation. The score reflects high confidence due to:

- ✅ Complete feature analysis and requirements
- ✅ Detailed codebase pattern research
- ✅ External documentation and best practices included
- ✅ Step-by-step implementation plan with specific file changes
- ✅ Error handling and edge cases considered
- ✅ Integration points clearly defined
- ✅ Validation strategy comprehensive

The only potential challenge is WebView debugging across different devices/platforms, but the implementation follows
official Flutter documentation and established patterns.