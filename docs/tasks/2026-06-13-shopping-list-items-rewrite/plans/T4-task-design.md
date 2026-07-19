# T4 — Mobile Pull Sync (full-list poll + diff) — Task Design

**Date:** 2026-07-03
**Status:** draft
**Task:** T4 in `tasks.md`
**Builds on:** `../hld.md` (§1.3–§1.4, §2.3, §3, §4), `plans/T3-task-design.md` (the sync service this extends), `plans/T2-task-design.md` (store / last-acked-version / dirty contract), `docs/ADRs/0003-shopping-list-full-refresh-over-delta.md`

## Summary

T4 extends the T3 `ShoppingListSyncService` with a **per-list background poller**
that fetches the full item set every **10s** while a list is open and **diffs it
into the local store** via a new `ShoppingListItemRepository.reconcileFromServer` —
adopting clean items, **keeping dirty items untouched** (never advancing their
last-acked version), inserting new remote items, and hard-removing non-dirty items
the server no longer has. **Poll and push are mutually exclusive per list** via a
shared `_busy` gate: on open the list is hydrated from the DB first, then polling
starts **ahead of** the drain (so the cold-start pull grabs `_busy` first and isn't
dropped); a poll pass is **dropped** while a push drain is in flight, and a drain
requested mid-poll **defers** until the poll releases `_busy` — which closes the
create-ack race. The service
tracks a single per-list `SyncStatus` (`{syncing, notSyncing, failure, offline}`)
set directly at each transition — no derived reachability object. A caught
**network exception** in a poll or push sets `offline` and is **not surfaced** to the
user; the service keeps polling and pushing on its timers/triggers and clears
`offline` on the next success. A non-network push rejection sets `failure`, which
raises the **retry-all banner**. The screen renders a **subtle list-level indicator**
from that single status (`offline` "Offline" / `failure` "!" / `syncing` "Syncing…" /
`notSyncing` synced tick). A pulled value that lands on an item whose text field is
**focused** overwrites the field and raises a toast through the same screen path
T3's rejection toasts use.

## Components and responsibilities

All under `mobile/lib/features/shopping_list/` unless noted.

- **`ShoppingListSyncService`** (MODIFY, `shopping_list_sync_service.dart`) — gains
  the **pull** half and the `offline` status:
  - A **per-list poll `Timer`** (`startPolling` fires an immediate poll then arms the
    periodic 10s timer; `stopPolling` cancels it) and poll pass (`fetchServerItems` →
    `reconcileFromServer`). The `_pollTimers` map's keys *are* the set of lists being
    polled — no separate tracking set.
  - **Mutual exclusion** (§Serialization): poll and push share the per-list `_busy`
    gate (T3's `_draining`, renamed and now acquired by the poll too). A poll pass is
    **dropped** while a drain holds `_busy`; a drain requested mid-poll **defers**
    (`_pending`) and is kicked when the poll releases `_busy`. The poll tick does
    **not** nudge the drain — push retries on its usual T3 signals. No outbox-empty
    check, no new lane abstraction — the per-item dirty-keep in the diff already
    protects queued edits, so `_busy` only has to close the in-flight create-ack race.
  - The exposed per-list `SyncStatus` gains an `offline` value and is **set directly
    at each transition** (§Status). A caught network exception (poll or push) sets
    `offline` and is swallowed; a non-network push rejection sets `failure`. A poll
    success clears `offline` only — it **never** clears a sticky `failure` (a poll can
    now run with a non-empty outbox).
  - App-lifecycle: pause poll timers on background, re-arm + immediate poll on
    resume (alongside the existing push fan-out). Still UI-agnostic — exposes state;
    the screen renders.
- **`ShoppingListItemRepository`** (MODIFY, `shopping_list_item_repository.dart`) —
  becomes the single item **GET** point too:
  - `fetchServerItems(listId, idToken)` → `List<ShoppingListItem>` (parses the
    `items` array of `GET /shopping-lists/{id}`), mirroring the item-write HTTP style.
  - `reconcileFromServer(listId, serverItems)` — the T2-reserved dirty-aware
    full-list diff, DB + cache coherent in one transaction, resident-or-DB per the
    T3 reconcile pattern, refreshing the notifier when resident.
- **`ShoppingListDetailService`** (MODIFY, `shopping_list_detail_service.dart`) —
  `openShoppingList` **orchestrates** open: `await openList` (hydrate — local items
  show instantly) → subscribe → `startPolling` (immediate poll + periodic timer) →
  `requestDrain` → `loadShoppingListDetail`. **`startPolling` is issued before
  `requestDrain`** so the immediate cold-start poll acquires `_busy` first; issuing the
  drain first lets it hold `_busy` and **drop** the immediate poll, delaying the first
  server refresh until the next 10s tick (§Serialization). The on-open GET is now **chrome-only**
  (name + role): `role` gates the owner menu and is not stored locally, so the GET
  stays, but it **no longer seeds items** — its `items[]` is ignored (the screen reads
  the store, not `detail.items`). Items are DB-hydrated then refreshed **solely by the
  poll** (immediate poll on open, then every 10s). `dispose` calls `stopPolling`. No
  new fields exposed — the screen reads the existing `syncStatus`.
- **`ShoppingListDetailScreen`** (MODIFY, `shopping_list_detail_screen.dart`) —
  renders the **list-level sync indicator** from `syncStatus` (replacing the
  `TODO(..., T4)` at line 482), and wires each item's new `onOverwrite`
  callback to a toast via the existing `ScaffoldMessenger` path (sibling of
  `_showRejectionToast`). `initState` calls `openShoppingList` (which now drives the
  chrome load) instead of a separate `loadShoppingListDetail`.
- **`ShoppingListItemWidget`** (MODIFY, `shopping_list_item_widget.dart`) — its
  `didUpdateWidget` overwrites the in-progress edit and fires `onOverwrite`
  when a **real content change** arrives while the field is **focused** (Option A,
  §Active-edit rule). `ItemDisplayData` gains a **`hasSameContentAs`** predicate so a
  real change is distinguishable from an ordinary rebuild.

## Interfaces and method signatures

### Sync service — pull + status

```dart
enum SyncStatus { syncing, notSyncing, failure, offline }   // gains `offline`

class ShoppingListSyncService with WidgetsBindingObserver {
  // existing T3: syncStatusFor, rejections, requestDrain, retry, start, dispose ...

  void startPolling(String listId);                          // immediate poll + arm periodic 10s timer
  void stopPolling(String listId);                           // cancel this list's timer
  // internal: _poll(listId), _canReconcile(listId) == !_busy.contains(listId),
  //           _busy (T3 _draining renamed; acquired by poll too),
  //           _pollTimers[listId] keys == lists being polled,
  //           _status[listId] set directly per transition (§Status)
}

const _pollInterval = Duration(seconds: 10);
```

### Repository — pull additions

```dart
// GET /shopping-lists/{listId} -> parse items[]. Only a 2xx with a parseable
// body is a result; any other outcome (network/timeout, 401/403/404/5xx) throws.
// The poll caller catches and leaves the store untouched — a non-2xx must never
// be mistaken for "the list is now empty".
Future<List<ShoppingListItem>> fetchServerItems(String listId, String? idToken);

// Full-list diff into the store (DB + cache coherent, one transaction).
// Per-item adopt is version-gated (§Pseudo-code) so an out-of-order/stale
// response can never regress an item already at a newer lastAckedVersion.
Future<void> reconcileFromServer(String listId, List<ShoppingListItem> serverItems);
```

#### Response handling (GET /shopping-lists/{id})

Only a **2xx with a parseable `items[]`** yields items to reconcile; **every other
outcome throws** and the poll caller leaves the store untouched. Splitting hairs
between error kinds is deliberately avoided:

- A caught **network exception / timeout** sets `SyncStatus.offline` (swallowed,
  §Status). The service keeps polling; the next success clears it.
- A **non-2xx** (401/403/404/5xx) also throws and leaves the store untouched. There is
  no "list gone" special case: a 403/404 is never read as "the server says this list is
  now empty," which would otherwise mass-delete every non-dirty local item via
  §Pseudo-code branch (b). If the list truly is gone, polls keep failing and the
  indicator reflects it; a distinct "list no longer accessible" surface is out of scope
  for T4.

### Widget — active-edit surface

```dart
class ShoppingListItemWidget extends StatefulWidget {
  final VoidCallback? onOverwrite;     // fired when a remote change overwrites an in-progress edit
}

class ItemDisplayData {
  bool hasSameContentAs(ItemDisplayData other);  // compares name, quantity, unit, checked
}
```

## Serialization (poll and push are mutually exclusive)

No general lane, no outbox-empty gate. Poll and push share **one per-list `_busy`
flag** (T3's `_draining`, renamed and now acquired by the poll too). A reconcile (poll
pass) runs **only when `_busy` is clear** — `_canReconcile(listId) == !_busy.contains(listId)`.
When a 10s tick fires while a drain holds `_busy` the poll is **dropped**; the next
tick re-checks. Symmetrically, a `requestDrain` arriving while a poll holds `_busy`
**defers** (sets `_pending`, per the existing T3 coalescing) and is kicked when the
poll releases `_busy` — so an explicit user edit during a poll is honoured, not lost.
The poll tick does **not** kick a drain; draining otherwise stays driven by the usual
T3 signals (a new outbox entry, app resume, user retry).

Why the outbox-empty check is unnecessary: the per-item **dirty-keep** in
`reconcileFromServer` (§Pseudo-code) already protects every queued edit — a dirty item
is never overwritten and its `lastAckedVersion` is never advanced by a pull. The
**only** thing left to guard is the create-ack race — the window where the server has
an item but the local create isn't yet `serverId`-matched, so the diff would
duplicate-insert it. That window lives entirely inside the drain (`createItem POST →
reconcileAck`), so a `_busy`-holding drain that excludes concurrent polls closes it.
A create that hasn't been POSTed yet is safe regardless of `_busy`: its `serverId ==
null`, the server doesn't have it, so branch (a) inserts nothing and branch (b) keeps
it. Local contents from the DB hydrate are shown throughout.

Consequence: a poll may now run with a **non-empty outbox** (queued but not-in-flight
edits). That is fine for the store (dirty-keep), but the status handling must not read
a poll success as "all synced" — a poll success clears `offline` only and **leaves a
sticky `failure` untouched** (§Status).

**On-open ordering.** Because a poll pass is dropped (not deferred) while a drain holds
`_busy`, `openShoppingList` must issue `startPolling` **before** `requestDrain`: the
immediate cold-start poll then acquires `_busy` first and the drain — which does `_busy.add`
synchronously before its first `await` — sees it held and **defers** via `_pending`,
running the moment the poll releases `_busy`. Issuing `requestDrain` first inverts this:
the drain grabs `_busy` and the immediate poll is silently dropped, so the first server
refresh waits for the next periodic tick (~10s). The two still never run concurrently, so
the create-ack race stays closed either way — only *which runs first on open* changes, and
a pull-first open is safe because `reconcileFromServer` never touches dirty items.

**Ordering caveat.** `_busy` gives mutual exclusion, not arrival ordering. Two poll
passes can still *arrive* out of request order — e.g. a slow **immediate poll** on open
resolves **after** a later periodic poll already applied fresher data. The per-item
version guard in `reconcileFromServer` (§Pseudo-code) is what prevents that stale
response from regressing the item.

## Status

One per-list `SyncStatus` ValueNotifier, **set directly at each transition** — no
derived reachability object, no `_recompute` combining two internal fields. The four
values and their triggers:

| Value | Set when | Surfaces |
|---|---|---|
| `syncing` | a push drain starts | subtle "Syncing…" |
| `notSyncing` | drain finishes, outbox empty, no failures | synced tick |
| `failure` | a **non-network** push rejection (T3's existing path) | "!" + **retry-all banner** |
| `offline` | a caught **network exception** in a poll or push | "Offline" |

Rules:

- `offline` is set whenever a poll or push hits a network exception; the exception is
  **swallowed, never surfaced**. The service keeps polling (10s timer) and pushing (on
  the usual signals); the next successful poll/push **clears** `offline` back to the
  push-derived value.
- `failure` is **sticky** — it persists (banner up) until the failed pushes are retried
  and succeed. A successful poll does not clear it (there are still failed pushes).
- Retry stays gated on `failure` only (no banner while `offline`, where the retry is
  automatic on the next signal).

## Data flow

**Open a list — offline-first:**

1. `openShoppingList(listId)`: `await openList` hydrates the store from the DB → the
   view renders **last-known local contents immediately** (req §3.3); subscribe to
   `watch`; `startPolling` fires an **immediate poll** (grabbing `_busy`) then arms the
   periodic 10s timer; `requestDrain` then flushes pending pushes — deferred behind the
   immediate poll via `_pending` (§Serialization "On-open ordering"). Polling is started
   **before** the drain so the cold-start pull isn't dropped by the drain holding `_busy`.
2. `loadShoppingListDetail(listId)` runs (after hydrate) — a **chrome-only** GET that
   sets name/role (`role` gates the owner menu, not stored locally). Its `items[]` is
   ignored; items are seeded/refreshed by the poll, not this GET.
3. The immediate poll from step 1 is the cold-start item load (HLD §1.4): it diffs the
   server items into the store (subject to `_busy`, §Serialization). If it hits a
   network exception → `offline`, the store keeps its hydrated local contents and the
   10s poll retries. (On open the chrome GET and the immediate poll hit the same
   endpoint — accepted for a single item-refresh path; see §Decisions.)

**A poll pass (steady state):**

1. Timer fires → if `_canReconcile` is false (a drain holds `_busy`), **skip** (the
   next tick re-checks).
2. `fetchServerItems(listId)` → on a caught **network exception** set `offline` and
   return (store untouched); on any other non-2xx also return, store untouched
   (§Response handling). A 401/403/404/5xx is never treated as "the list has no items."
3. On success: `reconcileFromServer(listId, items)` diffs (§Pseudo-code) → notifier
   re-emits; clear `offline` **iff it was set** — a sticky `failure` is left alone
   (§Status), since the poll may have run with a non-empty outbox.

**A push pass (T3 drain, now with offline handling):** the drain gets the **same
network-exception handling as poll** — a caught network exception sets `offline`
(swallowed, not surfaced) and leaves the outbox intact; the entry retries on the next
usual signal (a new outbox entry, resume, or user retry). A non-network server
rejection is the existing T3 `failure` path.

**Status / indicator:** the sync service sets one per-list `SyncStatus` directly at
each transition (§Status). The screen renders it: `offline` → **"Offline"**,
`failure` → **"!" in a circle**, `syncing` → subtle "Syncing…", `notSyncing` →
**synced tick**. The T3 bottom retry banner shows on `failure` only.

**Active edit meets a pull (req §3.7):** a pulled value adopts onto a **non-dirty**
item (store updates). Its widget — reused because `localId` keys it — gets
`didUpdateWidget` with changed content while its field is **focused**; it overwrites
the controller and fires `onOverwrite` → screen toasts. Sound because a focused
item is always non-dirty (local edits commit only on blur/submit), so any store
change to it while focused is necessarily remote.

**Active edit meets a remote delete — accepted gap, not covered by Option A:** if a
**non-dirty** item is being actively edited (focused, uncommitted text) and someone
else deletes it, §Pseudo-code branch (b) hard-removes it on the next poll. This is a
**removal**, not a content change, so `didUpdateWidget`'s `hasSameContentAs` check
never fires and no `onOverwrite`/toast happens — the widget's subtree is simply
unmounted as the item drops out of `watch()`. The user's in-progress, uncommitted
edit disappears with no explanation. Accepted for T4: req §3.7 only specifies the
overwrite-while-editing case, not disappearance-while-editing, and covering it would
require the screen to notice a focused item vanishing from the list (not just a
per-widget content diff). Revisit if this proves confusing in practice.

## Pseudo-code

```
reconcileFromServer(listId, serverItems):            # store; one DAO txn; resident-or-DB
  cache      = _cache[listId]                          # (resident during polling)
  serverById = { s.id: s for s in serverItems }
  localById  = { i.serverId: i for i in cache.values if i.serverId != null }

  txn:
    # (a) server items -> insert new / adopt clean / keep dirty
    for s in serverItems:
      local = localById[s.id]
      if local == null:                                # new remote item
        ins = LocalItem(localId=uuid(), serverId=s.id, listId, fields<-s,
                        lastAckedVersion=s.version, dirty=false, failed=false,
                        pendingDelete=false)
        cache[ins.localId] = ins; upsertTxn(ins)
      else if not local.dirty and s.version > local.lastAckedVersion:
                                                        # adopt server value (fields + version)
                                                        # equal version = identical acked state, adopting is redundant
        upd = local.copyWith(fields<-s, lastAckedVersion=s.version, pendingDelete=false)
        cache[local.localId] = upd; upsertTxn(upd)
      # else dirty -> KEEP local, do NOT touch fields or lastAckedVersion (push owns it)
      # else (not dirty but s.version < local.lastAckedVersion) -> KEEP local: this
      #   response is stale (raced behind a fresher reconcile), adopting it would
      #   regress the item; a later poll/reconcile with a current version corrects it

    # (b) local items missing from server -> deleted elsewhere
    for local in snapshot(cache.values):
      if local.serverId != null and serverById[local.serverId] == null:
        if not local.dirty:                            # safe hard delete
          cache.remove(local.localId); deleteItemRowTxn(local.localId)
        # dirty & missing -> KEEP: its own push resolves it (404->gone->discard,
        #   or 412->cascade). Deleting it would strand its outbox entries.
      # serverId == null -> locally-pending create, never on server yet -> KEEP

  if resident: _notifiers[listId].value = _visibleItems(listId)
```

```
# ── didUpdateWidget (Option A) ──
didUpdateWidget(old):
  if not widget.item.hasSameContentAs(old.item):     # real content change
    if _focusNode.hasFocus:                           # active edit -> remote change wins
      _controller.text = _formatItem()
      widget.onOverwrite?.call()                # screen toasts
    else:
      _controller.text = _formatItem()                # existing behavior
```

## Decisions made

- **Poller lives in `ShoppingListSyncService`, per list, driven by the detail
  service** — HLD §3 puts pull and push in the one sync service; the detail service
  owns lifecycle (`startPolling` on open, `stopPolling` on dispose). Push keeps
  running regardless of any open screen (unchanged from T3).
- **One item-refresh path: the poll** *(revised — dropped the on-open seed)* — items
  come from the DB hydrate (shown instantly) and are then refreshed **only** by the
  poller, which fires an **immediate poll** on open and every 10s after. The on-open
  `loadShoppingListDetail` GET stays but is **chrome-only** (name + role): `role` gates
  the owner-only menu and is neither stored locally nor passed via navigation (the
  detail screen is routed by `:id` alone), so a GET is unavoidable — but it no longer
  seeds items, and `reconcilePulled` is removed. Trade-off: the chrome GET and the
  immediate poll both hit `GET /shopping-lists/{id}` on open (the endpoint has no
  chrome-only variant), fetching items twice within milliseconds; accepted in exchange
  for a single, uniform item-sync path and no special seed-vs-poll ordering to reason
  about. `openShoppingList` still awaits `openList` before `startPolling`, so the list
  is resident when the immediate poll reconciles.
- **Poll interval 10s** — the low end of req §1.4's 10–30s, for snappier propagation
  at negligible cost (30–40 items).
- **Adopt is version-gated: `s.version > local.lastAckedVersion`** *(added in
  review)* — equal version = identical acked state, adopting is redundant. `_busy`
  prevents *concurrent* reconciles, but not out-of-order *arrival*:
  the immediate poll on open can resolve after a fresher periodic poll has already run
  (§Serialization ordering caveat). Without a version check, that stale response would
  silently regress the item's fields back in time until the next poll corrects it. The
  guard costs one comparison and closes the gap outright instead of relying on the next
  tick to paper over it.
- **`fetchServerItems` throws on every non-2xx; the store is left untouched** *(added
  in review)* — only a 2xx with a parseable body counts as a result. This guards
  specifically against a permission/lifecycle response (list deleted, access revoked)
  being mistaken for "the server says this list is now empty," which would otherwise
  mass-delete every non-dirty local item via §Pseudo-code branch (b). A network
  exception among these maps to `offline`; other non-2xx just leave the store as-is.
- **Status is set directly per transition; no derived reachability object** *(settled
  with user — simplified)* — the earlier design combined an internal `_reachable`
  boolean and a per-list `_pushStatus` via a `_recompute` precedence, plus a
  reconnect-drain kick. That was too complicated. Instead the single per-list
  `SyncStatus` is set at each transition (§Status): network exception → `offline`
  (swallowed, not surfaced), push rejection → `failure`, drain start/finish →
  `syncing`/`notSyncing`; a success clears `offline`. No `connectivity_plus` / platform
  channel, no reconnect-drain kick — push retries on its usual signals. Trade-off:
  offline is known only after a failed attempt — acceptable within the 10s poll window.
- **Offline handling is symmetric across poll and push** *(settled with user)* — both
  catch the network exception, set `offline`, and swallow it (no user-facing error);
  poll retries on its 10s timer, push retries on the usual outbox signals. Only a
  non-network push rejection reaches the `failure` / retry-banner path.
- **Indicator states = the four `SyncStatus` values** — `offline` "Offline",
  `failure` "!" in a circle, `syncing` subtle "Syncing…", `notSyncing` **synced tick**
  (the list is up to date). The T3 bottom failure banner stays gated on `failure`.
- **Pull never advances `lastAckedVersion` for a dirty item** — honours the T2
  contract: last-acked advances only by this device's own acks (T3), so queued
  entries still push against the right base. Clean items adopt the server version
  (HLD §2.3).
- **A dirty item missing from the server is NOT deleted by the pull** — hard
  correctness rule: it still has outbox entries, and T3's `_pushOne` reads the row
  with `readItem(...)!`; deleting it would strand the entries and crash the push. Its
  own push resolves it. Only **non-dirty** items missing from the server are removed.
- **Poll and push are mutually exclusive via a shared `_busy` flag** *(simplified —
  was an outbox-empty gate)* (§Serialization) — T3's `_draining` is renamed `_busy`
  and acquired by the poll too: a poll runs only when `_busy` is clear, and a drain
  requested mid-poll defers via `_pending`. The earlier "poll only when the outbox is
  empty" rule was **overkill** — the per-item dirty-keep in the diff already protects
  queued edits, so the gate only has to exclude a poll from the in-flight create-ack
  window (`POST → reconcileAck`), which lives inside the drain and is exactly what
  `_busy` covers. Dropping the outbox check also removes an async DB read from the
  hot gate. The poll tick does **not** nudge the drain (settled — resolves the earlier
  §Components/§Serialization contradiction); push retries on its usual T3 signals.
- **On open, `startPolling` is issued before `requestDrain`** *(fixed in
  implementation)* — because a poll pass is **dropped** (not deferred) while a drain
  holds `_busy`, and `_drain` grabs `_busy` synchronously before its first `await`,
  the original `requestDrain` → `startPolling` order let the drain swallow the immediate
  cold-start poll — so the first server refresh only arrived on the next 10s tick (a
  visible ~10s "no data" gap on open). Reversing to `startPolling` → `requestDrain`
  makes the immediate poll grab `_busy` first; the drain then defers via `_pending` and
  runs when the poll completes. Mutual exclusion is unchanged (they still never overlap),
  so this only makes the open a **pull-first** sequence — safe, since a pull never
  touches dirty items and pushes fire immediately after.
- **Active-edit rule = Option A: widget-local detection + screen toast** *(settled
  with user)* — the widget owns detection (it alone knows focus); a focused item is
  guaranteed non-dirty, so any store change to it while focused is a remote pull. It
  overwrites the field and fires `onOverwrite`, rendered through the **same
  `ScaffoldMessenger` SnackBar path as the T3 rejection toasts** (different copy). A
  sync-service event stream is rejected — the service can't scope a toast to the
  focused item, so it would still need this widget change plus extra parts.
- **`ItemDisplayData.hasSameContentAs` (not an `==` override)** *(settled with user)*
  — required supporting change: without a content comparison,
  `oldWidget.item != widget.item` is identity (a fresh instance every rebuild), so the
  Option-A branch would fire on every rebuild-while-focused (e.g. a sibling item
  changing) and clobber the edit. An explicit `hasSameContentAs(name, quantity, unit,
  checked)` predicate keeps equality semantics untouched elsewhere.
- **Poll paused in the background** — timers cancelled on `paused`/`inactive`,
  re-armed with an immediate poll on `resumed` (alongside T3's push fan-out).

## Assumptions to verify

- **Assumption:** `GET /shopping-lists/{id}` returns an `items` array whose elements
  parse with `ShoppingListItem.fromJson`, matching `ShoppingListDetail.fromJson`.
  **If wrong:** `fetchServerItems` parsing changes; the diff input shape is affected.
- **Assumption:** only one shopping-list detail screen is open at a time, so per-list
  polling effectively runs for one list.
  **If wrong:** the per-list poll map handles several, but verify start/stop pairing
  under nested navigation.
- **Assumption:** the shared `_busy` gate (a drain holds `_busy` across its whole
  `POST → reconcileAck`, Dart single-isolate, poll excluded while `_busy` is held)
  fully closes the create-ack race, so no separate outbox-empty check is needed.
  **If wrong:** re-introduce an explicit in-flight-create / outbox-empty guard before
  inserting a server item the local create hasn't been `serverId`-matched to.
- **Assumption:** awaiting `openList` inside `openShoppingList` before `startPolling`
  guarantees the list is resident when the immediate poll reconciles, so it hits the
  cache (not a DB-then-hydrate race).
  **If wrong:** make `reconcileFromServer` strictly resident-or-DB and re-hydrate, or
  gate the immediate poll on hydration completion.
- **Assumption:** a focused item's `State` is preserved across a pull-driven rebuild
  (stable `ValueKey(localId)`), so `didUpdateWidget` — not `initState` — runs. A
  remote **check/uncheck** while editing moves the item between the active/Done
  `ReorderableListView`s (different subtrees) and may reset the `State`, losing the
  toast in that corner.
  **If wrong / for that corner:** accept the minor deviation (field still ends on the
  server value; toast may be skipped for a concurrent remote check).
- **Assumption:** only a caught **network exception** drives `offline`; a non-network
  server error (e.g. a persistent poll 5xx) leaves the store and status untouched, so
  the indicator may keep showing the synced tick while a background poll silently fails.
  Acceptable — the user's own edits still push (or hit `failure`), and the store is
  never corrupted.
  **If wrong:** map poll 5xx to `offline` too, or split a distinct "server error" status.
- **Accepted (not just assumed):** while a push drain is actively in flight a poll
  tick is dropped (`_busy` held), so under a burst of continuous local edits — which
  keeps drains running back-to-back — other users' changes can lag past the 10–30s
  freshness target (req §1.4). Milder than the old outbox-empty gate (a merely
  *queued*, not-yet-draining edit no longer blocks a poll), bounded by how fast one
  person can type/tap, self-corrects the moment editing pauses, and no other list is
  affected.

## Required reading for implementation planning

- `plans/T3-task-design.md` (§Interfaces, §Pseudo-code, §Decisions) — the sync
  service this extends: the per-list `_draining`/`_pending` push state that the poll's
  `_canReconcile` gate reads, the existing `SyncStatus` transitions that `offline`
  slots into, the drain's network-failure handling to extend with the swallow-and-mark-
  `offline` path, the reconcile-mutation cache/notifier coherence pattern to mirror in
  `reconcileFromServer`, and `_pushOne`'s `readItem(...)!` (why a dirty item must never
  be pull-deleted).
- `plans/T2-task-design.md` §3–§8 — the store, the last-acked-version / dirty
  contract (pull must not advance last-acked for dirty items), tombstones, and
  active/Done sectioning + `(position, localId)` ordering for inserted remote items.
- `docs/ADRs/0003-shopping-list-full-refresh-over-delta.md` — the full-pull-and-diff
  decision and the canonical per-item diff rules this task implements.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — the T3
  service to extend (lane guard, `SyncStatus`, app-lifecycle observer, `dispose`).
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — the
  reconcile-mutation pattern (`_residentListId`, one `_dao.transaction`, notifier
  refresh) and the item-write HTTP style to mirror for `fetchServerItems`.
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` —
  `openShoppingList` / `loadShoppingListDetail` to reorchestrate for the single-GET
  seed, and the item-listener/dispose wiring.
- `mobile/lib/features/shopping_list/shopping_list_item_widget.dart` — the
  `didUpdateWidget` guard and `_focusNode` to change for Option A; `ItemDisplayData`
  to give `hasSameContentAs`.
- `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — where the
  indicator mounts (line-482 `TODO(..., T4)`), the `initState` calls to reorchestrate,
  and `_showRejectionToast` (lines 73–84) as the toast path `onOverwrite` reuses.
- `../hld.md` §1.3–§1.4 (poll = `GET`, cold start), §2.3 (the diff + active-edit
  overwrite), §2.4 (indicator vs failure surface), §3 (layer responsibilities &
  poller lifecycle).