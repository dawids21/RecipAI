# Limits — Database Schema

## Tables

### limit_config

- id: UUID PRIMARY KEY
- resource: VARCHAR(64) NOT NULL
- subject: VARCHAR(255) NULL — NULL is the default for this resource; a value is that subject's override
- kind: VARCHAR(16) NOT NULL CHECK (kind IN ('STOCK', 'FLOW'))
- max_value: INTEGER NOT NULL CHECK (max_value >= 0)
- period: VARCHAR(16) NULL CHECK (period IN ('DAY', 'WEEK', 'MONTH'))
- created_at: TIMESTAMP NOT NULL DEFAULT now()
- UNIQUE NULLS NOT DISTINCT (resource, subject) — at most one default row and one override row per subject
- CHECK (kind <> 'STOCK' OR period IS NULL) — a stock cap never restarts, so it may not carry a period

### limit_usage

- resource: VARCHAR(64) NOT NULL
- subject: VARCHAR(255) NOT NULL
- used: INTEGER NOT NULL
- period_start: TIMESTAMP NOT NULL
- PRIMARY KEY (resource, subject)

## Relationships

There are no foreign keys. `resource` and `subject` are opaque strings owned by the calling module,
not references into another module's tables — see `docs/ADRs/0006-shared-limits-module.md`.

- **limit_usage** ↔ **limit_config**: matched by `resource` at read time, not by constraint
    - A usage row exists only once a subject has successfully reserved at least once, or once the
      recompute below has seeded it
    - A subject with no usage row has a standing of zero; a `max_value` of 0 refuses without ever
      creating one

## Seeded Configuration

`V15__limits_schema.sql` seeds the one default row T1 needs, and `V16__owner_scoped_limit_config.sql`
adds the three owner-scoped defaults:

| resource             | subject | kind  | max_value | period |
|----------------------|---------|-------|-----------|--------|
| `EXTRACTION`         | NULL    | FLOW  | 2         | NULL   |
| `RECIPE`             | NULL    | STOCK | 5         | NULL   |
| `RECIPES_COLLECTION` | NULL    | STOCK | 2         | NULL   |
| `SHOPPING_LIST`      | NULL    | STOCK | 2         | NULL   |

A `FLOW` cap with no period is an "N ever" allowance. Operators raise or lower a limit by editing
`limit_config` directly — the change applies on the next request, with no restart. A subject override
is inserted separately (e.g. the developer's own account); it is never seeded by a migration.

## Recompute

`R__recompute_limit_usage.sql` is a repeatable migration that rebuilds `limit_usage` for `RECIPE`,
`RECIPES_COLLECTION` and `SHOPPING_LIST` from their owning module's permission tables
(`recipe_permission`, `recipes_collection_permission`, `shopping_list_permission`), counting rows with
`role = 'OWNER'`. It runs once at rollout, seeding usage for pre-existing owners, and again whenever a
later task extends the file, re-asserting every resource's count. It also serves as the drift repair
for a missed release: re-running it (by hand, or by bumping the file so its checksum changes) makes
`limit_usage` match the permission tables again. A subject whose effective configuration is `FLOW` is
excluded — the recompute would overwrite its `used`/`period_start` window with a stock count, so it
leaves that subject's row untouched. "Effective" resolves the same way a check does, the subject's own
override first and the resource default second, so flipping a default to `FLOW` spares every subject
that has no override.

## Indexes

- Primary key indexes on both tables
- Composite primary key index on `limit_usage(resource, subject)` — also the conflict target of the
  reserve upsert
- Unique index on `limit_config(resource, subject)` (NULLS NOT DISTINCT) — backs the resolution query
