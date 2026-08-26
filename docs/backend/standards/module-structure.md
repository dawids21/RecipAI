# Module Structure Conventions

### Package Layout Inside a Module
DTOs live in a `dto` sub-package and custom exceptions in an `exception` sub-package. Everything else
— controller, facade, application service, services, entities, repositories and the exception
handler — sits at the module root.

```
xyz/stasiak/recipai/shoppinglists/
├── dto/
│   ├── ShoppingListDto.java
│   └── CreateShoppingListRequest.java
├── exception/
│   └── ShoppingListNotFoundException.java
├── ShoppingListController.java
├── ShoppingListService.java
├── ShoppingList.java
├── ShoppingListRepository.java
└── ShoppingListsExceptionHandler.java
```

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
Each feature module has a dedicated `@ControllerAdvice` (or `@RestControllerAdvice`) class (e.g.,
`RecipesExceptionHandler`) that maps module-specific exceptions to HTTP responses. Custom exceptions
extend `RuntimeException` and are thrown directly from service methods.

A handler method returns a bare `ProblemDetail` built with `ProblemDetail.forStatusAndDetail(...)` and
`setTitle(...)`; Spring takes the response status from the `ProblemDetail` itself, so it is stated
once. Reach for `ResponseEntity<ProblemDetail>` only when the response needs something a
`ProblemDetail` return can't carry: an extra header (e.g. a 429's `Retry-After`) or a body that isn't
a `ProblemDetail` at all (e.g. a 412 returning the winning resource for a client to roll back to).

```java
@RestControllerAdvice
class RecipesExceptionHandler {
    @ExceptionHandler(RecipeNotFoundException.class)
    ProblemDetail handleNotFound(RecipeNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Recipe Not Found");
        return problemDetail;
    }
}
```

### A Service Owns Its Repositories
A service injects only the repositories for the entities it owns, and never a sibling service from
the same module. When an operation needs data or behaviour the service does not own, it does not
reach for the other service's repository — the coordination moves up to an application service.

### Application Service for Multi-Service Coordination
When one operation needs two or more services that are otherwise independent — typically to hold
them in a single transaction — put the coordination in a `<Module>ApplicationService`. The
application service owns the `@Transactional` boundary and calls each service in turn. The facade
delegates single-service calls straight to the service and coordinated calls to the application
service.

```java
@Service
@RequiredArgsConstructor
class PermissionsApplicationService {
    private final PermissionService permissionService;
    private final InviteService inviteService;

    @Transactional
    void revoke(String resourceType, UUID resourceId, String targetEmail, String requesterEmail) {
        boolean removed = permissionService.revoke(resourceType, resourceId, targetEmail, requesterEmail);
        if (!removed) {
            inviteService.cancel(resourceType, resourceId, targetEmail);
        }
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
