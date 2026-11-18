# ADR-0012: Shopping-list tidy is committed by one atomic server endpoint

**Date:** 2026-11-18
**Related ADRs:** [ADR-0003](0003-shopping-list-full-refresh-over-delta.md),
[ADR-0004](0004-shopping-list-item-store-aggregate.md), [ADR-0006](0006-shared-limits-module.md)

## Context

Shopping-list items are written one at a time. Each update or delete declares the item version it was based on, and
the server rejects it with 412 if the item has moved on. Writes to different items never contend. Multi-item actions
such as "delete all checked" are expanded by the mobile client into per-item calls queued in an offline outbox, so a
partial outcome is natural and accepted.

A "tidy" action merges duplicate items (survivor updated, others deleted) and reorders the active items so that
similar products sit together. Done through the client expansion, one tidy becomes dozens of queued writes. Other
members would see the intermediate states land one by one. A conflict on any single item rolls back only that item,
leaving the list partly merged, for example with a survivor whose quantity already includes a duplicate that was
restored. Tidying is also online-only anyway, because its grouping comes from an AI call on the server.

Each deleted item holds one unit of the per-list item quota. Every path that destroys an item must release that unit,
or recorded usage drifts from reality.

## Decision

A tidy is committed by **one endpoint that applies all merges and the new order in a single transaction, or nothing**.

- The client sends the merges it accepted, the full order, and the base version of every active item.
- The server rejects the whole tidy with 409 if the set of active items or any of their versions differs from what
  the client saw.
- The server recomputes merged quantities itself, using the same rule that produced the suggestion.
- It releases one item-quota unit per deleted item.
- It returns the full list.

The mobile client runs the call under its per-list sync lock, refuses to run it while the list's outbox is non-empty,
and reconciles the returned list through the ordinary pull-reconcile path. Other members pick up the result through
their normal full-list poll.

This is a deliberate exception to "bulk actions are expanded client-side". Ordinary multi-item actions keep that
model.

## Alternatives considered

- **Client-side expansion through the store and outbox.** This is consistent with every other bulk action and works
  offline. It was rejected because a conflict mid-tidy leaves the list partly merged, in states the user never saw in
  the preview.
- **Server-side commit without full version gating.** Gating only the merged items would ignore concurrent edits to
  reordered items. Renumbering writes every active item, so every one is gated.
- **A per-list version for the tidy.** This would make "list unchanged" a single check, but it reintroduces the shared
  list counter that the full-pull design rejected, because it makes edits to different items contend.

## Consequences

- A tidy is all-or-nothing. Any concurrent edit to an active item between suggestion and apply rejects it, and the
  user retries. This is acceptable at a few dozen items per list and could be noisy on busy shared lists.
- The server has a third item-destroying path that must release item quota. Its tests must cover the release.
- Tidy is unavailable offline and while the list has unsynced local changes.
- The mobile apply must hold the sync lock across the HTTP call and the reconcile. Otherwise a poll that fetched before
  the apply re-inserts merged-away items.
- No undo exists for a tidy. The preview is the safeguard. Adding undo later would mean re-creating deleted items,
  which uses new ids and re-reserves quota.
