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
- When writing or editing a markdown document, keep it concise and do not repeat information — state each fact once and
  cross-reference it instead of restating it.
- Documentation states the current behaviour in the present tense - never how the code used to work or what a change
  altered ("was X before", "no longer", "as of this task"), and never referencing `docs/tasks/` (task directories, task
  IDs, plans). `docs/tasks/` and `docs/ADRs/` are themselves exempt: they record history by design.

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
- Stick to planning when asked to plan. Do not start implementation unless the user explicitly asks for it.
- Always run `git` commands from the project root - never `cd` before git operations.
- Always follow the `committing` skill when writing commit messages.
