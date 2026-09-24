# ADR Index

Architecture Decision Records for RecipAI. Each ADR captures a single
non-obvious technical decision with its context, alternatives, and
consequences. Read an ADR before changing code in the area it covers.

## Records

- [ADR-0002: Mobile widget tests pump the real screen against a single-route test router with mocktail repositories](0002-mobile-widget-test-shape.md) —
  Establishes the widget-test shape: real screen in a test-local single-route
  `GoRouter`, `NavigatorObserver` for navigation assertions, mocktail at the
  repository boundary.
- [ADR-0003: Shopping-list items refresh via full-list pull, not a delta protocol](0003-shopping-list-full-refresh-over-delta.md) —
  At ~30–40 items per list the client re-fetches the whole list and diffs it
  locally instead of using a delta/cursor; a per-list change counter would
  serialise all writes and make different-item edits contend (req §2.4/§2.7).
- [ADR-0004: Shopping-list items are serialised through a dedicated store aggregate](0004-shopping-list-item-store-aggregate.md) —
  A new store object owns the item cache/notifiers/outbox and serialises every
  read-modify-write per list (network kept outside the section), closing the
  poll-vs-edit cache clobber and shrinking `_busy` to a single-flight-drain guard.
- [ADR-0005: Shopping-list sync is made deterministically testable via an injected scheduler and a single-entry push seam](0005-shopping-list-sync-test-seam.md) —
  All timer creation is routed through an injected scheduler (inert in tests),
  polling is decoupled from draining, and the single-entry push is an awaitable
  test-visible step with the sync lock moved inside it — so tests fix one exact
  push/reconcile ordering with no dependence on timers.
- [ADR-0006: Usage limits are owned end-to-end by a shared limits module keyed by an opaque subject](0006-shared-limits-module.md) —
  A shared `limits` module owns configuration, recorded usage and verification for
  every capped resource; callers ask and release against an opaque subject (user
  or list) and hold no limit knowledge, accepting drift risk for one uniform ask.
- [ADR-0007: Sharing is owned end-to-end by a shared permissions module, with role composition left to the composing module](0007-shared-permissions-module.md) —
  Replaces four duplicated per-module permission tables and access checks with one
  system of record that also owns pending invites and the role predicates; resource
  types stay opaque, so recipes composes its collection-derived access itself.
- [ADR-0008: A pending invite carries a display label snapshotted at invite time](0008-invite-label-snapshot.md) —
  The inviting module supplies the invite's human-readable title, stored opaquely and
  never refreshed, so the invitee's cross-resource list needs neither domain knowledge
  in the `permissions` module nor a read hole in the unreadable-while-pending rule.
- [ADR-0009: The Firebase identity is deleted by the mobile client, never by the backend](0009-client-side-firebase-identity-deletion.md) —
  The client SDK deletes the signed-in user's own identity after the re-authentication the
  deletion flow already performs, keeping the Firebase Admin SDK and a long-lived service
  account key off the backend; the admin path deletes data only and an operator finishes
  the identity by hand.
- [ADR-0010: Account deletion is orchestrated over per-module purges, not a bulk delete](0010-account-deletion-orchestrated-over-per-module-purges.md) —
  A `settings` module owns the call order and nothing else while each module removes its own
  data for an email through its existing delete path, preserving the recipe-deleted event,
  the S3 cleanup, the limits release and third-party permission clearing that no cascade does.
- [ADR-0011: Administrators are recognised by a configured email allowlist](0011-admin-authority-from-config-allowlist.md) —
  Admin emails are configuration granted as an authority at the token-conversion seam and
  enforced by a matcher in the existing chain, avoiding custom claims' refresh delay and
  service account, and a roles table's migration, while keeping the move to either reversible.
