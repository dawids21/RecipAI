# Local Development

## Running the backend

`recipai.sh` manages the backend locally on the `dev` profile — the environment, a dummy AI key,
and the postgres container it starts and stops through `backend/compose.yaml` are all handled for
you.

```bash
./recipai.sh run-backend      # foreground, Ctrl+C to stop — the human path
./recipai.sh start-backend    # detached, waits until healthy — for scripting/agents
./recipai.sh stop-backend     # graceful shutdown of a start-backend run
```

`start-backend` is idempotent — safe to call without checking first, it returns immediately if
already healthy. Logs from a detached run go to `backend/target/backend-run.log`. A token-free
readiness probe: `curl -s localhost:8080/actuator/health`.

**Limitation:** `start-backend`/`stop-backend` track a single run via one pidfile and one log path.
Running a second backend on another port (`SERVER_PORT`) will confuse `stop-backend`.

There is no `restart` command — run `./recipai.sh stop-backend && ./recipai.sh start-backend`.
Devtools restart is disabled on this profile, so **code changes are not hot-reloaded**: after
editing anything under `backend/src/main`, stop and start again or you are testing stale classes.

Postgres is a container the app starts and stops itself; `stop-backend` leaves it in place, so
**data survives a restart**. For a genuinely clean slate: `docker rm -f backend-postgres-1` — the
compose file declares no volume, so there is nothing else to clean up.

## Auth

Activating the `dev` profile arms a development authentication bypass — see
`docs/backend/standards/configuration-profiles.md` for the gate itself.

The token names the caller, and the two places that reference an identity want different forms:

- **In the `Authorization` header** — the bare name. Spring's bearer-token grammar excludes `@`, so
  `Bearer alice` (not `Bearer alice@local.test`) is required; the decoder appends `@local.test`
  itself. Using the full address here produces a 401 that reads like an auth failure but is really
  a grammar rejection.
- **In a request body or an assertion** — the full email. Share targets are `@Email`-validated, so
  a share goes to `bob@local.test`, and `shared_users` responses come back in that form too.

Two different bearer tokens are two different users with no setup: `Bearer alice` cannot see what
`Bearer bob` created until it is shared.

## Environment variables

| Variable | Notes |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Forced to `dev` by `recipai.sh`; never set it yourself. |
| `SPRING_AI_API_KEY` | Required for context creation — no default in `application.yml`. `recipai.sh` falls back to `dummy-key-for-local`, which boots fine but makes `/extract/**` fail at call time. Export a real key before starting if you need extraction. It is already set in `.claude/settings.local.json`, so agent sessions inherit a real key. |
| `SERVER_PORT` | Default `8080`; honoured by all three backend commands. |
| `RECIPAI_LIMITS_ENABLED` | Usage limits are off on the dev profile (`recipai.limits.enabled: false`), so 429 paths never fire. Set to `true` before starting to exercise them. |
| AWS credentials | Absent locally, so image upload and the presigned URLs in `images[]` fail. Recipes without images are unaffected. |

## Calling the API

Dev-profile error bodies embed a full Java stack trace in `trace` — tens of kilobytes. Always strip
it, and ask for just the status code when that's all you need:

```bash
curl -sS -H "Authorization: Bearer alice" localhost:8080/recipes | jq 'del(.trace)'
curl -sS -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer alice" localhost:8080/recipes
```

Two status codes mislead:

- **403 also means "no such route"** — the filter chain ends in `denyAll`, so a 403 on a path you
  expected to work is usually a typo, not a permission bug.
- **401 is only ever a missing or malformed token** — never a permissions problem.

`docs/backend/modules/*/api.md` is the authoritative contract for every endpoint's parameters and
status codes.
