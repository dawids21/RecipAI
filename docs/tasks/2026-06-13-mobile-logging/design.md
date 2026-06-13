# Mobile Feature Logging — Design

**Date:** 2026-06-13
**Status:** Design (ready for implementation)
**Requirements:** [requirements.md](./requirements.md)
**Approach:** Option A — `logging` → rotating file → native Android share sheet (via the existing platform channel, no `share_plus`)
**Platform:** Android only (share path). iOS is out of scope — internal testers are on Android.

## Scope decisions (resolving the open items)

The requirements left four open items. This design resolves them as follows:

| Open item | Decision |
|---|---|
| **1. Target feature** | **Shopping List Details** — adding, editing, removing items, and background syncing. Logger root name: `recipai`, feature loggers `recipai.shopping_list.detail` and `recipai.shopping_list.sync`. |
| **2. Redaction** | The `Authorization: Bearer <token>` header is **never written**. Network logging logs method, URL, status code, duration, and a body size — **not** raw request/response bodies (item names are low-sensitivity, but bodies can be large and we don't need them for these bugs). |
| **3. Scope & lifetime** | A feature flag `LOGGING_ENABLED` (`bool.fromEnvironment`, default `false`) gates **the "Send logs" UI only** — capture is always wired up (consistent with the feature-flag standard: flags gate rendering only). Rotating file, **single active file capped at ~1 MB**, **1 rotated backup kept** (≈2 MB on disk worst case). |
| **4. Standards Evolution** | Add `docs/mobile/standards/logging.md` capturing logger naming, levels, redaction, and the file-sink + share pattern. Update `docs/INDEX.md`. |
| **5. Share mechanism** | **No `share_plus`.** Reuse the existing `recipai/share` platform channel + `ShareIntentBridge.kt`, adding an outbound `shareFile` method (Android `ACTION_SEND` via `FileProvider`). **Android only.** |

### Important finding: there is no existing logging infrastructure

`logging` is a dependency and `AppConfig` creates a `Logger`, but **`Logger.root.onRecord` is never subscribed anywhere** — so today log records go nowhere, and **there is no default/automatic network-call logging** (the repository wraps `http` calls in `try/catch` and rethrows). Therefore:

- There is **nothing to duplicate** — we must build the sink and the network logging ourselves.
- Network logging is added **once**, in `ShoppingListRepository`, so the two services do **not** re-log HTTP traffic. The services log domain/behavioural events only.

## Architecture

```
                      Logger('recipai.*')  ──emit──►  Logger.root.onRecord
                                                            │
features/.../service ─┤                                     ▼
repository (network) ─┘                          AppLogSink (core/logging/)
                                                     │  formats record
                                                     │  redacts
                                                     ▼
                                         rotating file (path_provider)
                                                     │
                            "Send logs" action ──► recipai/share channel
                                                     │  (MethodChannel.invokeMethod 'shareFile')
                                                     ▼
                                  ShareIntentBridge.kt → Android ACTION_SEND
                                       (FileProvider URI, Android only)
```

- **`core/logging/`** holds the cross-cutting infrastructure (sink, setup, share). This is shared code, so it lives in `core/`, consistent with the architecture standard (shared/reusable code in `lib/core/`).
- **Outbound share reuses the existing platform channel.** The app already has a `recipai/share` `MethodChannel` and a native `ShareIntentBridge.kt` for *inbound* shares; we extend it with an outbound `shareFile` method rather than adding `share_plus`. Android only.
- **Capture is always on** (sink attached in `main()` unconditionally). The `LOGGING_ENABLED` flag gates only whether the "Send logs" UI is rendered — matching the feature-flag standard (flags gate rendering only). The performance cost of always-on capture is negligible (see *Performance* below).
- **Repository** logs network activity (one place, no duplication).
- **Services** (`detail`, `sync`) log behavioural/domain events — the "full activity trace, even with no crash" the requirements call for.

## What needs to be done

### Step 1 — Add dependency
- `pubspec.yaml`: add `path_provider` (pin to the current resolvable major during implementation; run `flutter pub get` and use whatever `pub` resolves). Nothing in the project resolves a writable app directory today (`shared_preferences` doesn't expose one), so this is needed for the log file location.
- **No `share_plus`.** The outbound share is implemented through the existing `recipai/share` platform channel (Step 8) — Android only.

### Step 2 — Feature flag
- `core/feature_flags.dart`: add
  ```dart
  static const bool loggingEnabled =
      bool.fromEnvironment('LOGGING_ENABLED', defaultValue: false);
  ```
- Document it in the Active Flags table in `docs/mobile/standards/feature-flags.md`.
- **Aligns with the flag standard:** the feature-flag standard says flags gate *rendering only*. This flag does exactly that — it gates rendering of the "Send logs" UI control. Capture (sink attachment) is always on and not behind the flag.

### Step 3 — Log sink (`core/logging/app_log_sink.dart`)
- `AppLogSink` class:
  - On construction, resolves the log directory via `path_provider` (`getApplicationSupportDirectory()`), file `recipai.log` + one backup `recipai.log.1`.
  - `Future<void> init()` — opens the file for append; idempotent.
  - `void handle(LogRecord record)` — formats one line and appends; triggers rotation when the active file exceeds the size cap.
  - Line format: `ISO8601  LEVEL  loggerName  message` and, when present, error + stack on following lines.
  - Rotation: when active file ≥ ~1 MB, rename `recipai.log` → `recipai.log.1` (replacing any existing backup), start a fresh `recipai.log`.
  - Writes are serialized (append-only, awaited/queued) so concurrent log calls don't interleave/corrupt lines.
  - `Future<File> currentLogFileForSharing()` — flushes and returns the file to share.

### Step 4 — Logging setup (`core/logging/logging_setup.dart`)
- `Future<void> setupLogging()` — **not** flag-guarded; capture is always on:
  - `Logger.root.level = Level.ALL;`
  - `await sink.init();`
  - `Logger.root.onRecord.listen(sink.handle);`
  - Register the sink in `get_it` so the share action can reach it.
- Wire into `main()` **after** `AppConfig.loadConfig()` and before `runApp` (so even existing `AppConfig` warnings are captured). Keep `main` ordering otherwise unchanged.
- Only the **"Send logs" UI** (Step 8) is gated by `FeatureFlags.loggingEnabled`.

### Step 5 — Network logging in `ShoppingListRepository` (one place, no duplication)
- Add `static final _log = Logger('recipai.shopping_list.repository');`.
- **No shared helper.** Add log calls inline at each relevant `http` call site: an `INFO` line after the response (`method url -> status (NNN ms)`), and a `WARNING`/`SEVERE` line in the failure path with the error.
  - **Level: `INFO`** for the normal request/response line (not `FINE`). The requirements want a full activity trace the developer reads from the shared file, so network activity should sit at the same prominence as domain events rather than being relegated to verbose/trace level. Failures go to `WARNING`/`SEVERE`.
  - **Redaction:** log the URL and method only; **never** log headers (the `Authorization` header carries the bearer token). Do not log request/response bodies — log a byte length instead if useful.
  - Add inline logs to the item-mutating endpoints exercised by detail/sync: `createItem`, `updateItem`, `deleteItem`, `moveItem`, `checkItem`, `uncheckItem`, and `fetchShoppingListDetail` (used by load + periodic background fetch + conflict refetch). Other endpoints (share/unshare/lists) are out of scope for this feature.
- This is the **only** layer that logs HTTP, so services must not log raw network traffic.

### Step 6 — Behavioural logs in `ShoppingListDetailService`
Logger: `recipai.shopping_list.detail`. Add `INFO`/`FINE` logs at these points (covering add/edit/remove + background sync from the requirements):

- `loadShoppingListDetail(id)` — start (`FINE`) and the load outcome. On error, log the `AsyncError` at `WARNING`.
- `processOperation(operation)` — `INFO`: which operation type + itemId is being applied optimistically and queued (this is the entry point for add / delete / move / check / uncheck / update from the UI). Include item id; include item name only for add/update (low sensitivity, useful for repro).
- `deleteAllCheckedItems()` / `uncheckAllItems()` — `INFO` with the count of affected items.
- `renameShoppingList` / `deleteShoppingList` — `INFO` start, `WARNING` on failure (these throw).
- **Background sync lifecycle:** `startSyncing` / `stopSyncing` / `pauseSyncing` / `resumeSyncing` — `FINE`, with listId, so traces show when the 10 s timer is active vs paused (app backgrounded).
- `_onPeriodicFetch()` — `FINE` when a periodic fetch runs vs is skipped (skipped because syncing/pending). The current `catch (_)` swallows errors silently — **change it to log the error at `WARNING`** (silent background failures are exactly the "wrong behaviour, no crash" bugs the requirements target). Behaviour (silent to the user) is preserved; only a log line is added.
- `_handleSyncEvent` / `_handleItemSynced` / `_handleConflict` — `INFO` on conflict (`SyncConflict` → refetch), `WARNING` on `SyncFailed` with the message, `FINE` on `ItemSynced` with the submitted→server id mapping.

### Step 7 — Behavioural logs in `ShoppingListSyncService`
Logger: `recipai.shopping_list.sync`. This is the background sync queue — the highest-value trace surface:

- `queueOperation(listId, op)` — `INFO`: operation queued, queue depth.
- `_processQueue` — `INFO`: queue processing started/finished for a listId (normal processing); per-operation `INFO` before dispatch (op type + itemId).
- On each successful op — `INFO`: `ItemSynced` emitted (submitted id → server id).
- `on ShoppingListItemApiConflictException` — `INFO`: conflict for itemId, queue entries dropped, `SyncConflict` emitted.
- `on ShoppingListItemApiException` — `WARNING`: with `e.message`, `SyncFailed` emitted.
- `catch (e)` (connection/retry branch) — **currently swallows the error and silently retries after 3 s.** Add a `WARNING` log of the error before the delay so the trace shows retry storms / offline loops. Behaviour unchanged (still retries).
- Do **not** log the bearer token (`await _authService.idToken` is passed to the repository — never log it).

### Step 8 — "Send logs" action (tester-facing, Android only)

**Dart side** — `core/logging/share_logs.dart`:
- `Future<void> shareLogs()`:
  - Get the current log file from the sink (`currentLogFileForSharing()`), flushing pending writes first.
  - `await const MethodChannel('recipai/share').invokeMethod('shareFile', {'path': file.path, 'mimeType': 'text/plain'});`
  - Reuse the **same `recipai/share` channel name** the inbound share already uses, so there's one bridge.

**Native side** — extend `android/app/.../ShareIntentBridge.kt`:
- In the existing `setMethodCallHandler`, add a `"shareFile"` branch alongside `"consumeInitialShare"`:
  - Read `path` (+ optional `mimeType`) from `call.arguments`.
  - Wrap the file in a `FileProvider` content URI and fire `Intent.ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION`, started via a chooser (`Intent.createChooser`). The bridge needs an `Activity`/launchable context — pass the activity from `MainActivity` (the current `attach()` only holds `applicationContext`; add the activity reference, or start the chooser with `FLAG_ACTIVITY_NEW_TASK`).
  - `result.success(null)` / `result.error(...)` on failure.
- **FileProvider config** (required for `ACTION_SEND` of a file URI on modern Android):
  - Add a `<provider android:name="androidx.core.content.FileProvider" android:authority="${applicationId}.fileprovider" exported=false grantUriPermissions=true>` to `AndroidManifest.xml` with a `meta-data` pointing at `res/xml/file_paths.xml`.
  - Add `res/xml/file_paths.xml` exposing the log directory (the sink writes to the dir from `getApplicationSupportDirectory()`; the path entry must cover that location — confirm the concrete dir during implementation and map it, e.g. a `<files-path>`).

**UI placement** — main screen app-bar overflow menu:
- In `lib/core/main_screen.dart`, the `AppBar` already has a `PopupMenuButton<String>` (recipes collections / generate shopping list / logout). Add a **"Send logs"** `PopupMenuItem` (value `send_logs`, e.g. `Icons.bug_report`) **only when `FeatureFlags.loggingEnabled`** (append conditionally in `itemBuilder`), and handle `send_logs` in `onSelected` by calling `shareLogs()`.
- The item is shown on the main screen regardless of the selected bottom-nav tab, so testers can send logs after exercising the shopping-list flows.

### Step 9 — Standard + index
- Create `docs/mobile/standards/logging.md`:
  - Logger naming: root `recipai`, then `recipai.<feature>.<layer>`.
  - Levels: `SEVERE`/`WARNING` for problems (including previously-silent background failures), `INFO` for user-driven domain events, `FINE` for high-frequency/trace detail.
  - Redaction rules: never log `Authorization` headers / bearer tokens; never log full request/response bodies; URLs + status + timing are allowed.
  - The file-sink + rotation pattern; the outbound share goes through the existing `recipai/share` platform channel (no `share_plus`), Android only; capture is always on and the `LOGGING_ENABLED` flag gates only the "Send logs" UI.
- Update `docs/INDEX.md` Mobile Standards section to list `logging.md`.

### Step 10 — Manual verification (no automated tests)
No widget/unit tests are added for this feature — it will be verified manually:

- Build with `--dart-define=LOGGING_ENABLED=true`, exercise add / edit / remove + background sync, tap "Send logs".
- Confirm the shared file contains the full trace and **no bearer tokens / `Authorization` headers**.
- Confirm with the flag off (default), the "Send logs" UI is absent (capture still runs, but is not user-reachable).

## Performance

No user-visible penalty is expected:

- **Capture is always on**, but the only cost is formatting + appending log lines to a file. The highest-frequency sources are the 10 s periodic fetch and the sync queue — a handful of lines per cycle, not a hot loop. There is no per-frame logging.
- File appends are serialized/queued off the UI work so they do not block or jank frames.
- Disk growth is bounded by rotation (~1 MB active + 1 backup ≈ 2 MB worst case).
- Bearer-token redaction is a header omission, not extra work.

## Files touched

**New**
- `mobile/lib/core/logging/app_log_sink.dart`
- `mobile/lib/core/logging/logging_setup.dart`
- `mobile/lib/core/logging/share_logs.dart`
- `docs/mobile/standards/logging.md`

**Modified**
- `mobile/pubspec.yaml` — add `path_provider`
- `mobile/lib/core/feature_flags.dart` — add `loggingEnabled`
- `mobile/lib/main.dart` — call `setupLogging()`
- `mobile/lib/features/shopping_list/shopping_list_repository.dart` — inline network logging (redacted)
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — behavioural logs; log previously-silent `_onPeriodicFetch` catch
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — behavioural logs; log previously-silent retry catch
- `mobile/lib/core/main_screen.dart` — flag-gated "Send logs" item in the app-bar overflow menu
- `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/ShareIntentBridge.kt` — add outbound `shareFile` method
- `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/MainActivity.kt` — pass activity reference to the bridge (if needed for the chooser)
- `mobile/android/app/src/main/AndroidManifest.xml` — add `FileProvider`
- `mobile/android/app/src/main/res/xml/file_paths.xml` — **new** FileProvider paths
- `docs/mobile/standards/feature-flags.md` — document `LOGGING_ENABLED`
- `docs/INDEX.md` — list the new logging standard

## Out of scope
- **iOS share.** The "Send logs" share is Android-only (internal testers are on Android; `ios/` has no native share implementation today). On iOS the menu item is effectively inert — guard the call so it no-ops cleanly if ever built for iOS.
- Other features / screens (this is single-feature per the requirements).
- Remote/automatic upload (delivery is manual share by design).
- Logging the share/unshare/list endpoints (not part of the details + sync trace).
- Reworking `ShoppingListRepository`'s broad `try/catch` error wrapping (only adding log lines, not changing control flow).
