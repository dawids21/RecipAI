# ADR-0013: All AI calls draw on one shared per-user AI budget

**Date:** 2026-11-18
**Related ADRs:** [ADR-0006](0006-shared-limits-module.md)

## Context

AI provider calls are billed per request. Recipe extraction from a web page or a photo was the only AI feature, and it
was capped by a per-user FLOW budget named after it, reserved before the provider is called and never refunded. A
second AI feature, tidying a shopping list (grouping and ordering items with the same model), adds another paid call
reachable by any user.

The limits module treats resources as opaque names and lets an operator change any user's cap by editing a database
row. The model client was configured privately inside the extraction module.

## Decision

All AI calls reserve one unit of a single per-user FLOW resource named `AI`, which replaces the extraction-specific
resource.

- The existing configuration rows, per-user overrides and recorded usage are renamed in place.
- The default cap rises from 2 to 3, so that two features fit in one budget.
- The model client and the resource name move to shared configuration, so any module can make an AI call.
- Each feature still reserves before calling the provider and never refunds.

## Alternatives considered

- **One resource per AI feature.** Each feature gets its own cap, but the cost driver is the same model call. An
  operator would have to reason about a growing set of budgets, and users would see a separate counter per feature.
- **Tidy without a limit.** Simplest, but it exposes an unbounded paid call, which is exactly what the limits module
  exists to prevent.
- **Keep the name `EXTRACTION` and share it.** No migration, but the name would misdescribe what it counts as soon as
  a second feature uses it.

## Consequences

- One counter answers how many AI uses a user has left. Heavy use of one feature starves the other, by design.
- A new AI feature adds no limit configuration; it reserves `AI`.
- The rename is a breaking change for app builds that look the budget up by its old name. They find no quota and fail
  open by hiding the counter until updated.
- Per-user overrides set for extraction now cap all AI use for that user.
