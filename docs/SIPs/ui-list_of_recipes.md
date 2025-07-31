# SIP: UI List of Recipes

## Goal

- Create a mobile Flutter app that displays a list of recipes fetched from the backend REST API
- Enable users to tap on a recipe to view detailed information including ingredients and instructions
- Implement simple, functional UI for demo phase without complex styling - focus on data display
- Success criteria: App loads recipe list from local backend, navigates to recipe details, handles basic error states

## Context

### Documentation and References

- **Project Documentation**: `/home/dawid/Projects/RecipAI/docs/prd.md` - Product Requirements Document with demo phase
  boundaries
- **API Documentation**: `/home/dawid/Projects/RecipAI/docs/backend/api.md` - Complete REST API specification with
  examples
- **Mobile Structure**: `/home/dawid/Projects/RecipAI/docs/mobile/mobile.md` - Current codebase structure
- **Flutter HTTP Best Practices**: https://docs.flutter.dev/cookbook/networking/fetch-data
- **ListView with API Data
  **: https://medium.com/@ashishpimpre/how-to-fetch-data-from-an-api-and-display-it-in-listview-in-flutter-770863f85959
- **Flutter Community Guide
  **: https://medium.com/flutter-community/fetching-data-in-flutter-and-displaying-in-listview-ec1bb72af22c

### Current Codebase Tree

```
mobile/
├── lib/
│   └── main.dart                       # Default Flutter counter app - needs complete rewrite
├── pubspec.yaml                        # Current dependencies: flutter, cupertino_icons, flutter_lints
├── test/
│   └── widget_test.dart               # Default widget test
└── analysis_options.yaml             # Dart linting rules
```

### Desired Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                       # App entry point with RecipAI theme
│   ├── services/
│   │   └── api_service.dart            # HTTP client for backend API calls
│   └── recipe/
│       ├── recipe.dart                 # Recipe data model
│       ├── recipe_detail.dart          # Detailed recipe model
│       ├── recipe_list_screen.dart     # Main screen showing list of recipes
│       ├── recipe_detail_screen.dart   # Detail screen showing full recipe
│       ├── recipe_list_item.dart       # Individual recipe list item widget
│       └── loading_widget.dart         # Reusable loading indicator
├── pubspec.yaml                        # Updated with http dependency
└── analysis_options.yaml              # Unchanged
```

### Known Gotchas of Our Codebase and Library Quirks

- **API Endpoints**: Backend uses `/recipes` for list and `/recipes/{uuid}` for details - both return different JSON
  structures
- **Network Configuration**: API runs on local computer, need to use `ip` command to find local IP address, not
  localhost
- **HTTP Package**: Use Flutter's built-in `http` package for simplicity, avoid dio for demo phase
- **State Management**: Store Future results in state variables, call API in initState(), never in build() method
- **JSON Parsing**: Backend returns nested `data` object for recipe details but not for recipe list

## Implementation Plan

### Tasks

```
Task 1: Update dependencies and project configuration
  Action: MODIFY
  File: mobile/pubspec.yaml
  Changes:
    - [ ] Add http: ^1.1.0 dependency for API calls
    - [ ] Update app name and description to match RecipAI

Task 2: Create data models for API responses
  Action: CREATE
  File: mobile/lib/recipe/recipe.dart
  Changes:
    - [ ] Create Recipe class matching GET /recipes response: id, name
    - [ ] Add fromJson factory constructor for JSON deserialization
    - [ ] Follow existing Dart conventions with proper null safety

Task 3: Create detailed recipe model
  Action: CREATE
  File: mobile/lib/recipe/recipe_detail.dart
  Changes:
    - [ ] Create RecipeDetail class matching GET /recipes/{id} response
    - [ ] Include nested data structure: ingredients, instructions
    - [ ] Add fromJson factory for complex nested JSON parsing

Task 4: Implement HTTP API service
  Action: CREATE
  File: mobile/lib/services/api_service.dart
  Changes:
    - [ ] Create ApiService class with base URL configuration
    - [ ] Implement fetchRecipes() returning Future<List<Recipe>>
    - [ ] Implement fetchRecipeDetail(String id) returning Future<RecipeDetail>
    - [ ] Add proper error handling for network failures and HTTP errors
    - [ ] Use singleton pattern for shared HTTP client instance

Task 5: Create recipe list screen with ListView
  Action: CREATE
  File: mobile/lib/recipe/recipe_list_screen.dart
  Changes:
    - [ ] Implement StatefulWidget with FutureBuilder pattern
    - [ ] Call ApiService.fetchRecipes() in initState()
    - [ ] Use ListView.builder for dynamic recipe list rendering
    - [ ] Handle loading, error, and success states appropriately
    - [ ] Add onTap navigation to recipe detail screen

Task 6: Create recipe detail screen
  Action: CREATE
  File: mobile/lib/recipe/recipe_detail_screen.dart
  Changes:
    - [ ] Accept recipe ID as constructor parameter
    - [ ] Fetch full recipe details using ApiService.fetchRecipeDetail()
    - [ ] Display recipe name, ingredients list, and instructions
    - [ ] Handle loading and error states for detail fetching
    - [ ] Add back navigation to list screen

Task 7: Create reusable UI widgets
  Action: CREATE
  File: mobile/lib/recipe/recipe_list_item.dart
  Changes:
    - [ ] Create simple ListTile widget for recipe display
    - [ ] Show recipe name with appropriate typography
    - [ ] Add onTap callback for navigation handling

Task 8: Create loading widget
  Action: CREATE
  File: mobile/lib/recipe/loading_widget.dart
  Changes:
    - [ ] Simple CircularProgressIndicator with centered layout
    - [ ] Reusable across list and detail screens

Task 9: Update main app entry point
  Action: MODIFY
  File: mobile/lib/main.dart
  Changes:
    - [ ] Replace counter app with RecipAI app
    - [ ] Set recipe list screen as home screen
    - [ ] Configure MaterialApp with appropriate theme and title
    - [ ] Remove all counter-related code completely

Task 10: Set up navigation between screens
  Action: MODIFY
  File: mobile/lib/recipe/recipe_list_screen.dart
  Changes:
    - [ ] Implement Navigator.push to recipe detail screen
    - [ ] Pass recipe ID to detail screen constructor
    - [ ] Handle navigation errors gracefully
```

### Per Task Pseudocode

```dart
// Task 4: API Service Implementation
class ApiService {
  static final String baseUrl = 'http://[LOCAL_IP]:8080';

  Future<List<Recipe>> fetchRecipes() async {
    final response = await http.get('$baseUrl/recipes');
    if (response.statusCode == 200) {
      final List<dynamic> jsonList = json.decode(response.body);
      return jsonList.map((json) => Recipe.fromJson(json)).toList();
    } else {
      throw Exception('Failed to load recipes');
    }
  }

  Future<RecipeDetail> fetchRecipeDetail(String id) async {
    final response = await http.get('$baseUrl/recipes/$id');
    if (response.statusCode == 200) {
      final Map<String, dynamic> json = json.decode(response.body);
      return RecipeDetail.fromJson(json);
    } else {
      throw Exception('Failed to load recipe detail');
    }
  }
}

// Task 5: Recipe List Screen Structure
class RecipeListScreen extends StatefulWidget {
  @override
  _RecipeListScreenState createState() => _RecipeListScreenState();
}

class _RecipeListScreenState extends State<RecipeListScreen> {
  late Future<List<Recipe>> futureRecipes;

  @override
  void initState() {
    super.initState();
    futureRecipes = ApiService().fetchRecipes();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(title: Text('RecipAI')),
        body: FutureBuilder<List<Recipe>>(
            future: futureRecipes,
            builder: (context, snapshot) {
              if (snapshot.hasData) {
                return ListView.builder(
                    itemCount: snapshot.data!.length,
                    itemBuilder: (context, index) {
                      return RecipeListItem(
                          recipe: snapshot.data![index],
                          onTap: () =>
                              Navigator.push(context, MaterialPageRoute(
                                  builder: (context) => RecipeDetailScreen(recipeId: snapshot.data![index].id)
                              ))
                      );
                    }
                );
              } else if (snapshot.hasError) {
                return Center(child: Text('Error: ${snapshot.error}'));
              }
              return LoadingWidget();
            }
        )
    );
  }
}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd mobile
flutter analyze

# Expected: No issues found. If errors, READ the error and fix.
```

### Manual Testing

Ask user to run the app and validate functionality:

- App launches and shows loading indicator
- Recipe list loads and displays recipe names
- Tapping recipe navigates to detail screen
- Detail screen shows ingredients and instructions
- Back navigation returns to list
- Error handling works when backend is offline

## Integration Points

- **Backend API Integration**: App connects to local backend server running on port 8080
- **Network Configuration**: Must use local IP address instead of localhost for device testing
- **JSON Data Flow**: Handle two different API response formats (list vs detail)
- **Navigation Flow**: Implement proper screen transitions between list and detail views

## Documentation

- **Mobile App Overview**: Update `/home/dawid/Projects/RecipAI/docs/mobile/mobile.md` with new codebase structure
- **UI Documentation**: Update `/home/dawid/Projects/RecipAI/docs/mobile/ui.md` with implemented screens and widgets
- **CLAUDE.md**: Add HTTP dependency and API service patterns to `/home/dawid/Projects/RecipAI/mobile/CLAUDE.md`

## Final Validation Checklist

- [ ] Correct syntax - flutter analyze passes
- [ ] Correct style - follows Dart/Flutter conventions
- [ ] Manual test successful - app lists recipes and shows details
- [ ] Error cases handled gracefully - offline mode, network errors
- [ ] Logs are informative but not verbose - appropriate debug output
- [ ] Documentation updated if needed - mobile docs reflect new structure

## SIP Confidence Score: 9/10

**Rationale**: This SIP provides comprehensive context with real API documentation, existing codebase analysis, external
research on Flutter best practices, and detailed implementation steps. The confidence is high because:

- **Complete API specification** with exact JSON structures and endpoints
- **Proven Flutter patterns** from official docs and community best practices
- **Detailed task breakdown** with specific file changes and pseudocode
- **Existing project structure** analysis showing exactly what needs to change
- **Error handling strategy** included for network failures
- **Validation approach** covers both automated and manual testing

The only uncertainty (losing 1 point) is the local network configuration aspect - finding the correct local IP address
for device testing may require iteration during implementation.