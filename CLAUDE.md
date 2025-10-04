# CLAUDE.md

## Project Overview

- RecipAI is an application that helps users manage recipes for daily cooking.
- Backend for the project is built with Spring Boot and Java. Source code is available in the `backend/` directory.
- Mobile application is built with Flutter and Dart, available in the `mobile/` directory.
- Documentation for the project is available in the `docs/` directory.
- Always read `docs/prd.md` at the start of a new conversation to understand the project

## Documentation

- `docs/prd.md` - **Product Requirements Document (PRD)** - Contains product overview, user problems, functional
  requirements, and user stories.
- `docs/backend/backend.md` - **Backend App Overview** - Provides an overview of the backend modules and codebase
  structure.
- `docs/backend/api.md` - **API Documentation** - Contains API endpoints, request/response formats, and examples.
- `docs/backend/db.md` - **Database Schema** - Describes the database structure, tables
- `docs/mobile/mobile.md` - **Mobile App Overview** - Provides an overview of the mobile app, implemented features, its
  codebase structure and usage patterns.
- `docs/mobile/ui.md` - **Mobile UI Components** - Lists the screens and UI components used in the mobile app. Contains
  navigation flow, theme system and data models.
- `docs/mobile/architecture.md` - **Mobile App Architecture** - Describes the architecture of the mobile app.
- `docs/mobile/upload_key.md` - **Upload Key Management** - Instructions for encrypting/decrypting the app upload key.

## AI Behavior

- When implementing new features, restrict changes to a single codebase (either backend or mobile).
- Never assume missing context. Ask questions if uncertain.
- Never hallucinate libraries or functions.