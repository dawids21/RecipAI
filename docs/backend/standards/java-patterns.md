# Java Patterns

### Java Records for DTOs
All request/response DTOs are Java records. Request records use Jakarta Bean Validation annotations on their components. Response records are plain records without validation.

```java
// Request DTO
public record CreateRecipeRequest(
    @NotBlank String name,
    @NotNull @Valid RecipeData data,
    UUID recipesCollectionId
) {}

// Response DTO
record ShoppingListDto(UUID id, String name, List<ShoppingListItemDto> items, ResourceRole role) {}
```

### JPA Entity Conventions
Entity classes follow a consistent structure:
- UUID primary key with `@GeneratedValue(strategy = GenerationType.UUID)`
- `Instant createdAt = Instant.now()` initialized inline with `updatable = false`
- Explicit `equals`/`hashCode` based on `id`
- Package-private constructor for business instantiation; `@NoArgsConstructor` for JPA

```java
@Entity
@Getter @Setter @ToString @NoArgsConstructor
class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    Recipe(String name, String userId) {
        this.name = name;
        this.userId = userId;
    }
}
```

### Derived Query Methods in Repositories
Repository methods are derived from their names. Reach for `@Query` only when the query cannot be
derived: a join across aggregates, a projection, native SQL, or a bulk `@Modifying` statement.

```java
// Derived from the method name — no annotation
Optional<ResourcePermission> findByIdResourceTypeAndIdResourceIdAndRole(String resourceType, UUID resourceId, ResourceRole role);
void deleteByResourceTypeAndResourceId(String resourceType, UUID resourceId);

// @Query — joins two aggregates, cannot be derived
@Query("SELECT mp FROM MealPlan mp INNER JOIN MealPlanPermission mpp ON mpp.id.planId = mp.id WHERE mpp.id.email = :email ORDER BY mp.createdAt")
List<MealPlan> findAllByUserEmail(String email);
```

### Package-Private Class Visibility
Controllers, services, entities, and repositories are package-private (no `public` modifier) unless accessed from outside their package. DTOs crossing package boundaries and custom exceptions are `public`.

```java
// package-private — internal to feature module
class RecipeController { ... }
class RecipeService { ... }

// public — used by other modules or as API types
public record CreateRecipeRequest(...) {}
public class RecipeNotFoundException extends RuntimeException { ... }
```