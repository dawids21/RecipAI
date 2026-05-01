# CLAUDE.md

## Project Overview

- RecipAI is an application that helps users manage recipes for daily cooking.
- Backend for the project is built with Spring Boot and Java. Source code is available in the `backend/` directory.
- Mobile application is built with Flutter and Dart, available in the `mobile/` directory.

## Documentation

- Documentation for the project is available in the `docs/` directory.
- Always start with docs, not code.
- Read `docs/INDEX.md` before reading any documentation. It indexes the project's documentation and coding standards
- Do not stop at INDEX.md — follow the links and read the actual documentation files relevant to the task.
- Follow standards in `docs/backend/standards/` and `docs/mobile/standards/` when writing code — they represent team
  decisions. If standards conflict with the task, ask the user.

### Standards Evolution

When you notice recurring patterns, fixes, or conventions during implementation that aren't yet captured in standards —
suggest adding them. Examples:

- A bug fix reveals a pattern that should be standardized (e.g., "always validate X before Y")
- PR review feedback identifies a convention the team wants enforced
- The same type of fix is needed across multiple files
- A new library/pattern is adopted that should be documented

When this happens, briefly suggest the standard to the user. If approved, create or update the standard.

## AI Behavior

- Never assume missing context. Ask questions if uncertain.
- Never hallucinate libraries or functions.
- Always run `git` commands from the project root - never `cd` before git operations.
- Always use Conventional Commits for commit messages, with a scope. Allowed types: `feat`, `docs`, `chore`, `refactor`, `build`, `test`, `style`. Format: `type(scope): subject` (e.g. `feat(mobile): add share target support`).
