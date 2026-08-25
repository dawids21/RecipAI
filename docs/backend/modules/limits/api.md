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

## Refusal Response

A call past a quota returns **429 Too Many Requests** with an RFC 7807 `ProblemDetail` carrying
`resource`, `kind`, `limit` and `used`:

```json
{
  "type": "about:blank",
  "title": "Limit Exceeded",
  "status": 429,
  "detail": "Limit for RECIPE reached (5 of 5 used)",
  "resource": "RECIPE",
  "kind": "STOCK",
  "limit": 5,
  "used": 5
}
```

A `FLOW` quota with a period additionally carries `retryAfterSeconds` and a `Retry-After` header;
`STOCK` quotas and period-less `FLOW` quotas carry neither, because there is no time at which the
refusal resolves itself.
