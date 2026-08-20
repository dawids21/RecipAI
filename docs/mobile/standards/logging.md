# Logging Standard

The mobile app uses the [`logging`](https://pub.dev/packages/logging) package. Log records are captured by a
file sink so testers can share a full activity trace from a build.

## Logger Naming

Loggers are named hierarchically from a single root:

- Root: `recipai`
- Per layer: `recipai.<feature>.<layer>`

Examples in use:

- `recipai.shopping_list.repository` — network activity
- `recipai.shopping_list.detail` — `ShoppingListDetailService` behavioural events
- `recipai.shopping_list.sync` — `ShoppingListSyncService` background sync queue

Declare one `static final` logger per class:

```dart
static final _log = Logger('recipai.shopping_list.detail');
```

## Levels

| Level | Use for |
|---|---|
| `SEVERE` / `WARNING` | Problems — failed requests, conflicts, and **previously-silent** background failures (e.g. swallowed retry loops). |
| `INFO` | User-driven domain events and the normal network request/response line. Network activity sits at `INFO` (not `FINE`) so it has the same prominence as domain events in the shared trace. |
| `FINE` | High-frequency / trace detail — periodic-fetch ticks, sync lifecycle (`startSyncing` / `pauseSyncing`), `ItemSynced` id mappings. |

When converting a previously-silent `catch` into a logged one, **preserve the existing behaviour** (e.g. still retry,
still fail silently to the user) and only add the log line.

## Redaction

These rules are mandatory — the shared file goes to testers:

- **Never** log the `Authorization` header or the bearer token (`idToken`).
- **Never** log full request/response bodies. Log a byte length instead if a size is useful.
- URLs, HTTP method, status code, and timing **are** allowed.

Network logging lives in **one place** — the repository — so services never re-log raw HTTP traffic.

## File Sink + Share

- `core/logging/app_log_sink.dart` (`AppLogSink`) formats each record as
  `ISO8601  LEVEL  loggerName  message` (error + stack on following lines) and appends to a rotating file.
- Single active file `recipai.log` capped at ~1 MB; one backup `recipai.log.1` kept (≈2 MB worst case). Writes are
  serialized so concurrent log calls don't interleave.
- `core/logging/logging_setup.dart` (`setupLogging()`) attaches the sink in `main()`. **Capture is always on** — it is
  not gated by any feature flag.
- The outbound "Send logs" share goes through the **existing `recipai/share` platform channel** (Android
  `ACTION_SEND` via `FileProvider`) — **no `share_plus`**, **Android only**.
