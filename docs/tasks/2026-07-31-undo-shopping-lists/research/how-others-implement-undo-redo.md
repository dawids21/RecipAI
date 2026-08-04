# How Other Developers Implement Per-User Undo/Redo

Survey of publicly documented undo/redo implementations — product engineering
blogs, open-source repositories, library docs, and design discussions — focused
on **per-user (local) undo**: each user undoes only their own actions, and one
user's undo never rewinds another user's work. This is research into what other
people did and why, not a design proposal for this project.

## Summary

Almost everyone who ships undo converges on the same core: the **Command
pattern** (store an inverse for each action) rather than the Memento pattern
(store state snapshots), because snapshots destroy concurrent changes made by
other users and cannot express side effects such as server calls. Per-user
isolation is achieved not by a clever algorithm but by a boring one — **the undo
stack is client-local and only records actions the local user originated** (Yjs
tags every change with an origin and tracks only its own; Excalidraw, Liveblocks
and Yorkie all keep "local-only" stacks). The genuinely hard parts, consistently
reported across sources, are three: **conflicts** (the thing you want to undo was
changed by someone else in the meantime), **grouping** (one user gesture must be
one undo step), and **deletes** (undoing a delete means resurrecting an entity
the server may consider gone). Products differ mainly in how they resolve
conflicts: skip the entry, rewrite the entry, or apply it anyway and let last-
write-win.

## Key findings

- **Command over Memento is near-universal.** Liveblocks, Contentsquare, the
  `undo` Dart package, rocicorp/undo, NSUndoManager and Isaac Hagoel's widely
  cited write-up all store `(do, undo)` function pairs. Snapshot-restore is
  explicitly rejected for collaborative or side-effecting apps.
- **"Per-user" is the *easy* undo model, and that is the point.** The academic
  taxonomy calls it **local undo** (undo your own operations in reverse order) as
  opposed to **global undo** (undo anyone's) and **selective undo** (undo an
  arbitrary past operation). Yorkie chose local-linear explicitly because
  "users can undo and redo only their own work" was all they needed.
- **Isolation mechanism = origin tagging.** Yjs stamps every transaction with an
  `origin` and the `UndoManager` only captures transactions whose origin is in
  `trackedOrigins`; remote changes are simply never pushed onto the stack.
- **Undo entries are deltas, not states.** Excalidraw's history stores invertible
  increments — a `deleted`/`inserted` pair of partial objects per changed
  property — inverted by swapping the two halves. This makes an undo touch only
  the properties the user actually changed, so a concurrent edit to a *different*
  property survives.
- **Conflict handling is the real design decision**, and there are four observed
  strategies: rewrite history at undo time (Figma), rebase the entry against
  current values (Excalidraw), drop conflicting entries with a subtle UI notice
  (Hagoel), or track "ownership" and let the last writer own the undo right.
- **Grouping is mandatory, and everyone does it differently**: time-window
  coalescing (Yjs `captureTimeout`, default 500 ms), explicit pause/resume around
  a gesture (Liveblocks), explicit `startGroup`/`endGroup` (rocicorp/undo,
  `Change.group()` in the Dart `undo` package), run-loop-cycle auto-grouping
  (NSUndoManager), or bundling by a per-interaction UUID (Contentsquare).
- **Async undo must be serialised.** Both Hagoel and the offline-first literature
  independently land on running undo/redo operations through a serial queue,
  because out-of-order promise completion corrupts the stack.
- **Undoing a delete is the expensive case.** Figma keeps the full property data
  of deleted/reparented objects **in the client's undo buffer, not on the
  server**, so the client can resurrect them alone. The server-side alternative is
  soft delete + an explicit undelete call (Google's AIP-164).
- **Bulk actions must be decomposed.** Hagoel's concrete example: a
  `completeAllItems` action cannot be undone as one opaque command — you must
  record which items it actually changed, or undo will "uncomplete" items that
  were already complete.
- **Many apps ship the 5% version instead**: a single-step snackbar undo on
  destructive actions, no stack at all. This remains the dominant mobile pattern.

## Details

### 1. Memento vs Command — and why Command wins

The two textbook options are **Memento** (snapshot the state, restore it later)
and **Command** (record the operation plus the information needed to invert it).
Snapshotting is trivially easy and is what most tutorials show — two stacks of
whole-state copies.

Every source that has actually shipped undo in a non-trivial or multi-user app
rejects it:

- Hagoel: Memento "doesn't deal with side-effects", and wholesale state
  replacement "breaks collaborative editing by erasing concurrent changes across
  different properties".
- Liveblocks makes the same call — store the *opposite command*, per user.
- Contentsquare found even a Redux-style store insufficient: one user action fans
  out into several asynchronous NgRx actions with interdependent side effects, so
  "store the state before" is not well-defined at any single instant.

The practical consequence is that undo is not a feature you bolt on. Hagoel's
summary is the most quoted line in this space: *"undo-redo is one of those
features that make every other feature in your app more complicated"* — every
new mutation must ship with a correct inverse, including its server effects.

### 2. The taxonomy: local, global, selective

The collaborative-editing literature (and the CSCW "General Multi-User Undo/Redo
Model") distinguishes:

- **Local undo** — undo *your own* last operation. Standard Ctrl+Z semantics.
- **Global undo** — undo the last operation by *anyone*. Almost universally
  rejected as bad UX: your undo silently reverts a colleague's work.
- **Selective undo** — undo an arbitrary operation from anywhere in the history
  (per-user history undo, region undo, multi-operation undo). This is where the
  academic complexity lives (site IDs, state vectors, operation transformation
  against everything that followed).

The "per user, not shared" requirement is **local undo**, which is by far the
cheapest of the three. Yorkie's issue thread makes this explicit: they picked
local-linear and deferred selective undo indefinitely. Collabs' documentation
gives the same advice — start with local undo, treat everything else as advanced.

Note the vocabulary trap: **local undo is still "linear"** in the sense that each
user pops their own stack LIFO. What varies independently is whether the *history
model* is linear or branching (see §8).

### 3. How real systems isolate one user's stack

**Yjs (`Y.UndoManager`)** — the clearest mechanism. Every mutation happens inside
`doc.transact(fn, origin)`; the origin is an arbitrary identifier. The manager is
constructed with `trackedOrigins`, and only transactions whose origin is in that
set become stack items:

```javascript
doc.transact(() => { ytext.insert(0, 'abc') }, userIdentifier)
const undoManager = new Y.UndoManager(ytext, { trackedOrigins: new Set([userIdentifier]) })
```

Remote users' changes carry different origins, are never captured, and therefore
cannot be undone locally. The same mechanism doubles as a filter for
*programmatic* changes you don't want in history (imports, migrations) — just
give them an untracked origin. `deleteFilter` narrows it further per item.

**Excalidraw** (PR #7348, shipped 2024) — "calculating invertible increments and
storing them inside **local-only** undo/redo stacks". A `Store` keeps an
always-current snapshot of committed state; changes are diffed against it into
deltas and emitted as increments, and history consumes those. Remote changes that
touch elements the local user is mid-editing update the snapshot **without
generating a delta**, so they never enter local history.

**Liveblocks** — `room.history` is per client by construction: "a user may only
undo or redo their own changes". Their history also stores non-document state
(selection, zoom, page focus) so undo restores the user's *context*, not just the
data — a detail most implementations miss.

**Figma** — per-user undo over a shared document, with an explicit design
principle: *if you undo a lot, copy something, and redo back to the present, the
document should not change.* The naive "redo puts back exactly what I did" would
clobber colleagues' newer edits.

**Yorkie** — same conclusion, driven by UX: prevent one user cancelling another's
edits.

### 4. Conflicts: someone else touched it since

This is where implementations genuinely diverge.

| Strategy | Who | Behaviour |
| --- | --- | --- |
| **Rewrite history at use time** | Figma | "An undo operation modifies redo history at the time of the undo, and likewise a redo modifies undo history at the time of the redo." Your undone change resurfaces *above* their newer one rather than blindly restoring an old snapshot. |
| **Rebase the entry** | Excalidraw | On applying an undo/redo entry, conflicting properties are detected and the **redo entry is updated with the latest conflicting values**, keeping the pair consistent with reality. |
| **Drop conflicting entries** | Hagoel | Each `UndoEntry` carries optional `hasUndoConflict()` / `hasRedoConflict()` predicates; the manager pops conflicting heads (plus same-scope siblings) until it finds an applicable one, and notifies the UI subtly. Explicitly a reaction to Figma's UX of "tapping undo repeatedly through failed operations". |
| **Ownership** | Hagoel | "The last user who directly modified a piece of data owns it" — owners may undo safely; non-owners are the conflict case. |
| **Accept approximation** | Collabs | Undo is *semantic*, not exact: "these undo operations are approximate; they do not give the exact same result as if the operation never happened." |

Two structural techniques make conflicts rarer rather than resolving them:

- **Property-level deltas** (Excalidraw): undoing a name change does not clobber a
  concurrent quantity change on the same entity.
- **Position-based, not index-based, references** (Collabs): an undo entry for a
  list insertion must record the item's stable identity, because indices shift
  under concurrent edits. Collabs also recommends `archive()`/`restore()` instead
  of `delete()` precisely so an undo has something to point at.

### 5. Grouping and coalescing

One user gesture must be one undo step. Observed mechanisms, roughly in order of
explicitness:

- **Time window** — Yjs `captureTimeout` merges edits within 500 ms by default
  (`0` to capture every change); `stopCapturing()` forcibly breaks the run.
- **Gesture bracket** — Liveblocks `pause()` on pointer-down, `resume()` on
  pointer-up; every intermediate drag position collapses into one entry.
- **Explicit group** — `startGroup()`/`endGroup()` assigning a shared `groupID`
  (rocicorp/undo pops recursively until the groupID changes); `Change.group()` in
  the Dart `undo` package for multi-field form edits.
- **Event-loop cycle** — NSUndoManager opens a group on the first registration in
  a run-loop cycle and closes it at the end of the cycle, automatically; nested
  groups are supported. Notably, *even a single action is packaged as a group*.
- **Correlation ID** — Contentsquare tags every framework action produced by one
  user interaction with the same UUID `actionId`, then keeps two parallel arrays
  (forward bundles, reverse bundles). This is the pattern to steal when one user
  action fans out into many internal state transitions.

### 6. Async, servers, and offline queues

For anything that talks to a server, sources agree on two rules:

1. **Undo/redo must run through a serial executor.** Hagoel: async operations
   "can complete out-of-order", so the manager needs a queue that runs entries
   sequentially. The offline-first literature reaches the same conclusion for
   sync operations generally.
2. **Undo should go through the same mutation path as the original action**, not
   around it. rocicorp/undo's whole design is that "undo/redo actions are just
   functions" — in Replicache you put `rep.mutate.putTodo()` in the `execute`
   half and `rep.mutate.deleteTodos()` in the `undo` half, so undo inherits
   offline queueing, retry and rollback for free. The library itself knows
   nothing about sync.

The offline-first pattern that supports this: mutations are appended to a local
queue/outbox, requests are idempotent with idempotency keys, records are
versioned for conflict detection, and "mutation types hold enough information
about the prior state to calculate the inverse operation — for example, a splice
operation holds a copy of deleted items". That last clause is the crucial one: an
outbox entry designed for sync usually does **not** carry the pre-state, and must
be extended (or paralleled by an undo log) if it is to be invertible.

Also documented but not recommended by anyone: server-side undo (the server keeps
a command log and exposes an "undo last" endpoint). No surveyed product does this
for interactive undo; it appears only as soft-delete/undelete for destructive
operations (§7).

### 7. Deletes, tombstones and resurrection

Undoing a delete is the case that leaks past the client:

- **Figma**: property data for deleted or reparented objects is retained **in the
  client's undo buffer rather than on the server**, so the client can restore the
  complete object by itself. Cheap for the server, but the undo stack now holds
  real data and is lost with the tab.
- **Google AIP-164 (soft delete)**: the server keeps the row with `delete_time`
  and `purge_time`, hides it from `List` by default (unless `show_deleted`),
  still returns it from `Get`, and exposes an explicit `Undelete` POST. Rationale
  is precisely "recovery from mistakes". Trade-off noted: IDs stay reserved and
  declarative clients get a worse experience.
- **CRDT flavour** (Collabs): use `archive()` rather than `delete()` so undo is
  `restore()`, keeping a stable identity for the entry to reference.

If undo must survive the entity being genuinely gone server-side, the undo entry
has to be a *re-create* command carrying the full payload — which then produces a
new server ID, and any other client's references to the old ID are stale.

### 8. Scope, persistence and limits

- **Session/tab scope is what users expect.** Hagoel: undo should span a session
  "within a single tab or window"; users do *not* expect a shared undo stack
  across devices, and crossing that boundary is confusing.
- **Persistence across restart is generally not done.** The common advice is to
  discard history on restart; if you must persist, store deltas (not states),
  cap the stack (~50 entries is the usual figure), and serialise compactly.
- **Stack limits are standard.** NSUndoManager exposes `levelsOfUndo`; general
  command-pattern guidance is to bound history to avoid unbounded memory.
- **Linear vs branching history.** Branching (Google Slides): undo a few steps,
  make a new edit, and the redo branch becomes unreachable. Linear "history undo"
  (Emacs origin): every operation is a point on a timeline you can walk backwards
  without ever losing a branch. Hagoel argues for linear as the better default UX.

### 9. The pragmatic alternative: snackbar undo

Worth stating because it is what most mobile list apps actually ship: no stack,
one step, destructive actions only. Two implementations exist, and the community
consensus favours the second:

1. **Defer the delete** until the snackbar times out. Simple, but feels laggy and
   breaks down when deleting several items quickly.
2. **Optimistic delete with a backup copy** — remove from UI/store immediately,
   stash the removed record in a variable, restore it if the user taps Undo, and
   commit permanently on snackbar timeout. Android's FlexibleAdapter `UndoHelper`
   is the canonical implementation: it extends `Snackbar.Callback` and fires
   `onUndoConfirmed` or `onDeleteConfirmed` accordingly.

This is a strictly weaker feature than an undo stack, but it costs a day rather
than a quarter and covers the majority of real "oops" moments.

### 10. Off-the-shelf building blocks (Dart/Flutter)

- **`UndoHistory` / `UndoHistoryController` (Flutter SDK)** — built in, exposes
  `canUndo`/`canRedo` and `undo()`/`redo()`. Designed for text editing, but the
  `UndoHistory` widget can wrap any interactive subtree (the documented example is
  strokes on a drawing canvas). Value-oriented: it manages a stack of values,
  not commands.
- **`undo` package (pub.dev)** — Command pattern. `ChangeStack` + `Change`
  objects, each `Change` being (old state, execute logic, undo logic);
  `Change.group()` batches; `SimpleStack<T>` wraps it for the single-state case.
  Zero dependencies, integrates with `ChangeNotifier` by calling
  `notifyListeners()` inside the callbacks, and claims architecture-neutrality
  (ChangeNotifier / Bloc / Riverpod).
- **rocicorp/undo (TS, but the design transfers)** — `add()` takes either
  `{redo, undo}` or `{execute, undo}`; groups via `startGroup`/`endGroup`. The
  useful idea is that the manager is deliberately ignorant of what the functions
  do, so it composes with any sync layer.

Note that none of these solve the multi-user question — rocicorp/undo has an open
issue precisely about lacking multiplayer support, and its per-client stacks
"could create consistency issues" under concurrent edits. Per-user isolation and
conflict handling remain application-level work in every library surveyed.

## Applicability notes for this project

These are inferences from the survey against the shopping-list architecture
described in `docs/tasks/2026-07-18-shopping-items-mobile-aggregate/research/`,
not findings from the sources:

- Shopping lists are **shared** (`shopping_list_permission`), so this is a
  multi-user problem, not a single-player one — the Yjs/Excalidraw/Figma
  isolation lessons apply, not just the todo-app tutorials.
- The existing **outbox + `dirty` + `localId`** machinery is the right substrate:
  an undo should enqueue an inverse mutation through the same `apply*` path
  (rocicorp/undo's model), inheriting offline queueing and reconcile for free.
  The gap is that outbox entries record the *new* value, not the prior one.
- Item-level deltas (Excalidraw) map cleanly onto per-item mutations
  (`applyEdit`, `applyChecked`, `applyReorder`), and `localId` gives the stable
  identity Collabs insists on for list positions.
- Bulk actions (`deleteAllChecked`, `uncheckAll`) are exactly Hagoel's
  decomposition warning — they must record the affected item set, and per §5 be
  grouped into one undo entry.
- `applyDelete` is the case needing a product decision (client-held payload à la
  Figma vs. server soft-delete à la AIP-164).

## Open questions / gaps

- **No public write-up was found** for undo in a plain CRUD-with-REST mobile app
  with shared entities. The detailed case studies are all either collaborative
  document editors (Figma, Excalidraw, Yjs, Liveblocks) or single-user desktop
  apps. The middle ground — a shared list synced over ordinary REST endpoints —
  is undocumented, so the sync-path design has to be derived rather than copied.
- **Nobody publishes their conflict UX in detail.** All four strategies in §4 are
  described mechanically; none of the sources report user-testing results on
  which one users actually understand.
- **Notion, Linear and Todoist have no public engineering write-ups on undo**
  despite all shipping it; searches surfaced only end-user help articles.
- **Cross-device undo** is universally dismissed as unexpected, but no source
  tested that assumption — it is asserted, not measured.
- **Redo semantics after a conflict** are the least documented area: whether the
  redo stack should be cleared, rewritten (Figma/Excalidraw) or left stale is
  handled differently everywhere and justified nowhere.
- **How much of the undo stack should survive app backgrounding** on mobile
  specifically (as opposed to a browser tab) is not addressed by any source.

## Sources

- [You Don't Know Undo/Redo — Isaac Hagoel, DEV](https://dev.to/isaachagoel/you-dont-know-undoredo-4hol)
  — the most complete practitioner write-up: linear vs branching history, session
  scope, ownership model, the `UndoEntry` type with conflict predicates, serial
  async executor, bulk-action decomposition.
- [How Figma's multiplayer technology works — Evan Wallace](https://madebyevan.com/figma/how-figmas-multiplayer-technology-works/)
  ([Figma blog version](https://www.figma.com/blog/how-figmas-multiplayer-technology-works/))
  — the undo-copy-redo principle, history rewriting at undo time, deleted-object
  payloads held in the client undo buffer.
- [feat: multiplayer undo / redo — excalidraw/excalidraw PR #7348](https://github.com/excalidraw/excalidraw/pull/7348)
  ([history.ts](https://github.com/excalidraw/excalidraw/blob/master/packages/excalidraw/history.ts))
  — local-only stacks, invertible `deleted`/`inserted` deltas, Store snapshot,
  filtering remote changes, rebasing redo entries on conflict.
- [How to build undo/redo in a multiplayer environment — Liveblocks](https://liveblocks.io/blog/how-to-build-undo-redo-in-a-multiplayer-environment)
  — client-specific command stacks, `pause()`/`resume()` gesture coalescing,
  storing selection/zoom alongside data.
- [Y.UndoManager — Yjs docs](https://docs.yjs.dev/api/undo-manager)
  ([source](https://github.com/yjs/docs/blob/main/api/undo-manager.md))
  — `trackedOrigins` as the per-user isolation mechanism, `captureTimeout`,
  `stopCapturing()`, `deleteFilter`, stack-item events and `.meta`.
- [Undo/Redo — Collabs documentation](https://collabs.readthedocs.io/en/latest/advanced/undo_redo.html)
  — local undo as the recommended starting point, semantic inverses,
  position-not-index references, `archive()`/`restore()`, the "approximate undo"
  caveat.
- [Support Multi-User Undo/Redo — yorkie-team/yorkie issue #49](https://github.com/yorkie-team/yorkie/issues/49)
  — a team explicitly choosing local-linear undo over selective, and citing
  Automerge and Yjs as references.
- [Rewriting History: Adding Undo/Redo to Complex Web Apps — Contentsquare Engineering](https://engineering.contentsquare.com/2023/history-undo-redo/)
  — Memento/Command hybrid, bundling fan-out actions under a per-interaction
  UUID, two parallel arrays of forward/reverse bundles.
- [rocicorp/undo](https://github.com/rocicorp/undo)
  ([multiplayer gap, issue #12](https://github.com/rocicorp/undo/issues/12))
  — `UndoRedo`/`ExecuteUndo` entries, `startGroup`/`endGroup`, undo expressed as
  ordinary Replicache mutations so it inherits offline sync; no multiplayer story.
- [A General Multi-User Undo/Redo Model (CSCW)](https://dl.eusset.eu/bitstreams/38450f07-8a9a-41b9-a2bc-a9cd8079a3e2/download)
  and [A multi-user selective undo/redo approach for collaborative CAD systems](https://academic.oup.com/jcde/article/1/2/103/5743536)
  — the local / global / selective taxonomy and why selective undo needs site IDs
  and state vectors.
- [UndoManager — Apple Developer (archive)](https://developer.apple.com/library/archive/documentation/General/Conceptual/Devpedia-CocoaApp/UndoManager.html)
  ([GNUstep NSUndoManager reference](https://www.gnustep.org/resources/documentation/Developer/Base/Reference/NSUndoManager.html))
  — run-loop automatic grouping, nested groups, target-action vs invocation
  registration, `levelsOfUndo`.
- [AIP-164: Soft delete — Google API Improvement Proposals](https://google.aip.dev/164)
  — `delete_time`/`purge_time`, `Undelete` POST, `show_deleted` on List, and the
  trade-offs of server-side undeletable deletes.
- [undo — pub.dev](https://pub.dev/packages/undo) and
  [UndoHistoryController — Flutter API](https://api.flutter.dev/flutter/widgets/UndoHistoryController-class.html)
  — the two off-the-shelf Dart/Flutter options: Command-pattern `ChangeStack` vs
  the SDK's value-oriented `UndoHistory` widget.
- [Step by Step: RecyclerView Swipe to Delete and Undo — Zachery Osborn](https://medium.com/@zackcosborn/step-by-step-recyclerview-swipe-to-delete-and-undo-7bbae1fce27e)
  and [FlexibleAdapter UndoHelper](https://github.com/davideas/FlexibleAdapter/wiki/5.x-%7C-UndoHelper)
  — the snackbar-undo alternative: optimistic removal with a backup copy,
  commit-on-timeout via `Snackbar.Callback`.
- [A Design Guide for Building Offline First Apps — Hasura](https://hasura.io/blog/design-guide-to-offline-first-apps)
  and [Undo — dotchain](https://dotchain.github.io/Undo.html)
  — mutation queues, idempotency keys, versioning, and "mutation types must hold
  enough information to calculate the inverse".
- [Implementing undo/redo with the Command Pattern — Gernot Klingler](https://gernotklingler.com/blog/implementing-undoredo-with-the-command-pattern/)
  and [bertilmuth/todolist](https://github.com/bertilmuth/todolist)
  — baseline Command-pattern mechanics and the adapter approach that keeps undo
  logic out of the domain classes.
