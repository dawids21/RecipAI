# ADR-0004: Shopping-list items are serialised through a dedicated store aggregate

**Date:** 2026-07-18
**Status:** accepted
**Related ADRs:** [ADR-0003](0003-shopping-list-full-refresh-over-delta.md) — the
full-list pull whose reconcile is one of the store paths serialised here.

## Context

The mobile shopping-list item store keeps three things that must stay coherent
for a list: the sqflite rows, an in-memory cache projected to the UI through a
`ValueNotifier`, and an append-only outbox of unsynced changes. Every store
method reads a cache/DB snapshot, `await`s a sqflite transaction, then writes the
cache and bumps the notifier using the pre-`await` snapshot.

sqflite serialises its transactions, so the **database** stays consistent. The
**cache** does not: a UI mutation (check, edit, delete) that runs during a poll's
reconcile transaction `await` writes the cache, and when the reconcile resumes it
writes its stale snapshot back — clobbering the user's change. The result is a
cache that disagrees with the DB and an outbox entry that renders as lost until
the list is reloaded from disk. The existing `_busy` gate in the sync service
only serialises poll-vs-push per list; it does not guard UI mutations against
reconciles at all, because those mutations run in a different object (the
repository) and take no lock.

This is preventive hardening plus a simplification: the author wants the
`_busy`/`_pending` gating reduced and the divergence class eliminated. Dart is
single-isolate, so "serialise" means ensuring one logical critical section (across
all its `await`s) completes before the next begins — an async serialiser, not a
thread mutex. A hard constraint: the critical section must be **local-only**, so
it cannot be held across the item HTTP calls, or all access to a list would stall
behind a slow request.

## Decision

Introduce a **new dedicated item-store object** as the single consistency
boundary for shopping-list items. It owns the in-memory cache, the per-list
`ValueNotifier`s, and the outbox coordination, and it serialises every logical
read-modify-write **per list** so each completes atomically across cache, DB,
outbox, and notifier before the next starts. The existing repository's local
mutation and reconcile logic moves into the store; the item HTTP endpoints remain
in a network-facing repository. The store is a *new class* — the repository is
**not** converted into the aggregate, and the boundary is not a lock-in-front
facade over the unchanged repository.

The serialised section is local-only: it wraps the pre-read and the
response-reconcile, never the HTTP call. The sync service performs its pull and
push reconciliation **through** the store.

`_busy` loses its store-exclusion and pull-vs-in-flight-push roles — the store's
per-list lock plus the unchanged dirty-gating and version-gating cover both. A
minimal **single-flight-drain guard** remains in the sync service (at most one
drain loop per list, extra kicks coalesced) purely so two drains can't read the
same outbox head and double-push it across the network; it holds no store state.

Conflict-resolution semantics are unchanged: the store only guarantees the
existing dirty-/version-gating rules apply atomically, not which value wins.

The serialiser **mechanism** (a lock package vs. an in-repo `Future`-chain), the
**reentrancy discipline** for bulk operations, and the **lock granularity's**
exact realisation are deliberately left to task-design; only per-list granularity
and the local-only-section rule are fixed here.

## Alternatives considered

- **Serialising facade (lock in front of the unchanged repository).** Closes the
  race with least churn, but the repository's mutation API stays public, so the
  single-point-of-access invariant holds only by convention and regresses the
  first time a caller bypasses the facade.
- **Repository-as-aggregate (lock the existing repository in place).** The
  least-churn structural option and what the solution research favoured, but it
  leaves HTTP and local-store concerns fused in one class; a dedicated store gives
  a cleaner, bypass-proof boundary.
- **Cache-free store (DB as the only source of truth).** Removes the divergent
  copy entirely, but publishing the notifier only after the DB commit adds a
  visible per-tap lag versus today's synchronous feedback; the optimistic-write
  fix reintroduces the very copy it removes.
- **Command-queue / actor store.** Reentrancy-free by construction, but the
  message-passing model is less familiar and buys little for this store's modest
  composition.
- **Keep `_busy`, widen it to cover UI mutations.** Smallest diff, but leaves
  serialisation in the sync service and adds no consistency boundary — the store
  approach supersedes it.

## Consequences

- The cache/DB/outbox divergence class is eliminated structurally: a UI mutation
  runs fully before or after a reconcile, never inside it. The primary lost-update
  race is closed.
- The cache becomes private to the store, so "all local access through one
  serialised point" is enforced by structure, not discipline.
- The sync service simplifies: `_busy`/`_pending` collapse to a single-flight-drain
  guard, and a poll's reconcile may run alongside a drain without being dropped.
- Larger blast radius than a facade: local logic relocates from the repository,
  DI re-wires, and every sync-service call site changes — the relocation is the
  main place a transcription bug could hide, so it needs careful review and the
  manual concurrency scenarios exercised.
- Follow-up decisions remain for task-design: serialiser mechanism, reentrancy
  discipline for bulk ops, and a deterministic interleave test harness.
- Uncontended mutations keep today's instant notifier feedback; under contention
  they defer only by one local transaction, which is imperceptible.
