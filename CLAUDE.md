# CLAUDE.md

## Project Overview

- RecipAI is an application that helps users manage recipes for daily cooking.
- Backend for the project is built with Spring Boot and Java. Source code is available in the `backend/` directory.
- Mobile application is built with Flutter and Dart, available in the `mobile/` directory.
- Shared documentation for the project is available in the `docs/shared` directory.
- App documentation is available in the `docs` directory in the corresponding subdirectories (eith backend or mobile).

## Documentation

- `./docs/shared/prd.md` - **Product Requirements Document (PRD)** - Contains product overview, user problems,
  functional
  requirements, and user stories.
- `./docs/shared/tech-stack.md` - **Tech Stack Overview** - Contains the list of technologies used in the project.

## AI Behavior

- When implementing new features, restrict changes to a single codebase (either backend or mobile).
- Never assume missing context. Ask questions if uncertain.
- Never hallucinate libraries or functions.