# Shopping List Items — High-Level Design

This document describes **what each part of the app must do** to satisfy
`requirements.md`. It stays at the level of responsibilities, data shapes, and
interactions — it is **not** an implementation plan and deliberately avoids
class names, method signatures, SQL, and library choices except where a choice
is itself a design decision the requirements force.

Where this document and `research/list-syncing.md` disagree, **the requirements win**.
The decisions that shaped this design are summarised in §0.

---

## 0. Decisions that shaped this design (read this first)

These were settled with the user; the rest of the document assumes them.

1. **First action wins, not LWW.** The research proposes Last-Write-Wins; the
   requirements mandate the opposite (req §2): a change made against a stale view
   of an item is **rejected**, discarded on the loser's device, and rolled back
   to the winning value. This design therefore uses **optimistic concurrency
   (compare-and-reject)**:
   - Every item carries a **version**. A change declares the version it was based
     on.
   - The server accepts **only if** the item is still at that version, then bumps
     the version. If the item moved on, the change is **rejected (HTTP 412
     Precondition Failed)** and
     the winner returned. LWW timestamps are **not** the conflict mechanism.

2. **Whole-item gate.** Any concurrent change to an item rejects the incoming one
   (not field-level). Matches req §2's "uniform" language and §3.6's "rejected
   together."

3. **No delta-pull, no list counter.** Lists are small (~30–40 items), so the
   client re-fetches the **whole list** on each poll and **diffs it locally**
   (§2.3). This was chosen specifically so that **writes to different items never
   touch a shared object** — a per-list change counter would have serialised all
   writes on one row and made different-item edits contend, violating req §2.4 /
   §2.7. There is therefore **no `seq`, no cursor, and no `change_counter`.**

4. **Hard deletes.** A delete is version-gated like any write; once it wins, the
   row is removed and the next full pull simply omits it. No soft-delete column
   is added. Future merge (req §5) is not precluded — provenance can be added
   later if needed.

5. **Local persistence may exceed `SharedPreferences`.** The full list state plus
   an unbounded pending-change queue surviving offline restarts (req §3.2 / §3.5)
   is a database-shaped need; a local database is appropriate. The persistence
   standard is being relaxed to allow this (§2.1).

6. **Append-only outbox, no collapse.** Pending changes are **immutable per-edit
   entries**, never merged; per item they push **FIFO, one in flight**, with the
   base version **read from the item at push time** rather than frozen into the
   entry. This kills a lost-update race the earlier *collapse* design had (a
   queued entry mutated mid-push could drop the concurrent edit or self-reject).
   req §3.6's "rejected together" is preserved via **cascade-discard on 412**
   (§2.2). Trade-off: N edits to one item become N sequential pushes, so peers may
   briefly see intermediate values — fine at 30–40 items. The mechanism is in §2.

Everything else in the research (state-based store, local outbox, room for a
future merge) is compatible and is kept.

---

## 1. Backend endpoints

The backend owns the **authoritative current state** of every item and is the
**sole arbiter of conflicts**. All conflict decisions happen here; the client
never decides who won.

### 1.1 Item state the backend stores

Today's `shopping_list_items` columns
(`name`, `quantity`, `unit`, `checked`, `position`, `version`) are **sufficient**
— the rewrite adds **no new item columns** and **no list-level counter**.

- The existing **`version`** is the conflict gate (per item, bumped on every
  accepted write). It is the only coordination state needed.
- **No `seq` / cursor / `change_counter`** — the client diffs a full pull instead
  (decision §0.3), so there is no shared per-list object for different-item
  writes to contend on.
- **No soft-delete column** — deletes are hard once they win (decision §0.4).
- Future merge (req §5) is not precluded: a `merged_into` provenance pointer
  *could* be added later, but is **not built now**.

### 1.2 Write endpoints — the conflict gate

The detail-level item operations the app needs. Each mutating call carries the
**base version** the user acted on, and the server applies the
**first-action-wins** gate.

- **Create item** — add a new item to a list.
  - New items **never conflict** (req §2.7). Always accepted. Server assigns id,
    initial version, `seq`, and a position. Duplicate names are allowed and not
    flagged.

- **Update item** (name / quantity / unit / checked / position, individually or
  together) — one endpoint that takes the target item id, the **base version**,
  and the new field values.
  - Accept **iff** the stored item is still at the base version → apply, bump
    version, return the new authoritative item.
  - Otherwise **reject (412)** and return the **current winning item** so the
    client can roll back and notify. This single gate covers name/quantity/unit
    edits, **check/uncheck**, and **reorder/position** uniformly (req §2.2).
  - Because a client may have batched several offline edits to one item into one
    pending change, a rejection rejects **the whole change for that item**, not
    field-by-field (req §3.6).

- **Delete item** (hard) — takes item id and base version.
  - First-action-wins vs. edit (req §2.6): if the item is still at the base
    version, **remove the row**. If it already moved on (someone edited it
    first), **reject (412)** — the edit wins and the item stays. The next full
    pull simply omits a removed item, and clients drop it locally.

- **Reorder** uses the same **Update item** path, changing only `position`. A
  **fractional position** is used so moving one item rewrites only that item's
  row — two users moving *different* items both succeed; only moving the *same*
  item concurrently conflicts (req §2.4).

> Bulk actions (delete-all-checked, uncheck-all — req §6) are **not** special
> endpoints. The client expands them into individual per-item Update/Delete
> calls, each independently subject to the gate, so partial outcomes are
> natural.

### 1.3 Receiving others' changes — full pull, no delta endpoint

req §7 asks the app to fetch "what has changed since last sync … not re-download
the entire list each time." Its stated purpose is to **meet the freshness target
without unnecessary data transfer**. At ~30–40 items per list, a full list fetch
*is* cheap, so this design satisfies the intent without a delta protocol:

- **No new "changes since cursor" endpoint.** The existing
  **`GET /shopping-lists/{id}`** (full current item set) is the poll endpoint.
- The client polls it every **~10–30s** while a list is open (req §1.4) and on
  reconnect, then **diffs the result against its local store** (§2.3) to decide
  what to update, insert, or delete locally.
- This is purely about **delivery**. Who wins was already decided at write time
  by the version gate (§1.2).

> Trade-off vs. req §7's literal wording: we re-download the whole (small) list
> rather than a delta. This is deliberate — a delta needs a shared per-list
> ordering object, which would make different-item writes contend and break
> req §2.4/§2.7 (decision §0.3). For 30–40 items the bandwidth cost is negligible.

### 1.4 Initial load

- **`GET /shopping-lists/{id}`** is also the cold-start / first-open path:
  the full current item set. It is the same endpoint used for polling (§1.3),
  so no separate initial-load call is needed.

### 1.5 What the backend does *not* do

- It does **not** push (no websockets / realtime) — clients poll
  `GET /shopping-lists/{id}` (req §1.3 allows lag).
- It does **not** expose a delta/changes endpoint, store a `seq`/cursor, or keep
  a per-list change counter (decision §0.3).
- It does **not** soft-delete (decision §0.4).
- It does **not** merge similar items in this rewrite (req §5); only the option
  to add a `merged_into` pointer later is preserved.
- It does **not** retry or queue on the client's behalf — retry is the client's
  job (§3 below).

---

## 2. Local store + sync mechanism (mobile)

The mechanism has **two local layers**, exactly as the research frames it, but
wired to a **reject-based** server:

1. **Local state store** — the current state of every item in every open list.
   This is the **source of truth for the UI**, which is what makes edits instant
   and fully functional offline (req §1.2, §3.1, §3.3). Each local item tracks
   its **last-acked server version** (the version the server last confirmed for
   it — the base for the next push, §2.2) and its **dirty** flag.

2. **Local outbox (pending-change queue)** — an append-only, per-device queue of
   **immutable per-edit entries** (item id + new values + a monotonic **seq** for
   ordering). Entries are **never merged** — editing the same item again appends
   another. They store **no base version**; it is read from the item at push time
   (§2.2). Drives push and retry; once the server acks an entry it is removed.
   (req §3.8: per-device queue.)

### 2.1 Persistence

Req §3.2 demands that **both the list contents and the pending-change queue
survive an app restart while offline**, and req §3.5 puts **no bound** on queue
size. A growing queue plus full per-list item state is a database-shaped problem,
not a key/value-blob one, so the local state store and outbox are backed by a
**local database**.

The persistence standard previously named `PreferencesService`
(SharedPreferences) as the *only* approved local store; that restriction is being
relaxed (the over-strict sentence removed) so a local database is permitted here.
`PreferencesService` remains the right tool for small key/value preferences.

### 2.2 How a single local change flows

1. **Write locally, instantly.** Update the local item, mark it **dirty**, bump
   its local view, re-render from the local store. Works offline.
2. **Enqueue intent.** **Append** a new immutable entry (seq + new values) to the
   outbox — see §2 layer 2 for its shape. No base is stored; it is read at push
   time (step 3).
3. **Push (background), FIFO per item.** When online, drain the outbox to the
   matching write endpoint. For a given item, push entries **in order, one in
   flight at a time**, sending as the base the item's **last-acked server
   version** read at that moment.
4. **Reconcile on accept.** Server returns the new authoritative item → adopt its
   version as the item's **last-acked version** and drop that entry. If more
   entries for the item remain, the next pushes against the just-adopted version
   — which is why a device never self-conflicts with its own earlier edit. Clear
   the item's **dirty** flag only once **no** entries for it remain.
5. **Roll back on reject (412) — cascade-discard.** Server returns the winning
   item → overwrite the local item with the **winning value**, then drop the
   rejected entry **and every later queued entry for the same item** (the whole
   offline set loses together — req §3.6). Rejections are **discarded, never
   retried** (req §4). Raise **one** per-item rejection notification (req §2.3).
6. **Offline stalls self-reschedule.** A push that fails on connectivity (not a
   412) is **not** retried with backoff (offline ≠ transient failure); instead
   it re-kicks itself via two triggers, so a closed list flushes without
   requiring an app resume: the poller re-kicks the drain on every successful
   poll (fast path when the list is open), and a fixed ~10s offline timer
   re-kicks it directly (covers closed lists, which don't poll). Neither
   trigger counts toward the transient-retry budget or the failed state
   (§2.4).

### 2.3 Pulling others' changes — full-list diff

- A **background poller**, running while a list is open, calls
  `GET /shopping-lists/{id}` every **~10–30s** (req §1.4) and on reconnect, and
  **diffs the returned full list against the local store**:
  - **Item present on server, not dirty locally** → adopt the server value
    (version + fields). This is how other users' changes appear.
  - **Item present on server, dirty locally** (unsynced local changes are still
    in the outbox) → **keep the local value**, and **do not touch the item's
    last-acked version** — it is advanced only by this device's own acks (§2.2
    step 4), never by a pull. Let the queued entries resolve it (accept or 412).
    Don't let a pull clobber a change the server hasn't ruled on yet.
  - **Item on server but missing locally** → insert it.
  - **Item local but missing from server** → it was deleted by someone else;
    remove it locally — **unless** it is a locally-pending *create* not yet
    pushed, which must be kept.
- **Interaction with active editing (req §3.7):** if the diff adopts a server
  value for an item the user is **actively editing** (and that item isn't dirty —
  e.g. they're mid-edit but haven't committed), the field is **overwritten
  immediately** with the server value and a toast shown; the user re-edits on
  top. A pulled value is already committed on the server, so it is the winner.

### 2.4 Sync-state indication & failure (req §3.4, §4)

- **List-level indicator** only: show a subtle "offline / not synced" indicator
  whenever there is no connectivity **or** the outbox is non-empty. No per-item
  *pending* markers.
- **The drain is the sole writer of sync state** — the poller never sets it, so
  poll and push can never race each other into a stale indicator. One accepted
  tradeoff follows: an idle device (empty outbox) that goes offline keeps
  showing the tick until the next local mutation, whose drain then flips it to
  offline. This is acceptable — the tick means "no pending local changes",
  which stays true while idle-offline.
- **Transient push failure** (server unreachable / flaky network, *not* a 412):
  retry a few times with backoff; if still failing, the **sync state** becomes
  **failed**.
- **Failure surface — single persistent bottom toast, no per-item marker.** While
  the **sync state** is **failed**, show **one persistent toast/banner pinned to
  the bottom** of the screen carrying a **"retry all"** button that re-pushes
  the pending changes at once. There is **no per-item failed marker**; the toast
  is the only failure affordance and disappears once the sync recovers.

  > **Deviation from req §4.** Requirements §4 calls for a *per-item* failed
  > marker plus a retry-all action. By decision, this design drops the per-item
  > marker and relies solely on the one persistent bottom toast with retry-all.
  > The retry-all semantics (re-attempt every failed change together) are
  > unchanged.
- A **412 is not a failure** — it is resolved (§2.2 step 5) and never retried.

### 2.5 Conflict model summary (mechanism ↔ requirement)

| Requirement | Mechanism |
|---|---|
| First action wins (§2.1) | Server version gate; later change → 412 |
| Reject → discard + roll back to winning value (§2.1) | §2.2 step 5 |
| Uniform across edit/check/reorder/delete (§2.2) | All routed through the versioned Update/Delete gate |
| New items never conflict (§2.7) | Create endpoint always accepts |
| Reorder: different items both succeed (§2.4) | Fractional position; only same-item move conflicts |
| Multiple offline edits to one item rejected together (§3.6) | Append-only entries + **cascade-discard**: a 412 on one drops every later queued entry for that item (§2.2 step 5) |
| Remote change wins during active edit (§3.7) | Pull overwrites field + toast |

---

## 3. How the app interacts with the sync mechanism

The detail screen never talks to the backend or the outbox directly. It follows
the project's **Repository → Service → View** architecture:

- **Repository** — stateless data access: the HTTP item endpoints (§1) **and**
  the local store / outbox reads and writes. No business logic.
- **A sync service** — owns the background loop: drains the outbox (push), polls
  `GET /shopping-lists/{id}` and diffs it into the local store (pull, §2.3), runs
  retry/backoff, tracks list-level sync state, and emits
  **rejection notifications**. Exposes sync state read-only via
  `ValueNotifier<AsyncValue<…>>`.
- **The detail service** — owns the **list-of-items** UI state for the open
  screen, read from the local store. User actions (§4) call into it; it writes
  locally (instant), enqueues intent, and lets the sync service carry it to the
  server. It listens to the local store so pulled/reconciled changes re-render.
- **The view** — renders via `ValueListenableBuilder`, shows the active/Done
  sections, the list-level sync indicator, the single persistent bottom
  failure toast with its "retry all" button (§2.4), and surfaces rejection toasts.

Lifecycle: when a detail screen opens, start the poller for that list; when it
closes, stop polling. Pushing of queued changes should continue regardless of
which screen is open so offline edits flush once connectivity returns.

This keeps the conflict authority on the server, the instant feel in the local
store, and the screen ignorant of sync plumbing.

---

## 4. Detail-screen user actions

Every action below has the **same shape**: apply to the local store instantly,
enqueue intent, let sync push it, and surface the outcome (accept silently /
reject via toast + rollback / failure via the persistent bottom toast). None of them block on the
network.

- **Add item** — parse the typed text into name/quantity/unit, insert locally at
  the end (active section), enqueue a create. Always eventually accepted
  (req §2.7). Clears the field and keeps focus for quick consecutive entry.

- **Edit item** (name / quantity / unit) — update locally, enqueue an update
  against the item's current version. On reject, roll back to winning value +
  toast.

- **Check / uncheck item** — toggle locally, move between **active** and
  **Done** sections immediately, enqueue an update. Subject to the same gate —
  may be rejected (req §2.5), rolled back, and toasted.

- **Delete item** — remove from the local view immediately, enqueue a delete
  against the item's current version. On reject (item was edited first), the item
  **reappears** with the winning value + toast (req §2.6).

- **Reorder item** — compute a new **fractional position** between neighbours,
  apply locally, enqueue an update of position only. Moving different items never
  conflicts; moving the same item concurrently may reject (req §2.4).

- **Delete all checked** (bulk) — locally clear the Done section instantly;
  enqueue **one delete per checked item**. Each is independently gated; some may
  reject and reappear, each with its own toast (req §6).

- **Uncheck all** (bulk) — locally move every Done item back to active instantly;
  enqueue **one update per item**. Each independently gated; partial outcomes
  possible, each toasted (req §6).

- **Retry all failed** — re-push the outstanding changes (req §4).
  Not a per-item action; triggered from the single persistent bottom failure
  toast (§2.4), the only failure affordance.

- **Open list** — load from the local store **immediately** (last-known
  contents, even offline — req §3.3); in parallel, if a cold start, fetch the
  full list (§1.4); start the poller. Show the list-level sync indicator if
  offline or the outbox is non-empty.

---

## Resolved decisions

All previously open questions have been settled with the user and folded into
§0:

- **Conflict model** — first-action-wins / reject, overriding the research's LWW
  (§0.1).
- **Gate granularity** — whole-item (§0.2).
- **Refresh strategy** — full-list pull + local diff, no delta endpoint / counter
  (§0.3). Recorded as an ADR (`docs/ADRs/`).
- **Deletes** — hard, version-gated (§0.4).
- **Persistence** — local database permitted; the over-strict persistence
  standard sentence is removed, with no new standard added (§0.5).
