# Shopping List Items — Solution Research

A description of the proposed solution for the shopping list details feature: a
shared, offline-first shopping list with background sync and a future
item-merging capability. This document describes **how the solution should
work** — it is not an implementation plan and does not cover changes required to
the current project.

## Requirements being addressed

- A shopping list is shared between multiple users.
- Each user may add, remove, check, and modify items.
- Local updates are instant, even on a poor or absent network connection.
- Sync with the backend happens in the background.
- Changes made by other users may become visible after some delay — real-time
  propagation is **not** required.
- A future feature will automatically merge similar items together.

Because per-user latency is relaxed (other users' changes may lag), real-time
collaboration technology (CRDTs, operational transforms, websockets) is not
needed. The hard requirement is offline-first behaviour with reliable
background sync — a simpler problem.

## The hybrid approach

The solution combines a **state-based store with per-field Last-Write-Wins
(LWW)** as the sync mechanism, plus a **thin local operation (outbox) layer**
used only to track unsynced changes. It is deliberately *not* event sourcing —
the current state is always the source of truth; the operation layer never
reconstructs state, it only records intent that has not yet been confirmed by
the server.

Two layers, each with one job:

1. **State-based store + LWW — the sync mechanism.**
   Both the backend database and the local client database hold the **current
   state** of each item (name, quantity, unit, checked, position). Sync
   reconciles state by comparing a version/timestamp per item. There is no
   authoritative event log.

2. **Local outbox — the change queue.**
   A local-only queue records "this device made change X to item Y that the
   server has not yet confirmed," so the background syncer knows what to push
   and can retry. Once the server acknowledges a change, its outbox entry is
   discarded. The persisted data stays state-based.

### Data flow

The local database is the source of truth for the UI, which makes updates
instant and fully functional offline. The backend database holds the
authoritative current state.

```
 Client UI  ──read/write──►  local store (current state, source of truth for UI)
                                   │  writes also append to ──►  outbox (unsynced intents)
                                   │
        ┌──────── background sync worker ────────┐
   push │ drain outbox → send changes            │ pull
        │ apply server ACK (version, clears dirty)│ GET changes since cursor → LWW merge
        └─────────────────────────────────────────┘
                                   ▼
                  Backend (authoritative current state)
```

A single change (e.g. "user checks off milk") flows like this:

1. **Write locally, instantly.** The local row is updated, a local
   `updated_at` is bumped, and the row is marked dirty. The UI re-renders
   immediately from the local store. Works fully offline.
2. **Enqueue intent.** An entry is appended to the local outbox describing the
   change and the version it was based on.
3. **Background sync pushes.** When online, the syncer sends queued changes to
   the backend, which applies LWW and returns the new authoritative version.
4. **Reconcile.** The local row's dirty flag clears, its version updates, and
   the outbox entry is removed.
5. **Pull others' changes.** Periodically and on reconnect, the client asks the
   backend for everything in the list changed since its last cursor, and merges
   those rows into local state via LWW. This is where the allowed delay lives.

### Conflict resolution

Conflicts are resolved per item (optionally per field) using Last-Write-Wins
based on a timestamp plus a device/user identifier. Three cases deserve special
handling on a shopping list:

- **`checked` toggles** — LWW on the `checked` value with its own timestamp;
  whoever toggled last wins. Acceptable for groceries.
- **delete vs. edit** — deletions are **soft deletes** carrying a timestamp that
  competes with the edit's timestamp; the latest wins. Soft deletes are
  essential: a hard delete racing a concurrent edit on another device cannot be
  reconciled, which is a common source of items resurrecting or disappearing.
- **reordering / position** — using a fractional position value keeps reorders
  mostly non-conflicting, because moving an item only rewrites that item's
  position, not its neighbours'.

Whole-row LWW is the recommended starting point. It can lose one of two
concurrent edits to the *same* row, which is acceptable for a shopping list.
Refine to field-level LWW only if real conflicts prove painful.

### The change cursor (delta pull)

So a client can pull **only what changed since it last synced** — rather than
re-downloading the whole list — each list carries a **monotonically increasing
change counter** ("seq"). Every item write in a list (insert, update, check,
soft-delete, merge) bumps the list's counter and stamps the affected row with
the new value.

A client stores the highest seq it has seen for a list. Pulling is then an
exact range query: return rows whose seq is greater than the client's stored
cursor, ordered by seq; the client advances its cursor to the maximum seq
received.

A per-list monotonic counter is preferred over relying on `updated_at` because
timestamps suffer from clock skew, equal-timestamp collisions, and
commit-order-vs-timestamp races — all of which can silently skip or
infinitely re-fetch rows. The counter is a single integer, clock-free, strictly
increasing, and yields an exact delta query.

The cursor governs **delivery** (have I seen this change yet?). LWW governs
**resolution** (whose value wins?). They are orthogonal; the solution uses both.

## The merge feature

"Merge" means two distinct things; the solution treats them as separate
concerns: **dedup-merge** (combining rows) and **similarity matching**
(deciding which rows are the same).

### Dedup-merge — combining rows

Combining multiple item rows that refer to the same product, summing
quantities (e.g. "2 onions" + "1 onion" = "3 onions").

Because the model is state-based, this is a **server-side transactional
operation on the state tables**, not a special sync concept:

1. Pick a **canonical survivor** item (e.g. the earliest-positioned).
2. Sum the compatible quantities into the survivor and bump its version /
   `updated_at`.
3. **Soft-delete** the other rows and stamp them with a `merged_into` pointer to
   the survivor.
4. Each of these writes bumps the list's change counter, so every client picks
   up the merge as **ordinary state deltas** — the survivor updated, the others
   soft-deleted — through the same delta-pull they already use. No client-side
   merge logic is required.

The `merged_into` provenance pointer enables undo and prevents two devices from
merging the same items into different survivors: a second merge sees the rows
already point somewhere and no-ops.

**Where it runs:** server-authoritative is strongly preferred — it is
transactional, consistent, and avoids two offline devices merging differently.
Clients may *suggest* merge candidates, but only the server commits the merge.
This means a merge is not instant offline, which is the correct trade-off:
offline destructive auto-merge across devices is a primary source of buggy
behaviour.

**Unit compatibility** is the subtlety. Quantities can be summed only when their
units are compatible (e.g. 200 g + 0.3 kg via conversion). Incompatible units
(e.g. "2 onions" + "1 cup chopped onion") cannot be summed and should remain
separate lines, or be grouped under one product without summing. A small
unit-conversion table handles the convertible cases.

### Similarity matching — deciding which rows are "the same"

The harder part is identifying which rows refer to the same product. Approaches,
in increasing sophistication, feeding the dedup-merge step above:

- **Normalize + exact match.** Lowercase, trim, singularize, strip units, then
  group by normalized name plus compatible unit. Cheap, predictable, covers the
  large majority of cases. This is the approach common shopping/recipe apps use
  (match on normalized title + unit).
- **Synonym / canonical dictionary.** A lookup table mapping equivalents (e.g.
  "scallion" → "green onion"), grown over time, with room for localization.
- **Fuzzy / embeddings / AI.** For looser equivalences such as "chopped onion" ≈
  "onion". The most powerful option and a natural later upgrade. It should act
  only as a **suggestion** layer feeding the dedup-merge step — never as an
  automatic destructive merge.

Similarity matching produces *candidates*; dedup-merge *commits* the change.
Keeping these separate lets the matching strategy evolve from exact-match to
AI-assisted without changing how merges are applied or propagated.

## Why this fits the requirements

- **State-based and database-native** — no event store; the data tables remain
  the current truth, and the only added "log" is a disposable local outbox.
- **Merge is natural, not bolted on** — it is a transactional state mutation
  that propagates through the same delta-pull clients already use. The
  `merged_into` pointer and soft deletes give merge a clean home.
- **Relaxed latency honoured** — periodic delta-pull, no real-time
  infrastructure.

## Trade-offs

- Soft deletes require an occasional purge/compaction job (hard-deleting rows
  soft-deleted beyond some retention window) so tables do not grow unbounded.
- Whole-row LWW can lose one of two concurrent edits to the same row; acceptable
  for groceries, refine to field-level only if it proves painful.
- Server-authoritative merge means a merge is not instant while offline — a
  deliberate choice to avoid divergent offline merges.
