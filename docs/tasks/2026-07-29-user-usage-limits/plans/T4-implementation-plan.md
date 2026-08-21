# T4: Per-list shopping-list item cap — Implementation Plan

**Date:** 2026-08-21

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/backend/standards/integration-tests.md` > *Testing a Suite Whose Module Is Capped by `limits`*
  — the class-level disable + `@Nested` enable shape, the "seed your own `limit_config` override"
  rule, and the "teardown deletes through the API, never through `limit_usage`" rule. The item cases
  join an existing nested class built to exactly this shape.
- `docs/backend/standards/integration-tests.md` > *Reading Data the Test Has No Access To* — the item
  standing is read through `LimitsFacade.currentUsage(listId.toString(), …)`, never with SQL against
  `limit_usage`.
- `docs/backend/standards/module-structure.md` — the facade rule (`shoppinglists` reaches `limits`
  only through `LimitsFacade`), and the `@ControllerAdvice`-per-module rule that explains why
  `ShoppingListsExceptionHandler` needs no edit: it does not handle `LimitExceededException`, so the
  refusal falls through to `LimitsExceptionHandler`.
- `docs/backend/standards/java-patterns.md` — record DTOs and JPA entity conventions for the new
  repository projection.
- `docs/mobile/standards/state-management.md` — the store/sync locking and `dispose()` rules the
  client change must not disturb.
- `docs/mobile/standards/widget-testing.md` — read only to confirm what the dropped widget test would
  have looked like; no widget test is written (see *Test plan*).
- `docs/backend/modules/limits/codebase_structure.md`, `db.md` — the module surface and the seeded
  defaults / recompute, both of which this task extends.

**Design & ADRs**

- `plans/T4-task-design.md` in full, and in particular *Correction after first implementation* — the
  config-subject / usage-subject split is the whole point of the revision; do not collapse it back.
- `plans/T2-task-design.md` > *Decisions made* — the release-and-recompute mechanism this task
  extends, the versioned-config / repeatable-usage migration split, and the `FLOW` guard.
- `plans/T2-implementation-plan.md` > *Risks surfaced during planning* — the shared-Spring-context
  finding across the nested `LimitsEnforced` classes applies unchanged here.
- `plans/T1-task-design.md` — the transaction, `Clock`, kill-switch and refusal-contract behaviour the
  new overloads and `clear` must match.
- `docs/ADRs/0006-shared-limits-module.md` — the opaque-subject rationale that makes a list UUID a
  legal usage subject and keeps the two-subject split free of domain knowledge.
- `docs/ADRs/0003-shopping-list-full-refresh-over-delta.md` — the ~30–40 item sizing behind the
  default of 50.
- `docs/ADRs/0004-shopping-list-item-store-aggregate.md`, `0005-shopping-list-sync-test-seam.md` —
  the store/sync locking and the test seam the client change works within.
- `HLD.md` > Feature areas > *Shopping-list items*; > Open questions > *Offline item refusals*.
- `requirements.md` > Edge cases > *Per-list vs per-user counting* — the requirement preserved
  unchanged by this revision.

**Code to mirror**

- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitService.java:23-72` — the two methods whose
  subject parameter splits in two (`reserve` at 24, `release` at 53) and the shape `clear` mirrors.
  Note `currentUsage` is untouched.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsFacade.java:25-45` — the kill-switch guard +
  `log.debug` + delegate shape every new facade method copies.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitUsageRepository.java:23-31` — the `release`
  native statement; `clear` copies its `@Modifying` + `{h-schema}` + `@Param` form.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitConfigRepository.java` — `resolve`'s
  override-then-default ordering, unchanged; it is simply called with the owner's email.
- `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsModuleArchitectureTest.java:29-40` — the
  shared-public-types list the overloads deliberately avoid touching (`LimitsFacade` is already on it).
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java:23,38,68-77,
  106-129,144-166` — the resource-key constant, T2's owner-scoped reserve/release, `createItem`,
  `deleteItem`'s version gate, and `deleteById`.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListPermissionRepository.java` —
  the positional-parameter (`?1`) `@Query` style the owner projection joins.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipePermissionRepository.java:13-14` — the
  single-column JPQL projection returning `Optional<…>`, the shape `findOwnerEmailByShoppingListId`
  copies. For the enum comparison, the string-literal form `… AND p.role = 'OWNER'` has precedent in
  the pre-T3 `MealPlanPermissionRepository.countOwnedByEmail` (`git show cb5b0d9~1:…`).
- `backend/src/main/resources/db/migration/V17__meal_plan_limit_config.sql` — the shape `V18` copies.
- `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql:57-79` — the `SHOPPING_LIST`
  block; the new block is that shape with the count taken from `shopping_list_items` and the owner
  reached by join.
- `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java:1018-1084`
  — the `LimitsEnforced` nested class being extended: `SUBJECT`, `@BeforeEach seedOverride`,
  `@AfterEach tearDown`, `usedFor`, `seedConfigOverride`.
- `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsIntegrationTest.java:404-441` — the
  `newResource()` / `newSubject()` / `seedConfig` / `seedUsage` helpers the new cases reuse.
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart:95-106,253-267` — the status
  ladder the `429` branch joins, and the `DiscardReason` enum with its per-value doc comments.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart:21,289-301` — `RejectionOutcome`
  and the `on ItemDiscardedException` branch. **The mapping there is a ternary, not a switch** — see
  *Risks surfaced during planning*.
- `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart:133-186` — `_runDestructive`'s
  undo bar (the only other SnackBar on the screen, and why nothing is removed from the queue) and
  `_showRejectionToast`'s `switch` expression.
- `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart:54-153,365-437` — the fake
  repository with its three `bool` fault flags, and the `push outcomes` group the new tests join.

## File inventory

**Backend — main**

- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsFacade.java` — three-argument
  `reserve`/`release` overloads plus `clear`; two-argument forms delegate.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitService.java` — `reserve` and
  `release` take `(configSubject, usageSubject, resource)`; `clear` added.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitUsageRepository.java` — native
  `clear` delete statement.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — the
  `SHOPPING_LIST_ITEM_RESOURCE` key, `requireOwnerEmail`, reserve in `createItem`, release in
  `deleteItem`, clear in `deleteById`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListPermissionRepository.java`
  — `findOwnerEmailByShoppingListId` projection.

**Backend — resources**

- **CREATE** `backend/src/main/resources/db/migration/V18__shopping_list_item_limit_config.sql` — one
  default `SHOPPING_LIST_ITEM` `STOCK` row, `max_value` 50.
- **MODIFY** `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — fifth block
  rebuilding per-list item usage from `shopping_list_items`.

**Backend — tests**

- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsIntegrationTest.java` — cases for
  the two-subject `reserve`/`release` and for `clear`.
- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java`
  — item-cap cases inside `LimitsEnforced`, plus the item override seed and the matching teardown.

**Mobile**

- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` —
  `DiscardReason.limitReached` and the `429` branch on `createItem`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` —
  `RejectionOutcome.limitReached`, and the discard mapping converted from a ternary to an exhaustive
  `switch`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — the `_limitBar`
  field and the suppressed `limitReached` toast in `_showRejectionToast`.
- **MODIFY** `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart` — per-list
  `capacity` on the fake repository and three new `push outcomes` cases.

**Documentation**

_None._ `T4-task-design.md` > *Modified — documentation* names eight files (`HLD.md`,
`tasks.md`, the two `limits` module docs, the two `shopping-lists` docs, the mobile
`shopping_list` structure doc and `architecture.md`). They are deliberately out of this plan's
scope and are left to the separate docs-updating step. `requirements.md` needs no change either
way — it says the count is per list and says nothing about where the value is configured.

## Step-by-step plan

### 1. `limits`: the two-subject split and `clear`

`LimitUsageRepository` — add below `release`, copying its form:

```java
@Modifying
@Query(value = """
        DELETE FROM {h-schema}limit_usage
         WHERE resource = :resource AND subject = :subject
        """, nativeQuery = true)
int clear(@Param("resource") String resource, @Param("subject") String subject);
```

`LimitService` — rename `reserve`'s and `release`'s single `subject` parameter into
`(String configSubject, String usageSubject, String resource)`, keeping `resource` last. In `reserve`,
`limitConfigRepository.resolve` takes `configSubject`; `limitUsageRepository.reserve(...)`, the
follow-up `findById(new LimitUsageId(resource, usageSubject))` and the `log.warn` take `usageSubject`.
In `release`, `resolve` takes `configSubject` and `limitUsageRepository.release` takes `usageSubject`.
Nothing else in either method changes — the `Instant`/cutoff arithmetic, the `FLOW` short-circuit, the
`retryAfterSeconds` derivation and the exception construction are untouched. Add:

```java
@Transactional
void clear(String subject, String resource) {
    int cleared = limitUsageRepository.clear(resource, subject);
    log.debug("Cleared usage of resource: {} for subject: {} (rows: {})", resource, subject, cleared);
}
```

No configuration is resolved and there is no `FLOW` branch — a subject that has ceased to exist has
nothing to refund. `currentUsage` is not touched.

`LimitsFacade` — add the three public methods, each with the same kill-switch guard and `log.debug`
shape as the existing pair, and make the two-argument forms delegate:

```java
public void reserve(String subject, String resource) {
    reserve(subject, subject, resource);
}

public void reserve(String configSubject, String usageSubject, String resource) {
    if (!limitsProperties.enabled()) { … return; }
    log.debug("Reserving resource: {} for subject: {} (configured by: {})", resource, usageSubject, configSubject);
    limitService.reserve(configSubject, usageSubject, resource);
}
```

`release` mirrors it; `clear(String subject, String resource)` guards on the kill-switch the same way
and calls `limitService.clear(subject, resource)`. No new public *type* is introduced, so
`LimitsModuleArchitectureTest` needs no edit.

- Files: `backend/src/main/java/xyz/stasiak/recipai/limits/LimitUsageRepository.java`,
  `LimitService.java`, `LimitsFacade.java`
- Verify: `./mvnw -q compile` from `backend/` succeeds (the five existing call sites in `recipes`,
  `recipes.collections`, `shoppinglists`, `planning`, `extraction` are untouched), then
  `./mvnw test -Dtest=LimitsIntegrationTest+LimitsModuleArchitectureTest` — all existing cases pass
  unchanged, which is the evidence the delegation is behaviour-preserving.

### 2. `LimitsIntegrationTest`: cover the new surface

Add cases against synthetic `TEST_LIMIT_*` resources, reusing `newResource()`, `newSubject()`,
`seedConfig`, `seedUsage` and `updateMaxValue` verbatim. The existing `@AfterEach` already sweeps
every `TEST_LIMIT_%` row, so teardown needs no change. Cases are listed in *Test plan*.

- Files: `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsIntegrationTest.java`
- Verify: `./mvnw test -Dtest=LimitsIntegrationTest` — old and new cases green.

### 3. Migrations

Create `V18__shopping_list_item_limit_config.sql`, copying `V17`'s formatting:

```sql
INSERT INTO limit_config (id, resource, subject, kind, max_value, period)
VALUES (gen_random_uuid(), 'SHOPPING_LIST_ITEM', NULL, 'STOCK', 50, NULL);
```

Append the fifth block to `R__recompute_limit_usage.sql` under a `-- SHOPPING_LIST_ITEM` comment,
exactly as written in `T4-task-design.md` > *Pseudo-code*. Three things differ from the four existing
blocks and must survive review: the count comes from `shopping_list_items` rather than a permission
table; `i.shopping_list_id::text` casts the UUID to match `limit_usage.subject`; and the `FLOW` guard
reaches the owner through `shopping_list_permission` because that is where configuration now lives —
on the insert side by join, on the delete side by a correlated subquery
(`p.shopping_list_id::text = u.subject AND p.role = 'OWNER'`). The join is inner and one-to-one, so it
neither inflates `COUNT(*)` nor emits a row for an ownerless list.

Also update the file's header comment: it currently says "for the owner-scoped resources … from their
owning module's permission table", which the new block contradicts. Add a sentence that one resource
is counted per list while its configuration is resolved from the list's owner.

- Files: `backend/src/main/resources/db/migration/V18__shopping_list_item_limit_config.sql`,
  `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql`
- Verify: `./mvnw test -Dtest=RecipAiApplicationTests` from `backend/` starts the context against a
  fresh container and applies both. Against that database,
  `SELECT resource, kind, max_value FROM recipai.limit_config WHERE subject IS NULL;` returns five
  rows including `SHOPPING_LIST_ITEM | STOCK | 50`.

### 4. `shoppinglists`: wire item creation and deletion onto the cap

`ShoppingListPermissionRepository` — add:

```java
@Query("SELECT slp.id.email FROM ShoppingListPermission slp WHERE slp.id.shoppingListId = ?1 AND slp.role = 'OWNER'")
Optional<String> findOwnerEmailByShoppingListId(UUID shoppingListId);
```

Add the `java.util.Optional` import. The string-literal enum comparison has precedent in this codebase
(see *Required reading*); if Hibernate rejects it, fall back to the fully-qualified
`xyz.stasiak.recipai.shoppinglists.UserRole.OWNER` literal rather than filtering in Java.

`ShoppingListService`:

- Add `static final String SHOPPING_LIST_ITEM_RESOURCE = "SHOPPING_LIST_ITEM";` beside the existing
  key.
- Add the private helper next to `requireEditorPermission`:

  ```java
  private String requireOwnerEmail(UUID listId) {
      return permissionRepository.findOwnerEmailByShoppingListId(listId)
              .orElseThrow(() -> new ShoppingListNotFoundException(listId));
  }
  ```

- `createItem` — **after** `requireEditorPermission(listId, userEmail)` and before constructing the
  entity:

  ```java
  String ownerEmail = requireOwnerEmail(listId);
  limitsFacade.reserve(ownerEmail, listId.toString(), SHOPPING_LIST_ITEM_RESOURCE);
  ```

  The ordering is load-bearing, not stylistic: reserving first would let any authenticated caller burn
  another user's list budget by POSTing to a guessed id, and would answer 429 where 403/404 is
  correct. It departs from T2's "reserve first" rule for that reason (`T4-task-design.md` >
  *Decisions made*). The reservation joins the method's existing transaction, so a later failure still
  rolls it back.

- `deleteItem` — as the **last** statement of the method, after the `try`/`catch` that deletes and
  flushes, so only the path that actually deleted refunds (a 412 or 404 returns nothing):

  ```java
  limitsFacade.release(requireOwnerEmail(listId), listId.toString(), SHOPPING_LIST_ITEM_RESOURCE);
  ```

- `deleteById` — after the existing `limitsFacade.release(userEmail, SHOPPING_LIST_RESOURCE);`:

  ```java
  limitsFacade.clear(id.toString(), SHOPPING_LIST_ITEM_RESOURCE);
  ```

  `clear` takes no config subject, which is what makes this safe: `deleteAllByShoppingListId` has
  already removed the OWNER row by this point, so an owner lookup here would fail.

`updateItem` is untouched (an edit holds no new unit), and so are `ShoppingListController`, the DTOs
and `ShoppingListsExceptionHandler` — `LimitExceededException` is not handled in `shoppinglists` and
falls through to `LimitsExceptionHandler`'s shared 429.

- Files: `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListPermissionRepository.java`,
  `ShoppingListService.java`
- Verify: `./mvnw test -Dtest=ShoppingListIntegrationTest` — the outer suite runs with
  `recipai.limits.enabled=false`, so all ~55 existing cases must pass untouched; the existing nested
  `shouldNotMoveListStandingWhenAddingAndDeletingItems` proves the item traffic still leaves the
  `SHOPPING_LIST` standing alone.

### 5. `ShoppingListIntegrationTest.LimitsEnforced`: the item-cap cases

Extend the existing nested class rather than adding a second one (it already runs with
`recipai.limits.enabled=true` and the enclosing instance's helpers are wired from the nested context).

- `@BeforeEach seedOverride` — seed a `SHOPPING_LIST_ITEM` override for `SUBJECT` alongside the
  existing `SHOPPING_LIST` one. Generalise `seedConfigOverride(subject, maxValue)` into
  `seedConfigOverride(resource, subject, maxValue)` and call it twice: `SHOPPING_LIST` at 2 (as today)
  and `SHOPPING_LIST_ITEM` at **3**. Seeding an override rather than leaning on the shipped 50 is the
  standard's rule and keeps the cases fast; 3 is small enough to hit in three `POST`s and large enough
  to leave a middle case.
- `@AfterEach tearDown` — the HTTP list deletions already fire `clear`, so item usage rows disappear
  with their lists. Add, after the existing statements: delete `limit_config` rows for
  `SHOPPING_LIST_ITEM` with a non-null subject, and delete any `limit_usage` row for
  `SHOPPING_LIST_ITEM` whose subject is not a surviving list (fabricated rows only — never a blanket
  sweep, which would erase the evidence a release fired). Keep the closing
  `assertThat(usedFor(SUBJECT)).isZero()` and add an assertion that the deleted lists' item usage is
  gone.
- Add a helper reading item standing through the facade:
  `limitsFacade.currentUsage(listId.toString(), ShoppingListService.SHOPPING_LIST_ITEM_RESOURCE)`.
  `SHOPPING_LIST_ITEM_RESOURCE` is package-private and the test is in the same package, so no
  visibility widening is needed.

Two constraints inherited from T2's finding, restated because a fifth nested context makes them
easier to trip: this class shares one Spring context and one Postgres container with the other nested
`LimitsEnforced` classes, so every assertion must be scoped to a specific `(resource, subject)` pair
and no case may create a recipe. And `SHOPPING_LIST` stays capped at 2 for `SUBJECT`, so a case
needing two lists uses exactly two.

- Files: `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java`
- Verify: `./mvnw test -Dtest=ShoppingListIntegrationTest` — outer and nested green. Then
  `./mvnw test` — the full backend suite passes and the five nested classes do not interfere.

### 6. Mobile: the new discard reason, outcome, and the toast

These three files change together — they cannot be split, because adding a value to `RejectionOutcome`
makes `_showRejectionToast`'s `switch` expression non-exhaustive and the analyzer fails until the new
arm exists.

`shopping_list_item_repository.dart` — add `limitReached` to `DiscardReason` with a doc comment in the
existing style ("429 on create — the list is at its item cap; waiting cannot resolve a stock cap"),
and add to `createItem`'s ladder only:

```dart
case 429:
  throw ItemDiscardedException(DiscardReason.limitReached);
```

`updateItem` and `deleteItem` are deliberately left alone: neither consumes budget, so a 429 from them
is a server bug and belongs in the transient `default:` branch.

`shopping_list_sync_service.dart` — add `limitReached` to `RejectionOutcome`, and **replace the
ternary** in the `on ItemDiscardedException` branch with an exhaustive switch expression:

```dart
final outcome = switch (e.reason) {
  DiscardReason.gone => RejectionOutcome.gone,
  DiscardReason.rejected => RejectionOutcome.rejected,
  DiscardReason.limitReached => RejectionOutcome.limitReached,
};
```

This is the fix for the finding in *Risks surfaced during planning* — as written, the ternary would
silently map `limitReached` to `rejected` with no analyzer complaint. Drain semantics, locking and the
single-flight guard are otherwise unchanged: the branch still returns `PushResult.pushed`, so the
queue keeps draining rather than jamming on an entry that no amount of waiting can resolve.

`shopping_list_detail_screen.dart` — add a nullable field beside `_undoTimer`:

```dart
/// Non-null exactly while a limit-refusal toast is on screen. A burst of
/// refusals raises one bar rather than a queue of them; the controller
/// `showSnackBar` returns *is* the guard, so no buffer and no timer.
ScaffoldFeatureController<SnackBar, SnackBarClosedReason>? _limitBar;
```

and route the new outcome in `_showRejectionToast`:

```dart
if (event.outcome == RejectionOutcome.limitReached) {
  if (_limitBar != null) return;
  final bar = ScaffoldMessenger.of(context)
      .showSnackBar(const SnackBar(content: Text("This list is full - items weren't added")));
  _limitBar = bar;
  bar.closed.then((_) {
    if (identical(_limitBar, bar)) _limitBar = null;
  });
  return;
}
```

The other three outcomes keep their current immediate behaviour and their item-specific copy. Nothing
is added to `dispose()` — the callback is guarded on the controller still being ours, so a completion
after unmount only clears a field. Nothing is ever removed from the SnackBar queue:
`removeCurrentSnackBar` would drop whatever is current (possibly `_runDestructive`'s undo bar), and
`_runDestructive`'s existing `hideCurrentSnackBar` completes our `closed` future and re-arms us, which
is the right behaviour anyway.

- Files: `mobile/lib/features/shopping_list/shopping_list_item_repository.dart`,
  `shopping_list_sync_service.dart`, `shopping_list_detail_screen.dart`
- Verify: `dart analyze` from `mobile/` is clean (in particular no non-exhaustive-switch error), and
  `flutter test` passes with the existing suite unchanged.

### 7. Mobile: sync-service tests for the refused create

Give `FakeShoppingListItemRepository` a per-list capacity beside its three `bool` fault flags:

```dart
final capacity = <String, int>{};
```

and enforce it in `createItem` only, after `_maybeFail()`:

```dart
final cap = capacity[listId];
if (cap != null && (_items[listId]?.length ?? 0) >= cap) {
  throw const ItemDiscardedException(DiscardReason.limitReached);
}
```

Leaving `capacity` empty by default keeps every existing test byte-for-byte unaffected. Add the three
cases listed in *Test plan* to the `push outcomes` group.

- Files: `mobile/test/features/shopping_list/shopping_list_sync_service_test.dart`
- Verify: `flutter test test/features/shopping_list/shopping_list_sync_service_test.dart` from
  `mobile/` — the whole file green, then `flutter test` for the full suite.

### 8. Manual end-to-end check

`application-dev.yml` sets `recipai.limits.enabled: false`, so the dev profile must be started with
the flag overridden (`--recipai.limits.enabled=true`) for any of this to fire — see the
`backend-running` skill for the boot and dev-auth idioms. Then run `tasks.md` > T4 > *How to verify*
in full, plus the owner-override case this revision exists for.

- Files: none
- Verify: with `UPDATE recipai.limit_config SET max_value = 5 WHERE resource = 'SHOPPING_LIST_ITEM'
  AND subject IS NULL;` — five `POST /shopping-lists/{A}/items` succeed and the sixth returns 429 with
  a `ProblemDetail` naming `SHOPPING_LIST_ITEM`, `kind` `STOCK`, `limit` 5, `used` 5 and **no**
  `Retry-After`; a `POST` to list B still succeeds; `DELETE` an item from A and the next `POST`
  succeeds; `SELECT subject, used FROM recipai.limit_usage WHERE resource = 'SHOPPING_LIST_ITEM';`
  shows one row per list keyed by the list UUID. Then
  `INSERT INTO recipai.limit_config (id, resource, subject, kind, max_value, period) VALUES
  (gen_random_uuid(), 'SHOPPING_LIST_ITEM', '<owner-email>', 'STOCK', 200, NULL);` — with no restart,
  both existing lists **and** a newly created list accept more items. Finally `DELETE` list A and
  confirm its usage row is gone.

## Test plan

**Unit tests**

_N/A — the project has no unit-test layer for services; `limits` behaviour is covered end-to-end by
`LimitsIntegrationTest`, and the only pure-logic addition (the toast suppression guard) is UI code
whose widget test was explicitly dropped._

**Integration tests**

`LimitsIntegrationTest` (`recipai.limits.enabled=true`, synthetic `TEST_LIMIT_*` resources):

- `shouldResolveConfigurationFromConfigSubjectAndCountAgainstUsageSubject` — default 1, override 3 on
  the config subject; three reserves against one usage subject all grant, and `currentUsage` on the
  *config* subject stays empty.
- `shouldCountTwoUsageSubjectsIndependentlyUnderOneConfigSubject` — two usage subjects sharing one
  config subject each reach the ceiling separately.
- `shouldApplyConfigSubjectOverrideToEveryUsageSubjectResolvingThroughIt` — raising the config
  subject's `max_value` by SQL admits the next reserve on *both* usage subjects with no restart.
- `shouldRefuseWithConfigSubjectLimitAndUsageSubjectUsed` — the `LimitExceededException` carries the
  config subject's `limit` and the usage subject's `used`; this is the case that fails loudly if the
  two arguments are ever transposed.
- `shouldFollowConfigSubjectKindOnTwoSubjectRelease` — config subject `FLOW`, usage subject holding a
  seeded row: release leaves `used` unchanged. With the config subject `STOCK`, the decrement lands on
  the *usage* subject's row and nowhere else.
- `shouldBehaveIdenticallyForTwoArgumentAndThreeArgumentFormsWithEqualSubjects` — the delegation is
  not a behaviour change.
- `shouldDeleteUsageRowOnClear` — a seeded row is gone (`currentUsage` empty) after `clear`.
- `shouldDoNothingWhenClearingAbsentSubject` — no throw, `currentUsage` still empty.
- `shouldClearFlowConfiguredSubjectToo` — `clear` has no `FLOW` branch, unlike `release`.
- `shouldClearWithNoConfigurationAtAll` — no `LimitConfigurationMissingException`; `clear` resolves
  nothing.

`ShoppingListIntegrationTest.LimitsEnforced` (`@Nested`, limits on, own overrides:
`SHOPPING_LIST` = 2, `SHOPPING_LIST_ITEM` = 3 for `user@example.com`):

- `shouldRefuseFourthItemWithLimitDetails` — three `POST /items` succeed; the fourth returns 429,
  `application/problem+json`, body `resource=SHOPPING_LIST_ITEM`, `kind=STOCK`, `limit=3`, `used=3`,
  no `Retry-After` header and no `retryAfterSeconds` key.
- `shouldKeepItemCountsIndependentBetweenTwoListsOwnedByTheSameUser` — list A is filled to refusal
  while list B still accepts items. This is the case that proves per-list counting survived the
  revision.
- `shouldAdmitNextItemAfterDeletingOne` — at the cap, `DELETE` one item, standing drops by one, the
  next `POST` succeeds.
- `shouldNotReleaseWhenItemDeleteIsRefused` — a `DELETE` with a stale `baseVersion` returns 412 and a
  `DELETE` of an absent item returns 404; the list's standing is unchanged by either.
- `shouldNotChargeItemBudgetOnUpdate` — a `PUT` that edits, checks and repositions an item leaves the
  standing where it was.
- `shouldAllowReadAndEditWhileOverCapButKeepItemCreationRefused` — with the item override lowered to 1
  after three items exist, `GET /shopping-lists/{id}` still returns all three and `PUT` still edits
  one, while `POST /items` stays 429.
- `shouldRaiseCapOnEveryListOwnedByTheUserWhenOverrideIsRaised` — with two lists at the cap, raise the
  owner's `SHOPPING_LIST_ITEM` override by SQL; **both existing lists** accept more items with no
  restart. The revision's headline case.
- `shouldApplyRaisedOverrideToAListCreatedAfterTheOverrideWasWritten` — after the raise, delete a list
  and create a fresh one; it starts with the raised ceiling, not the default. This is what a per-list
  key could not express.
- `shouldChargeTheListAndResolveTheOwnersCapWhenAnEditorAddsAnItem` — list shared with
  `user2@example.com`, who holds a *different* `SHOPPING_LIST_ITEM` override; the editor's `POST`
  advances the **list's** standing and is refused at the **owner's** ceiling, and the editor's own
  subject has no `SHOPPING_LIST_ITEM` usage row at all.
- `shouldClearItemUsageWhenTheListIsDeleted` — after `DELETE /shopping-lists/{id}`,
  `currentUsage(listId, SHOPPING_LIST_ITEM)` is empty (not merely zero).
- `shouldReproducePerListItemCountsViaRecompute` — items created over HTTP on two lists, `used`
  corrupted by SQL on one, `RecomputeMigration.run(dataSource)` restores both to their true counts.
  This is also what proves `shopping_list_id::text` matches what `UUID.toString()` writes.
- `shouldChangeNothingOnSecondItemRecomputeRun` — two consecutive runs yield the same counts.
- `shouldSpareListWhoseOwnerIsFlowConfiguredFromItemRecompute` — with a `SHOPPING_LIST_ITEM` `FLOW`
  override on the owner, a seeded usage row for their list keeps its `used` and `period_start` across
  a recompute. Exercises the join the delete side's correlated subquery performs in reverse.

Every assertion is scoped to an explicit `(resource, subject)` pair; none counts rows in `limit_usage`
globally, because the nested contexts share one database.

**Flutter widget/integration tests**

`shopping_list_sync_service_test.dart` > `push outcomes`:

- `429 on a create discards the item and the drain keeps going` — `capacity[listId] = 0`, one queued
  create: `pushNextEntry` returns `PushResult.pushed`, the local row is hard-removed, the outbox is
  empty and the backend holds nothing.
- `a queued delete behind refused creates still lands and frees a slot` — one accepted item, then
  `capacity[listId] = 1`, then two creates queued behind a delete of the accepted item; a full drain
  leaves the backend holding exactly the entries capacity allowed, the outbox empty, and no entry
  stalled.
- `a 429-refused create emits a limitReached rejection` — the `RejectionEvent` carries
  `RejectionOutcome.limitReached` and the discarded item's name. Guards the ternary-to-switch fix:
  before it, this assertion fails with `RejectionOutcome.rejected`.

_No widget test for `shopping_list_detail_screen.dart`_ — dropped at the user's direction
(`T4-task-design.md` > *Testing*). This leaves `_showRejectionToast`'s suppression guard as the only
new logic in the task with no automated coverage; the screen has no widget tests today, so it is the
status quo rather than a regression, but a future refactor of `_limitBar` has nothing to catch it.
Verified by hand instead — see below.

**Manual verification**

- The full curl sequence in step 8, including the owner-override raise and the list-deletion clear.
- In the app: open a list at its cap, add several items while offline (airplane mode), reconnect, and
  confirm the refused items disappear, **one** SnackBar reads "This list is full - items weren't
  added" rather than a queue of them, the list's sync indicator returns to idle (the outbox drained
  rather than stalled), and a subsequent queued edit to that list still syncs.
- In the app: with a burst still arriving, trigger a delete so `_runDestructive`'s undo bar shows —
  confirm the undo bar is not buried behind refusal toasts and that hiding it re-arms the next
  refusal toast.
- Bulk import into a full list via the generated-items review screen: it still reports "Added N
  item(s)" while fewer arrive. **Known gap, deliberately not fixed here** — see *Risks*.

## Verification checklist

- [ ] `./mvnw test` from `backend/` — all new and existing tests pass (only
      `ExtractionIntegrationTest`'s real-provider test stays `@Disabled`)
- [ ] `dart analyze` and `flutter test` from `mobile/` are clean
- [ ] `V18` and the changed repeatable apply cleanly against a fresh database, and re-running the
      repeatable by hand is a no-op
- [ ] `V18` is still the next free version number at merge time — two `V18`s make Flyway fail at
      startup
- [ ] `tasks.md` > T4 > *How to verify* succeeds end-to-end, including the offline app check
- [ ] The `SHOPPING_LIST_ITEM` literal is spelled identically in `ShoppingListService`, `V18` and both
      halves of the recompute block — `grep -rn "SHOPPING_LIST_ITEM" backend/src/main` shows no fourth
      spelling
- [ ] `LimitsModuleArchitectureTest` passes with **no edit** — the split introduced no new public type
- [ ] The five pre-existing `LimitsFacade` call sites (`recipes`, `recipes.collections`,
      `shoppinglists`, `planning`, `extraction`) are unchanged in the diff
- [ ] `createItem` reserves **after** `requireEditorPermission`, and a `POST` to a list the caller
      cannot edit still returns 403/404 with the list's standing untouched
- [ ] `deleteItem` releases only on the path that actually deleted — 412 and 404 refund nothing
- [ ] `deleteById` calls `clear`, not `release`, and does so after the OWNER row is gone
- [ ] All HTTP suites still carry the byte-identical `recipai.limits.enabled=false` string so the
      contexts stay shared
- [ ] The nested `tearDown` ends with `usedFor(SUBJECT)` at zero and the deleted lists' item usage
      absent — evidence release and clear both fired
- [ ] No `@Transactional` added or removed in `ShoppingListService`
- [ ] The `DiscardReason` → `RejectionOutcome` mapping is an exhaustive `switch`, not a ternary
- [ ] Logs at `INFO` are clean on the happy path; refusals at `WARN`
- [ ] The design's *Assumptions to verify* are resolved or explicitly carried forward (see below)

## Risks surfaced during planning

- **Risk:** the design's assumption *"adding a value to `DiscardReason` and `RejectionOutcome` breaks
  no other `switch` … If wrong: a compile error, not a silent fallthrough"* is **half wrong**.
  `_showRejectionToast` does use a `switch` expression and will fail to compile, but
  `_pushHeadEntry` (`shopping_list_sync_service.dart:292-294`) maps the reason with a **ternary**:
  `e.reason == DiscardReason.gone ? RejectionOutcome.gone : RejectionOutcome.rejected`.
  **Why it matters:** `DiscardReason.limitReached` would compile cleanly and silently surface as
  `RejectionOutcome.rejected`, producing the old "could not be synced" toast with no suppression — the
  exact silent fallthrough the assumption rules out, and one no analyzer run would catch.
  **Mitigation:** step 6 replaces the ternary with an exhaustive `switch` expression, and the third
  new Dart test asserts the emitted outcome is `limitReached`. Both are in the checklist.

- **Risk:** `R__recompute_limit_usage.sql`'s header comment says it rebuilds usage "for the
  owner-scoped resources … from their owning module's permission table". The item block is neither
  owner-scoped in its subject nor sourced from a permission table.
  **Why it matters:** the file is the operator's reference for drift repair; a header that misdescribes
  a fifth of its content will mislead whoever runs it at 3am.
  **Mitigation:** step 3 updates the header alongside the block. Flagged because the design does not
  mention it.

- **Risk:** the existing nested `@BeforeEach seedOverride` seeds only `SHOPPING_LIST`, and
  `seedConfigOverride` hard-codes that resource in its SQL.
  **Why it matters:** without a `SHOPPING_LIST_ITEM` override the item cases would run against the
  shipped default of 50 — 51 HTTP round trips per refusal case, and a suite that breaks the day an
  operator changes a production number, which is exactly what the standard forbids.
  **Mitigation:** step 5 generalises the helper to take the resource and seeds an item override of 3.
  Small change, but it touches a method four existing tests call.

- **Risk:** `requireOwnerEmail` runs one extra `SELECT` per item created **and** per item deleted, on
  the hottest write path in the module — the offline outbox drains one entry at a time, so a
  reconnect after a bulk import issues one owner lookup per queued item.
  **Why it matters:** the design justifies the dedicated projection over reusing
  `findAllByShoppingListId` on exactly these grounds, but does not note that `deleteItem` pays it too,
  where `deleteById`'s equivalent is avoided by `clear` taking one subject.
  **Mitigation:** accepted — it is a primary-key-prefixed index lookup returning one column, inside a
  transaction that is already doing a permission read and a write. Named here so it is a known cost
  rather than a surprise if item throughput is ever profiled.

- **Risk:** the shipped default of 50 lands live with `V18` and the recompute in one deploy, and the
  recompute seeds every existing list at its true count.
  **Why it matters:** any list already holding more than 50 items is over cap the moment this ships,
  with no warning and no in-app explanation of the number.
  **Mitigation:** accepted per the design (ADR-0003 sizes the sync design at ~30–40 items). Worth
  naming in the PR description alongside the raise-by-SQL command
  (`UPDATE limit_config SET max_value = … WHERE resource = 'SHOPPING_LIST_ITEM' AND subject IS NULL`),
  so whoever fields the first complaint has it to hand.

- **Risk:** this is the **last** planned task to edit `R__recompute_limit_usage.sql`.
  **Why it matters:** T2 and T3 could each assume a later task would bump the file's checksum and
  re-run the repair. After T4, repairing drift needs psql access or a cosmetic checksum bump — nothing
  exposes the recompute at runtime.
  **Mitigation:** accepted, unchanged from T2 and T3. Carry the on-call note into the PR description.

**Assumptions from `T4-task-design.md`, resolved during planning**

- *"Exactly two backend paths destroy a counted item"* — **confirmed.**
  `grep -rn "shoppingListItemRepository\.\|ShoppingListItem(" backend/src/main/java` outside
  `shoppinglists/` returns nothing; `planning` only produces `GeneratedShoppingListItemDto` and never
  writes the table; `V5__rename_list_id_and_update_position.sql` carries the
  `ON DELETE CASCADE` on `shopping_list_items.shopping_list_id`; `unshareShoppingList` removes a
  permission row and no items.
- *"Every list has exactly one `OWNER` permission row for as long as it has items"* — **confirmed.**
  `create` writes the list and the OWNER row in one `@Transactional` method, `unshareShoppingList`
  throws before removing an OWNER, `deleteById` removes permissions and list together, and nothing
  else writes `shopping_list_permission`. No ownership transfer exists.
- *"`ShoppingListsExceptionHandler` needs no edit"* — **confirmed**; it handles four module exceptions,
  none of them `LimitExceededException`, and none broad enough to shadow it.
- *"The nested `LimitsEnforced` class can host the item tests without splitting"* — **confirmed**; it
  already runs with limits on and the enclosing instance's `createItem`/`deleteItem`/`restClient`
  helpers resolve from the nested context.
- *"`V18` is free"* — **confirmed** at time of writing; `V17__meal_plan_limit_config.sql` is the
  highest version on `main`. Re-check at merge (checklist).
- *"`FakeShoppingListItemRepository` can carry a per-list capacity without disturbing existing tests"*
  — **confirmed**; an empty `capacity` map is inert, exactly like the three `bool` flags at `false`.
- *"`shopping_list_id::text` renders the same lowercase-hyphenated form as `UUID.toString()`"* —
  **carried forward.** It is Postgres' canonical `uuid` output, but nothing in this repo proves it
  today. `shouldReproducePerListItemCountsViaRecompute` is what will prove it, since it creates items
  over HTTP (writing subjects via `UUID.toString()`) and then asserts the recompute reproduces those
  same rows.
- *"A bulk import's refusals currently reach nobody"* — **confirmed and carried forward to T5.**
  `shopping_list_review_widget.dart:144,157` calls `importService.importItems`, shows
  "Added N item(s) to shopping list" and pops the route, so the target list's detail screen is not
  open, `RejectionEvent` has no subscriber and `_emit` drops it. The identical drop already happens
  for `gone` and `rejected`, so T4 does not dig the hole — but "this list is full" is the first
  refusal a normal user will actually hit. The honest fix needs T5's standing read path (pre-flight
  the remaining capacity and report the shortfall before writing).
