# Module Structure Conventions

### Facade Pattern for Cross-Module Access
Modules expose a `public` facade (e.g., `RecipeFacade`, `ProvisioningFacade`) for use by other modules. Internal service classes are package-private. Never access another module's service directly.

```java
// Correct: use the facade
class PlanningService {
    private final RecipeFacade recipeFacade; // public facade
}

// Wrong: do not access internal services from outside the module
class PlanningService {
    private final RecipeService recipeService; // package-private — not accessible
}
```

### Exception Handler per Feature Module
Each feature module has a dedicated `@ControllerAdvice` class (e.g., `RecipesExceptionHandler`) that maps module-specific exceptions to HTTP responses using `ResponseEntity<ErrorResponse>`. Custom exceptions extend `RuntimeException` and are thrown directly from service methods.

```java
@ControllerAdvice
class RecipesExceptionHandler {
    @ExceptionHandler(RecipeNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(RecipeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }
}
```

### RESTful Endpoint Conventions
- Resource paths are **plural kebab-case nouns** (`/recipes`, `/shopping-lists`, `/meal-plans`)
- Sub-actions use additional path segments as verbs (`/{id}/share`, `/{id}/check`, `/{id}/move`)
- Authenticated user email is always extracted via `jwt.getClaimAsString("email")`

```java
@RestController
@RequestMapping("/shopping-lists")
class ShoppingListController {
    @PostMapping("/{id}/check")
    void checkItem(@PathVariable UUID id, Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        ...
    }
}
```

### SLF4J Logging Pattern
Use `@Slf4j` (via Lombok) on all controllers and services. Call `log.debug(...)` at the start of each handler/service method with key parameters. Use `log.warn(...)` for business-rule violations and `log.info(...)` for significant state changes.

```java
@Slf4j
class RecipeController {
    ResponseEntity<?> getRecipe(UUID id, Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting recipe by id: {} for user: {}", id, userEmail);
        ...
    }
}
```
