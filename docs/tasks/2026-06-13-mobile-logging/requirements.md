# Mobile Feature Logging — Requirements

**Date:** 2026-06-13
**Status:** Requirements gathered (pre-design)

## Goal

Add diagnostic logging for **one specific mobile feature** so that, when the app is
used by **internal testers**, log data can reach the developer for **bug-finding**.

## Confirmed requirements

| Requirement | Decision |
|---|---|
| Primary purpose | **Diagnostic** — finding bugs (not usage analytics). |
| Who reads the logs | **Only the developer.** Testers do not read them. |
| Delivery | **Testers send logs manually** to the developer (a tester action is acceptable / preferred). |
| Log scope | **Full activity trace**, captured even when nothing crashes (many bugs are "wrong behaviour, no crash"). |
| Network calls | The feature makes network calls; **request/response activity must be captured** too. |

### Why these rule out the alternatives

- **"Full trace, even no crash"** rules out Firebase Crashlytics as the primary store —
  Crashlytics only reliably surfaces logs attached to a crash / recorded non-fatal, so a
  no-crash trace would be lost.
- **"Testers send manually"** means silent remote telemetry (Sentry/Crashlytics) is not
  required, and an **on-device capture + manual share** approach is the best fit.

## Existing project context (drives the approach)

- **`logging` (official Dart package) is already a dependency** in `mobile/pubspec.yaml`
  (`logging: ^1.2.0`) — use it as the log-emitting front-end.
- **Firebase is already wired up** (`firebase_core`, `firebase_auth`) — available but
  intentionally **not** used as the logging path (see above).
- Network calls use the plain **`http`** package (no Dio) — so network logging is done
  **manually** around `http` calls; Dio-based interceptor conveniences (e.g. talker) do
  not apply for free.
- **No logging standard exists** in `docs/mobile/standards/` yet, despite `logging`
  being a dependency.
- Relevant existing standards to align with:
  - `docs/mobile/standards/feature-flags.md` — feature flags via `bool.fromEnvironment()`,
    gate UI rendering only.
  - `docs/mobile/standards/architecture.md` — three-layer Repository–Service–View,
    feature-based directory layout.
  - `docs/mobile/standards/dependency-injection.md` — `get_it`, per-feature setup functions.

## Selected approach — Option A: `logging` → rotating file → native share sheet

The feature emits through a named `Logger('recipe.<feature>')` (consistent with the
existing `logging` package). A log handler writes records to a **rotating log file** via
`path_provider`. Network calls (`http` requests/responses) are logged into the same
stream. A **"Send logs" action** hands the file to the OS **share sheet** (`share_plus`),
and the tester sends the file to the developer (email/DM).

### New dependencies

- `path_provider` — locate a writable directory for the log file.
- `share_plus` — hand the log file to the native share sheet.

(Both are standard, widely used Flutter packages.)

### Why Option A

- ✅ **Full trace regardless of crashes** — meets the core requirement.
- ✅ **Manual send** — matches the chosen delivery model; testers tap share, developer
  receives a file.
- ✅ Reuses the existing `logging` standard; adds only two small conventional packages.
- ✅ No new backend / account; no Firebase coupling.
- ⚠️ Developer receives raw files, not a searchable dashboard — acceptable for a handful
  of internal testers.
- ⚠️ Requires care around **log rotation / size caps** and **redaction** (see open items).

### Options considered and rejected

- **Firebase Crashlytics** (`logging` → Crashlytics): reuses existing Firebase, silent
  upload, but crash-centric — loses no-crash traces. **Rejected** by "full trace" req.
- **Sentry** (`sentry_flutter`): first-class non-crash logs + search/alerting, but adds a
  new SDK/service. **Not required** given manual-send delivery and internal-tester scale.
- **`talker` / `talker_flutter`**: built-in capture + history + share-report UI, but adds
  a second logging philosophy overlapping the existing `logging` standard, and its network
  convenience is Dio-oriented. **Rejected** to avoid diverging from the standard.

## Open items to resolve in design

1. **Target feature** — which feature this is for (scopes the logger name and feature flag).
2. **Redaction** — `http` calls likely carry auth headers/tokens; decide what is stripped
   before being written to a shareable file.
3. **Scope & lifetime** — gate behind a **feature flag** (`bool.fromEnvironment()`) so it
   ships only to internal-tester builds; decide the **file size / rotation cap**.
4. **Standards Evolution** (per `CLAUDE.md`) — add a mobile **logging standard** to
   `docs/mobile/standards/` (feature-logger naming, log levels, redaction rules, the
   file-sink + share pattern), since none exists yet.

## References

- [Most Popular Flutter Logging Libraries (2025–2026)](https://blog.devgenius.io/most-popular-flutter-logging-libraries-2025-2026-6394a0b13c29)
- [talker — Flutter Gems](https://fluttergems.dev/packages/talker/)
- [Top Flutter Debugging and Logging packages — Flutter Gems](https://fluttergems.dev/debugging-logging/)
- [Logging Integration — Sentry for Flutter](https://docs.sentry.io/platforms/dart/guides/flutter/integrations/logging/)
- [A complete guide to Flutter remote logging](https://isaacadariku.medium.com/a-complete-guide-to-flutter-remote-logging-with-ad3c49e79a8c)
