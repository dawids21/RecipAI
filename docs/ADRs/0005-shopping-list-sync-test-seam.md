# ADR-0005: Shopping-list sync is made deterministically testable via an injected scheduler and a single-entry push seam

**Date:** 2026-07-21
**Status:** accepted
**Related ADRs:** [ADR-0004: Shopping-list items are serialised through a dedicated store aggregate](0004-shopping-list-item-store-aggregate.md)

## Context

The shopping-list item sync service drains a per-list outbox to the backend and
reconciles server pulls into a local store. It is the most concurrency- and
ordering-sensitive component in the mobile app, yet it has no unit coverage.

The service was written for production, not for testing. It makes progress
through mechanisms a test cannot control deterministically:

- Draining is fire-and-forget: a drain request launches an un-awaited background
  loop, so a caller has no completion signal to await.
- Progress after a transient or offline failure depends on real `Timer`s
  (backoff, offline retry) firing on wall-clock delays.
- A server pull ends by kicking a drain, so "just reconcile" cannot be exercised
  in isolation — a background push rides along.
- Periodic polling is driven by a real `Timer`.

A faithful unit test of this component must fix one exact ordering of
push/reconcile steps per test and assert the resulting end state, with no
dependence on timers, backoff durations, or poll cadence. None of that is
possible against the service as originally shaped: real timers leak across test
boundaries and make outcomes order- and timing-dependent — the precise
non-determinism the suite exists to eliminate.

Two production changes are needed to make the component drivable. The
alternative — leaving production untouched and advancing virtual time — was
rejected (see below), so the decision is which seams to add and how minimal to
keep them.

## Decision

Introduce three test seams into the sync service, all of which leave production
behaviour equivalent:

1. **Inject the scheduler that creates timers.** All `Timer` creation — poll,
   drain, backoff, offline — goes through an injected scheduler. Production
   supplies a real-timer scheduler. Tests supply an **inert** scheduler that
   never fires a timer, so no timer ever fires mid-test and a stalled drain can
   never spin on a scheduled backoff/offline kick.

2. **Decouple polling from draining, and expose a single-entry push step.** A
   server pull becomes pure fetch-and-reconcile and no longer kicks a drain; a
   dedicated drain timer (created through the injected scheduler, so inert in
   tests) owns the periodic drain-kick in production. The push of one outbox
   entry is exposed as an awaitable, test-visible step, and the per-list sync
   lock is acquired inside that step (rather than by the drain loop around it),
   so a direct call still exercises the real locking. Tests push one outbox
   entry at a time and choose the interleaving explicitly.

3. **Make the drain kick and the multi-list fan-out awaitable.** The per-list
   drain kick returns the future of its drain loop, and the fan-out over the
   lists with a pending outbox awaits/collects those futures so the fan-out
   method is itself awaitable. Production discards these futures with
   `unawaited()` at every call site, so draining stays fire-and-forget with
   timing unchanged. A multi-list test instead awaits the fan-out to quiescence
   and asserts the converged end state directly, which is stronger than
   inspecting which lists were scheduled and which is why seam 1 no longer needs
   the scheduler to record.

Transient/offline retry is driven in tests by re-invoking the push step after
changing the fake backend's state, never by waiting on a scheduled kick.

## Alternatives considered

- **Leave production untouched; drive it under `fakeAsync`** — Rejected: making
  progress requires advancing virtual time through backoff and poll cadence,
  which is exactly the timing dependence the suite must avoid, and
  `sqflite_common_ffi`'s real async I/O does not run inside a `fakeAsync` zone.
- **Extract a separate pure sync-engine object** — Rejected: it restructures the
  app's most delicate concurrency code before any coverage exists, so a
  regression introduced by the split is the one thing there is no test to catch;
  disproportionate to adding a test suite.
- **Thin test hooks with no scheduler injection** — Rejected: the push path arms
  real backoff/offline timers on a stall, so a stalled test leaves a live timer
  that later fires a stray background drain, reintroducing non-determinism.

## Consequences

- The sync service can be driven step-by-step and awaited, enabling
  single-ordering-per-test scenarios asserted against fixed end states.
- Polling and draining are now separate concerns in production, each with its
  own timer — a cleaner separation than the previous poll-completion kick.
- The scheduler becomes a construction-time dependency of the sync service; its
  wiring must be set up in `main()` / feature setup.
- The drain kick returns a future, so every production caller must opt into
  fire-and-forget explicitly via `unawaited()`; forgetting to do so at a new call
  site would make production block on a drain, which review must catch.
- Push-outcome and ordering scenarios drive the single-entry push step directly,
  bypassing the drain loop, so the loop's mid-drain single-flight coalescing
  guard is not their coverage target. The multi-list fan-out scenario does
  exercise the real drain loop (awaited to empty); the substantive
  reconcile/classification logic and the store are the coverage target
  throughout.
- `@visibleForTesting` affordances on the sync service are now part of its
  contract and must be kept in step with the tests that rely on them.
