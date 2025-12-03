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

The product is currently in the MVP phase. This version will be useful for users but will not have all the planned features.

The MVP should include:

**User Management:**
- User can create an account and login
- Everything that user creates is available only to them

**Recipe Management:**
- User can save recipes from links
- User can save recipes from images
- User can create recipes manually
- User can update and delete recipes
- User can view recipes in a list view
- User can view individual recipes
- User can share recipes with other users

**Shopping List Management:**
- User can create shopping lists
- User can add items to shopping lists either manually or from saved recipes
- If user adds an item that is already on the list, its quantity should be updated
- User can tick list items as completed
- User can share shopping lists with other users

**Excluded from MVP:**
- Meal planning functionality will not be included in this phase

## 5. User Stories

### US-001

- Title: Save recipe from link
- Description: As a user, I want to paste a link to a recipe into the app and save it, extracting all useful
  information.
- Acceptance Criteria:
    - A form to input a link to a recipe
    - A button to save recipe information
    - A list with all recipes saved by the user

### US-002

- Title: Create recipe manually
- Description: As a user, I want to create a recipe manually by entering all the details myself.
- Acceptance Criteria:
    - A form to input recipe name, serving size, ingredients, and preparation steps
    - Optional field for reference link
    - A button to save the manually created recipe
    - Recipe appears in the user's recipe list after saving

### US-003

- Title: Update and delete recipes
- Description: As a user, I want to edit existing recipes and delete recipes I no longer need.
- Acceptance Criteria:
    - An edit button on each recipe that opens the recipe form with pre-filled data
    - Ability to modify any recipe field (name, serving size, ingredients, preparation steps, reference link)
    - A save button to commit changes
    - A delete button with confirmation dialog
    - Updated/deleted recipes reflect changes immediately in the recipe list

### US-004

- Title: Share recipe with other users
- Description: As a user, I want to share my saved recipes with other users so they can access and use them.
- Acceptance Criteria:
    - A share button available on each recipe
    - Ability to select or input other users to share with
    - Shared recipes appear in the recipient's recipe list
    - Shared recipes can be updated by the recipient but cannot be deleted
    - Clear indication of which recipes are shared vs. owned

### US-005

- Title: Create shopping list and add items
- Description: As a user, I want to create shopping lists and add items to them, either manually or from my saved recipes.
- Acceptance Criteria:
    - Ability to create a new shopping list with a name
    - Option to add items manually with quantity
    - Option to add all ingredients from a selected recipe
    - If an item already exists on the list, quantities should be combined
    - View all created shopping lists
    - View individual shopping list with all items

### US-006

- Title: Share shopping list and track completion
- Description: As a user, I want to share shopping lists with other users and track completion status when both users are using the shared list.
- Acceptance Criteria:
    - A share button available on each shopping list
    - Ability to select or input other users to share with
    - Both users can add items to the shared shopping list
    - Both users can tick items as completed
    - Real-time synchronization of changes between all users with access
    - Clear indication of which items have been completed