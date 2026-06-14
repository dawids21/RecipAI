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
