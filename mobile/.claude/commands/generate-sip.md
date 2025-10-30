# Create SIP

## Feature file: $ARGUMENTS

Generate a complete SIP for general feature implementation with thorough research. Ensure context is passed to the AI
agent to enable self-validation and iterative refinement. Read the feature file first to understand what needs to be
created, how the examples provided help, and any other considerations.

The AI agent only gets the context you are appending to the SIP and training data. Assume the AI agent has access to the
codebase and the same knowledge cutoff as you, so its important that your research findings are included or referenced
in the SIP. The Agent has Websearch capabilities, so pass urls to documentation and examples.

## Research Process

**Use Plan subagent**

1. **Codebase Analysis**
    - Search for similar features/patterns in the codebase
    - Identify files to reference in SIP
    - Note existing conventions to follow
    - Check test patterns for validation approach

2. **External Research**
    - Search for similar features/patterns online
    - Library documentation (include specific URLs)
    - Implementation examples (GitHub/StackOverflow/blogs)
    - Best practices and common pitfalls

3. **User Clarification** (if needed)
    - Specific patterns to mirror and where to find them?
    - Integration requirements and where to find them?

## SIP Generation

Using `docs/SIPs/sip_template.md` as template:

### Critical Context to Include and pass to the AI agent as part of the SIP

- **Documentation**: URLs with specific sections
- **Code Examples**: Real snippets from codebase
- **Gotchas**: Library quirks, version issues
- **Patterns**: Existing approaches to follow

### Implementation Plan

- Start with pseudocode showing approach
- Reference real files for patterns
- Include error handling strategy
- List tasks to be completed to fulfill the SIP in the order they should be completed

### Validation

#### Python example

```bash
# Syntax/Style
ruff check --fix && mypy .

# Unit Tests
uv run pytest tests/ -v
```

#### Java example

```bash
# Syntax (no command for style)
mvn compile

# Unit Tests
mvn test

# Integration Tests
mvn test
```

### Integration Points

- Read project documentation for information about about existing Integration Points
- Check planned changes
- Specify which files need to be updated

### Documentation

- Check the current documentation
- Check planned changes
- Specify which files need to be updated

*** CRITICAL AFTER YOU ARE DONE RESEARCHING AND EXPLORING THE CODEBASE BEFORE YOU START WRITING THE SIP ***

*** ULTRATHINK ABOUT THE SIP AND PLAN YOUR APPROACH THEN START WRITING THE SIP ***

## Output

Save as: `docs/SIPs/{feature-name}.md`

## Quality Checklist

- [ ] All necessary context included
- [ ] Validation is executable by AI
- [ ] References existing patterns
- [ ] Clear implementation path
- [ ] Error handling documented
- [ ] Listed documentation to update

Score the SIP on a scale of 1-10 (confidence level to succeed in one-pass implementation using claude codes)

Remember: The goal is one-pass implementation success through comprehensive context.