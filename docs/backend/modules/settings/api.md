# Settings API

`/settings/**` is in the authenticated matchers of `config.security.SecurityConfig`. `/admin/**` requires
the admin authority — see `docs/backend/modules/config/module.md` → Administrator authority.

### DELETE /settings/account
- Description: Permanently delete the caller's account data
- Authenticated: true
- Behavior:
    - The account is the `email` claim of the caller's token; no parameter names another account
    - Deletes every recipe (with its images), collection, shopping list (with its items) and meal plan
      the caller owns, every permission row they hold in either role, every invite addressed to or sent
      by them, all their limit usage, and any quota override configured for them
    - Resources the caller holds only as EDITOR survive; only the caller's EDITOR row is removed
    - A later sign-in with the same account starts from an empty account with the default quotas
    - The purge order and partial-failure behaviour are in `module.md`
- Success: 204 No Content, also when the caller owns nothing
- Errors: 403 Forbidden (no token — CSRF protection refuses a token-less DELETE before authentication
  runs)

### DELETE /admin/accounts
- Description: Permanently delete the data of the account an administrator names
- Authenticated: true, with the admin authority
- Query params: `email` (required, valid email) — the account to delete, matched exactly as stored;
  its case is not normalised
- Behavior:
    - Deletes what `DELETE /settings/account` deletes, for the named email instead of the caller's
    - The Firebase identity is not deleted; the operator removes it from the Firebase console
- Success: 204 No Content, also when the named email owns nothing
- Errors:
    - 400 Bad Request (`email` missing, blank or malformed — reported to administrators only)
    - 403 Forbidden (caller without the admin authority — refused before `email` is validated, so the
      response does not reveal the route exists; also no token, as above)
