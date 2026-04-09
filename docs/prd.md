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

**Meal Planning:**

- User can create multiple distinct meal plans
- User can view a calendar with meal entries
- User can add recipes or placeholders to the calendar
- User can share meal plans with other users
- User can generate shopping lists from meal plans