# SIP: Setup Database Migrations with Flyway

## Goal

- Setup database versioning and migrations using Flyway for the PostgreSQL database in the RecipAI Spring Boot backend
- Use the current database schema as the first version (V1__initial_schema.sql)
- Transition from Hibernate's DDL auto-update to controlled Flyway migrations
- Update project documentation to reflect the new migration system

## Context

### Documentation and References

- **Current Database Schema**: `docs/backend/db.md` - Contains complete schema with tables: `recipes` (id, name, data)
  and `user_recipes` (email, recipe_id, role)
- **Backend Architecture**: `docs/backend/backend.md` - Shows current modular Spring Boot structure
- **Current Tech Stack**: `backend/CLAUDE.md` - Lists all current dependencies, needs Flyway version added
- **Flyway Spring Boot Guide
  **: https://documentation.red-gate.com/fd/community-plugins-and-integrations-spring-boot-277579373.html
- **Flyway PostgreSQL Examples
  **: https://blog.jetbrains.com/idea/2024/11/how-to-use-flyway-for-database-migrations-in-spring-boot-applications/
- **Spring Boot 3.5 Integration
  **: https://medium.com/@yohanesdwikiwitman/spring-boot-3-template-part-3-migrations-using-flyway-b1a85003019f

### Current Codebase Tree

```
backend/
├── pom.xml                                          # Maven configuration - needs Flyway dependency
├── src/main/resources/
│   └── application.yml                              # Spring config - has ddl-auto: update, needs Flyway config
└── src/main/java/xyz/stasiak/recipai/
    ├── recipes/
    │   ├── Recipe.java                              # Entity with @Table(name = "recipes")
    │   ├── UserRecipe.java                          # Entity with @Table(name = "user_recipes")
    │   └── UserRecipeId.java                        # Composite key for user_recipes
    └── security/SecurityConfig.java
```

### Desired Codebase Tree

```
backend/
├── pom.xml                                          # Maven configuration + Flyway dependency (Spring Boot managed version)
├── src/main/resources/
│   ├── application.yml                              # Updated with Flyway config, ddl-auto: validate
│   └── db/migration/                                # NEW: Flyway migrations directory
│       └── V1__initial_schema.sql                   # NEW: Current schema as first migration
└── src/main/java/xyz/stasiak/recipai/              # Unchanged
```

### Known Gotchas of Our Codebase and Library Quirks

- **Current Database State**: Application currently uses `spring.jpa.hibernate.ddl-auto: update` which creates schema
  automatically
- **Testcontainers Integration**: Tests use `TestcontainersConfiguration.java` with PostgreSQL - Flyway will run during
  tests
- **Schema Ownership**: Current schema uses UUID primary keys and JSONB columns (PostgreSQL-specific features)
- **Spring Boot Version Management**: Spring Boot 3.5.5 provides Flyway version automatically, no need to specify
  version
- **Entity Mapping**: JPA entities use `@Table` annotations that must match migration table names exactly

## Implementation Plan

### Tasks

```
Task 1: Add Flyway dependency using Spring Boot managed version
  Action: MODIFY
  File: backend/pom.xml
  Changes:
    - [ ] Add flyway-core dependency without version (use Spring Boot's managed version)
    - [ ] Place dependency in appropriate section after existing database dependencies

Task 2: Create Flyway migration directory structure
  Action: CREATE
  File: backend/src/main/resources/db/migration/
  Changes:
    - [ ] Create db/migration directory following Flyway conventions
    - [ ] Ensure directory is in src/main/resources for classpath inclusion

Task 3: Create initial schema migration from current database
  Action: CREATE
  File: backend/src/main/resources/db/migration/V1__initial_schema.sql
  Changes:
    - [ ] Create recipes table with UUID id, VARCHAR(255) name, JSONB data columns
    - [ ] Create user_recipes table with VARCHAR(255) email, UUID recipe_id, VARCHAR(255) role columns
    - [ ] Add PRIMARY KEY and FOREIGN KEY constraints exactly matching current schema
    - [ ] Add CHECK constraint for role validation (OWNER, EDITOR)
    - [ ] Use PostgreSQL-specific syntax for UUID generation and JSONB columns

Task 4: Update Spring Boot configuration for Flyway
  Action: MODIFY
  File: backend/src/main/resources/application.yml
  Changes:
    - [ ] Add spring.flyway.enabled: true configuration
    - [ ] Change spring.jpa.hibernate.ddl-auto from "update" to "validate"
    - [ ] Ensure database connection settings remain unchanged
    - [ ] No need to configure flyway.locations (db/migration is default)

Task 5: Update project documentation
  Action: MODIFY
  File: backend/CLAUDE.md
  Changes:
    - [ ] Add "Flyway" to the tech stack list (version managed by Spring Boot)
    - [ ] Place it in appropriate section after PostgreSQL entry
    - [ ] Follow existing documentation format and style
```

### Per Task Pseudocode

```sql
-- Task 3: V1__initial_schema.sql structure
CREATE TABLE recipes
(
    id   UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    data JSONB        NOT NULL
);

CREATE TABLE user_recipes
(
    email     VARCHAR(255) NOT NULL,
    recipe_id UUID         NOT NULL,
    role      VARCHAR(255) NOT NULL CHECK (role IN ('OWNER', 'EDITOR')),
    PRIMARY KEY (email, recipe_id),
    FOREIGN KEY (recipe_id) REFERENCES recipes (id)
);
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd backend
mvn compile

# Expected: No compilation errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
cd backend
mvn test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Flyway Validation

```bash
# Verify Flyway setup and migration status:
cd backend
mvn spring-boot:run
# Check logs for: "Flyway Community Edition" and "Successfully applied 1 migration"
# Database should show flyway_schema_history table with V1 migration record
```

## Integration Points

- **Database Schema**: Migration from Hibernate DDL auto-generation to Flyway controlled versioning
- **Testing Infrastructure**: Testcontainers will automatically apply Flyway migrations during test setup
- **Development Workflow**: Developers must create new migration files instead of modifying entities for schema changes
- **CI/CD Pipeline**: No changes required - Flyway runs automatically on application startup

## Documentation

- **backend/CLAUDE.md**: Add Flyway to tech stack documentation
- **docs/backend/db.md**: No changes needed - schema documentation remains accurate
- **README files**: No changes needed as per project guidelines

## Final Validation Checklist

- [ ] Correct syntax (mvn compile passes)
- [ ] Correct style (Maven standards followed)
- [ ] All tests pass (mvn test succeeds)
- [ ] Manual test successful (application starts without errors)
- [ ] Migration applied (flyway_schema_history table created with V1 entry)
- [ ] Schema matches entities (JPA validation passes with ddl-auto: validate)
- [ ] Error cases handled gracefully (Flyway errors logged clearly)
- [ ] Logs are informative but not verbose (Flyway logs at INFO level)
- [ ] Documentation updated (CLAUDE.md includes Flyway)

---

**SIP Confidence Score: 9/10**

This SIP provides comprehensive context for one-pass implementation including:

- Complete current schema definition from existing entities
- Exact file paths and directory structures needed
- Spring Boot managed dependency configuration
- Integration with existing Testcontainers setup
- Clear validation steps with expected outcomes
- Minimal documentation updates following project guidelines