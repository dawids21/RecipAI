## FEATURE:

Implement some minor UI refactors:

- Fix usage of ApiService in every place when data is fetched in didChangeDependencies (like in RecipeSharingDialog)
- Replace isAuthenticated boolean flag in AuthService by String? email (null means not authenticated), keep bool get
  isAuthenticated and add getter for email (empty string if not authenticated)
- Remove unshare button for the current user in recipe sharing dialog by adding AuthService and checking if the email to
  unshare is the same as the authenticated user email
- Update documentation to reflect these changes

## EXAMPLES:

- None

## DOCUMENTATION:

- `docs/mobile/ui.md` - UI overview, feature descriptions
- `docs/mobile/mobile.md` - Codebase structure, usage patterns

## OTHER CONSIDERATIONS:

- None
