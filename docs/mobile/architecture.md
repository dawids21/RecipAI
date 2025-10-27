# Architecture

## Overview and Introduction

This architecture implements a pragmatic layered approach for Flutter applications with clear separation between data
access, business logic, and UI. It uses dependency injection via get_it, reactive state management with ValueNotifier,
and an AsyncValue pattern for handling asynchronous operations.

## Core Architectural Principles

**Design philosophy**: Separation of concerns through three distinct layers (Repository, Service, View) with
unidirectional data flow and constructor-based dependency injection.

**Key architectural decisions**:

- ValueNotifier/ValueListenable for reactive state management (lightweight, built-in Flutter)
- get_it for dependency injection (simple service locator pattern)
- AsyncValue sealed class for consistent async state handling (Loading/Data/Error)
- Boolean flags in services to prevent concurrent method executions

**Layer separation strategy**:

- Repository Layer: Handles external interactions (API, database, storage), returns raw data types
- Service Layer: Manages application state using ValueNotifiers, exposes ValueListenables, coordinates repositories
- View Layer: Displays state and handles UI interactions, consumes service state via ValueListenableBuilder

**Constraints and boundaries**:

- Views cannot access repositories directly, only through services
- Services cannot access other services' private state
- Repositories have no dependencies on services or views
- Data flows unidirectionally: Repository → Service → View

## Project Structure

**Module/package structure**: Features are organized by domain (auth, products, cart), each containing all layers for
that feature.

**File naming conventions**:

- Repository: `*_repository.dart`
- Service: `*_service.dart`
- Screen: `*_screen.dart`
- Setup: `*_setup.dart`

## Layer Definitions

### Repository Layer

Handles external interactions (APIs, databases, local storage) and returns domain models or primitive types.
Repositories are stateless containers for data operations.

```dart
class ProductsRepository {
  Future<List<ProductModel>> getProducts(String user) async {
    final apiProducts = await apiClient.getProducts(user);
    return apiProducts.map((apiProduct) => ProductModel.fromJson(apiProduct)).toList();
  }

  Future<void> renameProduct(String user, int productId, String newName) async {
    await apiClient.renameProduct(user, productId, newName);
  }
}
```

### Service Layer

Manages application state using ValueNotifiers wrapped in AsyncValue. Services expose state via ValueListenable getters
and provide mutation methods. Boolean flags prevent concurrent method executions.

```dart
class ProductsService {
  final ProductsRepository productsRepository;
  final AuthRepository authRepository;

  ProductsService({
    required this.productsRepository,
    required this.authRepository,
  });

  final ValueNotifier<AsyncValue<List<ProductModel>>> _products =
  ValueNotifier(AsyncValue.loading());

  ValueListenable<AsyncValue<List<ProductModel>>> get products => _products;

  bool _isLoadProductsRunning = false;

  Future<void> loadProducts() async {
    if (_isLoadProductsRunning) return;
    _isLoadProductsRunning = true;
    _products.value = AsyncValue.loading();
    _products.value = await AsyncValue.guardAsync(() async {
      String? user = await authRepository.getUser();
      return productsRepository.getProducts(user!);
    });
    _isLoadProductsRunning = false;
  }
}
```

### View Layer

Displays state and handles user interactions. Views receive services via constructor injection and use
ValueListenableBuilder to rebuild on state changes. The AsyncValue.when() method handles different states.

```dart
class ProductsListScreen extends StatefulWidget {
  final ProductsService productsService;

  const ProductsListScreen({
    super.key,
    required this.productsService,
  });

  @override
  State<ProductsListScreen> createState() => _ProductsListScreenState();
}

class _ProductsListScreenState extends State<ProductsListScreen> {
  @override
  void initState() {
    widget.productsService.loadProducts();
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder(
      valueListenable: widget.productsService.products,
      builder: (context, asyncValueProducts, child) {
        return asyncValueProducts.when(
          loading: () => CircularProgressIndicator(),
          data: (products) =>
              ListView.builder(
                itemCount: products.length,
                itemBuilder: (context, index) {
                  return Text(products[index].name);
                },
              ),
          error: (error) => Text('Error: $error'),
        );
      },
    );
  }
}
```

## State Management

**State management solution**: ValueNotifier and ValueListenable from Flutter's foundation library. State is wrapped in
AsyncValue to handle loading, data, and error states uniformly.

**State flow patterns**:

1. Service updates internal ValueNotifier
2. View listens via ValueListenableBuilder
3. UI rebuilds automatically when value changes

**When and how to use state management**:

- Global state (auth, shared data): Use singleton services
- Screen-specific state: Use lazy singleton services, reset when screen is disposed
- Local state (form inputs): Use StatefulWidget state

## Dependency Injection

**DI approach and tools**: get_it service locator pattern. Global `getIt` instance provides centralized dependency
management. Dependencies are retrieved via `getIt<Type>()`.

**Dependency registration strategy**: Each feature has a setup function that registers its dependencies. Setup functions
are called in main() before app initialization.

```dart
// main.dart
final getIt = GetIt.instance;

void main() {
  setupAuth();
  setupProducts();
  runApp(MyApp());
}

// auth_setup.dart
void setupAuth() {
  getIt.registerSingleton(AuthRepository());
  getIt.registerSingleton(AuthService(
      authRepository: getIt<AuthRepository>()
  ));
}

// products_setup.dart
void setupProducts() {
  getIt.registerSingleton(ProductsRepository());
  getIt.registerLazySingleton(() =>
      ProductsService(
        productsRepository: getIt<ProductsRepository>(),
        authRepository: getIt<AuthRepository>(),
      ));
}
```

**Resetting lazy singletons**: Screen-specific services are reset when screen is disposed to prevent memory leaks:

```dart
@override
void dispose() {
  if (getIt.isRegistered<ProductsService>()) {
    getIt.resetLazySingleton<ProductsService>();
  }
  super.dispose();
}
```

**Important**: Lazy singletons are only created when first accessed. If a screen is disposed before the service is
used (e.g., in tests or quick navigation), attempting to reset an uninstantiated lazy singleton will throw an error.
Always guard the reset call with `isRegistered()` check.

## Navigation & Routing

**Navigation approach**: go_router with declarative routing and authentication guards.

**Route definitions**:

```dart
GoRouter createAppRouter(AuthService authService,
    ProductsRepository productsRepository) {
  return GoRouter(
    initialLocation: "/products",
    refreshListenable: authService.isAuthenticated,
    redirect: (context, state) {
      final isAuthenticated = authService.isAuthenticated.value;
      final isLoginRoute = state.matchedLocation == '/login';
      if (!isAuthenticated && !isLoginRoute) {
        return '/login';
      }
      if (isAuthenticated && isLoginRoute) {
        return '/products';
      }
      return null;
    },
    routes: [
      GoRoute(
        path: '/products',
        builder: (context, state) =>
            ProductsListScreen(
              authService: authService,
              productsService: getIt<ProductsService>(),
            ),
        routes: [
          GoRoute(
            path: ':productId',
            builder: (context, state) {
              final productId = int.parse(state.pathParameters['productId']!);
              return ProductScreen(
                authService: authService,
                productService: getIt<ProductService>(),
                productId: productId,
              );
            },
          ),
        ],
      ),
      GoRoute(
        path: '/login',
        builder: (context, state) => LoginScreen(authService: authService),
      ),
    ],
  );
}
```

Services are injected into routes via getIt. Authentication-dependent services use `refreshListenable` to trigger
redirects when auth state changes.

## Data Flow & Communication

**How data flows between layers**:

1. View calls service mutation method (e.g., `productsService.loadProducts()`)
2. Service sets state to loading, calls repository
3. Repository performs external operation, returns data
4. Service wraps result in AsyncValue, updates ValueNotifier
5. View rebuilds via ValueListenableBuilder

**Event/message passing**: Services communicate through direct method calls. Services can depend on multiple
repositories to coordinate data operations.

**API communication patterns**: Repositories handle API calls, services wrap them with AsyncValue.guardAsync():

```dart
Future<void> loadProducts() async {
  if (_isLoadProductsRunning) return; // Prevent concurrent calls
  _isLoadProductsRunning = true;
  _products.value = AsyncValue.loading();
  _products.value = await AsyncValue.guardAsync(() async {
    String? user = await authRepository.getUser();
    return productsRepository.getProducts(user!);
  });
  _isLoadProductsRunning = false;
}
```

## Error Handling

**Error handling strategy**: AsyncValue automatically catches exceptions via guardAsync(). Errors are propagated to the
UI as AsyncError state.

**Exception types and hierarchy**: AsyncValue uses Object type for errors, allowing any exception type. Repository
methods throw exceptions, services catch them automatically.

**User-facing error presentation**: Views use AsyncValue.when() to display error states.