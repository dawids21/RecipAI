---
date: 2026-11-18T13:00:11+01:00
researcher: Dawid Stasiak
git_commit: 547b5a63b074a900b3df1e910f0b4e2825443ad3
branch: tidy-shopping-list
repository: RecipAI
topic: "How shopping-list items, bulk writes, sync, undo, quotas and AI work today, as ground for a tidy-list action (merge duplicates, add quantities, group similar items)"
tags: [research, codebase, shopping-lists, shopping_list, extraction, planning, provisioning, limits, undo, sync]
status: complete
last_updated: 2026-11-18
last_updated_by: Dawid Stasiak
---

# Research: ground truth for a "tidy shopping list" action

**Date**: 2026-11-18
**Git Commit**: 547b5a63b074a900b3df1e910f0b4e2825443ad3
**Branch**: tidy-shopping-list

## Research Question

The `task.md` description: "Create an action to tidy up and organize user shopping list (merge duplicated
items, add quantities, group similar items together)." Scope agreed with the user: backend and mobile,
with equal weight on existing merge logic, reuse of the AI integration, bulk-write vs. sync/versioning
constraints, and quotas.

## Summary

1. **There is no merge, de-duplication, quantity summing, unit normalisation or grouping anywhere.**
   - Provisioning maps each ingredient to exactly one item (`backend/.../provisioning/ProvisioningService.java:13-26`).
   - Import always appends (`mobile/lib/features/shopping_list/shopping_list_item_import_service.dart:22-36`).
   - No item has a category, group or aisle field on either side.
2. **A merge was planned and deliberately deferred.**
   - The items rewrite put it out of scope but required the design to leave room for it (`docs/tasks/2026-06-13-shopping-list-items-rewrite/requirements.md:154-160`).
   - Duplicate names are allowed and not flagged, and de-duplication is left to that future feature (`requirements.md:86-88`).
   - ADR-0003 says merges are "ordinary state changes that show up in the next full pull" (`docs/ADRs/0003-shopping-list-full-refresh-over-delta.md:80-82`).
   - That earlier research also sketched a design: server-authoritative, with AI as a suggestion layer only (`research/list-syncing.md:150-155`, `:168-177`). It assumed delta sync and soft deletes, which ADR-0003 later rejected.
3. **The PRD requirement is unimplemented and contradicts the rewrite.** The PRD says "If user adds an item that is already on the list, its quantity should be updated" (`docs/project/prd.md:62`), but the rewrite allows duplicates (point 2).
4. **Items are written one at a time, and that is by decision.**
   - Item writes are per-item POST, PUT and DELETE only. PUT and DELETE are gated on `baseVersion` and answer 412 with the winning item (`ShoppingListService.java:89-156`).
   - The HLD says bulk actions are not special endpoints. The client expands them into per-item calls, so partial outcomes are natural (`items-rewrite/hld.md:120-123`).
   - On mobile, bulk operations hold the per-list lock once but loop over per-item cores. Each item gets its own transaction and its own outbox entry (`shopping_list_item_store_service.dart:100-162`).
5. **A merge changes item quota.**
   - `SHOPPING_LIST_ITEM` is a STOCK quota counted per list, capped at 50 (`V18__shopping_list_item_limit_config.sql`).
   - A create reserves a unit and a delete releases one (`ShoppingListService.java:96`, `:155`).
   - ADR-0006 requires every path that destroys a counted unit to release it (`docs/ADRs/0006-shared-limits-module.md:102-105`).
6. **AI is used in one place only.**
   - `ExtractionService` calls a bare `ChatClient` (Gemini 2.5 Flash) and gets typed output via `.entity(Class)` (`ExtractionService.java:39-41`).
   - It is paid for by the `EXTRACTION` FLOW budget, which is never refunded.
   - Tests deep-stub the ChatClient globally (`backend/src/test/java/xyz/stasiak/recipai/TestAiConfiguration.java:10-21`).
7. **Undo has a single slot and a closed (sealed) set of two variants**: `DeletedItemsUndo` and `UncheckedItemsUndo` (`mobile/lib/features/shopping_list/undoable_action.dart`). A new destructive action needs a new variant and new branches in the exhaustive switches.

## Detailed Findings

Backend paths below are relative to `backend/src/main/java/xyz/stasiak/recipai/`, and mobile paths to
`mobile/lib/features/`, unless they are written in full.

### Area 1 — Backend item model and write paths

**Entity** — `shoppinglists/ShoppingListItem.java:20-46`:
- Fields: `id`, `shoppingListId`, `name` (not null, 255), `quantity` (`BigDecimal`, `NUMERIC(12,3)`, nullable), `unit` (free-text `String`, 64, nullable), `checked`, `position`, `version` (`@Version Long`).
- `position` is a `BigDecimal` (21,12), normalised with `setScale(12, HALF_UP)` (`:53`, `:65-71`).
- There is no `createdAt` and no category.
- `ShoppingList.java:20-30` has no `@OneToMany`. Items are linked through the `shoppingListId` column.

**Ordering**:
- Reads use `findByShoppingListIdOrderByPositionAscIdAsc` (`shoppinglists/ShoppingListItemRepository.java:10`).
- The client supplies `position` on both create and update (`dto/CreateShoppingListItemRequest.java:17`, `dto/UpdateShoppingListItemRequest.java:16`).
- V14 dropped the uniqueness constraint on position.

**Create** — `createItem`, `shoppinglists/ShoppingListService.java:89-102`:
1. Editor permission check (`:93`).
2. Resolve the owner's email (`:95`).
3. `limitsFacade.reserve(ownerEmail, listId.toString(), SHOPPING_LIST_ITEM_RESOURCE)` (`:96`).
4. Save.

The method is `@Transactional`. Creates never conflict.

**Update** — `updateItem`, `ShoppingListService.java:104-131`:
- A version mismatch throws `ItemVersionConflictException(toItemDto(item))` (`:113-115`).
- Otherwise it replaces all five mutable fields (`:117-121`) and calls `saveAndFlush`, which catches an optimistic-lock failure and throws the conflict with the reloaded winner (`:123-130`).
- No quota is touched.

**Delete** — `deleteItem`, `ShoppingListService.java:133-156`:
- Applies the same version gate.
- Then calls `limitsFacade.release(requireOwnerEmail(listId), listId.toString(), SHOPPING_LIST_ITEM_RESOURCE)` (`:155`).

**412 response** — `ShoppingListsExceptionHandler.java:36-39` returns the raw winning item DTO as the body, not a ProblemDetail.

**Endpoints** — the item endpoints are only `POST /{id}/items`, `PUT /{id}/items/{itemId}` and `DELETE /{id}/items/{itemId}` (`ShoppingListController.java:86`, `:97`, `:109`). There is no bulk endpoint, and the repository has no bulk method.

**Permissions**:
- Every item operation needs EDITOR, which OWNER satisfies (`ShoppingListService.java:158-163`).
- Only list deletion needs OWNER (`:178`).
- There is no viewer role: `ResourceRole` has `OWNER` and `EDITOR` only.

**Cross-module access** — `ShoppingListsFacade.java:14-17` exposes only `deleteAllByEmail`. No other module can write items.

What this means for the question: on the backend, a merge can only be done today by updating the survivor and deleting the others. Each of those calls is version-gated on its own, and each delete releases one quota unit.

### Area 2 — Existing merge/aggregation paths (there are none)

**Meal-plan generation**:
- `POST /meal-plans/generate-shopping-list` (`planning/MealPlanController.java:138-144`) calls `MealPlanService.generateShoppingListItems` (`planning/MealPlanService.java:229-272`).
- That method builds one `ProvisioningIngredient` per ingredient per entry, with multiplier = entry servings ÷ recipe servings (`:259-263`).
- It returns a preview only and writes nothing.

**Provisioning** — `ProvisioningService.provision` (`provisioning/ProvisioningService.java:13-26`) is a 1:1 stream map:
- It appends `" (" + comment + ")"` to the name when a comment is present (`:16-18`).
- It multiplies the quantity and passes the unit through unchanged.
- The same ingredient from two recipes therefore becomes two items.

**Quirk** — `applyMultiplier` returns the multiplier itself when the quantity is null (`:28-33`). An item like "salt (to taste)" therefore gets a quantity such as `1` or `0.5`. Nothing documents whether this is intended.

**Mobile**:
- Recipe → list: `recipe/recipe_to_shopping_list_screen.dart:63-70` makes one generated item per ingredient.
- Planning is a pass-through: `planning/shopping_list_generation_service.dart:26-45`.
- The review widget sorts by name only and shows duplicates as separate rows with their `source` subtitle (`shopping_list/shopping_list_review_widget.dart:34`, `:228`).
- Import calls `store.applyCreate` for each item and never compares against items already on the list (`shopping_list/shopping_list_item_import_service.dart:22-36`).

**Names and units**:
- The mobile parser regex `^\s*(\d+[.,]?\d*)\s*([\p{L}]+)?\s+(.+)$` keeps the unit verbatim (`shopping_list/shopping_list_item_parser.dart:12-15`).
- There is no unit vocabulary, conversion table, name normalisation or category concept in either codebase. Searching backend `main` for category, aisle and group, and mobile `lib` for merge, dedup and normalise, found nothing relevant.

What this means for the question: everything the tidy action needs (matching, summing, converting units, grouping) is new. Item names may carry a provisioning comment suffix, which matters for any matching.

### Area 3 — Mobile store, outbox and sync

**Local item** — `LocalShoppingListItem` (`shopping_list/local_shopping_list_item.dart:1-13`):
- Fields: `localId`, `serverId?`, `listId`, `name`, `quantity` (`double?`), `unit` (`String?`), `checked`, `position` (double), `lastAckedVersion?`, `dirty`, `failed`, `pendingDelete`.
- Sort order is `(position, localId)` (`:103-107`).

**Database** — sqflite schema version 1, with no `onUpgrade` (`shopping_list/shopping_list_item_database_factory.dart:22-51`):
- `items` holds `quantity REAL`, `unit TEXT` and `position REAL`.
- `outbox` holds `seq`, `item_local_id`, `list_id`, `kind` (`create|update|delete`) and a JSON `payload`.
- The payload is a full snapshot of the mutable fields (`shopping_list_item_store_service.dart:533-541`).

**Store** — `ShoppingListItemStoreService` (ADR-0004):
- It keeps a per-list `Lock` from the `synchronized` package (`:21`, `:25`), a cache and a per-list `ValueNotifier`.
- Single-item operations: `applyCreate`, `applyEdit`, `applyChecked`, `applyReorder` and `applyDelete` (`:61-99`).
- Bulk operations: `deleteAllChecked`, `uncheckAll`, `applyRestore` and `applyCheckedAll` (`:100-162`). Each takes the lock once and then loops over the private per-item cores (`_deleteItem` `:513-529`, `_checkItem` `:473-489`, `_editItem` `:444-471`, `_createItem` `:351-406`).
- Each core updates the cache and notifier, then calls `_dao.writeItemAppendingOutbox`, which is one DB transaction per item (`shopping_list_item_dao.dart:76-85`).
- So a bulk operation is atomic against other local writers, but not one DB transaction. It produces N notifier fires and N outbox entries.

**Positions**:
- A new item goes at the midpoint after `afterLocalId`, or at last + 1.0 (`shopping_list_item_store_service.dart:365-378`).
- A reorder moves one item to a midpoint (`shopping_list/shopping_list_detail_service.dart:170-198`).
- Positions are never renumbered.

**Sync** — `ShoppingListSyncService`:
- A per-list sync lock spans one poll or one push (`shopping_list/shopping_list_sync_service.dart:73`, `:106`). The drain is single-flight (`:77-78`, `:112-118`, `:222-238`).
- Pushes go strictly FIFO per list, one HTTP call per outbox entry (`:267-373`).
- A 412 runs `cascadeDiscard`, which overwrites the item with the server's winner and drops that item's queued entries, then emits a `RejectionEvent` (`:281-288`; store `:297-327`). Other items in the same bulk operation still apply.
- 404/400/403/429 lead to `discardItem` (store `:331-341`).
- Polling runs every 10 s (`:176-188`). Reconcile never touches dirty items and deletes clean items that are missing on the server (store `:172-249`).

What this means for the question: a client-side tidy would follow the "lock once, loop the cores" pattern. Other members would see each intermediate per-item write as it lands. A conflict on one item would roll back only that item, leaving a partly merged list.

### Area 4 — Undo

- `UndoableAction` is a sealed class with two variants, `DeletedItemsUndo` (full snapshots) and `UncheckedItemsUndo` (local ids) (`shopping_list/undoable_action.dart`).
- There is a single slot, `_pendingUndo` (`shopping_list_detail_service.dart:72`). It is set by `deleteItem`, `deleteAllChecked` and `uncheckAll` (`:159-165`, `:200-218`).
- `undoLast` replays the inverse (`:223-239`):
  - A delete is undone by `applyRestore`, which re-creates each item with a new localId as a `create`. This re-reserves quota and can be refused with a 429 (store `_restoreItem` `:411-442`).
  - An uncheck-all is undone by `applyCheckedAll`.
- The screen's `_runDestructive` shows a 5 s snackbar (`shopping_list/shopping_list_detail_screen.dart:55`, `:144-176`). `_undoCopy` switches exhaustively over the variants (`:178-184`).
- Edits, reorders, adds and imports are not undoable.

What this means for the question: undoing a merge would have to restore the survivor's earlier fields and re-create the deleted rows. Re-created rows get new server ids and consume quota again.

### Area 5 — Detail screen UI and gating

**Overflow menu**:
- The app bar has a single `PopupMenuButton<String>` (`shopping_list_detail_screen.dart:604-625`).
- Its entries come from `_buildMenuItems()` (`:532-592`): `rename`, `share`, `delete_checked`, `uncheck_all`, and `delete` (owner only, `:576`).

**Sections**:
- `_splitSections` (`:410-416`) splits items into an active `ReorderableListView` and a "Done" `ReorderableListView` (`:672-774`).
- Both sections share one position space.
- There are no other headers or groups.

**Editing** is inline in `ShoppingListItemWidget` (`shopping_list/shopping_list_item_widget.dart:94-295`), which parses the text on blur.

**Quota**:
- The per-list quota is fetched from `GET /shopping-lists/{id}/limits` (`shopping_list_detail_service.dart:95-109`).
- The count is `items.length`, checked items included. At the cap the add widget is disabled (`shopping_list_detail_screen.dart:376-380`).

**Feature flags**:
- Flags are `bool.fromEnvironment` constants in `mobile/lib/core/feature_flags.dart`. Only `devAuthEnabled` exists today.
- They may only be read in UI rendering or in setup files (`docs/mobile/standards/feature-flags.md`).

**Tests**:
- `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart` covers the real store and sync service over in-memory sqflite, using `_TestScheduler` and `FakeShoppingListItemRepository` (ADR-0005).
- There is no widget test for `ShoppingListDetailScreen`.

### Area 6 — AI integration (`extraction/`)

**Wiring**:
- `ExtractionConfig.java:10-13` defines a single bare `ChatClient` bean: no default system prompt, no options, no advisors.
- The model is `gemini-2.5-flash` via `spring-ai-starter-model-google-genai`, Spring AI 2.0.0 (`backend/src/main/resources/application.yml:21-28`; `backend/pom.xml:31`, `:52`).

**Call shape** — `ExtractionService.extractFromText` (`extraction/ExtractionService.java:31-50`):
1. `limitsFacade.reserve(userEmail, EXTRACTION_RESOURCE)` first (`:32`).
2. A `PromptTemplate` with a single user message (`:36-37`).
3. `chatClient.prompt(prompt).call().entity(ExtractedRecipe.class)` (`:39-41`).
4. A null result throws `ExtractionFailedException`, which maps to 500 (`:43-45`; `ExtractionExceptionHandler.java:18-23`).

Image extraction follows the same shape with a media `UserMessage` (`:52-73`).

**Budget** — `EXTRACTION` is FLOW, max 2, never refunded (`backend/src/main/resources/db/migration/V15__limits_schema.sql:24`; `limits/LimitConfig.java:61-63`).

**Tests**:
- `TestAiConfiguration` provides a `@Primary` deep-stubbed mock and is imported globally (`backend/src/test/java/xyz/stasiak/recipai/TestAiConfiguration.java:10-21`; `TestcontainersConfiguration.java:12`).
- `ExtractionIntegrationTest.java:72-74` stubs `prompt(any(Prompt.class)).call().entity(ExtractedRecipe.class)`.

**Privacy** — sending list contents to Gemini would need the Data safety / privacy-policy disclosure noted in `docs/tasks/2026-09-02-play-store-public-release/research/play-store-publishing-requirements.md:146-151`.

What this means for the question: the `ChatClient` and the structured-output pattern can be reused as they are. No AI quota resource applies to lists today.

### Area 7 — Quotas

**`LimitsFacade` methods**:
- `reserve(configSubject, usageSubject, resource)` (`limits/LimitsFacade.java:30`)
- `release(...)` (`:39`)
- `clear` (`:44`)

Each call reserves or releases exactly one unit; there is no count parameter (`limits/LimitService.java:29-70`).

**`SHOPPING_LIST_ITEM`**:
- STOCK quota, counted per list, with the value resolved from the list's owner (`docs/backend/modules/shopping-lists/module.md:55-61`).
- An edit costs nothing.
- A missed release causes drift, and the only repair is the repeatable recompute (`docs/ADRs/0006-shared-limits-module.md:102-105`; `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql`).

**Mobile push order** — because pushes are FIFO, a `create` queued before a `delete` can get a 429 at the cap even when the net count would fit.

## Code References

- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java:89-156` — item create/update/delete, version gate, quota reserve/release
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListController.java:86-118` — the only item endpoints
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListsExceptionHandler.java:36-39` — 412 with the raw winning item
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListItem.java:20-71` — item entity, no category
- `backend/src/main/java/xyz/stasiak/recipai/provisioning/ProvisioningService.java:13-33` — 1:1 ingredient→item map; null-quantity quirk
- `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanService.java:229-272` — generation preview
- `backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractionService.java:31-73` — the only AI call site
- `backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractionConfig.java:10-13` — `ChatClient` bean
- `backend/src/test/java/xyz/stasiak/recipai/TestAiConfiguration.java:10-21` — global ChatClient mock
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsFacade.java:26-64` — reserve/release/clear API
- `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart:100-162` — bulk operations as loops under one lock
- `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart:297-341` — 412 cascade-discard and discard
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart:267-373` — FIFO per-entry push and outcomes
- `mobile/lib/features/shopping_list/undoable_action.dart` — sealed undo variants
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart:72`, `:159-239` — undo slot and replay
- `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart:532-625` — overflow menu
- `mobile/lib/features/shopping_list/shopping_list_item_import_service.dart:22-36` — append-only import
- `mobile/lib/features/shopping_list/shopping_list_item_parser.dart:12-15` — quantity/unit/name regex
- `mobile/lib/features/shopping_list/shopping_list_item_database_factory.dart:22-51` — sqflite schema v1

## Architecture Insights

**Item writes**:
- Every item change is a per-item, version-gated, first-action-wins write.
- Two different items must never conflict (items-rewrite requirements §2.4/§2.7; ADR-0003). This is why there is no per-list version or counter.
- A list-level "tidy transaction" or list version would cut against this.

**Mobile multi-item actions**:
- They are one store-lock section over private per-item cores (ADR-0004).
- New drain call sites are wrapped in `unawaited()`, and sync tests go through the injected scheduler (ADR-0005).

**Quota**:
- Every path that destroys an item must release its `SHOPPING_LIST_ITEM` unit (ADR-0006).
- The limits T4 design audited exactly two backend item-destroying paths (`docs/tasks/2026-07-29-user-usage-limits/plans/T4-task-design.md:544-553`). A server-side merge would be a third.

**Backend module boundaries**:
- Cross-module access goes through facades (`docs/backend/standards/module-structure.md`).
- The shopping-lists facade currently exposes no item writes.

**AI calls**:
- The pattern is: reserve the budget before the call, then `.entity(Record.class)` structured output, then null → `*FailedException` → 500.
- Tests stub the shared deep mock.

## Historical Context

**Merge feature, deferred** (`docs/tasks/2026-06-13-shopping-list-items-rewrite/`):
- `requirements.md:154-160` puts merge out of scope and says the design must not preclude it.
- `hld.md:43-44` and `tasks.md:59` defer the `merged_into` provenance pointer.

**Merge pre-design** (`research/list-syncing.md:122-181`):
- Matching (which rows are the same) is kept separate from dedup-merge (committing it).
- Server-authoritative commit (`:150-155`).
- Survivor = earliest position, with compatible quantities summed into it (`:136-148`).
- Only convertible units are summed, via a small conversion table (`:157-161`).
- Matching tiers: normalise + exact match, then a synonym dictionary, then AI as a suggestion layer only (`:168-177`).
- This assumed delta sync with soft deletes, which ADR-0003 replaced with full pull and hard deletes.

**ADR-0003** (`:80-82`) — a merge fits the full-pull scheme. The ADR also says to revisit the scheme if lists grow large (`:74-76`).

**Bulk actions** — the client expands them into per-item calls (`hld.md:120-123`).

**Undo** (`docs/tasks/2026-07-31-undo-shopping-lists/`):
- Two alternatives were rejected: backend soft-delete + undelete, and whole-list snapshot restore. The latter would re-assert other members' changes and enqueue an update for every item.

**Silent bulk-import refusals** — T4 noted that a bulk import's refusals reach nobody: the user is told N items were added and silently gets fewer (`docs/tasks/2026-07-29-user-usage-limits/plans/T4-task-design.md:566-575`).

**List-level versioning** — an earlier attempt was reverted with empty commit messages (`29514f0`, `6b22f13`, 2025-11-13). The reason is unknown.

## Open Questions

**Requirements**:
- **Requirements conflict.** The PRD says re-adding an existing item should update its quantity (`prd.md:62`), while the rewrite allows duplicates (`requirements.md:86-88`). Is the PRD line stale, or should tidy also cover add-time merging?
- **Grouping has no data model.** Is a group or category a persisted item field (backend column, sqflite schema v2 migration), a display-only grouping computed on the client, or expressed through `position` alone?

**Code behaviour**:
- **Null-quantity quirk.** Is `applyMultiplier` returning the multiplier for null quantities intended (`ProvisioningService.java:28-33`)? It affects "add quantities".
- **Position crowding.** Repeated midpoint inserts can crowd positions together, and the items rewrite left the rebalancing strategy open (`items-rewrite/tasks.md:80-81`). Whether this was ever handled is unconfirmed.
- **Silent import refusals.** Whether T5 fixed the bulk-import silent-loss gap is unconfirmed.

**Docs vs. code**:
- `docs/mobile/modules/shopping_list/ui.md` calls the Done section "collapsible", but no collapse toggle was found in `shopping_list_detail_screen.dart:735-743`.
- ADR-0004 still describes a `_busy` guard that no longer exists in the code.
