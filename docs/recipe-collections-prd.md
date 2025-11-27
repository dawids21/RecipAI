# Product Requirements Document (PRD) - Recipe Collections

## 1. Feature Overview

The Recipe Collections feature introduces a folder-based organization system to RecipAI. Currently, users manage recipes
in a single, flat list. This feature allows users to group recipes into specific named collections (e.g., "Holiday
Dinner", "Keto Favorites").

Beyond personal organization, Collections serve as the primary vehicle for bulk sharing. Users can share a specific
collection with other users, granting them "Editor" access. This creates a shared repository where multiple users can
contribute recipes and view a curated set of meals, streamlining collaboration for households and groups.

## 2. User Problem

Users currently face two primary issues regarding recipe management:

1. **Disorganization:** As a user saves more recipes, the main list becomes cluttered and difficult to navigate. Users
   cannot group recipes by theme, occasion, or dietary preference, making retrieval time-consuming.
2. **Inefficient Sharing:** Sharing recipes is currently limited to individual items. For families or groups planning
   meals together, sharing recipes one by one is tedious. There is no central space where a group can maintain a "living
   list" of agreed-upon recipes.

## 3. Functional Requirements

### 3.1 Collection Structure

- Collections function as folders. There is a strict 1-to-1 relationship between a recipe and a collection. A recipe
  cannot exist in two collections simultaneously.
- The hierarchy is flat; nested collections (sub-folders) are not supported.

### 3.2 Collection Management

- Users can create new collections with a custom name.
- Users can rename existing collections. Renaming a shared collection updates the name for all users with access.
- Users can delete collections they own.

### 3.3 Recipe Assignment

- Recipes can be assigned to a collection during creation or import via a dropdown menu.
- Existing recipes can be moved from one collection to another (or to "Unassigned") via the recipe edit screen.
- If a user is viewing a specific collection and creates a new recipe, it is automatically assigned to that collection.

### 3.4 Sharing and Permissions

- **Owner Role:** The user who creates the collection. They have full control, including the ability to delete the
  collection.
- **Editor Role:** Users with whom the collection is shared. Editors can add recipes to the collection and rename the
  collection. They cannot delete the collection itself.
- **Onboarding:** Sharing follows a "push" model. Recipients are automatically added to the collection without needing
  to accept an invitation.

### 3.5 Data Ownership and Deletion Logic

- **Collection Deletion:** If an Owner deletes a collection, it is removed for all users.
    - Recipes owned by the Owner become "Unassigned" in the Owner's library.
    - Recipes owned by Editors (contributors) are preserved but moved to "Unassigned" in the Editor's personal library.
- **Recipe Movement:** If a recipe is moved out of a shared collection into a private one, shared access is immediately
  revoked for other users.

## 4. Feature Boundaries

### In Scope

- Creating, renaming, and deleting collections.
- Assigning recipes to collections (1-to-1 relationship).
- Filtering the main view by "All", specific collection, or "Unassigned".
- Sharing collections with Editor permissions.
- "Last Save Wins" conflict resolution for edits.
- Context-aware creation (auto-assign based on current view).

### Out of Scope

- Nested collections or sub-folders.
- Tagging system (many-to-many relationships).
- Bulk actions (e.g., selecting multiple recipes to move at once).
- Shopping list aggregation from collections.
- In-app notifications or badges for updates.
- Acceptance flow for shared collection invitations.

## 5. User Stories

### US-COL-001 - Create Collection

- Description: As a user, I want to create a new collection so that I can categorize my recipes.
- Acceptance Criteria:
    - A mechanism to create a new collection from the main view.
    - User must provide a name for the collection.
    - Upon creation, the collection list is updated immediately.
    - The new collection is empty by default.

### US-COL-002 - Filter Recipe List by Collection

- Description: As a user, I want to filter my recipe view so that I only see recipes belonging to a specific category.
- Acceptance Criteria:
    - A filter element (e.g., dropdown or sidebar) displaying "All Recipes", "Unassigned", and a list of user-created
      collections.
    - Selecting a collection displays only recipes assigned to that collection.
    - Selecting "All Recipes" displays everything.
    - The default sort order for the filtered view is "Date Added" (Newest First).

### US-COL-003 - Assign Recipe to Collection (Create/Import)

- Description: As a user, I want to assign a recipe to a collection while I am creating or importing it so that it is
  organized immediately.
- Acceptance Criteria:
    - The "Create Recipe" and "Import Recipe" forms include a dropdown to select a collection.
    - The dropdown lists all available collections plus an "Unassigned" option.
    - Saving the recipe places it in the selected collection.
    - If the user was viewing a specific collection prior to clicking "Create", that collection is pre-selected in the
      dropdown.

### US-COL-004 - Move Recipe Between Collections

- Description: As a user, I want to change the collection a recipe belongs to so that I can reorganize my library.
- Acceptance Criteria:
    - The "Edit Recipe" form includes a collection selection dropdown.
    - Changing the selection from Collection A to Collection B moves the recipe.
    - The recipe no longer appears in Collection A (1-to-1 relationship).
    - If the recipe was in a shared collection and is moved to a private one, other users lose access to that recipe.

### US-COL-005 - Share Collection

- Description: As a user (Owner), I want to share a collection with another user so that we can collaborate on a meal
  list.
- Acceptance Criteria:
    - A "Share" option is available on the collection management menu.
    - Owner can input the email or username of the recipient.
    - Upon submission, the recipient is immediately granted access (no invite acceptance required).
    - The recipient receives "Editor" permissions.

### US-COL-006 - Access Shared Collection

- Description: As a recipient, I want to see collections shared with me in my filter list so that I can access those
  recipes.
- Acceptance Criteria:
    - Shared collections appear in the user's collection filter list.
    - There is a visual distinction or label indicating the collection is shared (if UI allows, otherwise seamless).
    - Clicking the collection shows all recipes contained within, regardless of who created the individual recipe.

### US-COL-007 - Editor Contributes Recipe

- Description: As an Editor (recipient), I want to add a recipe to a shared collection so that the Owner can see my
  suggestion.
- Acceptance Criteria:
    - Editors can select the shared collection when creating or editing a recipe they own.
    - The recipe appears in the collection for all users who have access.
    - The Editor retains ownership of the recipe they added.

### US-COL-008 - Rename Collection (Sync)

- Description: As an Owner or Editor, I want to rename a collection so that the name reflects its contents better.
- Acceptance Criteria:
    - Owners and Editors have access to a "Rename" function.
    - Changing the name updates it for ALL users with access (Global Renaming).
    - "Last Save Wins" applies if two users rename simultaneously.

### US-COL-009 - Delete Collection (Owner)

- Description: As an Owner, I want to delete a collection so that I can remove unwanted organization structures.
- Acceptance Criteria:
    - Only the Owner has the option to delete the collection.
    - Upon deletion, the collection disappears for the Owner and all shared Editors.
    - Recipes created by the Owner are moved to "Unassigned" in the Owner's library.
    - Recipes created by Editors are moved to "Unassigned" in the specific Editor's library.
    - No recipes are deleted; only the folder association is removed.

### US-COL-010 - Leave Shared Collection

- Description: As an Editor, I want to remove a shared collection from my view if I no longer want to participate.
- Acceptance Criteria:
    - Editors have an option to "Leave" or "Unlink" a shared collection.
    - The collection is removed from the Editor's list.
    - Recipes owned by the Editor that were in the collection are moved to the Editor's "Unassigned" list.
    - The Owner retains the collection and any recipes the Owner added, but loses access to the recipes the departing
      Editor took with them.

### US-COL-011 - Secure Collection Access

- Description: As a system, I want to ensure only authorized users can view recipes within a private or shared
  collection.
- Acceptance Criteria:
    - API endpoints for retrieving collection details must verify the requesting user is either the Owner or listed in
      the shared users list.
    - Attempts to access a collection ID by a non-authorized user return a 403 Forbidden error.
    - Moving a recipe out of a shared collection immediately updates Access Control Lists (ACLs) so that former
      collaborators can no longer access the recipe via direct link.

## 6. Success Metrics

While this feature is driven by user request rather than strict quantitative goals, the following metrics will help
assess adoption and utility:

1. **Adoption Rate:** Percentage of active users who have created at least one custom collection.
2. **Collaboration Rate:** Percentage of collections that are shared with at least one other user.
3. **Organization Depth:** Average number of recipes per collection (indicates if users are finding the grouping
   useful).