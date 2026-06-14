# ADR-0003: Shopping-list items refresh via full-list pull, not a delta protocol

**Date:** 2026-06-14
**Status:** accepted
**Related ADRs:** _None._

## Context

The shopping-list-items rewrite is offline-first: each client holds the current
state of a list locally and must periodically pick up other users' changes
within a ~10–30s freshness target while a list is open (requirements §1.4, §7).
Requirements §7 phrases this as "fetch what has changed since last sync … not
re-download the entire list each time."

The conflict model is **first-action-wins** (requirements §2): a write declares
the item `version` it was based on, and the server rejects it if the item has
moved on. A hard constraint falls out of §2.4 / §2.7: **writes to two different
items must never conflict** — two users editing different items concurrently must
both succeed.

A literal delta-pull ("changes since cursor") needs a per-list ordering key that
every write advances — a monotonic change counter or sequence stamped on the
list. That shared object is exactly what the different-items constraint forbids:
every item write would have to bump the list's counter, serialising all writes on
one row and making different-item edits contend. Per-item timestamps as the
cursor key were considered and rejected (clock skew, equal-timestamp ties,
commit-order-vs-timestamp races — the same hazards the solution research warns
about).

The decision is how a client picks up others' changes.

## Decision

**The client re-fetches the whole list (`GET /shopping-lists/{id}`) on each poll
and diffs it against its local store.** There is no delta endpoint, no per-list
change counter, no `seq`, and no client cursor.

The local diff, per polled item:

- present on server, not dirty locally → adopt the server value (fields +
  version);
- present on server, dirty locally (unsynced change still in the outbox) → keep
  local; let its own push resolve (accept or 412);
- on server, missing locally → insert;
- local, missing from server → deleted elsewhere, remove locally — unless it is a
  locally-pending create not yet pushed.

This is justified by list size: shopping lists hold ~30–40 items, so a full fetch
is cheap and meets §7's actual intent (freshness without meaningful waste) without
a delta protocol. Deletes become **hard** (no soft-delete column needed): a
removed row is simply absent from the next pull and dropped locally.

## Alternatives considered

- **Delta-pull with a per-list change counter / sequence.** The textbook
  offline-first approach and what the solution research proposed. Rejected: the
  shared counter serialises all writes on the list row, so two users editing
  different items contend — a direct violation of requirements §2.4 / §2.7.
- **Delta-pull keyed by per-item `updated_at` + id.** Avoids a shared row but
  reintroduces timestamp-cursor hazards (clock skew, equal-timestamp collisions,
  commit-order races) that can silently skip or re-fetch rows. Rejected as
  fragile for the small benefit it buys at this list size.
- **Soft deletes returned by the pull.** Needed only to *deliver* deletions
  through a delta feed. With full pulls, an absent row already communicates the
  deletion, so soft deletes (and a purge job) add cost with no benefit here.

## Consequences

- No per-list ordering object exists, so writes to different items are fully
  independent — the different-items constraint is satisfied structurally, not by
  careful locking.
- The backend needs **no new item columns and no list counter**; the existing
  per-item `version` is the only coordination state. `GET /shopping-lists/{id}`
  serves both initial load and polling.
- Each poll transfers the whole list. Acceptable at ~30–40 items; **this decision
  should be revisited if lists are ever allowed to grow large** (hundreds+), at
  which point a contention-safe delta scheme would be needed.
- Pull/merge complexity moves to the **client diff** (dirty-aware), which must not
  clobber unsynced local changes — the key correctness point for the
  implementation.
- A future item-merge feature (requirements §5) still works: merges are ordinary
  state changes that show up in the next full pull; a `merged_into` provenance
  pointer can be added later without changing this refresh scheme.
