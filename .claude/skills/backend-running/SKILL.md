---
name: backend-running
description: Run the RecipAI backend locally and call its HTTP API with curl. Use this skill whenever you need a live backend — to verify a change actually works end to end, to reproduce a bug, to inspect a real response body, to check a status code, or when the user asks you to hit an endpoint, create test data, or "try it against the app". Trigger it even when the request never mentions running anything: "does the recipe filter work", "what does GET /meal-plans/calendar return", "check that the 429 fires", and "test this against the API" all need a running backend. Covers booting the app on the dev profile, the token-names-the-caller dev auth, and the curl idioms for every module. Do NOT use it for unit or integration tests (`./mvnw test` needs none of this), for the Flutter app, or for the deployed instance — this is localhost only.
---

# Running the backend and calling it

The backend runs locally on the `dev` profile, which arms a **development authentication
bypass**: any bearer token is accepted, and the token names the caller — `Bearer alice` is
the user `alice@local.test`. That removes Firebase, the network and token expiry from the
loop, so calling the API costs one constant header.

Everything the app needs — the profile, a dummy AI key, a postgres container — is
handled by `scripts/backend.sh`. Use it rather than assembling the Maven invocation yourself;
the environment has three separate ways to fail silently and the script encodes all of them.

## Start here

```bash
.claude/skills/backend-running/scripts/backend.sh start    # boots, waits until healthy
.claude/skills/backend-running/scripts/backend.sh status   # up? which pid? where are logs?
.claude/skills/backend-running/scripts/backend.sh logs 80  # last 80 lines
.claude/skills/backend-running/scripts/backend.sh restart  # after changing backend code
.claude/skills/backend-running/scripts/backend.sh stop
```

`start` is idempotent — it returns immediately if the app is already healthy, so it is safe
to call at the top of any task without checking first. A boot takes about 10 s (postgres
container, Flyway, Tomcat), and the first one after a clean checkout longer while Maven
resolves dependencies; the script blocks until `/actuator/health` reports `UP` and fails
loudly with the tail of the log if it doesn't.

**Leave it running** while you work, and stop it when the task is done. Restarting per request
wastes ten seconds and a container cycle each time.

Code changes are **not** hot-reloaded — devtools restart is disabled on this profile. After
editing anything under `backend/src/main`, run `restart` or you will be testing the old class
files and drawing wrong conclusions.

## Identity: the token names the caller

```bash
curl -sS -H "Authorization: Bearer alice" localhost:8080/recipes | jq
```

Set the caller once and reuse it — the examples below and in `references/curl-recipes.md`
assume these:

```bash
B=http://localhost:8080
AUTH="Authorization: Bearer alice"
JSON="Content-Type: application/json"
```

`alice@local.test` becomes the `email` claim, and every controller resolves the caller with
`jwt.getClaimAsString("email")`. Two different tokens are two different users with no setup:
`alice` owns what `alice` created, `bob` cannot see it until it is shared.

**Send a bare name, but address the user by its full email.** The decoder appends
`@local.test`, so `Bearer alice` is the caller `alice@local.test`. The two forms are not
interchangeable and each place wants a specific one:

- **In the header** — the bare name. Spring's `DefaultBearerTokenResolver` enforces RFC 6750's
  grammar (`[a-zA-Z0-9-._~+/]+=*`), which excludes `@`, so `Bearer alice@local.test` is
  rejected as malformed *before* the decoder runs. You get a 401 that reads like an auth bug
  but is a syntax error.
- **In a request body or an assertion** — the full email. Share targets are validated with
  `@Email`, and `shared_users` responses come back as `alice@local.test`.

So sharing round-trips completely: share to `bob@local.test`, then come back as `Bearer bob`
and read the recipe as EDITOR.

The bypass is gated on `@Profile("dev")` and `application.yml` defaults to `prod`, so it is
off unless the profile is explicitly requested — which is exactly what the script does. The
startup log announces it, and names the domain: `AUTHENTICATION BYPASS ENABLED`. Never point this at anything but
localhost.

## Reading responses

Dev-profile error bodies include a **full Java stack trace** in a `trace` field — often tens
of kilobytes. Dumping one raw floods your context for no benefit. Always strip it:

```bash
curl -sS -H "$AUTH" "$B/recipes" | jq 'del(.trace)'
```

When you only care whether something worked, ask for the status code and discard the body:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -H "$AUTH" "$B/recipes/$RID"
```

Status codes here are more informative than usual, and one pair is a reliable trap:

| Code | Means |
|------|-------|
| 401 | No token, or a token that violates the bearer grammar (an `@`). Never a permissions problem. |
| 403 | Authenticated but denied — *including a URL that doesn't exist*, because the filter chain ends in `denyAll`. A 403 on a path you expected to work is usually a typo, not a permission bug. |
| 429 | A usage cap, with an RFC 7807 body naming the resource. Should not appear: `recipai.limits.enabled` is `false` on this profile. |
| 412 | Stale `baseVersion` on a shopping-list item write; the body is the current item. |

`/actuator/**` is the only anonymous path — `curl -s localhost:8080/actuator/health` is a
token-free readiness probe.

## Endpoints

`references/curl-recipes.md` has worked examples for the payloads that are awkward to guess:
recipe `data`, version-gated shopping-list items, meal-plan entries and the calendar,
generate-shopping-list, and sharing. Read it before writing a request body by hand.

The authoritative contract for every endpoint is `docs/backend/modules/*/api.md`
(`recipes`, `shopping-lists`, `planning`, `extraction`). Go there for the full parameter and
status-code list rather than guessing from a 400.

## What does not work locally

Two areas fail by design, and recognising them saves a debugging detour:

- **`/extract/**`** needs a real `SPRING_AI_API_KEY`. The script sets a dummy, so extraction
  fails at call time. That is deliberate: it keeps a runaway loop from spending real Gemini
  quota. If you genuinely need extraction, export a real key before `start`.
- **Image endpoints** need AWS credentials. Recipes without images work fine; upload and the
  presigned URLs in `images[]` do not.

## Data

Postgres runs in a container the app starts and stops itself (`backend/compose.yaml`, via
`spring-boot-docker-compose`). `stop` stops the container without removing it, so **data
survives a restart** — useful, but it also means test rows accumulate. There is no reset
endpoint; delete what you create, or remove the container (`docker rm -f backend-postgres-1`)
for a genuinely clean slate, since the compose file declares no volume.

## When something is wrong

Read the log first — `backend.sh logs 100` — before theorising. The three failures worth
recognising on sight:

- **`start` reports the process exited during startup.** Almost always a compile error; the
  log shows it directly.
- **`start` says something else is on port 8080.** Another backend is running, possibly one
  the user started by hand. The script refuses to kill processes it did not start; say so
  rather than hunting the pid. `RECIPAI_PORT=8081 ... start` sidesteps it.
- **The docker daemon is unreachable.** The app cannot boot without its database; this needs
  the user to start Docker.
