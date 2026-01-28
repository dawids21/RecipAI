# Product Requirements Document (PRD) - Meal Planning

## 1. Feature Overview

This feature allows users to organize their cooking schedule by assigning recipes or text placeholders to specific dates
on a calendar.

The system supports a multi-plan architecture, allowing a single user to manage distinct contexts (e.g., Personal Diet
vs. Family Dinners) simultaneously via a layered calendar view. These plans can be shared with other users via email
with specific permissions. Crucially, this feature integrates deeply with the Shopping List module, allowing users to
generate ingredient lists directly from their planned meals while adhering to strict privacy rules regarding recipe
ownership.

## 2. User Problem

While users can currently store recipes in RecipAI, the process of utilizing them for weekly execution remains manual
and disconnected. Users face the following challenges:

1. Decision Fatigue: Without a visual plan, users struggle to decide what to cook daily, often forgetting about recipes
   they previously saved.
2. Context Switching: Users often manage meals for different groups (themselves vs. their family), making a single
   linear plan insufficient.
3. Inefficient Shopping: converting a meal plan into a shopping list is tedious. Users must manually open every recipe
   planned for the week, calculate the required ingredient quantities based on needed serving sizes, and write them
   down.

## 3. Functional Requirements

### 3.1 Plan Management

1. Users must be able to create multiple distinct meal plans (e.g., Home, Work, Diet).
2. Users can assign a specific color to each plan for visual distinction.
3. Users can toggle the visibility of individual plans on the calendar view.
    - **Note:** Visibility state is stored locally on the user's device and does not sync across sessions or devices.
4. Plan Owners can delete plans.

### 3.2 Calendar and Visualization

1. Provide a **Weekly Agenda** interface.
    - A navigation strip allows users to switch between weeks.
    - The main view displays a vertical list of all 7 days for the selected week (e.g., Monday to Sunday).
    - Under each day header, meal entries from all visible plans are displayed, color-coded by their respective plan.
2. Support Calendar Layering: Entries from all visible plans are displayed simultaneously.
3. Users can view details of a planned meal by clicking the entry.

### 3.3 Meal Entry and Editing

1. Users can add a Recipe Entry to a specific date on a specific plan.
    - Requires selecting an existing recipe from the user's library.
    - Requires defining a Yield Multiplier (Serving Size) specific to this meal instance, independent of the original
      recipe's default serving size.
2. Users can add a Placeholder Entry to a specific date.
    - Consists of free-text (e.g., Leftovers or Pizza Night).
    - Does not link to a recipe ID.
3. If a recipe is deleted from the database, any calendar entries linking to it must remain as **Text Placeholders**.
    - The entry loses its link to the recipe ID.
    - The entry retains the original recipe name as its description.
    - Visually, it becomes indistinguishable from a manually added Placeholder.
4. Users can edit the date, plan assignment, serving size, or text of an entry.
5. Users can change the associated recipe of an entry, even if the current entry is a "Restricted Recipe" (see 3.4).

### 3.4 Sharing and Access Control

1. Plans follow an Owner/Editor model.
    - Owners have full control (including deletion of the plan).
    - Editors can add, edit, or remove entries within the plan.
    - Editors can rename the plan and change the plan color.
    - Sharing is initiated using the recipient's User Email.
2. Privacy Rule: If a user views a shared plan containing a recipe they do not own:
    - They see the Recipe Name.
    - They cannot view ingredients or instructions.
    - Clicking the entry triggers a toast message: Recipe not shared.
3. Synchronization: Updates to plans are fetched on request or view refresh (WebSockets are not required).

### 3.5 Shopping List Integration

1. Users can initiate a Generate Shopping List flow from the calendar.
2. Flow requires: Selection of Source Plan -> Selection of specific days (via a **multi-select calendar view**) ->
   Selection of Target Shopping List.
3. Aggregation Logic:
    - Recipe Entries: Ingredients are scaled by the entry's Yield Multiplier.
    - Placeholder Entries: The text title is added as a single line item.
    - **No Merging:** Items are not aggregated or merged server-side (e.g., if "Salt" appears twice, it remains two
      separate items). Items are sorted by name to assist the user.
    - Restricted Recipes: Ingredients are skipped. A summary warning is displayed to the user indicating which meals
      could not be processed due to lack of recipe ownership.

## 4. Feature Boundaries

### Included in Scope

- Creation and management of multiple meal plans per user.
- Color-coded calendar overlay.
- Text-only Placeholders.
- Variable serving sizes per meal instance.
- Sharing plans with Editor permissions (including renaming/recoloring).
- Server-side shopping list generation with privacy checks.
- Mobile and Web responsiveness.

### Excluded from Scope

- Monthly calendar view.
- Drag-and-drop rescheduling (standard edit forms will be used).
- Automated conflict resolution for shopping lists (no server-side merging of ingredients).
- Editing item details (name/quantity) during the shopping list generation review step (users can only check/uncheck
  items).
- Nutritional calculation based on plans.
- Printing or PDF export of the calendar.
- Real-time socket-based collaboration (sync happens on load/refresh).
- Quick Create recipe modal from the calendar view.
- Syncing "Visible" plan settings across devices.

## 5. User Stories

### US-MP-001 - Create and Manage Meal Plans

- Description: As a user, I want to create distinct meal plans with specific names and colors so that I can organize
  different aspects of my cooking life (e.g., personal vs. family).
- Acceptance Criteria:
    - A single unified list displays all available plans (both "My Plans" and "Shared Plans").
    - Button to create a new plan requiring a Name and Color selection.
    - Option to toggle a Visible checkbox for each plan (setting saved to local storage).
    - Option to delete a plan (only if the user is the Owner).
    - Deleted plans remove all associated calendar entries.

### US-MP-002 - View Weekly Agenda

- Description: As a user, I want to see a list of all meals planned for the current week, grouped by day, so I can
  understand the week's schedule without clicking individual dates.
- Acceptance Criteria:
    - Week Strip navigation allows changing the visible week.
    - The main view displays a vertical list of days (Monday - Sunday).
    - Each day section lists the meals scheduled for that date.
    - Each entry has a background color matching its parent Plan.
    - Entries show the Recipe Name or Placeholder Text.
    - Non-visible plans do not show entries.

### US-MP-003 - Add Recipe to Plan with Custom Yield

- Description: As a user, I want to schedule a recipe for a specific day and define how many people I am cooking for, so
  that the shopping list will reflect the correct amounts.
- Acceptance Criteria:
    - Clicking an "Add" button opens a creation modal.
    - The date defaults to the start date of the currently viewed week.
    - User selects the target Plan (e.g., Family).
    - User selects Recipe type and searches their library.
    - User inputs a Serving Size for this specific meal (defaults to recipe's original size).
    - On save, the entry appears on the calendar.

### US-MP-004 - Add Placeholder to Plan

- Description: As a user, I want to add a text-only entry for meals that don't need a recipe (like Takeout), so that my
  calendar reflects reality.
- Acceptance Criteria:
    - In the Add Meal modal, user selects Placeholder type.
    - Input field for Description (Text).
    - No ingredient or serving size fields are displayed.
    - On save, the entry appears on the calendar.

### US-MP-005 - Secure Plan Sharing

- Description: As a user, I want to share a meal plan with another user so we can collaborate on what to eat.
- Acceptance Criteria:
    - Share button on the Plan settings.
    - Input field for recipient User Email.
  - Recipient sees the plan in their Plan list.
    - Recipient has Editor rights: they can add/remove meals, rename the plan, and change the plan color.
    - Recipient cannot delete the Plan itself.

### US-MP-006 - Restricted Recipe Access

- Description: As a user viewing a shared plan, I should not be able to see the details of a recipe I do not own,
  ensuring the owner's privacy settings are respected.
- Acceptance Criteria:
    - When viewing a shared plan on the calendar, entries for unowned recipes are visible.
  - Clicking an unowned recipe entry DOES NOT open the recipe detail view (Toast notification only).
  - Users CAN edit the entry to change the date, yield, or replace the restricted recipe with a different one.
    - Ingredients from these recipes are not accessible via API responses for the viewer.

### US-MP-007 - Generate Shopping List from Plan

- Description: As a user, I want to generate a shopping list from selected days so that I can buy groceries for specific
  meals while skipping days where I don't need to shop (e.g., dining out).
- Acceptance Criteria:
    - Button Generate Shopping List.
    - Step 1: User selects which visible Plans to include.
  - Step 2: User is presented with a month-view calendar to multi-select specific days.
    - Step 3: User selects a target Shopping List (new or existing).
    - System calculates total ingredients based on the Yield Multiplier set in the plan.
    - System adds Placeholder titles as single-line items.
    - Items are appended to the target list.

### US-MP-008 - Shopping List Export Privacy Handling

- Description: As a user generating a list from a shared plan, I need to know if ingredients were skipped because I
  don't have access to the recipe.
- Acceptance Criteria:
    - During the generation process (US-MP-007), if a selected day contains a Restricted Recipe (User does not own it):
    - The ingredients are NOT added to the shopping list.
    - A summary modal appears after generation: The following meals were skipped because you do not have access to the
      recipes: [List of Meal Names].

### US-MP-009 - Handling Deleted Recipes

- Description: As a user, I want my past meal plans to remain readable even if I delete the original recipe from my
  library.
- Acceptance Criteria:
    - If a user deletes a recipe that is used in a Meal Plan:
    - The Calendar Entry remains visible.
  - The Entry converts to a Text Placeholder.
  - Clicking the entry shows the name as a standard text placeholder.
    - Shopping list generation treats it as a Placeholder (adds Name to list, no ingredients).

## 6. Success Metrics

To validate the success of the Meal Planning feature, the following metrics will be tracked:

1. Plan-to-Shop Conversion Rate: The percentage of created meal plans (weekly cohorts) that result in a Generate
   Shopping List action. This measures the utility of the integration.
2. Weekly Active Planners: The number of unique users who add at least one entry to a calendar per week.
3. Shared Plan Adoption: The number of plans containing more than one user (Owner + at least one Editor).
4. Placeholder Usage Rate: The percentage of calendar entries that are Placeholders vs. Linked Recipes (helps determine
   if users prefer flexibility or structure).