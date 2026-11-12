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
