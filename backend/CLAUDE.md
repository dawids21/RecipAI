# AI Rules for RecipAI backend

## Coding Practices

### Modular Architecture

- Modules (packages) should be split by feature not by layer (like controller, entity, repository).
- Each module should have all required classes to provide a single feature
- Most of the classes should have package-private visibility unless they need to be public
- DTOs should be placed in a `dto` subpackage within the module
- Custom exceptions should be placed in an `exception` subpackage within the module

### Testing

- Write unit tests for methods with complex logic
- Write integration tests for methods that interact with external systems (e.g., database, external APIs)