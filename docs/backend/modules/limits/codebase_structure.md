# Limits Module — Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── limits/
    ├── LimitsFacade.java                        # Public facade — reserve a unit, release one, clear a vanished subject's row, read a subject's standing, resolve a subject's caps
    ├── LimitsController.java                    # Package-private @RestController — GET /limits returns the caller's resolved caps
    ├── LimitService.java                        # Resolves the config subject's configuration, derives the period cutoff, turns a refused reserve into an exception, decides whether a release refunds, applies the elapsed-period rule virtually on a standing read
    ├── LimitConfig.java                         # Entity over limit_config — default row (subject IS NULL) or one subject's override
    ├── LimitConfigRepository.java               # Override-then-default resolution query, for one resource or (resolveAll) every configured one
    ├── LimitUsage.java                          # Entity over limit_usage — read only to report a standing
    ├── LimitUsageId.java                        # Composite key (resource, subject)
    ├── LimitUsageRepository.java                # Native conditional upsert that *is* the indivisible reserve, the floored decrement behind release, and the row delete behind clear
    ├── LimitStanding.java                       # Public standing DTO (used, periodStart, resetsInSeconds)
    ├── LimitCap.java                            # Public resolved-cap DTO (resource, kind, limit) — carries no period; the client does no period arithmetic
    ├── LimitKind.java                           # Public enum STOCK / FLOW — rides on the refusal
    ├── LimitPeriod.java                         # Enum DAY / WEEK / MONTH — the only place period arithmetic lives
    ├── LimitExceededException.java              # Public refusal carrying resource, kind, limit, used, optional retryAfterSeconds
    ├── LimitConfigurationMissingException.java  # Public — no configuration resolved for the resource
    ├── LimitsExceptionHandler.java              # Maps the two exceptions to 429 and 500 (ProblemDetail)
    ├── LimitsProperties.java                    # recipai.limits.* configuration properties
    └── LimitsModuleConfig.java                  # Enables LimitsProperties
```

The `Clock` the module reads time from is supplied by `config.time.TimeConfig`.

## Module Boundary

`limits` holds no domain knowledge: callers pass an opaque `subject` (a user email or a shopping
list's UUID today) and an opaque `resource` key that the *calling* module owns.
`LimitsModuleArchitectureTest` enforces this with ArchUnit — no class in `..limits..` may depend on
any other `xyz.stasiak.recipai` package, and only `LimitsFacade`, `LimitExceededException`,
`LimitConfigurationMissingException`, `LimitKind`, `LimitStanding` and `LimitCap` may be public — the
controller stays package-private. See `docs/ADRs/0006-shared-limits-module.md`.

## Behaviour

- **Resolution** — configuration is read from the database on every check, with no cache: a subject
  override wins over the resource default, and a `max_value` edited by SQL takes effect on the next
  request with no restart. No configuration at all is a server error (500), not a refusal.
- **Two subjects** — `reserve` and `release` resolve configuration against one subject and count usage
  against another. The two-argument forms pass the same subject for both, which is what every
  owner-keyed consumer wants; the three-argument forms exist for `SHOPPING_LIST_ITEM`, where the count
  belongs to the list but the cap value belongs to its owner, so one override row raises every list
  that user owns, present and future. A per-*usage-subject* override is not expressible.
- **Reserve** — check-and-reserve is one conditional upsert whose affected-row count is the answer
  (1 granted, 0 refused), so concurrent requests for the same subject cannot both be admitted at the
  cap. A `max_value` of 0 refuses before any usage row exists.
- **Stock vs. flow** — a `STOCK` cap never restarts. A `FLOW` cap with a `period` restarts lazily
  inside the same statement that reserves, once `period_start` is older than the cutoff; a `FLOW` cap
  with no period is an "N ever" allowance that also never restarts.
- **Release** — resolves configuration exactly as reserve does, so it is the *config* subject's `kind`
  that decides refundability: it no-ops for a `FLOW`-configured subject (flow is consumed, never
  returned), and otherwise decrements the usage subject's row with a floor at zero via
  `GREATEST(used - 1, 0)`. It never throws: missing configuration logs at `ERROR` and returns, and a
  release with no prior reserve is a no-op rather than an insert of `-1`. A delete must never be
  blocked or turned into a 500 by the limits module.
- **Clear** — deletes a subject's usage row outright, for when the subject itself has ceased to exist
  (a deleted shopping list has no records left to refund one at a time). It resolves no configuration,
  so it takes a single subject, has no `FLOW` branch, and never throws.
- **Standing read** — `standing` reports what the *next* reserve would compare against, and writes
  nothing: a window whose `period_start` is already past the cutoff reports zero used and the row is
  left alone, so a read never re-anchors a subject's reset time. It carries `resetsInSeconds` only for
  a live `FLOW` window with a period, computed from the same `LimitPeriod` arithmetic the refusal's
  `retryAfterSeconds` uses. An absent usage row is `Optional.empty()` — no row, not "no configuration".
- **Cap read** — `caps` resolves every configured resource for a subject in one query and `cap`
  resolves one, both by the same override-then-default rule as a check. Neither touches usage, so a
  cap can be read for a subject that has never used anything.
- **Recompute** — `R__recompute_limit_usage.sql`, a repeatable migration, rebuilds `limit_usage` for
  the owner-scoped resources from their owning module's permission tables, and `SHOPPING_LIST_ITEM`
  from `shopping_list_items` grouped by list. It is both the rollout seed and the drift repair for a
  missed release; see `db.md`.

## Refusal Contract

`LimitExceededException` is mapped to **429 Too Many Requests** with an RFC 7807 `ProblemDetail`
carrying `resource`, `kind`, `limit` and `used`. A `FLOW` cap with a period additionally carries
`retryAfterSeconds` and a `Retry-After` header; `STOCK` caps and period-less `FLOW` caps carry
neither, because there is no time at which the refusal resolves itself.

## Configuration

`recipai.limits.enabled` (default `true`) is a kill-switch: when `false`, `LimitsFacade.reserve`,
`release` and `clear` are all no-ops, nothing is recorded and nothing is refused, and `caps`/`cap`
resolve nothing — so a client that displays caps shows none. `standing` ignores the flag and keeps
reporting whatever usage is recorded. It is set to `false`
in `application-dev.yml`, so **limits do not apply in local development** — tests that need them enable
the flag explicitly, either for a whole class or for a `@Nested` class inside a suite that runs with
limits off; see `docs/backend/standards/integration-tests.md`.

## Consumers

- `extraction` — reserves one unit of `EXTRACTION` per `/extract/text` or `/extract/image` call
- `recipes` — reserves one unit of `RECIPE` on create, releases on delete; owner-keyed
- `recipes.collections` — reserves one unit of `RECIPES_COLLECTION` on create, releases on delete;
  owner-keyed
- `shoppinglists` — reserves one unit of `SHOPPING_LIST` on create, releases on delete; owner-keyed.
  Also reserves one unit of `SHOPPING_LIST_ITEM` per item created and releases it on item delete,
  counted against the list and configured from the list's owner; deleting a list clears its item row.
- `planning` — reserves one unit of `MEAL_PLAN` on create, releases on delete; owner-keyed
