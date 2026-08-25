# Limits API

One endpoint, and it is a read: `limits` exposes the quotas resolved for the caller so a client can show
`used / limit` before an action and block it at the quota. It never exposes usage — each limited module
answers for its own resource through its own `/balance` endpoint (see that module's `api.md`), so
`limits` keeps no resource vocabulary at the HTTP edge.

No endpoint accepts a subject parameter: the route determines the subject, because `limits` cannot
authorise an opaque string and a caller-supplied subject would let anyone read anyone. `/limits/**` is
in the authenticated matchers of `config.security.SecurityConfig`.

### GET /limits
- Description: Get every quota configured for the authenticated caller, resolved override-then-default.
  The subject is the `email` claim of the JWT.
- Authenticated: true
- Example response:
  ```json
  [
    {"resource": "EXTRACTION", "kind": "FLOW", "limit": 2},
    {"resource": "RECIPE", "kind": "STOCK", "limit": 5},
    {"resource": "RECIPES_COLLECTION", "kind": "STOCK", "limit": 2},
    {"resource": "SHOPPING_LIST", "kind": "STOCK", "limit": 2},
    {"resource": "MEAL_PLAN", "kind": "STOCK", "limit": 2},
    {"resource": "SHOPPING_LIST_ITEM", "kind": "STOCK", "limit": 50}
  ]
  ```
- Behavior:
    - A quota carries no period — the only time-derived value a client displays (`resetsInSeconds`) rides
      on the balance returned by a module's `/balance` endpoint instead
    - `SHOPPING_LIST_ITEM` appears here as the caller's *own* configured value; the quota that applies to
      a particular list is resolved from that list's owner and read from
      `GET /shopping-lists/{id}/limits`
    - Returns `[]` when `recipai.limits.enabled` is `false` (as on the dev profile), which a client
      reads as "no quotas known" and leaves every action enabled
- Success: 200 OK

## Balance Reads Live With Their Module

`GET /recipes/balance`, `GET /collections/balance`, `GET /shopping-lists/balance`,
`GET /meal-plans/balance` and `GET /extract/balance` are documented in their own modules' `api.md`.
All five share one contract:

- The body is the subject's **recorded** balance, asked of `LimitsFacade.getBalance`, never a count of
  owned rows. It is the same number a reserve compares against, so a client that greys out an action
  at `used >= limit` refuses exactly what the server would have refused.
- Three fields, of which only `used` is always present:
  ```json
  {"used": 3, "periodStart": "2026-08-23T10:00:00Z", "resetsInSeconds": 82799}
  ```
    - `used` — the recorded count
    - `periodStart` — when the window the count belongs to opened
    - `resetsInSeconds` — how long until it restarts, populated only when that subject's quota resolves
      to a `FLOW` **with a period**, which under the seeded defaults is nowhere
- **A null field is omitted, not sent as null.** `spring.jackson.default-property-inclusion` is
  `non_null`, so the seeded defaults emit `{"used": 3, "periodStart": "2026-08-23T10:00:00Z"}` and a
  balance of zero emits `{"used": 0}` — a client must treat an absent key as null rather than expect
  it.
- The subject is the `email` claim of the JWT; no endpoint takes a subject parameter.
- A subject with no usage row reports `used: 0` rather than 404 — never having created anything is a
  balance of zero, not a missing resource. Such a body carries no `periodStart`, and neither does one
  whose periodic window has passed: the read reports the virtual restart as zero without writing it.
- Unlike the quotas read, these ignore `recipai.limits.enabled` and keep reporting recorded usage —
  which the kill-switch keeps recording, so the number stays true while nothing is refused.
