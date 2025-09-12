## FEATURE:

Currently, when user choose extract recipe option they are taken to a screen where they can paste a URL, extract text
from it
and then extract recipe from that text.
I want to implement a dialog that is shown when user taps extract recipe button.
This dialog will have two options:

1. Extract from URL (current behavior)
2. Extract from Image (new behavior)
   When user selects "Extract from Image", they will be presented with an option to upload an image from their device.
   Once the image is uploaded, it will be sent to the backend API endpoint that will return the extracted recipe data
   which should be used the same way as the data extracted from URL.

## EXAMPLES:

- None

## DOCUMENTATION:

- `docs/mobile/mobile.md` - Mobile app documentation
- `docs/mobile/ui.md` - Mobile UI overview

## OTHER CONSIDERATIONS:

- Rename current ExtractionScreen to UrlExtractionScreen
- Create new ImageExtractionScreen for image upload and extraction