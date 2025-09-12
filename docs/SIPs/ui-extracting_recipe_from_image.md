# SIP: UI - Extracting Recipe from Image

## Goal

- Implement a dialog that appears when user taps "Extract Recipe" button with two options: "Extract from URL" and "
  Extract from Image"
- Create image selection functionality that allows users to pick images from camera or gallery
- Implement image upload to backend `/extract/image` API endpoint with proper multipart/form-data formatting
- Maintain existing URL extraction flow while adding new image extraction capabilities
- Follow existing UI patterns and error handling approaches used in the current extraction feature

## Context

### Documentation and References

- **Official image_picker package**: https://pub.dev/packages/image_picker - Flutter plugin for selecting images from
  Android/iOS gallery and camera
- **MultipartRequest documentation**: https://pub.dev/documentation/http/latest/http/MultipartRequest-class.html -
  Official Dart documentation for multipart file uploads
- **Backend API documentation**: `docs/backend/api.md` - Contains `/extract/image` endpoint specification
- **Mobile app documentation**: `docs/mobile/mobile.md` - Current mobile architecture and patterns
- **UI documentation**: `docs/mobile/ui.md` - Current UI components and navigation flow
- **Current extraction implementation**: `mobile/lib/features/extraction/extraction_screen.dart` - Pattern to follow for
  API integration and navigation
- **Route configuration**: `mobile/lib/core/routes.dart` - Navigation patterns using AppRoute enum

### Current Codebase Tree

```
mobile/lib/features/extraction/
├── extraction_screen.dart          # Current WebView-based URL extraction
├── extracted_recipe.dart           # Data models for API responses
└── web_recipe_extractor.dart       # WebView HTML extraction utility

mobile/lib/features/recipe/
└── recipe_list_screen.dart         # Speed Dial implementation with Extract Recipe button
```

### Desired Codebase Tree

```
mobile/lib/features/extraction/
├── url_extraction_screen.dart      # Renamed from extraction_screen.dart
├── image_extraction_screen.dart    # NEW: Image selection and upload screen
├── extraction_dialog.dart          # NEW: Dialog for choosing URL vs Image extraction
├── extracted_recipe.dart           # No changes needed
└── web_recipe_extractor.dart       # No changes needed

mobile/lib/features/recipe/
└── recipe_list_screen.dart         # Modified to show extraction dialog
```

### Known Gotchas of Our Codebase and Library Quirks

- **Route Order Rule**: In `core/routes.dart`, always define most specific routes first (e.g., `/create` before `/:id`)
- **InheritedWidget Pattern**: All external services (ApiService, AuthService) must be accessed via
  `InheritedApiService.of(context)` pattern
- **Theme Access**: Always use `final theme = Theme.of(context);` at beginning of build methods
- **Error Handling**: Follow try-catch pattern with user-friendly SnackBar messages as seen in existing extraction
  screen
- **Navigation**: Always use `AppRoute.routeName.name` for type-safe navigation with GoRouter
- **image_picker Requirements**: iOS needs NSPhotoLibraryUsageDescription and NSCameraUsageDescription in Info.plist
- **MultipartRequest**: Must use `http.MultipartFile.fromPath()` for file uploads and `await request.send()` for
  streaming responses
- **Flutter File Handling**: XFile from image_picker needs to be converted to MultipartFile for HTTP uploads

## Implementation Plan

### Tasks

```
Task 1: Add image_picker dependency
  Action: MODIFY
  File: mobile/pubspec.yaml
  Changes:
    - [ ] Add `image_picker: ^1.0.7` to dependencies section
    - [ ] Run `flutter pub get` to install the package

Task 2: Create extraction method selection dialog
  Action: CREATE
  File: mobile/lib/features/extraction/extraction_dialog.dart
  Changes:
    - [ ] Create ExtractionDialog StatelessWidget with two buttons
    - [ ] Follow Material 3 theming from `core/theme.dart`
    - [ ] Use AlertDialog with "Extract from URL" and "Extract from Image" options
    - [ ] Return ExtractionMethod enum value on selection
    - [ ] Handle dialog dismissal (null return)

Task 3: Rename current extraction screen
  Action: MODIFY
  File: mobile/lib/features/extraction/extraction_screen.dart
  Changes:
    - [ ] Rename class from ExtractionScreen to UrlExtractionScreen
    - [ ] Update all references to maintain existing functionality
    - [ ] Keep all existing WebView and URL extraction logic unchanged

Task 4: Create image extraction screen
  Action: CREATE
  File: mobile/lib/features/extraction/image_extraction_screen.dart
  Changes:
    - [ ] Create ImageExtractionScreen StatefulWidget
    - [ ] Follow existing screen patterns from UrlExtractionScreen
    - [ ] Implement image selection using ImagePicker (camera and gallery options)
    - [ ] Add image preview functionality
    - [ ] Implement multipart file upload to `/extract/image` endpoint
    - [ ] Follow existing error handling and loading state patterns
    - [ ] Navigate to create recipe screen on success with extracted data

Task 5: Add API method for image extraction
  Action: MODIFY
  File: mobile/lib/core/api_service.dart
  Changes:
    - [ ] Add `extractRecipeFromImage(XFile imageFile)` method
    - [ ] Use MultipartRequest with proper authentication headers
    - [ ] Return ExtractedRecipe matching existing `extractRecipeFromText` format
    - [ ] Handle HTTP multipart/form-data upload with file parameter
    - [ ] Follow existing error handling patterns

Task 6: Add new routes
  Action: MODIFY
  File: mobile/lib/core/routes.dart
  Changes:
    - [ ] Add urlExtraction('url-extraction') to AppRoute enum
    - [ ] Add imageExtraction('image-extraction') to AppRoute enum
    - [ ] Update route configuration to use UrlExtractionScreen
    - [ ] Add new route for ImageExtractionScreen
    - [ ] Update import statements for renamed screen

Task 7: Modify recipe list screen
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_list_screen.dart
  Changes:
    - [ ] Update _onExtractionTap method to show ExtractionDialog
    - [ ] Handle dialog result to navigate to appropriate extraction screen
    - [ ] Add navigation to both URL and image extraction routes
    - [ ] Maintain existing Speed Dial functionality and styling
```

### Per Task Pseudocode

```dart
// Task 2: ExtractionDialog Pseudocode
class ExtractionDialog extends StatelessWidget {
  Widget build(context) {
    return AlertDialog(
        title: "Extract Recipe",
        content: "Choose extraction method",
        actions: [
        TextButton("Extract from URL") -> return ExtractionMethod.url,
    TextButton("Extract from Image") -> return ExtractionMethod.image,
    ]
    )
  }
}

// Task 4: ImageExtractionScreen Pseudocode
class ImageExtractionScreen extends StatefulWidget {
  State createState()

  -

  >

  _ImageExtractionScreenState
}

class _ImageExtractionScreenState {
  XFile? selectedImage

  ImagePicker imagePicker

  bool isUploading = false

  _pickImage(ImageSource source) async {
    selectedImage = await imagePicker.pickImage(source: source)
    setState()
  }

  _extractRecipe() async {
    if (selectedImage == null) return

      isUploading = true
    try {
      extractedRecipe = await _apiService.extractRecipeFromImage(selectedImage)
      navigate to create recipe screen with extractedRecipe.toRecipeDetail()
    } catch (error) {
      show error snackbar
    } finally {
      isUploading = false
    }
  }
}

// Task 5: API Service Method Pseudocode
Future<ExtractedRecipe> extractRecipeFromImage(XFile imageFile) async {
  request = MultipartRequest('POST', '$baseUrl/extract/image')
  request.headers = await _getAuthHeaders()
  request.files.add(await MultipartFile.fromPath('file', imageFile.path))

  response = await request.send()
  responseBody = await Response.fromStream(response)

  if (response.statusCode == 200) {
    return ExtractedRecipe.fromJson(json.decode(responseBody.body))
  } else {
    throw Exception('Failed to extract recipe')
  }
}

// Task 7: Recipe List Screen Update Pseudocode
_onExtractionTap(BuildContext context) async {
  final method = await showDialog<ExtractionMethod>(
    context: context,
    builder: (context) => ExtractionDialog(),
  )

  if (method == ExtractionMethod.url) {
    context.goNamed(AppRoute.urlExtraction.name)
  } else if (method == ExtractionMethod.image) {
    context.goNamed(AppRoute.imageExtraction.name)
  }
}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd mobile && flutter analyze
cd mobile && dart format --set-exit-if-changed lib/

# Expected: No errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
cd mobile && flutter test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Integration Tests

```bash
# Manual testing required - no automated integration tests currently exist
# Test the full flow: Speed Dial → Dialog → Image Selection → Upload → Recipe Creation
```

## Integration Points

- **API Integration**: New `/extract/image` endpoint usage with multipart/form-data - already implemented in backend
- **Navigation Flow**: Integration with existing GoRouter configuration and route protection
- **Data Flow**: ExtractedRecipe → RecipeDetail conversion maintains compatibility with create recipe screen
- **Authentication**: Image upload must include Bearer token authentication like existing API calls
- **Platform Integration**: Camera and gallery access on iOS/Android through image_picker plugin

## Documentation

- **Update mobile/ui.md**: Add documentation for new ImageExtractionScreen and ExtractionDialog
- **Update mobile/mobile.md**: Document new image extraction feature in features section
- **No CLAUDE.md updates needed**: Following existing patterns and conventions

## Final Validation Checklist

- [ ] Correct syntax (flutter analyze passes)
- [ ] Correct style (dart format passes)
- [ ] All tests pass (flutter test)
- [ ] Manual test successful (complete image extraction flow)
- [ ] Error cases handled gracefully (network errors, invalid images, API failures)
- [ ] Logs are informative but not verbose (following existing SnackBar patterns)
- [ ] Documentation updated if needed (mobile app docs updated)

**SIP Confidence Score: 9/10** - High confidence for one-pass implementation success. All required patterns exist in
codebase, API endpoint is ready, and Flutter image_picker is well-documented. Only minor integration testing needed to
ensure proper flow between components.