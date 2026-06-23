# Shopping List Items Rewrite — Tasks

**Date:** 2026-06-23
**Status:** draft

## Summary

- **T1:** Backend version-gated item endpoints (create / update / delete)
- **T2:** Mobile local store + offline detail screen
- **T3:** Mobile push sync (FIFO outbox drain, accept reconcile, 412 cascade-discard, failure toast)
- **T4:** Mobile pull sync (full-list poll + diff, others' changes, sync indicator)

## Cross-task notes

- **Sequencing.** T1 and T2 depend on nothing and can be built in parallel.
  T3 needs both; T4 builds on T3 (see below). The critical path is
  T1/T2 → T3 → T4.
- **T4 depends on T3 deliberately.** Pull and push both live in the one **sync
  service** and both write the local store. Rather than race two tasks against a
  half-built service and an unspecified last-acked-version contract, T3 stands
  the sync service up (loop, local-store reconcile, list-level sync state) and
  T4 extends it. This trades parallelism for smaller PRs and a clean push-only
  feedback moment.
- **Last-acked-version contract lands in T2.** T2 must pin down the per-item
  **last-acked server version** (the base for the next push), the **dirty**
  rule (clear only when no outbox entries remain for the item), and that a pull
  must never advance last-acked. T3 and T4 both rely on this; if it stays vague
  the T3/T4 seam gets messy.
- **No feature flag.** The prior item-management code was already deleted, so
  this is a clean rebuild, not a side-by-side migration.

---

## T1: Backend version-gated item endpoints

**User-visible outcome**

A frontend developer (curl / Postman) can create, update, and delete shopping
list items where every mutating call carries the base version, and a stale
write is rejected with **412 + the winning item** — first-action-wins, server
side.

**Scope**

- Create / Update / Delete item endpoints, each routed through the per-item
  **version gate**.
- Create always accepts (new items never conflict); Update covers
  name / quantity / unit / checked / **position** uniformly; Delete is hard and
  version-gated.
- **Fractional position** so moving different items doesn't contend.
- Reuse the existing `GET /shopping-lists/{id}` unchanged as the full-list read
  (no delta endpoint, no `seq`/cursor/counter).
- Any migration needed for fractional positions rides in this task.

**Out of scope**

- Bulk delete-all-checked / uncheck-all endpoints — **not built**; clients
  expand these into per-item calls, exercised in T3.
- Soft-delete / `merged_into` provenance — deferred.
- Any client-side retry/queue — that is the client's job, T3.

**Depends on:** none

**References**

- `docs/ADRs/0003-shopping-list-full-refresh-over-delta.md`

**How to verify**

- `curl` an Update with a stale base version → `412` whose body is the current
  winning item; a second Update at the correct version → `200` with bumped
  version.
- Two Updates changing `position` on **different** items both `200`; two
  Updates moving the **same** item — second is `412`.
- Delete at the current version removes the row (absent from next
  `GET /shopping-lists/{id}`); Delete after a concurrent edit → `412`.

**Risks / unknowns**

- Fractional-position rebalancing strategy when positions crowd — confirm the
  approach during implementation planning.

---

## T2: Mobile local store + offline detail screen

**User-visible outcome**

The end user can open a shopping list and add, edit, check/uncheck, delete, and
reorder items **instantly and entirely offline**, with all of it surviving an
app restart — no server sync yet.

**Scope**

- Local **database**-backed state store + append-only **outbox**, both
  surviving restart while offline.
- Each local item tracks its **last-acked server version**, **dirty**, and
  **failed** flags; the store defines the last-acked-version / dirty contract
  T3 and T4 rely on.
- Outbox holds **immutable per-edit entries** (item id + new values + a
  monotonic `seq`); entries are **never merged** — editing the same item again
  **appends** another. Entries store **no base version** (it is read at push
  time, in T3).
- Detail screen wired **Repository → detail service → view**: add / edit /
  check-uncheck / delete / reorder applied to the local store, with active/Done
  sections.
- "Open list" loads last-known local contents immediately, even offline.

**Out of scope**

- All server communication — push is T3, pull is T4.
- FIFO push ordering, cascade-discard, retry/backoff, failure/rejection
  toasts — T3.
- Background poller and list-level sync indicator — T4.
- Bulk actions — T3.

**Depends on:** none (parallel with T1)

**How to verify**

- With networking disabled: add several items, check some, reorder, delete one —
  every change renders instantly and appends an outbox entry.
- Force-kill and relaunch the app still offline, reopen the list → all items and
  queued outbox entries are intact and correctly ordered into active/Done.

---

## T3: Mobile push sync (outbox → server, reconcile, reject, failure)

**User-visible outcome**

The end user's offline edits propagate to the server once online; a change made
against a stale item is **rolled back to the winning value with a rejection
toast** (and any later queued edits to that item are dropped with it), and
persistent push failures surface a single bottom **"retry all"** toast.

**Scope**

- Sync service background loop drains the outbox to the T1 write endpoints
  **FIFO per item, one in flight**, sending as the base the item's **last-acked
  server version read at push time**. Different items push in parallel; only
  same-item pushes serialise.
- **Reconcile on accept**: adopt the returned version as the item's last-acked
  version, drop that entry, and clear **dirty** only once no entries for the
  item remain.
- **412 → cascade-discard**: overwrite the local item with the winning value,
  drop the rejected entry **and every later queued entry for that item**, raise
  **one** per-item rejection toast; rejections are never retried.
- **Transient failure → retry/backoff → mark failed → one persistent bottom
  toast with "retry all"** (no per-item marker — a noted deviation from the
  per-item-marker requirement).
- **Bulk actions** (delete-all-checked, uncheck-all) expanded into per-item
  gated calls with independent/partial outcomes.
- Stands up the sync service shell (loop, local-store reconcile path,
  list-level sync **state** value) that T4 extends.

**Out of scope**

- Background **polling / pulling** others' changes and the full-list diff — T4.
- Rendering the list-level offline **indicator** chrome — T4 (T3 exposes the
  state; T4 lands the poller-driven offline detection and the indicator).
- Active-edit overwrite-on-pull behaviour — T4.

**Depends on:** T1, T2

**How to verify**

- Edit an item offline, reconnect → the server reflects it
  (`GET /shopping-lists/{id}`).
- Queue two offline edits to one item, then force a concurrent server change so
  the first push hits `412` → the item rolls back to the winning value, **both**
  queued entries are dropped, and a single rejection toast appears.
- Stop the server, make an edit → after backoff the persistent bottom toast
  appears; restart the server and tap **retry all** → the change pushes and the
  toast clears.
- Delete-all-checked with one item concurrently edited → that item reappears
  with its own toast while the rest are removed.

**Risks / unknowns**

- Push ordering for a create-then-edit-then-delete sequence on one not-yet-acked
  item (the first push must establish the id/version the rest build on) — confirm
  during planning.

---

## T4: Mobile pull sync (full-list poll + diff)

**User-visible outcome**

The end user sees other people's changes appear in an open list within the poll
window, and a list-level "offline / not synced" indicator reflects connectivity
and unsynced work.

**Scope**

- Background **poller** (~10–30s + on reconnect) calling
  `GET /shopping-lists/{id}` while a list is open.
- **Full-list diff** into the local store: adopt clean / **keep dirty** /
  insert / delete-missing (except a not-yet-pushed local create). A pull on a
  dirty item keeps the local value **and never advances its last-acked
  version** — only this device's own acks do that.
- **Active-edit overwrite + toast** when a pulled value lands on a non-dirty
  item the user is editing.
- Cold-start / first-open full fetch.
- **List-level sync indicator** rendered: offline **or** outbox non-empty.
- Poller lifecycle: start on screen open, stop on close; pushing (T3) keeps
  running regardless.

**Out of scope**

- Push, reconcile, reject, retry, failure toast — all in T3.
- Any delta/changes endpoint or cursor — explicitly not built.

**Depends on:** T3 (extends the same sync service; relies on its local-store
reconcile path and sync-state surface)

**References**

- `docs/ADRs/0003-shopping-list-full-refresh-over-delta.md`

**How to verify**

- Open the list on device A; change an item from device B (or curl) → within
  the poll window the change appears on A.
- Delete an item elsewhere → it disappears from A's open list; a not-yet-pushed
  local create on A is **not** removed by a pull.
- Edit a field on A (not yet committed/dirty) while the same item is changed
  remotely → the field is overwritten with the server value and a toast shows.
- Go offline or leave the outbox non-empty → the list-level indicator shows;
  it clears once synced and online.

**Risks / unknowns**

- The "dirty locally and changed on the server" path (keep local value, don't
  advance last-acked, let the queued entries resolve it) is the subtlest case —
  worth a focused test plan.
