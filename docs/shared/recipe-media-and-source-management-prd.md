# Product Requirements Document (PRD) - Recipe Media and Source Management

## 1. Feature Overview

The Recipe Media and Source Management feature enhances the core Recipe entity by allowing users to attach visual media
and source attribution to their recipes. This feature enables users to upload up to two images per recipe (either of the
finished dish or a photo of a physical recipe source) and attach a direct URL to the original source.

This addition serves two primary business goals:

1. **Visual Inspiration:** Improving the browsing experience in the list view via thumbnails.
2. **Content Archival:** Allowing users to quickly digitize physical cookbooks or handwritten notes without immediate
   manual transcription.

## 2. User Problem

Currently, recipes in the application are purely text-based. This creates several friction points for users:

1. **Lack of Visual Recognition:** Users struggle to quickly identify recipes in a long list without visual cues (
   thumbnails).
2. **Tedious Data Entry:** When a user wants to save a recipe from a physical cookbook or index card, they are forced to
   type out all ingredients and steps. This high effort often discourages users from saving the recipe at all.
3. **Lost Context:** Users often copy recipe text from a website but forget to save the link. Later, if they want to
   check the original article for comments or variations, they have no way to return to the source.

## 3. Functional Requirements

### 3.1. Image Management

* **Capacity:** Each recipe supports a maximum of 2 images.
* **File Constraints:** Supported formats are JPG and PNG. Maximum file size is 5MB per image.
* **Input Sources:**  Direct access to Camera and Device Gallery.
* **Display Logic:**
    * **List View:** The first uploaded image serves as the thumbnail.
    * **Detail View:** Images are displayed in a carousel or grid. Clicking an image opens a full-screen viewer with
      zoom capabilities.
* **Storage:** Images are stored statically. Deletion is permanent and immediate (no trash bin).

### 3.2. Source Link Management

* **Capacity:** Single URL field per recipe.
* **Validation:** Input must be a valid URL format (http/https).
* **Display:**
    * The UI must extract and display the domain name (e.g., "nytimes.com") alongside the link for user context.
    * Clicking the link opens the URL in the device's default external browser, not an internal WebView.

### 3.3. Shared Recipe Logic (Single Source of Truth)

* **Synchronization:** Images and links are associated with the recipe entity. Changes made by the owner or any user
  with shared access are reflected immediately for all users.
* **Destructive Actions:** Deleting an image or removing a link affects all users. This requires specific UI warnings.

## 4. Feature Boundaries

### Included in Scope

* Uploading images from device storage or camera.
* Viewing images in list (thumbnail) and detail (full-screen) modes.
* Adding, editing, and removing a single source URL.
* Domain name extraction for UI display.
* Configurable limits (currently set to 2 images, 5MB limit) defined in backend configuration.

### Excluded from Scope

* **Video Uploads:** No support for video files.
* **Image Captions:** No text descriptions attached specifically to images.
* **Internal WebView:** Links will not open inside the app; they strictly hand off to the OS browser.
* **Multiple Links:** Only one source link is allowed per recipe.
* **OCR Processing:** While OCR may *feed* into this feature in the future, this feature is strictly about the storage
  and display of the image, not the text extraction process.

## 5. User Stories

### US-001 - Upload Image during Creation

* **Title:** Upload image from device while creating a recipe
* **Description:** As a user, I want to upload a photo of my food or a cookbook page when creating a new recipe so that
  I can visually identify it later.
* **Acceptance Criteria:**
    * The "Create Recipe" form contains an "Add Image" button.
    * On mobile, tapping "Add Image" prompts the user to choose between "Camera" or "Gallery".
    * User can upload up to 2 images.
    * If the user attempts to upload a file larger than 5MB, an error message is displayed.
    * If the user attempts to upload a non-image file (e.g., PDF), an error message is displayed.
    * Uploaded images are visible as previews in the form before saving.

### US-002 - Add Source Link

* **Title:** Add and View Source URL
* **Description:** As a user, I want to attach a link to the recipe and see its source domain so I know where the recipe
  came from.
* **Acceptance Criteria:**
    * The recipe form contains a single input field for "Source".
    * The system validates that the input is a proper URL format.
    * On the Recipe Detail view, the link is displayed with the extracted domain name as the label (e.g., "Source:
      bonappetit.com").
    * Clicking the link opens the URL in the device's external browser.

### US-003 - View Recipe Images

* **Title:** View images in List and Detail modes
* **Description:** As a user, I want to see thumbnails in my recipe list and full-size images in the recipe details.
* **Acceptance Criteria:**
    * In the Recipe List view, recipes with images display the first image as a thumbnail.
    * In the Recipe List view, recipes without images display a default placeholder icon.
    * In the Recipe Detail view, all attached images are displayed.
    * Tapping an image in Detail view opens a full-screen viewer.
    * User can pinch-to-zoom or double-tap to zoom within the full-screen viewer.

### US-004 - Edit and Replace Media

* **Title:** Edit recipe to add or remove media
* **Description:** As a user, I want to edit an existing recipe to replace a photo or change the source link.
* **Acceptance Criteria:**
    * User can access the "Edit Recipe" screen for an existing recipe.
    * User can delete an existing image.
    * User can upload a new image if the total count is under 2.
    * User can edit the Source URL text.
    * Changes are not committed until the user clicks "Save" on the form.

### US-005 - Image Limit Enforcement

* **Title:** Enforce image count limits
* **Description:** As a user, I should be prevented from uploading more than the allowed number of images.
* **Acceptance Criteria:**
    * If a recipe already has 2 images, the "Add Image" button is disabled or hidden.
    * If a user tries to upload multiple images at once that exceed the remaining slot count, the system rejects the
      excess images with a clear message.