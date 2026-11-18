# Tidy Shopping List — Plan Brief

> Full plan: `docs/tasks/2026-11-18-tidy-shopping-list/plan.md`

## What and why

Lists built from several recipes or meal-plan days end up with the same product on several rows, scattered around the
list. A "Tidy up" action merges duplicates, adds up their quantities and places similar items together, after the
user has previewed the result.

## Starting point

- Item writes are per-item and version-gated. Each delete releases one `SHOPPING_LIST_ITEM` quota unit. There are no
  bulk endpoints.
- No merge, quantity summing or grouping logic exists anywhere, and items have no category field.
- The only AI use is recipe extraction (Gemini via `ChatClient`), paid for by a FLOW `EXTRACTION` budget (2, never
  refunded).
- Mobile is offline-first: an outbox, a full-list poll every 10 s, and a sync lock that spans HTTP and reconcile.

## Desired end state

- The overflow menu has **Tidy up**. It goes from preview to Apply, and merges and regrouping land in one atomic
  server write.
- Other members see the result on their next poll. Checked items are untouched.
- Extraction and tidy share one per-user `AI` budget (default 3).

## Key decisions

| Decision | Choice | Why | Source |
|---|---|---|---|
| Matching | Gemini on the backend groups items and orders them | Grouping "similar" items needs product knowledge | plan |
| Commit | One atomic server endpoint | All-or-nothing; no partly-tidied list (ADR-0012) | plan |
| Grouping model | `position` only | No schema change on either side | plan |
| Confirmation | Preview, then accept or cancel the whole tidy; no undo | AI only suggests and the user commits | plan |
| Scope | Active (unchecked) items only | Merging Done into Active has no clear meaning | plan |
| Quantities | Server sums equal units (trimmed, case-insensitive); survivor = earliest position | Predictable, no AI arithmetic | plan |
| Budget | Shared `AI` resource, renamed from `EXTRACTION`, default 3 | One budget for paid AI calls (ADR-0013) | plan |
| Unsynced edits | Drain first; refuse if the outbox is still non-empty | The apply is version-gated against server state | plan |

## Scope

**In scope:** the `AI` budget rename, suggestion and apply endpoints, the mobile menu, preview and locked apply, docs
and the privacy policy.

**Out of scope:** add-time merging (PRD line), categories and headers, undo, per-merge toggles, unit conversion,
tidying checked items, offline tidy.

## Approach

1. Rename the budget to `AI` and move `ChatClient` to `config.ai`.
2. Suggestion: AI groups numbered items, then the server sanitises them and applies one merge rule.
3. Apply: re-validate all active items' versions, then merge, renumber, delete and release quota in one transaction;
   any change in between gives 409.
4. Mobile: drain, suggest, preview, then apply under the sync lock and reconcile the returned list.

## Phases at a glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Shared AI budget | `EXTRACTION` → `AI` (3), shared `ChatClient` | Installed builds hide the counter (fail-open) |
| 2. Suggestion endpoint | AI grouping + deterministic merges, read-only | Low-quality AI groupings |
| 3. Apply endpoint | Atomic merge/reorder with quota release | Missing a release causes quota drift |
| 4. Mobile flow | Menu, preview sheet, locked apply, docs | Stale-poll race if the apply runs outside the sync lock |

**Prerequisites:** None.
**Estimated effort:** about 4 implementation sessions.

## Open risks and assumptions

- Any edit to an active item between suggestion and apply rejects the whole tidy (409). This is acceptable for short
  lists; watch for complaints on busy shared lists.
- Gemini now receives shopping-list content. The privacy policy is updated in P4; the Play Console Data safety form is
  a manual step.
- Positions are renumbered 1..N for active items only, so Done items keep their old positions in the shared space.
  This is harmless because the sections split on `checked`.

## Success criteria

- A list with duplicate same-unit items becomes one row per product with summed quantities, and similar items sit
  together.
- A concurrent change or unsynced local edits block the tidy without writing anything.
- Item quota usage drops by exactly the number of rows merged away.
