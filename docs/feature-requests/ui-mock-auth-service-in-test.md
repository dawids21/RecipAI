## FEATURE:

I need to mock AuthService used in mobile app so I can run tests without Firebase.
Create an interface for AuthService with isAuthenticated, idToken, signIn, signOut, dispose methods.
Additionaly create a InheritedAuthService to provide AuthService down the widget tree.
In the main.dart inject the real AuthService using main app parameter and pass it to InheritedAuthService.
In test create mock AuthService and inject it to InheritedAuthService.

## EXAMPLES:

- None

## DOCUMENTATION:

- `docs/mobile/mobile.md` - mobile app overview

## OTHER CONSIDERATIONS:

- Ensure that smoke test is now working