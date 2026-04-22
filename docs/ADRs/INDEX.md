# ADR Index

Architecture Decision Records for RecipAI. Each ADR captures a single
non-obvious technical decision with its context, alternatives, and
consequences. Read an ADR before changing code in the area it covers.

## Records

- [ADR-0001: Sync service communicates results via a per-list event stream](0001-sync-service-event-stream.md) —
  Decouples `ShoppingListSyncService` from `ShoppingListDetailService` by
  replacing the callback bundle with a broadcast `Stream<SyncEvent>` per list.
