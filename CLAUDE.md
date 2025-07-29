# CLAUDE.md

## Project Overview

- RecipAI is an application that helps users manage recipes for daily cooking.
- Backend for the project is built with Spring Boot and Java. Source code is available in the `backend/` directory.
- Mobile application is built with Flutter and Dart, available in the `mobile/` directory.
- Documentation for the project is available in the `docs/` directory.

## Project Context

- When implementing new features, restrict changes to a single codebase (either backend or mobile).
- Always read `docs/prd.md` to understand the product requirements and current scope.
- Before planning a new feature on the backend, check the `docs/api.md` file to check API endpoints and `docs/db.md` to check database structure.
- Before planning a new feature on the mobile app, check the `docs/api.md` file to check backend API endpoints and `docs/ui.md` to check UI components.

## Planning and Implementation

## AI Behavior
- Never assume missing context. Ask questions if uncertain.
- Never hallucinate libraries or functions.