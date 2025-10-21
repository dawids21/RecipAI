# Product Requirements Document (PRD) - Shopping Lists Management

## 1. Feature Overview

This document outlines the requirements for the Shopping Lists Management feature within the RecipAI application. This
feature will provide users with a dedicated interface to create, manage, and share shopping lists. A key component is
the seamless integration with the existing Recipe Management system, allowing users to add ingredients from their saved
recipes directly to a shopping list. The feature is designed to be collaborative, enabling multiple users to access and
modify a shared list in near real-time (within 5-10 seconds). It will also support robust offline functionality,
allowing users to fully manage
their list items (add, edit, delete, check) even without an internet connection.

## 2. User Problem

The current process for users to create a shopping list is disjointed and inefficient. Users find good recipes from
various sources and save them in the app, but when it comes to grocery shopping, they must manually review each recipe
and compile a list in a separate note-taking application. This is a tedious process, prone to errors, and makes it
difficult to manage ingredients that are duplicated across different recipes. For households where more than one person
shops, coordinating the list and tracking what has been purchased is an additional challenge that relies on out-of-app
communication. This feature aims to solve these problems by creating a centralized, integrated, and collaborative
shopping list solution.

## 3. Functional Requirements

- 3.1. List Creation and Management
    - 3.1.1. Users must be able to create multiple, uniquely named shopping lists.
    - 3.1.2. A dedicated screen will display all shopping lists created by or shared with the user.
- 3.2. Item Management
    - 3.2.1. Users can manually add items to a list, specifying a name, quantity, and unit.
    - 3.2.2. Users can mark items on the list as "checked" to indicate they have been purchased.
    - 3.2.3. Users can manually reorder items within a list via a drag-and-drop interface.
    - 3.2.4. Users have an option to "uncheck all items" on a list, facilitating the reuse of lists for recurring
      shopping trips.
    - 3.2.5. Users have an option to "delete all checked items" from a list for quick cleanup.
- 3.3. Integration with Recipes
    - 3.3.1. Users can add ingredients to a shopping list from a single saved recipe at a time.
    - 3.3.2. The workflow for adding ingredients from a recipe will present users with a screen to review, select, and
      edit the ingredients (name, quantity, unit) before adding them to a target list.
    - 3.3.3. When adding ingredients, if an item with the exact same name and unit already exists on the list, the
      quantities will be merged. No summary message of the merge action will be shown to the user.
    - 3.3.4. Ingredients added from a recipe will default to an unchecked state.
- 3.4. Sharing and Collaboration
    - 3.4.1. Users can share shopping lists with other registered RecipAI users via email invitation.
    - 3.4.2. A two-tiered permission system will be used:
        - OWNER: The creator of the list. Can edit the list, manage collaborators, and delete the list.
        - EDITOR: A user invited to the list. Can edit the list and manage other EDITORs, but cannot remove the OWNER.
    - 3.4.3. All changes made to a shared list (adding/editing/checking items) by one collaborator will be reflected in
      near real-time (within 5-10 seconds) for all other collaborators.
    - 3.4.4. Conflict Resolution: In the event of simultaneous conflicting actions, a "first action wins" strategy will
      be implemented. The system will log the conflict, but the user whose action was rejected will receive no feedback.
    - 3.4.5. An EDITOR can choose to leave ("unfollow") a list they have been invited to.
    - 3.4.6. If the OWNER deletes a shopping list, it will be immediately and permanently removed for all collaborators.
- 3.5. Offline Functionality
    - 3.5.1. The application will cache shopping lists on the user's device.
    - 3.5.2. Users can view lists and fully manage list items (add, edit, delete, check/uncheck) while offline.
    - 3.5.3. All changes made while offline will be synced with the server once an internet connection is
      re-established.

## 4. Feature Boundaries

- In Scope:
    - Dedicated screen for managing all shopping lists.
    - Manual creation of lists and list items.
    - Adding ingredients from a single saved recipe at a time.
  - Near real-time collaboration (within 5-10 seconds) for shared lists with OWNER/EDITOR roles.
    - Basic conflict resolution ("first action wins").
    - Manual drag-and-drop reordering of list items.
    - Bulk actions: "Uncheck all" and "Delete all checked".
    - Full offline support for list item management (add, edit, delete, check/uncheck).
- Out of Scope:
    - Meal planning functionality.
    - Adding ingredients from multiple recipes simultaneously.
    - Automatic categorization of shopping list items (e.g., produce, dairy).
    - Advanced conflict resolution with user feedback.
    - In-app guides or onboarding tutorials for this feature's initial release.
    - Price tracking or budgeting features.
    - Integration with third-party grocery services.

## 5. User Stories

### US-SL-001 - Create a new shopping list

- Description: As a user, I want to create a new, empty shopping list so I can start adding items I need to buy.
- Acceptance Criteria:
    - There is a button or control on the main shopping list screen to initiate list creation.
    - I am prompted to enter a name for the new list.
    - The new list appears on my screen of all shopping lists after it is created.

### US-SL-002 - Manually add an item to a shopping list

- Description: As a user, I want to manually add an item to my shopping list so I can include things that are not part
  of a recipe.
- Acceptance Criteria:
    - Inside a specific shopping list, there is a form or button to add a new item.
    - The form allows me to enter an item name, quantity, and unit.
    - After adding, the new item appears as an unchecked item in the list.

### US-SL-003 - Add ingredients from a saved recipe

- Description: As a user, I want to add all or some ingredients from one of my saved recipes to a shopping list to save
  time and avoid manual entry.
- Acceptance Criteria:
    - There is an "Add to Shopping List" option on the recipe details view.
    - There is an "Add from Recipe" option within a shopping list view.
    - Both options lead to a view where I can select a target shopping list (or create a new one).
    - I am then shown a list of all ingredients from the selected recipe with checkboxes.
    - I can edit the name, quantity, and unit for each ingredient before adding them.
    - Upon confirmation, the selected ingredients are added to the target shopping list.
    - If an item with an identical name and unit already exists, its quantity is added to the existing item's quantity.

### US-SL-004 - Check and uncheck shopping list items

- Description: As a user, I want to check off items on my shopping list as I buy them, so I can track my progress.
- Acceptance Criteria:
    - Each item on the shopping list has a checkbox next to it.
    - Tapping the checkbox marks the item as completed, providing a clear visual distinction (e.g., strikethrough).
    - Tapping the checkbox again un-marks the item.

### US-SL-005 - Reorder items in a shopping list

- Description: As a user, I want to reorder the items in my shopping list so I can group them in a way that makes sense
  for my shopping trip.
- Acceptance Criteria:
    - I can press and hold on a list item to initiate a drag-and-drop action.
    - I can move the item to a new position in the list.
    - The new order is saved and reflected for all collaborators.

### US-SL-006 - Share a shopping list with another user

- Description: As an owner of a shopping list, I want to share it with another user so we can collaborate on it.
- Acceptance Criteria:
    - There is a "Share" button on the shopping list details view.
    - I can input the email address of another RecipAI user to send an invitation.
    - The invited user receives a notification and the shared list appears in their account with "EDITOR" permissions.
    - I can see a list of all users who have access to my list.

### US-SL-007 - Collaborate on a shared list in near real-time

- Description: As a collaborator on a shared shopping list, I want to see updates from other users in near real-time (
  within 5-10 seconds) so we
  are always working with the most current version.
- Acceptance Criteria:
    - When another collaborator adds an item, it appears on my list within 5-10 seconds.
    - When another collaborator checks an item, its state updates on my list within 5-10 seconds.
    - When another collaborator edits or deletes an item, the change is reflected on my list within 5-10 seconds.

### US-SL-008 - Leave a shared shopping list

- Description: As a user who has been invited to a shopping list, I want to be able to leave the list if I no longer
  need access to it.
- Acceptance Criteria:
    - On a shopping list shared with me (where I am an EDITOR), there is an option to "Leave List".
    - After confirming, the list is removed from my account.
    - The list owner and other collaborators see that I am no longer a member.
    - Items I previously added to the list remain on the list.

### US-SL-009 - Delete a shopping list

- Description: As the owner of a shopping list, I want to delete a list I no longer need.
- Acceptance Criteria:
    - There is a "Delete" option available only to the OWNER of the list.
    - A confirmation dialog is shown to prevent accidental deletion.
    - Upon confirmation, the list is permanently removed from my account and from the accounts of all collaborators.

### US-SL-010 - Manage list with bulk actions

- Description: As a user, I want to quickly manage my list by unchecking all items for a new trip or deleting everything
  I've already bought.
- Acceptance Criteria:
    - The shopping list view contains an "Uncheck all items" option.
    - Activating this option changes the status of all checked items to unchecked.
    - The shopping list view contains a "Delete all checked items" option.
    - Activating this option removes all checked items from the list after a confirmation.

### US-SL-011 - Full offline list management

- Description: As a user, I want to be able to fully manage my shopping list items even when I don't have an internet
  connection.
- Acceptance Criteria:
    - I can open the app and navigate to my shopping lists without an internet connection.
    - I can view all items on my lists while offline.
    - I can check and uncheck items while offline.
    - I can add new items to the list while offline.
    - I can edit or delete existing items while offline.
    - When my device reconnects to the internet, all changes I made offline (adds, edits, deletes, checks) are
      automatically synced to the server.

### US-SL-012 - Secure list access

- Description: As a logged-in user, I want to be sure that only I and the people I share with can access my shopping
  lists.
- Acceptance Criteria:
    - A user must be logged in to create or view any shopping lists.
    - When querying for lists, the API returns only the lists created by the user or lists that have been explicitly
      shared with the user.
    - A user cannot access a shopping list via a direct link or ID unless they are the owner or an invited collaborator.

## 6. Success Metrics

The success of the Shopping Lists Management feature will be evaluated based on the following Key Performance
Indicators (KPIs), measured within the first three months post-launch:

1. Recipe Integration: A minimum of 50% of all items added to shopping lists must be generated from saved recipes. This
   will indicate that the feature is successfully solving the core user problem of manual list creation.
2. Collaboration Adoption: A minimum of 50% of all created shopping lists must have more than one collaborator (an OWNER
   and at least one EDITOR). This will validate the utility of the sharing and near real-time collaboration
   functionality.
