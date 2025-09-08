## FEATURE:

Implement user authentication for a Flutter application using Firebase Authentication.
If app is opened and user is not authenticated, show login screen (use guarded route with Go Router for that).
After successful login, navigate to recipe list screen.
Use ChangeNotifier to store authentication token.
Use authentication token in API requests to backend.
Add action to the app bar to log out the user on the recipe list screen.

## EXAMPLES:

- `docs/examples/ui-authentication.dart` - simple App with login screen and auth ChangeNotifier

## DOCUMENTATION:

- `docs/mobile/mobile.md` - mobile app overview
- `docs/mobile/ui.md` - mobile app UI and navigation documentation
- `docs/backend/api.md` - backend API documentation to indicate which endpoints require authentication

## OTHER CONSIDERATIONS:

- None
