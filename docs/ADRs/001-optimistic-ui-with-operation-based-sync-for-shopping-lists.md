# ADR-001: Optimistic UI with Operation-Based Sync for Shopping Lists

**Status:** Accepted  
**Date:** 2025-10-22  
**Deciders:** Development Team  
**Technical Story:** Shopping Lists Management Feature Implementation

---

## Context

RecipAI requires a Shopping Lists Management feature that allows users to create, manage, and collaboratively edit
shopping lists. The feature must integrate with existing Recipe Management to add ingredients to lists. Key requirements
include:

- Immediate UI updates even with poor network connectivity
- Support for collaborative editing (2-3 users per list)
- Acceptable 5-10 second delay for synchronization (real-time not required)
- Offline operation support for user actions (add, edit, delete, check items)
- Lists should be fetched once and operations handled while offline
- Server as single source of truth with conflict resolution

Technical constraints:

- Backend: Java/Spring Boot REST API with PostgreSQL
- Mobile: Flutter with ValueListenable for state management, no existing local database
- No current real-time infrastructure (WebSockets/SSE)
- Expected scale: 3-4 lists per user, ~30 items per list, 2-3 collaborators per list

The challenge is balancing immediate user feedback, offline capability, and collaborative editing without introducing
significant infrastructure complexity or requiring a full local database solution.

## Decision

We will implement an **Optimistic UI with Operation-Based Sync architecture** with the following characteristics:

1. **Client-side approach:**
    - Every user action generates an immutable operation object with UUID, type, payload, and timestamp
    - Operations apply immediately to in-memory state (ValueListenable) for instant UI feedback
    - Operations persist in SharedPreferences as a pending queue (survives app restarts)
    - No SQLite database required; lists fetched on-demand and stored in memory

2. **Synchronization strategy:**
    - Periodic sync every 10 seconds when list is active
    - Immediate debounced sync (500ms) after user actions when online
    - Batch POST pending operations to server endpoint
    - Server responds with acceptance/rejection status and authoritative current state

3. **Backend implementation:**
    - REST endpoint: `POST /api/shopping-lists/{id}/operations` accepting operation batches
    - Shopping list tables with version counter for optimistic concurrency control
    - Operations validated against current version; first-write-wins conflict resolution
    - Response includes accepted/rejected operation IDs and complete current state

4. **State reconciliation:**
    - After sync, apply server state as baseline
    - Re-apply any operations added to queue during sync (overlay pattern)
    - Preserves user changes made during network round-trip

## Consequences

### Positive Consequences

- **Immediate user feedback:** All actions reflect instantly in UI regardless of network conditions
- **Simple storage:** SharedPreferences sufficient; no SQLite complexity or schema migrations
- **Natural offline handling:** Operations queue automatically when offline, sync when reconnected
- **Fits existing patterns:** Works seamlessly with ValueListenable state management
- **Scalable for expected load:** Operation queue easily handles expected usage (100+ operations in ~10KB)
- **Audit trail:** Operation log provides debugging capability and future analytics opportunities
- **No new infrastructure:** Uses existing REST API; no WebSockets, message queues, or real-time servers needed
- **Server-authoritative:** Simplifies conflict resolution; server state is always ground truth

### Negative Consequences

- **Operation queue management:** Requires careful handling of queue lifecycle, race conditions, and size limits
- **Backend complexity:** Must implement operation validation, version checking, and idempotency logic
- **Testing complexity:** Need to test concurrent scenarios, network failures, and sync race conditions
- **Limited offline viewing:** Lists must be fetched at least once; not fully available offline until accessed
- **Potential reconciliation jarring:** If many operations queued offline and rejected, UI state change may be
  noticeable
- **SharedPreferences limits:** Practical limit of ~100 operations before queue management needed

### Neutral Consequences

- **5-10 second sync delay:** Acceptable per requirements but users won't see collaborator changes instantly
- **Eventual consistency:** System converges over time rather than providing strong consistency
- **Operation-based thinking:** Developers must frame all actions as operations with clear semantics

## Alternatives Considered

### Alternative 1: Stateless Mobile + Smart Polling with Etag Caching

- **Description:** In-memory only state, HTTP caching with ETag headers, conditional GET requests every 5-10 seconds, no
  local persistence
- **Pros:**
    - Simplest implementation
    - Minimal storage footprint
    - HTTP caching reduces bandwidth
    - Server always source of truth
- **Cons:**
    - No offline viewing of lists
    - Pending changes lost if app killed while offline
    - Must re-fetch all lists on every app launch
    - Doesn't meet PRD's offline requirement for operations
- **Reason for rejection:** Insufficient offline capability; user loses changes if app is killed while offline, doesn't
  meet PRD requirement that "all changes made while offline will be synced"

### Alternative 2: Local-First SQLite with Incremental Delta Sync

- **Description:** Full SQLite mirror of server data, track dirty records, bi-directional delta sync using timestamps,
  soft deletes for conflict detection
- **Pros:**
    - True offline-first with full functionality
    - All lists available offline
    - Efficient delta syncing
    - Survives all app lifecycle events
- **Cons:**
    - SQLite adds significant complexity
    - Must manage database schema migrations in mobile app
    - Complex sync logic with timestamp comparisons
    - Overkill for expected usage (3-4 lists, 30 items)
    - More difficult to debug state issues
- **Reason for rejection:** Over-engineered for requirements; SQLite complexity not justified given lists are fetched
  once and only operations need offline support. User confirmed "lists are not available offline as they are fetched
  once" is acceptable.

## References

- PRD: Shopping Lists Management Feature Requirements
