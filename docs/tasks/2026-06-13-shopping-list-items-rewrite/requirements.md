# Shopping List Items — Refined Requirements

This document defines the user-facing requirements for shopping-list items: the
behaviour and rules a designer and implementer must satisfy. It states **what
the feature must do**, not *how*. Storage engines, sync protocols, conflict
mechanisms, and clock strategies are design decisions left to the design phase,
constrained by the behaviour below.

This document is self-contained. It does not assume any existing implementation.

---

## 1. Core experience

1. A shopping list is **shared between multiple users**; each may add, remove,
   check/uncheck, edit, and reorder items.
2. **Local edits are instant**, including with a poor or absent network
   connection.
3. **Sync happens in the background.** Other users' changes may lag — real-time
   propagation is not required.
4. Target freshness for other users' changes: **~10–30 seconds** while a list is
   open.

---

## 2. Conflict model — first action wins

When two users change the same item around the same time, the **first action
wins**: the change that is committed first is kept, and a later change to the
same item — based on an out-of-date view of it — is rejected.

### 2.1 Rules

- A change to an existing item is made against the version of the item the user
  was looking at.
- If that item has already moved on (someone else's change was committed first),
  the incoming change is **rejected** — the first committed change wins.
- A **rejected change is discarded** on the rejected user's device, and their
  item is rolled back to the **winning value**.
- The user who lost is **notified** (see §2.3).

### 2.2 Scope — uniform

The first-action-wins rule applies **uniformly to all changes** to an existing
item:

- name / quantity / unit edits
- **check / uncheck** (see §2.5)
- **reordering / position** (see §2.4)
- delete (see §2.6)

> **Accepted rough edge:** because check/uncheck is covered by the rule, checking
> an item off can be rejected if someone else changed that same item first. This
> is a known, accepted trade-off for a consistent model. The designer may revisit
> it if it proves painful, but the default is uniform.

### 2.3 Notification on rejection

- Show a **transient toast / snackbar per rejected change**, naming the item
  (e.g. "Milk was changed by someone else").
- The rolled-back item displays the **winning value**.

### 2.4 Reordering conflicts

- Moving one item must not require changing other items' positions, so two users
  moving **different** items both succeed.
- A conflict only arises when the **same** item is moved concurrently; the first
  move wins, the second is rejected.

### 2.5 Check / uncheck conflicts

- Treated like any other change: checking or unchecking an item that has already
  been changed by someone else is **rejected**, rolled back to the winning value,
  and the user notified.

### 2.6 Delete vs. edit

- **First action wins.** If an item was **edited before** it was deleted, the
  edit wins and the item **stays**. If the delete was committed first, the item
  is gone and a concurrent edit to it is rejected.

### 2.7 New items never conflict

- Creating an item does not conflict with anything. **All offline creates are
  accepted; both appear** when two users add items independently.
- Duplicate names (e.g. two "milk" entries) are **allowed** and **not** flagged.
  De-duplication is the job of the future merge feature (§5), which is out of
  scope here.

---

## 3. Offline behaviour

### 3.1 Instant local edits

- All edits apply to the local list **immediately**, online or offline.

### 3.2 Persistence

- **List contents AND the queue of pending (unsynced) changes must survive an app
  restart while offline.** The user can close the app offline, reopen it, still
  see the list, and still have their pending changes queued.

### 3.3 Opening offline

- Opening a shopping list with no network shows the **last-known list contents
  immediately**, with the offline / not-synced indicator, and **edits are
  allowed**.

### 3.4 Sync-state indication

- Show a **subtle "not synced / offline" indicator** at the list level when there
  are pending changes or no connectivity. Per-item pending markers are **not**
  required (except the failed-state marker in §4).

### 3.5 Offline duration

- **No limit** on how long a list may be edited purely offline. The pending-
  change queue may grow without bound and syncs whenever connectivity returns.

### 3.6 Multiple offline edits to one item

- If the user makes several offline edits to the **same** item and that item was
  changed by someone else in the meantime, the user's **whole set of pending
  changes to that item is rejected together** (not field-by-field), rolled back
  to the winning value, with one notification.

### 3.7 A remote change winning during active editing

- If a winning remote change arrives while the user is **actively editing** an
  item, the field is **overwritten immediately**, a toast is shown, and the user
  re-edits on top of the new value.

### 3.8 Multiple devices, same user

- The queue of pending changes is **per device**. Each device syncs its own queue
  independently. A change made on one device exists only there until it reaches
  the server. (Uninstalling/reinstalling before syncing loses that device's
  unsynced changes — an accepted limitation.)

---

## 4. Sync failure & retry

- On **transient** push failures (server unreachable, flaky network): **retry a
  few times**, then **mark that change as failed** and surface it.
- Failed changes are shown with a **per-item failed marker**, plus a single
  **"retry all" action** that re-attempts every failed change at once.
- A conflict rejection (§2) is **not** a failure — it is a resolved outcome and
  is discarded, not retried.

---

## 5. Merge feature — out of scope (design must leave room)

- The future auto-merge capability (combining e.g. "2 onions" + "1 onion") is
  **out of scope** for this rewrite.
- The data model and sync design **must not preclude it** — it should be possible
  to add merge later without reworking how items sync or how changes propagate.
- No manual merge and no similarity matching ship in this rewrite.

---

## 6. Checked-item lifecycle

- Checked items move to a **"Done" section** and **remain until explicitly
  deleted**. There is no automatic time-based cleanup of checked items.
- Two bulk actions are available:
  - **Delete all checked**
  - **Uncheck all**
- Bulk actions **apply locally instantly** and sync as **individual per-item
  changes**, each subject to the first-action-wins rule (§2). Partial outcomes
  are possible (e.g. one item in the batch was concurrently changed and its
  delete/uncheck is rejected), each reported via the standard per-change
  rejection toast (§2.3).

---

## 7. Receiving other users' changes

- The app must be able to **fetch what has changed in a list since its last
  successful sync** — not re-download the entire list each time — so that updates
  from other users appear within the freshness target (§1.4) without unnecessary
  data transfer.
