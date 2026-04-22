# ADR-0001: Sync service communicates results via a per-list event stream

**Date:** 2026-04-18
**Status:** accepted
**Related ADRs:** _None._

## Context

The shopping-list feature has an operation queue that processes user actions
(add, update, check, move, delete) against the backend asynchronously while
the UI renders optimistic state. The component that owns the queue (sync
service) must tell the component that owns the rendered state (detail
service) three kinds of things:

1. An operation completed successfully — here is the authoritative server
   item (new id for adds, new version for updates) so optimistic state can
   be reconciled.
2. An operation failed with a 412 conflict — the list is stale and must be
   re-fetched; any queued follow-ups for the same item were dropped.
3. An operation failed with another API error — surface a message to the
   user.

These must be delivered many-to-one (many queued operations complete over
time, one consumer applies them to the single `AsyncValue<ShoppingListDetail>`
source of truth). The queue lives in a long-lived singleton that may serve
multiple lists concurrently; the consumer is scoped to one list at a time
and must be able to attach and detach cleanly when the user enters and
leaves the detail screen. A new operation can arrive while a previous one is
still in flight, so the channel must be ordered and lossless for the subset
of events the consumer was subscribed to.

The decision is how this channel is shaped.

## Decision

The sync service exposes, per `listId`, a broadcast `Stream<SyncEvent>` where
`SyncEvent` is a sealed class with three cases:

- `ItemSynced(String submittedItemId, ShoppingListItem serverItem)` — emitted
  after a successful add (where `submittedItemId` is the client-generated
  temp id) or a successful update/check/uncheck/move (where
  `submittedItemId` equals `serverItem.id`). Delete emits no event.
- `SyncConflict()` — emitted when an operation returns 412 and subsequent
  queued operations for the same item have been dropped.
- `SyncFailed(String message)` — emitted for non-conflict API errors.

Consumers subscribe on entry to the detail screen and cancel the
subscription on exit. The stream is scoped to a single `listId`; the sync
service keeps a `StreamController<SyncEvent>` per active list and closes it
when no subscribers remain or the service is disposed.

## Alternatives considered

- **Per-operation `Future<SyncResult>`** — `queueOperation` returns a future
  that completes when the op is processed. Rejected: the queue already
  retains an operation across retries on connection error, so the future
  could stay pending indefinitely; and a 412 drops several operations at
  once, which maps awkwardly onto per-operation futures.
- **Keep the current callback bundle** — reject because the problem the
  refactor exists to solve is exactly this callback tangle: registering a
  `_SyncCallbacks` record couples the two services tightly and forces
  bidirectional knowledge.
- **`ValueListenable<SyncEvent?>`** — a notifier carrying the latest event.
  Rejected: notifiers deduplicate and lose events if two land between
  rebuilds, which is unacceptable for reconciliation.
- **Shared append-only event log (list + notifier)** — works but requires
  the consumer to track a read cursor; a stream expresses the same idea
  more cleanly with built-in backpressure and cancellation semantics.

## Consequences

- Sync service has no awareness of detail-service methods or state; its
  outward surface is `queueOperation`, a sync-status `ValueListenable<bool>`,
  a `Stream<SyncEvent>`, and a snapshot getter for pending operations.
- Detail service becomes the only subscriber and owns all reconciliation
  logic in one place (stream handler), making the flow readable top-down.
- The sync service must manage `StreamController` lifecycles per list
  (create on first use, close on dispose) — modest extra bookkeeping
  compared to the current callback map.
- Testing becomes easier: tests can drive the sync service and assert on
  emitted events, rather than installing callback spies.
