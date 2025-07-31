# SIP Template

## Goal
- [What needs to be built - be specific about the end state and desired outcomes]
- [User-visible behavior and technical requirements]
- [Success criteria]

## Context

### Documentation and References
- [List all context needed to implement the feature - must read]
- [Official API docs URL]
- [Example from codebase]
- [Library documentation URL]
- [Project documentation]

### Current Codebase Tree
[File tree from the root of the project to get an overview of the codebase]

### Desired Codebase Tree
[Updated file tree from the root of the project with the desired changes]

### Known Gotchas of Our Codebase and Library Quirks
- [Any quirks found in the used libraries]
- [Framework/library requirements and limitations]
- [Language-specific considerations]

## Implementation Plan

### Tasks
[List of tasks to be completed to fulfill the SIP in the order they should be completed]

```
Task 1: [Brief description]
  Action: [CREATE/MODIFY/DELETE]
  File: [path/to/file]
  Changes:
    - [ ] Specific change or addition required
    - [ ] Pattern to follow from existing code
    - [ ] Integration points to consider

Task 2: [Brief description]
  Action: [CREATE/MODIFY/DELETE]
  File: [path/to/file]
  Changes:
    - [ ] Specific change or addition required
    - [ ] Dependencies on other tasks
    - [ ] Configuration or setup requirements

# Continue for all implementation tasks...
```

### Per Task Pseudocode
[Pseudocode for a given task if needed (complex logic, critical integration points, etc.)]
```
# Task 1 Pseudocode
function/method new_feature(parameters) {
    validate_input(parameters)
    
    handle(parameters)
    
    return response
}
```

## Validation
[Specify what can be used to check if the feature is complete and meets certain standards]

### Syntax and Style
[CLI commands used for validating syntax and style]
```bash
# Run these FIRST - fix any errors before proceeding
syntax_command
style_command
any_other_command...

# Expected: No errors. If errors, READ the error and fix.
```

### Unit Tests
[CLI commands used for running unit tests]
```bash
# Run and iterate until passing:
command_to_run_unit_tests
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Integration Tests
[CLI commands used for running integration tests]
```bash
# Run and iterate until passing:
command_to_run_integration_tests
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

## Integration Points
- [Any changes related to the integration with other systems (like between backend-frontend, backend-database etc.)]
- [Changes to API]
- [Changes to database schema]

## Documentation
- [Specify documentation files that need to be updated after implementing the feature]
- [Also, add CLAUDE.md files that need to be updated]

## Final Validation Checklist
- [ ] Correct syntax
- [ ] Correct style
- [ ] All tests pass
- [ ] Manual test successful
- [ ] Error cases handled gracefully
- [ ] Logs are informative but not verbose
- [ ] Documentation updated if needed