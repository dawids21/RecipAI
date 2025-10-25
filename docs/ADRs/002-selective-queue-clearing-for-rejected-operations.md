# ADR-002: Selective Queue Clearing for Rejected Operations

**Status:** Accepted  
**Date:** 2025-10-22  
**Deciders:** Development Team  
**Technical Story:** Shopping Lists Management - Operation Sync Conflict Resolution

---

## Context

With operation-based sync (ADR-001), multiple users can modify the same shopping list concurrently. When the client
sends operations to the server, some may be rejected due to conflicts (e.g., another user modified the same item first,
creating a stale version).

**Critical race condition identified:**

1. User performs action (op-a), which enters the queue
2. Sync begins, sending op-a to server
3. User performs another action (op-b), added to queue while sync is in progress
4. Sync completes; server may reject op-a
5. Question: What happens to op-b?

Initial consideration was "Solution 3: Full Queue Invalidation" - discard entire queue on any rejection. However, this
creates data loss:

**Problem scenario:**

- User updates Milk (op-a), then adds Eggs (op-b) during sync
- Server rejects op-a (Milk was modified by another user)
- Full queue invalidation discards both op-a AND op-b
- User loses their Eggs addition despite it being independent and valid

The system must handle:

- Operations sent and rejected (should be discarded)
- Operations added during an in-flight sync (should be preserved)
- Multiple operations on the same item in sequence
- Maintaining user's perception that their latest actions are preserved

PRD specifies "first action wins" with no user feedback for conflicts, so rejected operations should silently disappear.

## Decision

We will implement **Selective Queue Clearing with Re-application of Pending Operations:**

1. **Queue snapshot approach:**
    - When sync begins, snapshot the current queue as "operations to send"
    - This snapshot represents operations being synchronized
    - New operations added during sync are NOT part of this snapshot

2. **Post-sync queue management:**
    - Remove ONLY the operations that were in the snapshot (whether accepted or rejected)
    - Operations added during sync remain in queue
    - This preserves user actions that occurred during network round-trip

3. **State reconciliation pattern:**
    - Apply server's authoritative state as baseline (includes all processed operations)
    - Load remaining queue (operations added during sync)
    - If remaining queue is not empty: re-apply these operations on top of server state
    - Update ValueListenable with reconciled state

4. **Operation re-application:**
    - Implement pure function `_applyPendingOperations(serverState, operations)`
    - Sequentially apply each operation type (CHECK_ITEM, UPDATE_ITEM, ADD_ITEM, DELETE_ITEM, etc.)
    - Resulting state represents server truth + user's latest local changes

**Implementation guarantees:**

- User's most recent changes always visible in UI
- Server remains authoritative for all operations it has processed
- No data loss for operations created during sync
- Eventually consistent: next sync cycle will send remaining operations

## Consequences

### Positive Consequences

- **Prevents data loss:** Operations added during sync are never discarded
- **Preserves user intent:** Latest user actions remain visible even if earlier operations rejected
- **Predictable behavior:** Clear rule - only sent operations are removed from queue
- **No phantom state:** User never sees their changes disappear unexpectedly
- **Simple mental model:** Queue represents "operations server hasn't seen yet"
- **Eventually consistent:** System naturally converges within 1-2 sync cycles

### Negative Consequences

- **Increased complexity:** Must manage queue snapshots and re-application logic
- **Potential for cascading issues:** If op-b depends on rejected op-a, op-b may fail in next sync (but will be handled
  then)
- **Re-application overhead:** Must apply operations to state object on every sync with pending operations
- **Testing burden:** Need comprehensive tests for concurrent queue modifications
- **Operation semantics must be robust:** Each operation type must be safely re-applicable to any valid state

### Neutral Consequences

- **More code:** Additional helper functions for queue management and operation re-application
- **State immutability required:** Must not mutate server state during re-application
- **Operation design constraint:** Operations must be designed to be idempotent where possible

## Alternatives Considered

### Alternative 1: Cascading Rejection (Item-Level Invalidation)

- **Description:** Track operations by item_id; when operation on item rejected, discard all queued operations on that
  same item
- **Pros:**
    - Granular: only affected items lose edits
    - Independent items proceed normally
    - Balances simplicity with preservation
- **Cons:**
    - Requires item_id in every operation (adds complexity)
    - Doesn't solve the race condition (op-b on Milk during sync still lost)
    - More complex queue management
    - Harder to reason about operation dependencies
- **Reason for rejection:** Doesn't address the core race condition of operations added during sync; still loses user
  data in the identified scenario

### Alternative 2: Operation Rebasing

- **Description:** After rejection, attempt to "rebase" remaining operations onto new server state (e.g., "check Milk
  qty=2" becomes "check Milk qty=3" if server has different quantity)
- **Pros:**
    - Maximizes preservation of user intent
    - Sophisticated conflict resolution
    - Could handle complex operation dependencies
- **Cons:**
    - Extremely complex rebasing logic needed
    - Hard to determine which operations can be rebased vs. must be discarded
    - Some operation types fundamentally can't be rebased (e.g., edit field that changed differently)
    - Difficult to test all rebase scenarios
    - Over-engineered for "silent conflict resolution" requirement
- **Reason for rejection:** Complexity far exceeds benefit; PRD specifies silent conflict resolution with no user
  feedback, making sophisticated rebasing unnecessary

### Alternative 3: Full Queue Invalidation (Original Solution 3)

- **Description:** On ANY operation rejection, discard entire queue and accept server state completely
- **Pros:**
    - Simplest conflict resolution
    - No risk of inconsistent state
    - Clear "server is always right" semantic
    - Easy to implement and understand
- **Cons:**
    - Loses ALL pending changes (even unrelated items)
    - Harsh UX for poor connectivity scenarios
    - **Critical flaw:** Loses operations added during sync (the identified race condition)
    - User sees their latest action disappear without explanation
- **Reason for rejection:** Causes unacceptable data loss; user's most recent changes should always be visible per
  requirement "I want to update the UI immediately even if user has poor network connection"

### Alternative 4: Synchronous Queue Lock During Sync

- **Description:** Lock the queue during sync; buffer new operations separately, merge after sync completes
- **Pros:**
    - Clear separation of in-flight vs. new operations
    - Prevents queue modification during sync
- **Cons:**
    - Adds complexity with dual queues/buffers
    - Still need to handle rejected operations
    - Locking may delay user actions
    - Doesn't fundamentally solve the problem, just adds layer
- **Reason for rejection:** Snapshot approach achieves same goal with simpler implementation; no need for explicit
  locking mechanism

## References

- ADR-001: Optimistic UI with Operation-Based Sync
- PRD Section 3.4.4: Conflict Resolution ("first action wins" strategy, no user feedback)
