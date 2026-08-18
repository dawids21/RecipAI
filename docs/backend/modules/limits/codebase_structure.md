# Limits Module — Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── limits/
    ├── LimitsFacade.java                        # Public facade — reserve a unit, read a subject's standing
    ├── LimitService.java                        # Resolves configuration, derives the period cutoff, turns a refused reserve into an exception
    ├── LimitConfig.java                         # Entity over limit_config — default row (subject IS NULL) or one subject's override
    ├── LimitConfigRepository.java               # Override-then-default resolution query
    ├── LimitUsage.java                          # Entity over limit_usage — read only to report a standing
    ├── LimitUsageId.java                        # Composite key (resource, subject)
    ├── LimitUsageRepository.java                # Native conditional upsert that *is* the indivisible reserve
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
- **No release** — T1 ships reserve only. Releasing a held unit arrives with the first stock resource.

## Refusal Contract

`LimitExceededException` is mapped to **429 Too Many Requests** with an RFC 7807 `ProblemDetail`
carrying `resource`, `kind`, `limit` and `used`. A `FLOW` cap with a period additionally carries
`retryAfterSeconds` and a `Retry-After` header; `STOCK` caps and period-less `FLOW` caps carry
neither, because there is no time at which the refusal resolves itself.

## Configuration

`recipai.limits.enabled` (default `true`) is a kill-switch: when `false`, `LimitsFacade.reserve` is a
no-op, nothing is recorded and nothing is refused. It is set to `false` in `application-dev.yml`, so
**limits do not apply in local development** — tests that need them enable the flag explicitly with
`@SpringBootTest(properties = "recipai.limits.enabled=true")`.

## Consumers

- `extraction` — reserves one unit of `EXTRACTION` per `/extract/text` or `/extract/image` call
