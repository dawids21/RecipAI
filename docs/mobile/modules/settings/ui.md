# Settings — UI

## Screen

### Settings Screen (`/settings`)

Full-screen list titled "Settings", reached from the Main Screen's overflow menu
(`docs/mobile/modules/core/ui.md`); back returns to the Main Screen. Its rows, in order:

- **Send logs** — shares the current log file (Android only; see `docs/mobile/standards/logging.md` → File Sink +
  Share).
- **Logout** — asks "Are you sure you want to logout?" (`Cancel` / `Logout`). Confirming signs out to the Login
  Screen (`docs/mobile/modules/auth/ui.md` → Flow); a failed sign-out shows `Failed to logout: $error` and leaves
  the user on the screen.
- **Delete account** — shown in the error colour; see Flow.

## Flow

### Delete account

1. A confirmation dialog states that all recipes, collections, shopping lists and meal plans are deleted immediately
   and that this cannot be undone (`Cancel` / `Delete`). `Cancel` deletes nothing.
2. `Delete` replaces the list with a "Deleting your account..." spinner and blocks back navigation, then opens the
   Google account prompt (a dev build skips it).
3. The result, per outcome (see `module.md` → Services and State):
   - **Success** — the app lands on the Login Screen; a new sign-in with the same account starts empty.
   - **Prompt dismissed** — back to the list, no message.
   - **Re-authentication failed** (including choosing a different Google account) — snackbar `Could not confirm your
     identity. Nothing was deleted.`
   - **Backend purge failed** — snackbar `Failed to delete your account. Please try again.`; the user stays signed in.
   - **Identity deletion failed** — a dialog that cannot be dismissed by tapping outside, "Something went wrong":
     the data was deleted but the account could not be removed, please contact support. `OK` signs out to the Login
     Screen.
