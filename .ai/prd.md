# Product Requirements Document (PRD) - RecipAI

## 1. Product Overview

The RecipAI app helps users manage recipes for daily cooking.
It stores recipes imported from various sources, creates shopping lists, and allows users to plan
meals for specific days. The app is available on both mobile and web platforms.

## 2. User Problem

When creating a meal plan, users need to find recipes from many different sources and create
the plan in a note-taking app.
This process makes it difficult to remember good recipes, which may be lost if the user forgets to save
the link for a given recipe.
Additionally, writing a shopping list is tedious, as users need to go through all planned meals
and check all the required ingredients, which may be duplicated across different recipes.

## 3. Functional Requirements

1. Save recipes
    - Allow creating, updating, deleting, and listing saved recipes
    - Each recipe should have a name, serving size, reference link, ingredients, and preparation steps
2. Import recipes from various sources
    - Extract information about a recipe from a given web page, screenshot, or file
3. Plan meals
    - Plan meals for a specific day using saved recipes
4. Create shopping lists
    - Add ingredients needed for planned meals
    - Add ingredients from any saved recipe to a shopping list
5. Sharing
    - Users can share recipes with other users
    - Users can share meal plans with other users
    - Users can share shopping lists with other users

## 4. Product Boundaries

The product is currently in the demo phase. In this phase, the product should present minimum functionality
that is useful for users.
The product should:

- Allow users to import recipes from links
- List recipes saved by the user as a single list
- The demo phase does not require user management or authorization; each saved recipe will be visible
  whenever the app is opened

## 5. User Stories

### US-001

- Title: Save recipe from link
- Description: As a user, I want to paste a link to a recipe into the app and save it, extracting all useful
  information.
- Acceptance Criteria:
    - A form to input a link to a recipe
    - A button to save recipe information
    - A list with all recipes saved by the user
