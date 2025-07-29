name: "Base Implementation Plan Template v2 - Context-Rich with Validation Loops"
description: |

## Purpose
Template optimized for AI agents to implement features with sufficient context and self-validation capabilities to achieve working code through iterative refinement.

## Core Principles
1. **Context is King**: Include ALL necessary documentation, examples, and caveats
2. **Validation Loops**: Provide executable tests/lints the AI can run and fix
3. **Information Dense**: Use keywords and patterns from the codebase
4. **Progressive Success**: Start simple, validate, then enhance
5. **Global rules**: Be sure to follow all rules in CLAUDE.md

---

## Goal
[What needs to be built - be specific about the end state and desires]

## Why
- [Business value and user impact]
- [Integration with existing features]
- [Problems this solves and for whom]

## What
[User-visible behavior and technical requirements]

### Success Criteria
- [ ] [Specific measurable outcomes]

## All Needed Context

### Documentation & References (list all context needed to implement the feature)
```yaml
# MUST READ - Include these in your context window
- url: [Official API docs URL]
  why: [Specific sections/methods you'll need]
  
- file: [path/to/example]
  why: [Pattern to follow, gotchas to avoid]
  
- doc: [Library documentation URL] 
  section: [Specific section about common pitfalls]
  critical: [Key insight that prevents common errors]

- docs: [docs/file.md]
  why: [docs that the user has pasted in to the project]

```

### Current Codebase tree (run `tree` in the root of the project) to get an overview of the codebase
```bash

```

### Desired Codebase tree with files to be added and responsibility of file
```bash

```

### Known Gotchas of our codebase & Library Quirks
- CRITICAL: [Library name] requires [specific setup]
- Example: Python FastAPI requires async functions for endpoints
- Example: This ORM doesn't support batch inserts over 1000 records

## Implementation Blueprint

### Data models and structure

Create the core data models, we ensure type safety and consistency.
```
Examples: 
 - orm models
 - value models
 - ...
```

### list of tasks to be completed to fulfill the PRP in the order they should be completed

```yaml
Task 1:
MODIFY src/existing_module:
  - FIND pattern: "class OldImplementation"
  - INJECT after line containing ...
  - PRESERVE existing method signatures

CREATE src/new_feature.py:
  - MIRROR pattern from: src/similar_feature
  - MODIFY class name and core logic
  - KEEP error handling pattern identical

...

Task N:
...

```


### Per task pseudocode as needed to be added to each task
```

```

### Integration Points
```yaml
DATABASE:
  - migration: "Add column 'feature_enabled' to users table"
  - index: "CREATE INDEX idx_feature_lookup ON users(feature_id)"
  
CONFIG:
  - add to: config/...
  - pattern: "FEATURE_TIMEOUT = X"
```

## Final validation Checklist
- [ ] No type errors
- [ ] All tests pass
- [ ] No linting errors
- [ ] Manual test successful: [specific curl/command]
- [ ] Error cases handled gracefully
- [ ] Logs are informative but not verbose
- [ ] Documentation updated if needed

---

## Anti-Patterns to Avoid
- ❌ Don't create new patterns when existing ones work
- ❌ Don't skip validation because "it should work"
- ❌ Don't ignore failing tests - fix them
- ❌ Don't hardcode values that should be config
- ❌ Don't catch all exceptions - be specific
