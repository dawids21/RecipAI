# Shopping List Service Refactor

**Date:** 2026-04-17
**Type:** refactor
**Status:** brainstorming

## Summary

Simplify `ShoppingListSyncService` and `ShoppingListDetailService` by giving each a clear, non-overlapping responsibility: sync service manages only the operation queue, detail service owns all item state and the periodic fetch timer.

## Context

The two services currently have tangled responsibilities connected through a complex callback pattern. Sync service manages the operation queue, processes operations against the backend, AND manages the periodic fetch timer — then pushes results back to detail service via callbacks (`onItemAdded`, `onItemUpdated`, `onSync`, `onConflict`, `onError`). Detail service manages the shopping list detail state and applies optimistic updates, but delegates timer lifecycle and queue management to sync service while providing those callbacks.

This makes both classes harder to read and reason about than they need to be. The refactor is motivated purely by readability — there are no bugs or feature gaps driving it.

## Requirements

### SyncService responsibilities (after refactor)

- Manage the operation queue: enqueue operations, process them sequentially against the backend API.
- Retry on connection errors (keep operation in queue, retry after delay).
- Expose sync status: a `ValueNotifier<bool>` that is `true` while the queue has pending operations being processed.
- On API conflict (412): drop the failed operation and all subsequent queued operations targeting the same item, then signal the conflict.
- On other API errors: drop the failed operation and signal the error.

### DetailService responsibilities (after refactor)

- Own the `ValueNotifier<AsyncValue<ShoppingListDetail>>` — the single source of truth for what the UI renders.
- Apply optimistic updates to that state when the user performs an action (before the operation reaches the server).
- Own the periodic fetch timer (currently 10s polling). Merge fetched server state with any pending optimistic updates.
- Be aware of the queue's processing state to decide when to skip or merge periodic fetches.
- Handle conflict signals from sync service: re-fetch the full list from the server and replace the current state.
- Retain all non-item responsibilities: rename, delete, sharing, shared users.
- Remain the only service the screen interacts with.

## Anti-requirements

- Not changing any user-facing behavior. The UI should work identically before and after.
- Not redesigning the optimistic update pattern itself (sealed `ShoppingListOperation` classes, `applyOperation` logic).
- Not changing the operation model (`ShoppingListOperation` and its subclasses).

## Constraints & assumptions

- Backend changes are allowed if they produce a simpler overall solution, but no specific backend change is planned.
- Sync status in the UI means "queue is processing" only, not "periodic fetch in progress."
- The screen (`ShoppingListDetailScreen`) continues to interact only with `ShoppingListDetailService`.
- The communication mechanism between sync service and detail service (how sync service signals operation results, conflicts, and errors back to detail service) is left as a design decision for the planning/implementation phase.

## Acceptance criteria

- [ ] `ShoppingListSyncService` has no knowledge of shopping list detail state or the periodic fetch timer.
- [ ] `ShoppingListDetailService` has no knowledge of queue internals (operation processing, retry logic, version propagation).
- [ ] Each service can be read and understood independently without tracing callback chains between them.
- [ ] The periodic fetch timer is owned and managed by `ShoppingListDetailService`, including pause/resume on app lifecycle.
- [ ] Optimistic updates, conflict resolution (re-fetch on 412), sync indicator, pause/resume, retry on network error — all behave identically to the current implementation.
- [ ] On a 412 conflict, all queued operations for the conflicted item are dropped.
- [ ] `ShoppingListDetailScreen` only interacts with `ShoppingListDetailService`.

## Edge cases

- **Connection error during operation processing**: Sync service retains the operation in the queue and retries after a delay (current behavior: 3s). This stays in sync service.
- **412 conflict**: Sync service drops the failed operation and all subsequent queued operations for the same item. Detail service re-fetches the full list and replaces current state. User sees a snackbar notification.
- **Periodic fetch while queue is processing**: Detail service skips the periodic fetch (or defers merging) to avoid overwriting optimistic state with stale server state.
- **App lifecycle pause/resume**: Detail service pauses the periodic fetch timer on pause and resumes (with an immediate fetch) on resume. Queue processing in sync service continues regardless.

## Integration points

- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — primary refactor target.
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — primary refactor target.
- `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — consumer of detail service; API surface should remain unchanged.
- `mobile/lib/features/shopping_list/shopping_list_operation.dart` — operation model, not expected to change.
- `mobile/lib/features/shopping_list/shopping_list_repository.dart` — data access layer called by sync service; interface not expected to change.
- `mobile/lib/features/shopping_list/shopping_list_setup.dart` — dependency injection wiring may need updates.

## Open questions

- What communication mechanism should sync service use to signal operation results (completed items with new IDs/versions), conflicts, and errors back to detail service? Options include streams, notifiers, callbacks, or futures. Deferred to design/planning phase.
