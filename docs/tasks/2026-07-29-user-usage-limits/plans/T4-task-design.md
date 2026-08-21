# T4: Per-list shopping-list item cap — Task Design

**Date:** 2026-08-19 (revised 2026-08-21 — see *Correction after first implementation*)

## Correction after first implementation

**This design was rewritten after T4 had already been implemented once.** The first version keyed
*both* the usage count and the configuration lookup on the list's UUID. That works for counting, but
it makes the cap unconfigurable in practice: `limit_config` overrides are written per subject, so
raising one user's item allowance meant inserting an override row **per list they own — and another
one every time they create a new list**. The default row (`subject IS NULL`) covers new lists, but
nothing else does. An operator has no way to say "this user gets 200 items per list".

The fix splits the two roles the subject was playing:

| | First implementation | This design |
|---|---|---|
| Usage counted against | the list's UUID | the list's UUID — **unchanged** |
| Configuration resolved against | the list's UUID | **the list owner's email** |

So the requirement is preserved exactly — `requirements.md` > Edge cases > *Per-list vs per-user
counting* still holds, each list is still counted independently — while the cap **value** now lives on
the owner, alongside their `RECIPE`, `SHOPPING_LIST` and `MEAL_PLAN` limits. One override row raises
every list that user owns, present and future.

The cost is that a per-*list* override is no longer expressible: "this one list gets 500 items" has no
representation. That was never asked for, and the requirements name the user as the unit of
configuration throughout.

The first implementation has been reverted; this design is written against `main` as it stands after
T3, not as a diff on top of the reverted work. Two further changes are folded in:

- `LimitsFacade` gains **two-subject overloads** of `reserve` and `release`. `limits` resolves
  configuration internally, so a caller that wants the two keys to differ has to hand the module both.
- The planned widget test for `shopping_list_detail_screen.dart` is **dropped** at the user's
  direction. See *Testing* for what that leaves uncovered.

## Summary

`shoppinglists` becomes a second consumer of the `limits` module, this time counting against the
**list**: `createItem` reserves one `SHOPPING_LIST_ITEM` unit against the list's UUID and `deleteItem`
releases one, so each list is counted independently and sharing an item never touches a user's own
records. The cap that count is measured against is resolved from the **list's owner**, so it is
configured exactly like every other limit. `limits` grows two-subject overloads of `reserve` and
`release` to express that split, plus a third facade method, `clear`, because a deleted list's usage
subject ceases to exist and its row has nothing left to refund. On the client, a refused create is
classified as a permanent discard — the outbox keeps draining rather than jamming on an entry that no
amount of waiting can resolve — and the resulting rejections collapse into a single toast instead of a
queue of them.

## Components and responsibilities

### Modified — `backend/src/main/java/xyz/stasiak/recipai/limits/`

- **`LimitsFacade`** (MODIFY) — gains three methods, all behind the same `recipai.limits.enabled`
  kill-switch as the existing pair:
  - `reserve(String configSubject, String usageSubject, String resource)` — the two-subject form.
  - `release(String configSubject, String usageSubject, String resource)` — likewise; `release` needs
    the config subject because whether a unit is refundable depends on the resolved `kind`.
  - `clear(String subject, String resource)` — never throws, takes **one** subject: it resolves no
    configuration at all (see *Decisions made*), so there is no second key to give it.

  The existing two-argument `reserve`/`release` stay and delegate with both keys equal, so the five
  current call sites in `recipes`, `recipes.collections`, `shoppinglists`, `planning` and `extraction`
  are untouched. No new public *type* is introduced, so `LimitsModuleArchitectureTest`'s
  shared-public-types list needs no change.
- **`LimitService`** (MODIFY) — `reserve` and `release` take the two subjects; `clear` is new.
  `currentUsage` is untouched (a read needs no configuration). The delegation for the single-subject
  case lives in the facade, so `LimitService` carries only the two-subject form and there is exactly
  one copy of each algorithm.
- **`LimitUsageRepository`** (MODIFY) — gains the native delete statement backing `clear`, keeping
  `limit_usage` written only through native SQL. `reserve` and `release` are unchanged: they already
  take the usage key as a plain `:subject` parameter and never see configuration.
- **`LimitConfigRepository`** (UNCHANGED) — `resolve(resource, subject)` already does the right thing;
  it is simply called with the owner's email instead of the list's UUID.

### Modified — migrations, `backend/src/main/resources/db/migration/`

- **`V18__shopping_list_item_limit_config.sql`** (CREATE) — seeds the one default `limit_config` row.
  Versioned and one-shot, exactly as `V16` and `V17`. `V17` is T3's meal-plan seed and has merged, so
  `V18` is the next free number.
- **`R__recompute_limit_usage.sql`** (MODIFY) — a fifth block rebuilding `SHOPPING_LIST_ITEM` usage
  from `shopping_list_items`, grouped by `shopping_list_id`. This is the first block whose usage
  subject is not an email, and the first whose configuration lookup and grouping key differ — it joins
  `shopping_list_permission` to reach the owner for the `FLOW` guard.

### Modified — `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/`

- **`ShoppingListService`** (MODIFY) — owns `SHOPPING_LIST_ITEM_RESOURCE`. Three call sites, each
  needing the list's owner:
  - `createItem` — reserves `(ownerEmail, listId)`, **after** `requireEditorPermission`, before the write.
  - `deleteItem` — releases `(ownerEmail, listId)`, after the delete has succeeded.
  - `deleteById` — clears the list's item usage row alongside the existing owner-scoped release.

  A private `requireOwnerEmail(UUID listId)` helper wraps the lookup. `deleteById` already loads the
  caller's permission and checks `hasOwnerRights()`, so there the caller *is* the owner and no extra
  query is needed — but `clear` takes no config subject anyway, so it needs neither.

  `ShoppingListController`, the DTOs and `ShoppingListsExceptionHandler` are untouched:
  `LimitExceededException` is not handled there and falls through to `LimitsExceptionHandler`, which
  renders the shared 429. `updateItem` is untouched — an edit holds no new unit.
- **`ShoppingListPermissionRepository`** (MODIFY) — gains
  `Optional<String> findOwnerEmailByShoppingListId(UUID)`. A targeted projection rather than filtering
  `findAllByShoppingListId` in Java: the existing method loads every collaborator's row to find one
  email, on a path that runs per item created.

### Modified — `mobile/lib/features/shopping_list/`

Unchanged from the first design — the client sees the same 429 with the same `resource`, and per-list
counting means "this list is full" is still literally true.

- **`shopping_list_item_repository.dart`** (MODIFY) — `createItem` maps **429** to
  `ItemDiscardedException(DiscardReason.limitReached)`, a new reason on the existing enum. `updateItem`
  and `deleteItem` are left alone: neither consumes budget, so a 429 from them would be a server bug
  and belongs in the transient `default:` branch, not in a discard.
- **`shopping_list_sync_service.dart`** (MODIFY) — `RejectionOutcome` gains `limitReached`; the
  existing `on ItemDiscardedException` branch maps the new reason through to it. Drain semantics,
  locking and the single-flight guard are **unchanged**.
- **`shopping_list_detail_screen.dart`** (MODIFY) — `_showRejectionToast` routes `limitReached` to a
  suppressed toast: one SnackBar with fixed copy, raised only when one is not already on screen, so a
  burst of refusals produces one bar rather than a queue of them. The other three outcomes keep firing
  immediately. No buffer and no timer — the guard is the `ScaffoldFeatureController` that
  `showSnackBar` returns, cleared when its `closed` future completes.

### Modified — documentation

- **`docs/tasks/2026-07-29-user-usage-limits/HLD.md`** — *Shopping-list items* currently says "The item
  cap is keyed by the list, not by the user". Amended to state that usage is counted per list while the
  cap value is configured against the list's owner.
- **`docs/tasks/2026-07-29-user-usage-limits/tasks.md`** — T4's first scope bullet says "with the
  list's identity as the subject rather than the user's". Amended the same way.
  `requirements.md` needs **no** change: per-list counting survives intact.
- **`docs/backend/modules/limits/codebase_structure.md`** — the two-subject overloads and `clear` in
  the file tree and behaviour list; the config-subject/usage-subject distinction stated once, here,
  since it is a property of the module rather than of `shoppinglists`.
- **`docs/backend/modules/limits/db.md`** — the seeded default row and the item block of the recompute,
  including that its usage subject is a list UUID while its configuration is read from the owner.
- **`docs/backend/modules/shopping-lists/api.md`** — the item cap on `POST /items`, its 429 body, the
  release on item delete, and that the count is per list while the value comes from the owner.
- **`docs/backend/modules/shopping-lists/codebase_structure.md`** — the second resource key on
  `ShoppingListService` and the new permission-repository query.
- **`docs/mobile/modules/shopping_list/codebase_structure.md`** — the new discard reason and rejection
  outcome.
- **`docs/project/architecture.md`** — the item cap in the limits consumer list.

## Interfaces and method signatures

### Crossing the module boundary

```java
public class LimitsFacade {
    // existing — configuration and usage share one subject
    public void reserve(String subject, String resource);            // throws LimitExceededException
    public void release(String subject, String resource);            // never throws
    public Optional<LimitUsageDetails> currentUsage(String subject, String resource);

    // new — configuration resolved against one key, usage counted against another
    public void reserve(String configSubject, String usageSubject, String resource);
    public void release(String configSubject, String usageSubject, String resource);

    // new — the usage subject has ceased to exist
    public void clear(String subject, String resource);              // never throws
}
```

The two-argument forms delegate: `reserve(s, r)` is `reserve(s, s, r)`. Every existing caller keeps
its current shape, and the split is visible only where it is real.

`clear` says "this subject no longer exists"; `release` says "this subject holds one fewer". They are
different statements and only the caller knows which is true, so `clear` is not expressible as a loop
over `release`.

### Internal to `limits`

```java
class LimitService {
    @Transactional void reserve(String configSubject, String usageSubject, String resource);
    @Transactional void release(String configSubject, String usageSubject, String resource);
    @Transactional void clear(String subject, String resource);
    @Transactional(readOnly = true) Optional<LimitUsageDetails> currentUsage(String subject, String resource);
}

interface LimitUsageRepository extends JpaRepository<LimitUsage, LimitUsageId> {
    @Modifying
    int clear(String resource, String subject);   // native; rows affected is diagnostic only
}
```

`LimitService.reserve` changes in exactly two places: `limitConfigRepository.resolve` takes
`configSubject`, and the `limitUsageRepository.reserve` call plus the follow-up
`findById(new LimitUsageId(resource, usageSubject))` take `usageSubject`. The refusal that
`LimitExceededException` carries is built from the config subject's `maxValue` and the usage
subject's `used` — which is the whole point. `release` changes the same way.

### Resource key and subject

```java
class ShoppingListService {
    static final String SHOPPING_LIST_RESOURCE      = "SHOPPING_LIST";        // existing, owner-keyed
    static final String SHOPPING_LIST_ITEM_RESOURCE = "SHOPPING_LIST_ITEM";   // new, owner-configured, list-counted
}

interface ShoppingListPermissionRepository extends JpaRepository<ShoppingListPermission, ShoppingListPermissionId> {
    // new — JPQL projection of the single OWNER row's email
    Optional<String> findOwnerEmailByShoppingListId(UUID shoppingListId);
}
```

The usage subject is `listId.toString()` — the bare UUID, unprefixed. `limit_usage` is keyed on
`(resource, subject)`, so a list UUID and an email can never collide even though both are opaque
`VARCHAR(255)` values in the same column.

### Configuration seed

```sql
-- V18__shopping_list_item_limit_config.sql
INSERT INTO limit_config (id, resource, subject, kind, max_value, period)
VALUES (gen_random_uuid(), 'SHOPPING_LIST_ITEM', NULL, 'STOCK', 50, NULL);
```

Identical to the first design. `subject IS NULL` is the default row; a per-user override is now an
ordinary `INSERT ... VALUES (..., 'SHOPPING_LIST_ITEM', 'alice@example.com', 'STOCK', 200, NULL)`,
which is the change this revision exists to make possible.

### Mobile

```dart
enum DiscardReason { gone, rejected, limitReached }                // limitReached is new
enum RejectionOutcome { conflict, gone, rejected, limitReached }   // limitReached is new
```

Both are additive: every existing branch keeps its meaning, and the compiler's exhaustiveness checking
on the `switch` expressions in `_pushHeadEntry` and `_showRejectionToast` finds every site that must
handle the new value.

## Data flow

**Item creation, granted.**

1. `ShoppingListController.createItem` extracts `jwt.getClaimAsString("email")` and calls
   `ShoppingListService.createItem`, already `@Transactional`.
2. `requireEditorPermission(listId, userEmail)` runs **first** — a caller without editor rights gets
   403 (or 404 for an absent list) and consumes nothing.
3. `requireOwnerEmail(listId)` resolves the list's owner. For a list the caller owns this returns the
   caller; for a shared list it returns whoever owns it.
4. `limitsFacade.reserve(ownerEmail, listId.toString(), SHOPPING_LIST_ITEM_RESOURCE)` resolves the
   owner's cap and runs the conditional upsert against the **list's** row, joining the caller's
   transaction.
5. The item row is written and returned as a `ShoppingListItemDto` with version 0.

**Item creation, refused.** Step 4's upsert affects zero rows, `LimitService` reads the list's standing
and throws `LimitExceededException`; `LimitsExceptionHandler` renders the shared 429. `kind` is
`STOCK`, so neither `retryAfterSeconds` nor the `Retry-After` header is present. Nothing is written,
and the `resource` field reads `SHOPPING_LIST_ITEM` — distinguishable by the client from the
`SHOPPING_LIST` refusal T2 introduced on the same module. The `limit` in the body is the *owner's*
configured maximum and the `used` is *this list's* count, which is exactly the pair a message like
"this list holds 50 of 50 items" needs.

**Item deletion.** `deleteItem` already requires editor rights and gates on `baseVersion`. Only the
path that actually deletes releases: a stale `baseVersion` throws `ItemVersionConflictException` (412)
with the item still present, and a missing item throws `ItemNotFoundException` (404) having deleted
nothing. `limitsFacade.release(ownerEmail, listId.toString(), SHOPPING_LIST_ITEM_RESOURCE)` is the
last statement, after the flush, inside the same transaction.

**List deletion.** `deleteById` deletes the permission rows and the list; the items go with it by
`ON DELETE CASCADE` on `shopping_list_items.shopping_list_id`. It then calls
`release(userEmail, SHOPPING_LIST)` — unchanged from T2 — and
`clear(listId.toString(), SHOPPING_LIST_ITEM)`, which drops the list's item usage row rather than
leaving a row for a subject that no longer exists. Note the ordering constraint: `clear` needs no
configuration and therefore no owner lookup, which matters because `deleteAllByShoppingListId` has
already removed the OWNER row by that point.

**Mobile, offline add refused.**

1. The user adds items while offline. `store.applyCreate` writes each local row and queues a create
   entry; the items are visible immediately.
2. On reconnect the drain pushes the head entry. The server refuses with 429.
3. `ShoppingListItemRepository.createItem` throws `ItemDiscardedException(limitReached)`.
4. `_pushHeadEntry`'s existing discard branch calls `store.discardItem`, which hard-removes the local
   row and clears every queued entry for that item, emits a `RejectionEvent` with `limitReached`, and
   returns `PushResult.pushed` — **so the queue keeps draining** rather than stalling on an entry that
   will never succeed.
5. If the detail screen for that list is open it raises one toast naming the cap, and stays silent for
   the rest of the burst while that toast is up; the item disappears from the list as it does.

**Mobile, undo after a delete.** `applyRestore` re-creates a deleted item as a fresh row, so the undo of
a delete (or of "Delete All Checked") queues N creates. Each reserves again — correct, since the
original deletes released. If someone else filled the freed slots in between, the restore is refused
and the item vanishes a second time with the same toast. Accepted: the alternative is holding budget
open for an undo the user may never take.

**Rollout.** `V18` seeds the default; the modified `R__recompute_limit_usage.sql` runs after it in the
same Flyway execution and rebuilds `SHOPPING_LIST_ITEM` usage per existing list, so no list starts at
zero used with items already on it.

## Pseudo-code

The two-subject reserve — the existing method with the one key split in two:

```
reserve(configSubject, usageSubject, resource):
    config = configRepo.resolve(resource, configSubject)          # <- owner's email
             or throw LimitConfigurationMissingException(resource)

    now    = clock.instant()
    cutoff = config.period == null ? EPOCH : config.period.cutoffFrom(now)

    granted = usageRepo.reserve(resource, usageSubject,           # <- list UUID
                                now, cutoff, config.maxValue)
    if granted == 1: return

    usage = usageRepo.findById(LimitUsageId(resource, usageSubject))
    ...                                                           # unchanged from here down
    throw LimitExceededException(resource, config.kind, config.maxValue, used, retryAfterSeconds)
```

`release` splits identically: `resolve(resource, configSubject)` for the `FLOW` short-circuit,
`usageRepo.release(resource, usageSubject)` for the decrement.

The clear path:

```
clear(subject, resource):
    if not limitsProperties.enabled: return       # kill-switch, as reserve and release

    usageRepo.clear(resource, subject)            # 0 rows affected is normal, not an error
```

```sql
DELETE FROM limit_usage WHERE resource = :resource AND subject = :subject
```

No configuration is resolved and no `FLOW` branch exists — see *Decisions made*.

The owner lookup in `shoppinglists`:

```
requireOwnerEmail(listId):
    return permissionRepository.findOwnerEmailByShoppingListId(listId)
           or throw ShoppingListNotFoundException(listId)
```

Unreachable in practice: `create` writes the OWNER row in the same transaction as the list, and
`deleteById` removes list and permissions together, so a list with items but no owner cannot exist.
Throwing rather than falling back to the default keeps a broken invariant loud instead of silently
handing that list the default cap.

The recompute block. It differs from the four existing blocks in three ways: the count comes from the
resource table itself rather than a permission table, the usage subject needs an explicit cast because
`shopping_list_id` is a `UUID` while `limit_usage.subject` is `VARCHAR(255)`, and the `FLOW` guard has
to reach the owner because that is where the configuration now lives:

```sql
DELETE FROM limit_usage u
 WHERE u.resource = 'SHOPPING_LIST_ITEM'
   AND COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource
               AND c.subject = (SELECT p.email FROM shopping_list_permission p
                                 WHERE p.shopping_list_id::text = u.subject
                                   AND p.role = 'OWNER')),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = u.resource AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW';

INSERT INTO limit_usage (resource, subject, used, period_start)
SELECT 'SHOPPING_LIST_ITEM', i.shopping_list_id::text, COUNT(*), now()
  FROM shopping_list_items i
  JOIN shopping_list_permission p
    ON p.shopping_list_id = i.shopping_list_id
   AND p.role = 'OWNER'
 WHERE COALESCE(
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'SHOPPING_LIST_ITEM' AND c.subject = p.email),
           (SELECT c.kind FROM limit_config c
             WHERE c.resource = 'SHOPPING_LIST_ITEM' AND c.subject IS NULL)
       ) IS DISTINCT FROM 'FLOW'
 GROUP BY i.shopping_list_id
    ON CONFLICT (resource, subject) DO NOTHING;
```

The join is one-to-one — `shopping_list_permission` has at most one `OWNER` row per list — so it does
not inflate `COUNT(*)`. It is an inner join, so a list with no owner contributes no usage row at all:
the same invariant `requireOwnerEmail` asserts, failing safe here rather than loudly, since a
repeatable migration is the wrong place to abort a deploy. The delete side's correlated subquery does
the same lookup in reverse, from an existing usage row back to its list's owner.

A list with no items produces no row, which reads as a standing of zero — the same as every other
resource. The `FLOW` guard is carried over from the existing blocks for consistency, though a
`SHOPPING_LIST_ITEM` resource configured `FLOW` ("50 items ever added, per list") is a curiosity
rather than a real case.

The refusal toast in the detail screen — the only new client-side logic:

```
_showRejectionToast(event):
    if event.outcome != limitReached:
        showSnackBar(copy(event))            # unchanged for conflict / gone / rejected
        return

    if _limitBar != null: return             # one is already saying it
    _limitBar = showSnackBar("This list is full - items weren't added")
    _limitBar.closed.then:                   # timed out, swiped away, or hidden by the undo bar
        if still ours: _limitBar = null      # re-armed for the next burst
```

The controller `showSnackBar` returns is the whole mechanism: it is non-null exactly while a refusal
toast is live. Nothing is added to `dispose()` — the callback is guarded on the controller still being
ours, so a completion after unmount only clears a field.

## Testing

- **`LimitsIntegrationTest`** (MODIFY) — against the synthetic `TEST_LIMIT_*` resources:
  - The two-subject `reserve`: configuration read from one subject, usage counted against another; an
    override on the config subject changes the ceiling for every usage subject that resolves through
    it; two usage subjects sharing one config subject still count independently.
  - The two-subject `release`: the `FLOW` short-circuit follows the *config* subject's kind, and the
    decrement lands on the *usage* subject's row.
  - The two-argument forms still behave exactly as before (the delegation is not a behaviour change).
  - `clear` semantics: clearing an existing row, clearing an absent row, clearing a `FLOW`-configured
    subject, and clearing with no configuration at all.
- **`ShoppingListIntegrationTest`** (MODIFY) — the existing `@Nested LimitsEnforced` class from T2
  gains the item-cap tests: refusal at the cap with the 429 body; **independence between two lists
  owned by the same user** (the case that proves per-list counting survived the change); release on
  item delete; reads and edits still working while the list is over cap; **a per-user override raising
  the cap on every list that user owns, including one created after the override was written** (the
  case this revision exists for); an EDITOR's add charging the list while resolving the *owner's* cap,
  not the editor's; the usage row being cleared when the list is deleted; and the recompute
  reproducing the per-list counts, including under an owner override configured `FLOW`. Its
  `@AfterEach` grows the matching `SHOPPING_LIST_ITEM` cleanup.
- **`shopping_list_sync_service_test.dart`** (MODIFY) — `FakeShoppingListItemRepository` gains a
  per-list `capacity`, so a drain naturally meets the cap partway through a queued run. New tests: a
  refused create is discarded and the queue keeps draining, a queued delete behind refused creates
  still lands and frees a slot for a later create, and the rejection event carries `limitReached`.
- **No widget test for `shopping_list_detail_screen.dart`** — dropped at the user's direction. This
  leaves `_showRejectionToast`'s suppression guard as the only new logic in the task with no automated
  coverage; it is verified by hand against the *How to verify* steps in `tasks.md` > T4. The screen has
  no widget tests today, so this is the status quo rather than a regression — but it does mean a future
  refactor of `_limitBar` has nothing to catch it.

## Decisions made

- **Configuration is resolved against the list's owner; usage is still counted against the list.** The
  correction this revision exists for. A cap that can only be raised per list, with a fresh row needed
  for every list a user creates, is not operable given the anti-requirement that limits are changed by
  editing the database directly — there is no admin surface to automate it. Keying configuration on
  the owner makes `SHOPPING_LIST_ITEM` configurable exactly like every other resource while leaving
  `requirements.md`'s per-list counting untouched.
- **The split is expressed as three-argument overloads on `LimitsFacade`, not a new subject type.**
  Chosen over a `LimitSubject` record. It keeps the module's public surface at its current five types,
  so `LimitsModuleArchitectureTest` needs no edit, and leaves all five existing call sites verbatim.
  The accepted cost is two adjacent `String` parameters that a caller could transpose without a
  compile error; the guard is the parameter names plus the integration test that asserts an owner
  override changes a *list's* ceiling, which fails loudly if the two are swapped.
- **The two-argument facade methods delegate rather than being replaced.** Making every caller pass
  both keys would have five call sites repeating the same value twice to say nothing, and would make
  the interesting case invisible among the boring ones.
- **`clear` keeps a single subject.** It is the one operation that reads no configuration, so there is
  no second key for it to take. This also removes an ordering hazard in `deleteById`, which has
  already deleted the OWNER permission row by the time it clears.
- **The usage subject is the raw list UUID string, unprefixed.** `(resource, subject)` is the primary
  key, so a `SHOPPING_LIST_ITEM` row for a list UUID cannot collide with a `RECIPE` row for an email. A
  prefix would buy readability in the operator's `SELECT` at the cost of a format the recompute's cast
  would also have to reproduce.
- **Reserve happens *after* the editor-permission check, departing from T2's "reserve first" rule.**
  T2's ordering is safe because its subject is the caller's own email. Here the count lands on the
  *list*, so reserving first would let any authenticated stranger burn another user's list budget by
  POSTing to a guessed list id, and would answer 429 where 403/404 is correct — leaking that the list
  exists. It is also now a hard ordering requirement rather than a preference: the owner lookup needs
  the list to exist. The reservation still shares the transaction, so a later failure still rolls it back.
- **A per-list override is no longer expressible, and that is accepted.** "This one list gets 500
  items" has no representation once configuration is keyed on the owner. It was never a requirement,
  and the fallback — raise the owner's limit — is strictly easier to operate.
- **The owner lookup is a dedicated projection query, not a filter over `findAllByShoppingListId`.**
  The existing method returns every collaborator's full permission row; this runs once per item
  created and needs one column of one row.
- **List deletion clears the item usage row through `LimitsFacade.clear`.** The list UUID is the usage
  subject, and when the list is gone that subject is gone; leaving the row would accumulate one dead
  record per deleted list forever, cleaned only by the next recompute run.
- **`clear` resolves no configuration and has no `FLOW` branch.** `release` needs the lookup because
  whether a unit is refundable depends on the kind. `clear` is not a refund — the subject has ceased to
  exist, and a `FLOW` window anchored to a deleted list is as meaningless as a `STOCK` count.
- **The `SHOPPING_LIST_ITEM` default stays 50, `STOCK`, no period.** Unaffected by the correction,
  since the count it bounds is still per list. ADR-0003 sizes the full-refresh sync design at ~30–40
  items per list, so 50 sits just above the shape the client was built for while still bounding a
  runaway list, and a meal-plan import of a week's ingredients fits without refusal.
- **Only `createItem` reserves; `updateItem` holds no new unit.** An edit, a check, and a reorder all go
  through `updateItem`, which changes an item the list already holds.
- **Release only on the path that actually deletes.** A 412 (`baseVersion` stale) leaves the item in
  place and a 404 deleted nothing, so neither returns a unit.
- **An EDITOR's add charges the list and resolves the owner's cap.** The requirements' "shared items
  count only against the owner" holds in both halves: the editor's own records are untouched, and the
  ceiling applied is the one the owner is configured for — an editor cannot bring a larger allowance
  with them into someone else's list.
- **429 on create is a permanent discard, classified alongside 400/403.** Settles
  `HLD.md` > Open questions > *Offline item refusals*. The cap is `STOCK`, so waiting resolves nothing;
  treating it as transient would retry five times and then jam the list's outbox behind the refused
  create, blocking every later edit to that list. Discarding reuses machinery that already exists and is
  already tested. The cost is that the user's typed item is lost and must be retyped once space is
  freed — accepted over an item that is silently never synced.
- **Drain semantics stay FIFO with no short-circuit on the first refusal.** A bulk import of N items
  into a full list therefore costs N refused round trips. This is deliberate: the outbox is mixed-kind,
  so a queued *delete* sitting behind the creates frees capacity, and a rule that discarded every
  remaining create on the first 429 would drop creates that would legitimately have succeeded. The
  wasted requests are cheap (one config resolve plus an upsert affecting zero rows) and bounded by
  import size.
- **A burst of refusals raises one toast, suppressed while it is visible; the other three outcomes are
  not.** Only `limitReached` arrives in bursts — ten items added offline to a full list produce ten
  events, and `ScaffoldMessenger` queues SnackBars rather than replacing them, so untreated that is
  ten bars marching past at four seconds each, burying the undo bar behind them. Conflict, gone and
  rejected stay immediate: they fire one at a time in practice and carry item-specific copy.
- **The refusal copy names neither the item nor a count.** A count needs a buffer, a debounce window
  and a bar whose content updates in place — and the window would have to be tuned against the drain's
  spacing, since it pushes one entry at a time and refusals therefore arrive an HTTP round trip apart
  rather than together. Fixed copy makes that question disappear: "is a bar up" needs no timing
  assumption. The cost is a message that cannot name the single refused item the way the other three
  outcomes do — accepted, since the user typed it moments ago and watched it vanish; what they lack is
  the reason, not the identity.
- **Nothing is ever removed from the SnackBar queue, and the guard is "while visible" rather than a
  cooldown.** `removeCurrentSnackBar` drops whatever is current, which need not be ours — a refusal
  toast raised while `_runDestructive`'s undo bar is up sits *behind* it — and
  `ScaffoldFeatureController.close` asserts the controller is first in the queue. Suppression needs
  neither: `_runDestructive`'s existing `hideCurrentSnackBar` completes our `closed` future and re-arms
  us, which is the right behaviour anyway. A burst outlasting the bar's own duration then raises a
  second bar after the first closes — bounded, and still true when it appears. The bar carries no
  action, so Flutter honours that duration and no dismissal timer is needed (the
  accessible-navigation bug behind `_undoTimer` only affects bars with an action).
- **`HLD.md` and `tasks.md` are amended; `requirements.md` is not.** The first two assert the item cap
  is "keyed by the list, not by the user", which is now only half the story. `requirements.md` says the
  count is per list and says nothing about where the value is configured, so it is already correct.
- **The silent-loss case on bulk import is documented and handed to T5, not fixed here.** See
  *Assumptions to verify* — it is a pre-existing gap that T4 makes far more likely to fire, and the
  honest fix needs the standing read path T5 builds.

## Assumptions to verify

- **Assumption:** every shopping list has exactly one `OWNER` permission row, for the whole time it has
  items. `create` writes the list and the OWNER row in one `@Transactional` method; `unshareShoppingList`
  explicitly refuses to remove an OWNER; `deleteById` removes all permissions and the list together;
  there is no ownership transfer. Read during design: nothing else writes `shopping_list_permission`.
  **If wrong:** `createItem` throws `ShoppingListNotFoundException` on a list that plainly exists, and
  the recompute's inner join silently omits that list. Both fail safe rather than mis-charging, but
  both are confusing.
- **Assumption:** the audit of destroying paths is complete — exactly two backend paths destroy a
  counted item, `ShoppingListService.deleteItem` (one item, version-gated) and
  `ShoppingListService.deleteById` (the whole list, via `ON DELETE CASCADE` on
  `shopping_list_items.shopping_list_id`, confirmed in `V5`). Read during design: nothing else in
  `shoppinglists` deletes items, no other module touches the table (`planning` only *generates* item
  DTOs and never writes them), unsharing removes a permission row and no items, and the client's bulk
  operations ("Delete All Checked") issue one ordinary `DELETE /items/{id}` per item through the outbox
  rather than any bulk endpoint.
  **If wrong:** a missed release leaves a list permanently poorer until the recompute is re-run — the
  failure mode `tasks.md` > Cross-task notes names as the design's principal cost.
- **Assumption:** splitting `LimitService.reserve`'s subject into two parameters changes no behaviour
  for the four existing consumers, because the facade passes the same value for both.
  **If wrong:** every existing limits integration test fails at once, which is the desired blast radius.
- **Assumption:** `shopping_list_id::text` renders a UUID in the same lowercase-hyphenated form that
  `UUID.toString()` produces in Java, so the recompute's usage subject matches what the reserve path
  writes. Postgres' canonical `uuid` output is exactly that form.
  **If wrong:** the seed writes rows under subjects no check ever resolves, every list starts at zero
  used, and the divergence is invisible until a list exceeds its cap. Guarded by the nested test that
  creates items over HTTP and asserts the recompute reproduces the count.
- **Assumption:** `V18` is free. T3 merged as `V17__meal_plan_limit_config.sql`, and T4 is now the only
  task in flight, so the number is no longer contingent as it was in the first design.
  **If wrong / when merging:** renumber to the next free version; two `V18`s make Flyway fail at startup.
- **Assumption:** a bulk import's refusals currently reach nobody. `shopping_list_review_widget.dart`
  picks the target list from a dialog, calls `importItems`, shows "Added N item(s) to shopping list"
  and pops the route — so the target list's detail screen is not open, `RejectionEvent` has no
  subscriber, and `_emit` drops it. The user is told 12 items were added and silently gets 5. This is
  **not** a hole T4 digs — the identical drop already happens today for `gone` and `rejected` — but
  "this list is full" is the first refusal a normal user will actually hit.
  **If wrong / follow-up:** T5 owns it. Once the standing read path exists, the import wizard should
  pre-flight the list's remaining capacity and report the shortfall *before* writing, instead of
  promising N and delivering fewer. Flagged explicitly rather than patched with a durable
  "items were dropped" marker that T5 would then remove.
- **Assumption:** adding a value to `DiscardReason` and `RejectionOutcome` breaks no other `switch`.
  Both are matched exhaustively in `_pushHeadEntry` and `_showRejectionToast` only; the analyzer will
  point at anything else.
  **If wrong:** a compile error, not a silent fallthrough.
- **Assumption:** `ShoppingListIntegrationTest`'s existing `@Nested LimitsEnforced` class can host the
  item tests without splitting — it already runs with `recipai.limits.enabled=true` and its own Postgres
  container, and the enclosing instance's `createItem`/`deleteItem` helpers are wired from the nested
  context (the T2 finding).
  **If wrong:** the item tests move to a second `@Nested` class, at roughly 4.6s of extra context
  startup.
- **Assumption:** no existing test in `ShoppingListIntegrationTest` creates more than 50 items on one
  list, so the shipped default cannot break the outer suite even though it runs with limits off.
  **If wrong:** only matters if the class-level disable is ever removed.
- **Assumption:** `FakeShoppingListItemRepository` can carry a per-list capacity without disturbing the
  existing tests, mirroring its three current fault flags.
  **If wrong:** the 429 tests use a dedicated fake rather than extending the shared one.

## Required reading for implementation planning

- `plans/T2-task-design.md` — the release and recompute mechanism this task extends, and the
  `@Nested`-class test shape its *Decisions made* established.
- `plans/T1-task-design.md` — the transaction, clock, kill-switch and refusal-contract behaviour the
  new overloads and `clear` must match.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitService.java` — the two methods whose subject
  parameter splits in two, and the shape `clear` mirrors.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitConfigRepository.java` — `resolve`'s
  override-then-default ordering, which is what makes an owner-keyed override work unchanged.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitUsageRepository.java` — the native-statement
  convention; `limit_usage` is never written through JPA.
- `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsModuleArchitectureTest.java` — the
  shared-public-types rule the overloads deliberately avoid touching.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — the three call
  sites, the version gate on `deleteItem`, and T2's existing owner-scoped reserve/release.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListPermissionRepository.java` — the
  positional-parameter `@Query` style the owner projection joins.
- `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the block structure and the
  `FLOW` guard to adapt.
- `backend/src/main/resources/db/migration/V4__shopping_list_permission.sql`,
  `V5__rename_list_id_and_update_position.sql` — the permission table's columns and the item table's
  `ON DELETE CASCADE`, both load-bearing for the recompute and the release audit.
- `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java` — the
  `LimitsEnforced` nested class being extended, including its teardown and config-seeding helpers.
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — the status-code
  classification the new 429 branch joins.
- `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — `_showRejectionToast`, and
  `_runDestructive`'s undo bar: the only other SnackBar on the screen and the reason nothing is
  removed from the queue.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — the discard branch in
  `_pushHeadEntry`, and why the drain must return `PushResult.pushed` rather than stalling.
- `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart` — the fake repository and
  the `push outcomes` group the new tests join.
- `docs/ADRs/0003-shopping-list-full-refresh-over-delta.md` — the ~30–40 item sizing behind the default.
- `docs/ADRs/0004-shopping-list-item-store-aggregate.md`, `0005-shopping-list-sync-test-seam.md` — the
  store/sync locking the client change must not disturb.
- `docs/ADRs/0006-shared-limits-module.md` — the opaque-subject rationale that makes a list a legal
  usage subject and keeps the two-subject split free of domain knowledge.
- `HLD.md` > Feature areas > *Shopping-list items*; `HLD.md` > Open questions > *Offline item refusals*
  — the behaviours in scope and the question settled here.
- `requirements.md` > Edge cases > *Per-list vs per-user counting* — the requirement this revision
  preserves rather than changes.
- `docs/backend/standards/module-structure.md`, `integration-tests.md`;
  `docs/mobile/standards/state-management.md` — facade, logging and test conventions.
