# Tidy Shopping List — Plan

**Date:** 2026-11-18
**Complexity:** HIGH — backend + mobile, a new AI call and a renamed shared budget, a new atomic item-destroying path
against the sync/quota invariants of ADR-0003/0004/0006.
**Inputs:** `task.md`, `research.md`
**ADRs:** `docs/ADRs/0012-shopping-list-tidy-atomic-server-endpoint.md`, `docs/ADRs/0013-shared-ai-budget.md`

## Overview

A "Tidy up" action in the shopping-list overflow menu asks the backend for a tidy suggestion. Gemini groups the
list's active items. The server turns those groups into deterministic merges with summed quantities and a new order,
and the user previews the result. On accept, one atomic endpoint commits every merge and the new order. AI calls
from extraction and tidy share one per-user `AI` budget (renamed from `EXTRACTION`, default raised to 3).

## Current state

- **Item writes are per-item and version-gated.** `ShoppingListService.updateItem`/`deleteItem`
  (`backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java:104-156`) check `baseVersion`
  and answer 412 with the winning item. `deleteItem` releases one `SHOPPING_LIST_ITEM` unit against
  (owner email, list id) (`:155`). There are no bulk endpoints (`ShoppingListController.java:86-118`).
- **Only `ExtractionService` calls AI.** It reserves `EXTRACTION` first, then calls `chatClient.prompt(..).call()
  .entity(Record.class)`, and a null result means a 500 (`extraction/ExtractionService.java:21-50`). The `ChatClient`
  bean is package-private in `extraction/ExtractionConfig.java:10-13`.
- **The `EXTRACTION` budget** is seeded in `db/migration/V15__limits_schema.sql:23-24` (FLOW, 2, no period). The latest
  migration is `V24`. Mobile names it `LimitResources.extraction` (`mobile/lib/features/limits/limit_quota.dart:9`,
  `:24`). It is used by the extraction screens (`image_extraction_screen.dart:193`, `:211`;
  `url_extraction_screen.dart:283`, `:319`) under the counter noun `'extractions'`.
- **Tests stub AI globally.** `TestAiConfiguration` is a `@Primary` deep-stubbed mock, reset and stubbed per test
  class (`backend/src/test/java/xyz/stasiak/recipai/extraction/ExtractionIntegrationTest.java:71-76`).
- **Mobile pull reconcile** `ShoppingListItemStoreService.reconcileFromServer`
  (`mobile/lib/features/shopping_list/shopping_list_item_store_service.dart:172-251`) adopts newer server versions for
  clean items, inserts unknown items, and deletes clean items that are missing on the server. Dirty items are left
  alone.
- **The sync lock** spans HTTP and reconcile. `fetchAndReconcile` (`shopping_list_sync_service.dart:202-209`) and
  `pushNextEntry` (`:267-268`) both take `_syncLockFor(listId)`.
- **The overflow menu** is built by `_buildMenuItems` (`shopping_list_detail_screen.dart:531-592`) and dispatched by
  `onSelected` on string values (around `:610-620`).

## Desired state

- Editors and owners see a **Tidy up** menu entry. It is disabled when the list has fewer than two active items.
- Tapping it drains the list's outbox. If anything is still queued, the user sees "Sync your changes first" and
  nothing else happens.
- Otherwise `POST /shopping-lists/{id}/tidy/suggestion` reserves one `AI` unit for the requester, sends the active
  items to Gemini, and returns merges plus an order. If there are no merges and the order is unchanged, the user
  sees "Already tidy".
- A preview sheet lists each merge ("onion 2 pcs + onion 1 pcs → onion 3 pcs") and says the items will be regrouped.
  The user picks Apply or Cancel for the whole tidy.
- Apply calls `POST /shopping-lists/{id}/tidy` in a single transaction:
  - Survivors are updated.
  - Merged-away items are deleted, each releasing one `SHOPPING_LIST_ITEM` unit.
  - Active items are renumbered.
  - The response is the full list, which mobile reconciles into the store.
- If any active item was added, removed or changed since the suggestion, the apply answers 409. The user sees
  "List changed — try again", and nothing is written.
- Other members pick up the result through their normal 10 s poll.
- The extraction counter and its 429 now track the shared `AI` resource (default 3, FLOW, never refunded).
- **Unchanged:**
  - Checked (Done) items are never touched by tidy.
  - The per-item endpoints, the outbox and reconcile semantics.
  - `/extract/balance` keeps its path and now reports the `AI` balance.

### Key discoveries

- **The apply must hold the mobile sync lock across HTTP and reconcile.** Otherwise a poll that fetched before the
  apply and reconciles after it re-inserts merged-away items until the next poll. This is the ADR-0004 race. The same
  lock also guarantees no push is in flight.
- **`requestDrain` returns immediately when a drain is already running** (`shopping_list_sync_service.dart:112-116`).
  So `await requestDrain(...)` does not prove the outbox is empty. The authoritative "outbox empty" check is the one
  inside the apply's sync-lock section. The check before the suggestion is only a fast fail.
- **`reconcileFromServer` already handles the apply result.** Survivors and reordered items carry newer versions and
  are adopted. Merged-away ids are absent, so they are deleted. The store needs no new mutation.
- **The tidy apply is a third item-destroying path.** It must release one unit per deleted item (ADR-0006).
  `LimitsFacade.release` has no count parameter, so call it once per deleted item.
- **Renaming the budget hides the counter on installed builds.** They look up `'EXTRACTION'` in `GET /limits`, find
  nothing, and fail open with no counter shown. Nothing breaks.
- **Gemini sees item indexes, not UUIDs.** The prompt numbers the items 1..N and the plan refers to those numbers.
  Long UUIDs invite transcription errors, and the server has to sanitise the output anyway.
- **Item names are user content inside the prompt.** A crafted name can only skew the grouping, because the server
  validates indexes and computes quantities itself. It does not need further hardening.

## What we're NOT doing

- Add-time merging. The PRD line "If user adds an item that is already on the list, its quantity should be updated"
  (`docs/project/prd.md:62`) stays unimplemented.
- A persisted category or group headers. Grouping lives in `position` only and erodes as new items are appended.
- Undo of a tidy. The preview is the safeguard, so there is no new `UndoableAction` variant.
- Toggling individual merges in the preview.
- Unit conversion (200 g + 0.3 kg), and AI-proposed names or quantities.
- Tidying checked items, and tidying offline.
- Fixing the `ProvisioningService.applyMultiplier` null-quantity quirk.
- A `merged_into` provenance pointer.
- The Play Console Data safety form. It lives outside the repo and is listed under Migration notes as a manual step.

## Implementation approach

1. Generalise the AI budget first (P1), so that tidy reserves the same `AI` resource extraction uses, from a shared
   `config.ai` home.
2. The suggestion endpoint (P2) is read-only apart from the budget. AI produces groups and an order over numbered
   items. The server sanitises them and computes merges with one deterministic merge rule.
3. The apply endpoint (P3) takes the client's accepted merges, order and base versions. It re-validates everything
   against current state using the same merge rule, and commits atomically or not at all.
4. Mobile (P4) orchestrates: drain, suggest, preview, apply under the sync lock, then reconcile the returned list
   through the existing store path.

## Critical implementation details

- **One merge rule, used by both endpoints.** Put it in a single package-private class and call it from P2 and P3.
  - Survivor: the group's item with the lowest `(position, id)`. It keeps its name.
  - Compatible: units are equal after trim and case-insensitive comparison. A null or blank unit matches only another
    null or blank unit.
  - Quantity: the sum of the non-null quantities, or null if every quantity is null.
  - An incompatible group is not merged, but its items stay adjacent in the order.
- **The apply version check covers all active items, not only merged ones.** Renumbering writes every active item.
  The request's `baseVersions` key set must equal the current active id set, and each version must match. Otherwise
  answer 409. An `ObjectOptimisticLockingFailureException` at flush also maps to 409.
- **Mobile mapping.** The suggestion and apply payloads use server ids. The preview renders from the item snapshot in
  the suggestion response, not from local items, so it never needs a serverId→localId lookup.

## Phase 1: Shared AI budget

### Overview

`EXTRACTION` becomes `AI` (default 3). The `ChatClient` and the resource name live in `config.ai`, and extraction and
mobile use the new name.

### Changes required

#### 1. Migration

**File**: `backend/src/main/resources/db/migration/V25__rename_extraction_limit_to_ai.sql` (new)

**Intent**: rename the resource everywhere it is stored and raise the default.

**Contract**:
- `limit_config.resource` and `limit_usage.resource` change `'EXTRACTION'` → `'AI'`. Per-user overrides keep their
  values.
- The default row (`subject IS NULL`) gets `max_value = 3`.
- The repeatable `R__recompute_limit_usage.sql` does not mention `EXTRACTION`, so it needs no change.

#### 2. AI config home

**Files**: `backend/src/main/java/xyz/stasiak/recipai/config/ai/AiConfig.java` (new);
`extraction/ExtractionConfig.java` (deleted)

**Intent**: make the `ChatClient` bean and the budget name shared infrastructure, alongside `config.s3`,
`config.security` and `config.time`.

**Contract**: `AiConfig` declares the `ChatClient` bean (body moved from `ExtractionConfig`) and
`public static final String AI_RESOURCE = "AI"`.

#### 3. Extraction

**File**: `extraction/ExtractionService.java`

**Intent**: reserve and report `AiConfig.AI_RESOURCE` instead of `EXTRACTION_RESOURCE`. Drop the old constant.

#### 4. Mobile

**Files**: `mobile/lib/features/limits/limit_quota.dart`; `extraction/image_extraction_screen.dart`;
`extraction/url_extraction_screen.dart`

**Intent**:
- `LimitResources.extraction = 'EXTRACTION'` becomes `LimitResources.ai = 'AI'`, still in `perUser`.
- The extraction counters use it, with the noun `'AI uses'`.

#### 5. Docs

**Files**:
- `docs/backend/modules/limits/{module,db,api}.md`
- `docs/backend/modules/extraction/module.md` (Limits section, test-tree comment)
- `docs/backend/modules/config/module.md` (`config.ai`)
- `docs/mobile/modules/limits/module.md`
- `docs/mobile/modules/extraction/*.md` where the budget is named

**Intent**: rename to `AI`, state it is shared by extraction and shopping-list tidy, and update the default to 3.

### Success criteria

#### Automated verification

- `ExtractionIntegrationTest` passes using `AiConfig.AI_RESOURCE`, and its 429 body asserts `resource == "AI"`.
- `LimitsApiIntegrationTest` asserts `"AI"` instead of `"EXTRACTION"`, with default 3.
- `./mvnw verify` (backend) passes.
- `flutter analyze` and `flutter test` (mobile) pass.

#### Manual verification

- On the URL extraction screen the counter reads `0 / 3 AI uses` for a fresh user.

---

## Phase 2: Tidy suggestion endpoint

### Overview

`POST /shopping-lists/{id}/tidy/suggestion` returns AI-grouped, server-merged proposals for the active items.

### Changes required

#### 1. AI plan model and prompt

**Files**: `shoppinglists/TidyPlan.java` (new, package-private records); `shoppinglists/TidyService.java` (new)

**Intent**: build a prompt that numbers the active items 1..N as `index. name | quantity | unit`. Ask Gemini to group
items that are the same product (for merging) and to order all items so that similar products are adjacent. Read
the reply with `.entity(TidyPlan.class)`.

**Contract**:
- `record TidyPlan(List<TidyGroup> groups, List<Integer> order)`, with `record TidyGroup(List<Integer> items)`.
- A null plan throws `TidyFailedException`, which maps to 500 ProblemDetail like `ExtractionFailedException`.

#### 2. Sanitising and merging

**File**: `shoppinglists/TidyMerger.java` (new, package-private): the single merge rule from Critical implementation
details.

**Contract**:
- Sanitise the plan:
  - Drop unknown or duplicate indexes.
  - An index can belong to at most one group; the first group wins.
  - Drop groups with fewer than two items.
  - The order keeps the first occurrence of each index and appends any missing items in current position order.
- Merging: compatible groups become merges. Every group's items are made contiguous in the order, placed at the
  group's first index.

#### 3. Service and endpoint

**Files**: `shoppinglists/TidyService.java`; `ShoppingListController.java`; `ShoppingListsExceptionHandler.java`;
`shoppinglists/dto/TidySuggestionDto.java`, `dto/TidyMergeDto.java` (new)

**Intent**: run the steps below. The suggestion writes nothing.
1. Check EDITOR permission.
2. Load the active items.
3. Refuse fewer than two items with 400 *before* reserving.
4. `limitsFacade.reserve(userEmail, AiConfig.AI_RESOURCE)`. The requester pays, and a refusal is a 429 handled by
   `limits`.
5. Call the AI.
6. Sanitise and merge.

**Contract**:
- `POST /shopping-lists/{id}/tidy/suggestion` → 200
  `TidySuggestionDto(List<ShoppingListItemDto> items, List<TidyMergeDto> merges, List<UUID> order)`.
  - `items` is the active-item snapshot with versions.
  - `TidyMergeDto(UUID survivorId, List<UUID> mergedItemIds, String name, BigDecimal quantity, String unit)`.
    `mergedItemIds` excludes the survivor; name, quantity and unit are the survivor's resulting fields.
  - `order` covers every item in `items`, merged-away ones included.
- Errors: 404 list, 403 not editor, 400 fewer than two active items, 429 `AI` budget, 500 AI returned nothing.
- `TidyService` lives in `shoppinglists` and uses `ShoppingListItemRepository` and the permission/owner helpers.
  Move the helpers it needs (`requireEditorPermission`, `toItemDto`) somewhere both services can share, following
  `docs/backend/standards/module-structure.md`. Don't duplicate them.

#### 4. Docs

**File**: `docs/backend/modules/shopping-lists/api.md`, `module.md` (file tree, Limits note on `AI`)

### Success criteria

#### Automated verification

- `ShoppingListTidyIntegrationTest` (new) stubs `prompt(any(Prompt.class)).call().entity(TidyPlan.class)` and covers:
  - Same-unit duplicates merge with summed quantity and the earliest-position survivor.
  - Case- and space-different units merge; mismatched units don't merge but end up adjacent.
  - All-null quantities give a null result.
  - Checked items are excluded from the prompt and the response.
  - Garbage indexes, duplicates and an incomplete order are sanitised.
  - A null plan gives 500.
  - Fewer than two active items gives 400, with no unit consumed.
  - A non-member gets 403; an exhausted `AI` budget gives 429 with `resource == "AI"`.
  - A successful call consumes one `AI` unit of the requester (not the owner).
  - The suggestion writes nothing (item versions unchanged).
- `TidyMerger` unit test covers the merge rule's edge cases.
- `./mvnw verify` passes.

#### Manual verification

- Against the dev profile with a real Gemini key, a list with "onion 2", "Onion 1", "milk 1 l", "bread" returns one
  onion merge and an order that places the onions together.

---

## Phase 3: Tidy apply endpoint

### Overview

`POST /shopping-lists/{id}/tidy` commits accepted merges and the order atomically, or rejects the whole tidy.

### Changes required

#### 1. Apply

**Files**: `shoppinglists/TidyService.java`; `ShoppingListController.java`; `dto/ApplyTidyRequest.java` (new);
`exception/TidyConflictException.java` (new); `ShoppingListsExceptionHandler.java`

**Intent**: one `@Transactional` method that does the following:
1. Check EDITOR permission.
2. Load the active items.
3. Validate the request (below).
4. Recompute each merge with `TidyMerger`.
5. Set each survivor's quantity and unit.
6. Assign active positions `1, 2, 3…` following `order`, skipping merged-away ids.
7. Delete the merged-away items.
8. `saveAndFlush`.
9. Release one `SHOPPING_LIST_ITEM` unit per deleted item against (owner email, list id).
10. Return the full list.

**Contract**:
- Request: `ApplyTidyRequest(List<List<UUID>> merges, List<UUID> order, Map<UUID, Long> baseVersions)`, validated with
  `@Valid` and not-null constraints. Each merge lists all its ids, survivor included; the server picks the survivor.
- 409 `TidyConflictException` → ProblemDetail, title "Shopping List Changed". It is raised when the `baseVersions` key
  set ≠ current active ids, any version differs, or optimistic locking fails at flush.
- 400 when:
  - a merge has fewer than two ids, or ids overlap across merges;
  - an id is not in `baseVersions`;
  - `order` is not a permutation of `baseVersions` keys;
  - a merge is incompatible under `TidyMerger`.
- 200 `ShoppingListDto` (same shape as `GET /shopping-lists/{id}`).
- Checked items: never read for writing, positions untouched.
- No `AI` unit is consumed.

#### 2. Docs

**Files**:
- `docs/backend/modules/shopping-lists/api.md`
- `docs/backend/modules/shopping-lists/module.md` (Limits: tidy deletes release their units)
- `docs/backend/modules/limits/module.md` consumers list

### Success criteria

#### Automated verification

- `ShoppingListTidyIntegrationTest` apply cases:
  - Merges update the survivor's quantity and unit and delete the others.
  - Positions follow `order`; checked items are unchanged.
  - Item quota usage drops by the number of deleted items (via `limitsFacade` balance read).
  - A stale version, an extra active item (added after the suggestion) or a missing one each give 409, with nothing
    written.
  - Incompatible-unit merges, overlapping merges and a non-permutation order each give 400.
  - A non-editor gets 403.
  - A suggestion → apply round trip with the suggestion's own payload succeeds.
- `./mvnw verify` passes.

#### Manual verification

- Use the `backend/http/` suite (add `tidy.http`): suggest, then apply the response; `GET /shopping-lists/{id}` shows
  the merged list.

---

## Phase 4: Mobile tidy flow

### Overview

Users tidy a list from the overflow menu with a preview, and the result lands in the store like a poll.

### Changes required

#### 1. Models and repository

**Files**: `mobile/lib/features/shopping_list/shopping_list_tidy.dart` (new: `TidySuggestion`, `TidyMerge`);
`shopping_list_item_repository.dart`

**Contract**:
- `Future<TidySuggestion> suggestTidy(String listId, String? token)`. A 429 throws `TidyLimitReachedException`. No
  network throws `ShoppingListNetworkException`. Other statuses throw a generic `Exception`.
- `Future<List<ShoppingListItem>> applyTidy(String listId, TidySuggestion suggestion, String? token)`. It builds
  `ApplyTidyRequest` from the suggestion. A 409 throws `TidyConflictException`; other failures are handled as above.
- Log lines follow the existing `METHOD url -> status (ms)` shape, with no bodies (`docs/mobile/standards/logging.md`).

#### 2. Sync service

**File**: `shopping_list_sync_service.dart`

**Contract**:
- `Future<bool> hasPendingChanges(String listId)`: true when `_store.nextOutboxEntry(listId)` is non-null.
- `Future<void> applyTidy(String listId, TidySuggestion s)`: under `_syncLockFor(listId)` it does the following:
  1. If the outbox is non-empty, throw `TidyPendingChangesException` without making the HTTP call.
  2. Call `repository.applyTidy`.
  3. Pass the result to `_store.reconcileFromServer`.

  On any exception the store is left unchanged.

#### 3. Detail service

**File**: `shopping_list_detail_service.dart`

**Contract**:
- `Future<TidySuggestion> suggestTidy()`:
  1. `await _syncService.requestDrain(listId)`.
  2. If `hasPendingChanges`, throw `TidyPendingChangesException`.
  3. Fetch a token and call the repository.
- `Future<void> applyTidy(TidySuggestion)`: delegates to the sync service.
- `bool TidySuggestion.isNoop`: no merges, and `order` equals the snapshot's current order.
- Tidy does not touch `_pendingUndo`.

#### 4. UI

**Files**: `shopping_list_detail_screen.dart`; `shopping_list_tidy_sheet.dart` (new)

**Intent**:
- Menu: a `tidy` entry ("Tidy Up", `Icons.auto_fix_high`) for all roles, disabled when fewer than two active items
  are shown.
- On select:
  1. Show a progress indicator and call `suggestTidy`.
  2. If the suggestion is a no-op, show the snackbar "Already tidy". Otherwise open the sheet.
- The sheet:
  - Lists each merge as "<source items> → <name quantity unit>", with the line "Items will be regrouped" when the
    order changes.
  - Has Cancel and Apply buttons. Apply shows progress, then closes.
- Snackbars:

  | Condition | Message |
  |---|---|
  | Pending changes | "Sync your changes first" |
  | 429 | "AI limit reached" |
  | Network | "You're offline" |
  | 409 | "List changed — try again" |
  | Other | "Couldn't tidy the list" |

- Style follows `docs/mobile/standards/theming.md`.

#### 5. Docs and privacy

**Files**:
- `docs/mobile/modules/shopping_list/module.md` (file map, Services and State: tidy; Sync: tidy apply under the sync
  lock)
- `docs/mobile/modules/shopping_list/ui.md` (menu entry, sheet, messages)
- `site/privacy-policy.html:71-73` (Gemini also receives shopping-list item names, quantities and units when a user
  tidies a list)

### Success criteria

#### Automated verification

- `shopping_list_sync_service_test.dart` gets new cases, extending `FakeShoppingListItemRepository` with `applyTidy`:
  - `applyTidy` with a non-empty outbox throws `TidyPendingChangesException` and makes no repository call.
  - On success it reconciles: the survivor is updated, merged-away items are removed and positions are adopted.
  - A 409 leaves the store unchanged.
- `flutter analyze` and `flutter test` pass.

#### Manual verification

- On a device with a shared list:
  - Tidy merges the duplicates and regroups the list.
  - The other member sees the result within one poll.
  - Tidy while offline shows "You're offline" or "Sync your changes first".
  - Editing an item from the other device between suggestion and Apply gives "List changed — try again".

**Implementation note**: needs P2 and P3 deployed to the backend used for manual checks.

---

## Testing strategy

### Unit tests

- `TidyMerger`: survivor choice, unit equality (case, whitespace, null/blank), quantity sums, sanitising of
  malformed plans.

### Integration tests

- Backend `ShoppingListTidyIntegrationTest` for both endpoints, including the quota release on apply and the `AI`
  budget on suggestion.
- Mobile sync-service tests for the locked apply, through the injected scheduler (ADR-0005).

### Manual testing steps

1. Create a list with "2 onion", "1 Onion", "500 g flour", "0.5 kg flour", "milk", and a checked "bread".
2. Tidy up. The preview shows the onion merge (3). The flour items are not merged but sit together. Bread is not
   mentioned.
3. Apply. The list shows the merged onion, the flour items adjacent, and bread still in Done. The item counter drops
   by 1.
4. On a second account sharing the list, the change appears within ~10 s.
5. Use tidy until the `AI` budget is exhausted. The URL extraction counter shows the same usage and the next tidy is
   refused.

## Migration notes

- `V25` renames the `EXTRACTION` config and usage rows to `AI` and raises the default to 3. Existing extraction usage
  carries over into the shared budget.
- App builds already installed show no extraction counter until users update (fail-open).
- Manual, outside the repo: update the Play Console Data safety form to say that shopping-list content is shared with
  Google Gemini.

## References

- `docs/tasks/2026-11-18-tidy-shopping-list/research.md`
- `docs/tasks/2026-06-13-shopping-list-items-rewrite/research/list-syncing.md:122-181` (merge pre-design)
- ADR-0003, ADR-0004, ADR-0005, ADR-0006, ADR-0012, ADR-0013
- Sibling code: `extraction/ExtractionService.java` (AI call shape), `ShoppingListService.deleteItem` (release),
  `ShoppingListSyncService.fetchAndReconcile` (locked fetch + reconcile)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <short sha>` when the phase's commit lands. Never renumber or rename entries — /implement ticks these.

### Phase 1: Shared AI budget

#### Automated

- [ ] 1.1 `ExtractionIntegrationTest` passes on `AI`, 429 body asserts `resource == "AI"`
- [ ] 1.2 `LimitsApiIntegrationTest` asserts `AI` with default 3
- [ ] 1.3 `./mvnw verify` passes
- [ ] 1.4 `flutter analyze` and `flutter test` pass

#### Manual

- [ ] 1.5 URL extraction counter reads `0 / 3 AI uses` for a fresh user

### Phase 2: Tidy suggestion endpoint

#### Automated

- [ ] 2.1 `ShoppingListTidyIntegrationTest` covers all suggestion cases listed in Phase 2
- [ ] 2.2 `TidyMerger` unit test covers the merge rule's edge cases
- [ ] 2.3 `./mvnw verify` passes

#### Manual

- [ ] 2.4 Real Gemini suggestion merges onions and orders them together

### Phase 3: Tidy apply endpoint

#### Automated

- [ ] 3.1 `ShoppingListTidyIntegrationTest` covers all apply cases listed in Phase 3
- [ ] 3.2 `./mvnw verify` passes

#### Manual

- [ ] 3.3 `tidy.http` suggest → apply round trip shows the merged list

### Phase 4: Mobile tidy flow

#### Automated

- [ ] 4.1 Sync-service tests cover pending-changes refusal, successful reconcile and 409
- [ ] 4.2 `flutter analyze` and `flutter test` pass

#### Manual

- [ ] 4.3 Device check: merge + regroup, other member sees it, offline/pending and 409 messages
