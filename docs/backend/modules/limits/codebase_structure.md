# Limits Module — Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── limits/
    ├── LimitsFacade.java                        # Public facade — reserve a unit, release one, read a subject's standing
    ├── LimitService.java                        # Resolves configuration, derives the period cutoff, turns a refused reserve into an exception, decides whether a release refunds
    ├── LimitConfig.java                         # Entity over limit_config — default row (subject IS NULL) or one subject's override
    ├── LimitConfigRepository.java               # Override-then-default resolution query
    ├── LimitUsage.java                          # Entity over limit_usage — read only to report a standing
    ├── LimitUsageId.java                        # Composite key (resource, subject)
    ├── LimitUsageRepository.java                # Native conditional upsert that *is* the indivisible reserve, and the floored decrement behind release
    ├── LimitUsageDetails.java                   # Public standing DTO (used, periodStart)
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

`limits` holds no domain knowledge: callers pass an opaque `subject` (a user email today) and an
opaque `resource` key that the *calling* module owns. `LimitsModuleArchitectureTest` enforces this
with ArchUnit — no class in `..limits..` may depend on any other `xyz.stasiak.recipai` package, and
only `LimitsFacade`, `LimitExceededException`, `LimitConfigurationMissingException`, `LimitKind` and
`LimitUsageDetails` may be public. See `docs/ADRs/0006-shared-limits-module.md`.

## Behaviour

- **Resolution** — configuration is read from the database on every check, with no cache: a subject
  override wins over the resource default, and a `max_value` edited by SQL takes effect on the next
  request with no restart. No configuration at all is a server error (500), not a refusal.
- **Reserve** — check-and-reserve is one conditional upsert whose affected-row count is the answer
  (1 granted, 0 refused), so concurrent requests for the same subject cannot both be admitted at the
  cap. A `max_value` of 0 refuses before any usage row exists.
- **Stock vs. flow** — a `STOCK` cap never restarts. A `FLOW` cap with a `period` restarts lazily
  inside the same statement that reserves, once `period_start` is older than the cutoff; a `FLOW` cap
  with no period is an "N ever" allowance that also never restarts.
- **Release** — resolves configuration exactly as reserve does, no-ops for a `FLOW`-configured
  subject (flow is consumed, never returned), and otherwise decrements with a floor at zero via
  `GREATEST(used - 1, 0)`. It never throws: missing configuration logs at `ERROR` and returns, and a
  release with no prior reserve is a no-op rather than an insert of `-1`. A delete must never be
  blocked or turned into a 500 by the limits module.
- **Recompute** — `R__recompute_limit_usage.sql`, a repeatable migration, rebuilds `limit_usage` for
  the owner-scoped resources from their owning module's permission tables. It is both the rollout seed
  and the drift repair for a missed release; see `db.md`.

## Refusal Contract

`LimitExceededException` is mapped to **429 Too Many Requests** with an RFC 7807 `ProblemDetail`
carrying `resource`, `kind`, `limit` and `used`. A `FLOW` cap with a period additionally carries
`retryAfterSeconds` and a `Retry-After` header; `STOCK` caps and period-less `FLOW` caps carry
neither, because there is no time at which the refusal resolves itself.

## Configuration

`recipai.limits.enabled` (default `true`) is a kill-switch: when `false`, both `LimitsFacade.reserve`
and `LimitsFacade.release` are no-ops, nothing is recorded and nothing is refused. It is set to `false`
in `application-dev.yml`, so **limits do not apply in local development** — tests that need them enable
the flag explicitly, either for a whole class or for a `@Nested` class inside a suite that runs with
limits off; see `docs/backend/standards/integration-tests.md`.

## Consumers

- `extraction` — reserves one unit of `EXTRACTION` per `/extract/text` or `/extract/image` call
- `recipes` — reserves one unit of `RECIPE` on create, releases on delete; owner-keyed
- `recipes.collections` — reserves one unit of `RECIPES_COLLECTION` on create, releases on delete;
  owner-keyed
- `shoppinglists` — reserves one unit of `SHOPPING_LIST` on create, releases on delete; owner-keyed.
  Item creation and deletion are untouched by `limits`.
