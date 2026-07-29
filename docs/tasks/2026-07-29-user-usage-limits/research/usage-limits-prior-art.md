# Prior art: how other developers introduced usage limits to prevent abuse

**Date:** 2026-07-29
**Scope:** Case studies, open-source implementations and design discussions on introducing per-user
usage limits. Read alongside `../requirements.md`.

## Summary

Almost nobody building an app at RecipAI's stage buys a limits product. The consistent pattern across
case studies is: **rate limiting middleware (bought or library) for the cheap "requests per second"
problem, and a hand-rolled database-backed quota for the expensive "this costs me money" problem.**
The two are treated as different mechanisms because they have different consistency requirements — a
rate limiter may lose state on a Redis restart, a cost budget may not. The strongest architectural
consensus found is the split that the requirements already imply: **volume/"stock" caps are counted
from the authoritative table; per-period/"flow" caps are counted in a dedicated counter keyed by
period.** The most repeated failure mode, mentioned in nearly every source, is check-then-act race
conditions — reserving budget *before* doing the work, atomically, is the single non-negotiable
detail.

## Key findings

### The motivating failure is well documented

- **Tom Blomfield's Recipe Ninja** (an AI recipe app, i.e. exactly RecipAI's shape) launched without
  limits: *"I woke up and saw that I had a $700 OpenAI bill. Someone had been abusing the site and
  costing me a lot of OpenAI credits by creating a single recipe over and over again."* The abuser
  generated the same recipe 12,000 times. His own diagnosis: *"Obviously, I had not put any rate
  limiting in."* The fix took ~10 minutes of work and *"the abuse stopped dead in its tracks"*
  ([Vibecoding a Production App](https://tomblomfield.com/post/778601470234918912/vibecoding-a-production-app)).
- The same app also demonstrated the *other* abuse axis — content abuse, with users generating
  recipes for "uranium bomb" and "actual cocaine"
  ([Sifted](https://sifted.eu/articles/tom-blomfield-ai-recipe-app-trolled)). Usage limits cap the
  bill; they do not cap reputational damage. Out of scope here, but worth knowing it arrived in the
  same wave.
- This class of attack has a name: **Denial of Wallet (DoW)**, distinct from DoS. The attacker stays
  within performance limits while selecting the most expensive operations. *"A single user can stay
  well within rate limits while running up hundreds of dollars in LLM charges"*
  ([Hands-on Architects, part 1](https://handsonarchitects.com/blog/2025/denial-of-wallet-cost-aware-rate-limiting-part-1/)).
- Provider-side spend caps are **not** a backstop. OpenAI's monthly budget threshold no longer
  hard-stops requests — it emails you and keeps billing
  ([Freemius](https://freemius.com/blog/ai-api-cost-protection/)). Firebase documents the same:
  *"budgets and budget alerts do not cap your usage or charges"*, with alert delays of up to days
  ([Firebase docs](https://firebase.google.com/docs/projects/billing/avoid-surprise-bills)). The
  wider genre — $23,000 Vercel bills from DDoS, $1,100 bandwidth bills on a $20 plan
  ([UsageBox](https://usagebox.com/articles/vercel-23000-dollar-bill-usage-based-platform-bill-shock-2026),
  [ServerlessHorrors](https://serverlesshorrors.com/)) — is why the enforcement has to live in the
  app, before the spend happens.

### Build vs. buy: what people actually did

| Layer | Typical choice | Why |
|---|---|---|
| Per-second/per-minute request throttling | Library or gateway (Bucket4j, Kong, KrakenD, Cloudflare) | Solved problem, no business semantics |
| Per-user cost/credit budgets | **Almost always hand-rolled on the primary database** | Needs to be durable, auditable, and joined to app data |
| Full metering + billing | Buy (Stripe, Lago, OpenMeter, Orb) | Only once money changes hands |

- The build/buy line falls where money does. On the HN thread
  [*Ask HN: How to design database schema for usage based billing?*](https://news.ycombinator.com/item?id=32669344),
  experienced commenters warn strongly against DIY **billing** (*"billing systems seem 'easy' but
  they really aren't… if you fuck up you are gonna overcharge, undercharge or… piss your customers
  off"*) — but that warning is about invoicing, not about enforcement. RecipAI has an explicit
  no-billing anti-requirement, which puts it firmly on the "build it, it's a hundred lines" side.
- Gateway vs. application enforcement is a real trade. Gateways give uniform 429s and one dashboard,
  but *"API gateways cannot implement business-specific rate limiting rules that require database
  queries, user context, or complex conditional logic"*
  ([Gravitee](https://www.gravitee.io/blog/rate-limiting-apis-scale-patterns-strategies),
  [dotMock](https://dotmock.com/blog/api-gateway-rate-limiting)). Per-user overrides read from a
  table are exactly that case.
- **Bucket4j** is the default Java/Spring answer and is genuinely a quota framework, not just a
  throttle — multiple bandwidths per bucket, Spring Boot starter, distributed backends
  ([GitHub](https://github.com/bucket4j/bucket4j),
  [INNOQ](https://www.innoq.com/en/blog/2024/03/distributed-rate-limiting-with-spring-boot-and-redis/)).
  It also has a JDBC/Postgres proxy manager, and the maintainer confirms you can point it at custom
  table and column names via `BucketTableSettings.customSettings` with `PrimaryKeyMapper.STRING`
  ([discussion #382](https://github.com/bucket4j/bucket4j/discussions/382)). **But** — and this is
  the catch for RecipAI — Bucket4j owns the row: it stores an opaque serialised bucket state, so the
  requirement *"limits are changed by editing the database directly"* fights the library's model. A
  token-bucket state blob is not something a human edits with `UPDATE`. Bucket4j fits the flow caps
  and fits nothing else in the table of resources.

### How homegrown systems are modelled

Three distinct models recur, and they map cleanly onto the requirements' stock/flow split.

**1. Counter-column-next-to-limit-column (simplest; used by Dub).**
[Dub.co](https://github.com/dubinc/dub) — a widely-used open-source SaaS — puts paired columns
directly on the workspace row:

```
usage           Int @default(0)     usageLimit           Int @default(1000)
linksUsage      Int @default(0)     linksLimit           Int @default(25)
aiUsage         Int @default(0)     aiLimit              Int @default(10)
foldersUsage    Int @default(0)     foldersLimit         Int @default(0)
partnersUsage   Int @default(0)     partnersLimit        Int @default(0)
                                    domainsLimit         Int @default(3)
                                    tagsLimit            Int @default(5)
                                    usersLimit           Int @default(1)
plan            String @default("free")
planTier        Int @default(1)
billingCycleStart Int               -- day of month
```

Note what this design does and does not do: flow resources get a `*Usage`/`*Limit` **pair**; pure
stock resources (`domainsLimit`, `tagsLimit`, `usersLimit`) get **only a limit column** and are
counted from their own tables. That is the same distinction RecipAI needs, arrived at
independently. Also note every limit is a plain integer column on the row — trivially editable by
hand, which is precisely the requirement. The cost is a wide, ever-growing table and one migration
per new limited resource.

**2. Balance + immutable ledger (Makerkit, credit-system guides).**
A `credits` table holding the balance, plus an append-only `usage_event` table as audit trail. The
balance is *"the single source of truth for fast lookups and cheap decrements"* while the event log
*"functions as the audit trail and source for analytics"*
([Makerkit](https://makerkit.dev/docs/next-supabase-turbo/billing/credit-based-billing)). HN
commenters split on whether to keep the denormalised balance at all — *"I wouldn't keep a
running_balance column… you shouldn't normally store a value that you can compute from other data"*
— with consensus landing on ledger-of-record plus a balance for speed. Two details from that thread
generalise well: **encode the rate/cost with the event** so historical rows never change meaning,
and **keep raw events**, because *"you will get a query on a bill by a customer… you need to be
able to dig into the raw data"*.

**3. Grants and burn-down (OpenMeter — the most elaborate).**
[OpenMeter](https://deepwiki.com/openmeterio/openmeter/3.4-entitlements-and-grants) models
entitlements as `CustomerID` + `FeatureKey` + `UsagePeriod` + `MeasureUsageFrom` +
`IssueAfterReset`, with separate *grants* carrying `Amount`, `Priority`, `EffectiveAt`,
`Expiration`, `ResetMaxRollover`. Balance is **recomputed** from grants minus usage rather than
stored. Burn-down order is priority, then soonest expiry, then oldest grant. Resets write a
`usage_reset` row and apply rollover caps. It also distinguishes **metered / static / boolean**
entitlements — a useful vocabulary: RecipAI's flow caps are metered, its stock caps are closer to
static. The honest trade-off from the same source: no snapshots means every balance read is a live
aggregation query, and *"complex burn-down logic increases implementation surface area for bugs"*.
This is what the requirements' anti-requirements are correctly refusing to build yet.

### The one thing everyone agrees on: reserve atomically, before the work

This is the highest-signal finding and directly addresses the "concurrent creation" edge case.

- *"A counted limit must be reserved before the work happens, not measured after. Race conditions
  here are revenue."* Resolution order: tenant context → entitlements lookup → quota reservation →
  side effect
  ([Multi-Tenant SaaS Architecture Hub](https://www.multi-tenant-saas.com/tenant-billing-usage-metering/subscription-and-plan-enforcement/)).
- The naive read-check-decrement *"has a race condition under concurrent requests"*; the fix is *"a
  database-side conditional update to atomically reserve credits"*. In Postgres terms, either
  `UPDATE … SET used = used + 1 WHERE user_id = ? AND used < limit` and check the affected row
  count (0 = rejected, 1 = granted), or `SELECT … FOR UPDATE`. The conditional update *"executes
  atomically and sets a row count of 0 or 1"*; `FOR UPDATE` is correct but *"has serious performance
  implications in a high throughput system"*
  ([OneUptime](https://oneuptime.com/blog/post/2026-01-25-postgresql-race-conditions/view),
  [on-systems.tech](https://on-systems.tech/blog/128-preventing-read-committed-sql-concurrency-errors/)).
  At RecipAI's scale, either works; the conditional update is one statement and needs no
  transaction.
- Stock caps counted with `COUNT(*)` have the same race and no counter to guard it. Options: a
  serialisable transaction, a `SELECT … FOR UPDATE` on the owner row, or accepting the tiny
  overshoot. The literature's warning here is about the *other* failure: *"a `SELECT count(*) FROM
  projects` without a tenant predicate counts every tenant's rows and refuses everyone once any
  tenant fills up"* — enforcement code that forgets the owner filter is its own outage.

### Stock vs. flow: where the counters live

The clearest statement of the split found anywhere, worth quoting at length
([Multi-Tenant SaaS Architecture Hub](https://www.multi-tenant-saas.com/tenant-billing-usage-metering/subscription-and-plan-enforcement/)):

> Volume counts — projects, members, rows — are better derived from the authoritative table with a
> tenant-scoped COUNT, because a Redis counter that drifts from the source of truth will eventually
> let a tenant exceed a hard limit or block one wrongly. […] Daily and per-second rate counters
> belong in Redis: an atomic INCRBY with a TTL-bounded window is a single round trip.

And: *"Never use a Redis counter as the source of truth for volume; reconcile nightly and fail
closed if drift exceeds 0.1%."* Its enforcement matrix:

| Limit type | Policy | Mechanism | Response |
|---|---|---|---|
| Rate (calls/day) | Throttle | Atomic counter with TTL | 429 + `Retry-After` |
| Volume (projects, rows) | Block or bill | Postgres `COUNT` | 402 or 200 metered |
| Seats | Block | Count active members | 402 |
| Features | Block | Cached flag | 403/402 |

Enforcement point also differs: volume checks run *"at member activation, project creation, or row
writes — not login or every request"*. Applied to RecipAI: recipe/list/plan/collection caps need no
counter table at all, just a `COUNT` at the create endpoint; only extractions need durable state.

Note the divergence from RecipAI's requirements on one point: this and most sources put rate
counters in **Redis**. RecipAI's extraction budget is a durable, human-editable, possibly-lifetime
("5 extractions ever") quantity — that is a database row, not a TTL'd key. The Firebase case study
below independently reaches the same conclusion for a comparable app.

### Reset semantics for flow caps (open question in the requirements)

Three implementations, three answers:

- **TTL-keyed window (Sentry, Cloudflare, Redis idiom).** The counter key encodes the window; expiry
  *is* the reset. Sentry's `RedisQuota` enforces per-60-second windows with a Lua script
  (`is_rate_limited.lua`) for atomicity
  ([design docs](https://develop.sentry.dev/backend/application-domains/quotas/),
  [source](https://github.com/getsentry/sentry/blob/master/src/sentry/quotas/redis.py)). No reset job
  exists because no reset happens.
- **Scheduled reset job.** The `pay` gem discussion settles on a `UsagePeriod` model plus an hourly
  cron that finds subscriptions whose latest period has ended. Rationale for hourly rather than
  daily: *"if cron didn't run for some reason, you'd be able to catch it in the next hour"*
  ([pay#971](https://github.com/pay-rails/pay/discussions/971)).
- **Lazy reset on read.** Store `period_start` on the counter row; on each check, if the stored
  period is stale, reset to zero as part of the same atomic upsert. No scheduler, no drift, and it
  naturally supports "5 ever" by simply having no period. This is the least-documented option in
  blog posts but the most common in practice, and it fits RecipAI's constraint of no scheduler
  infrastructure. It also sidesteps the "which timezone" question by anchoring to the user's first
  use rather than wall-clock midnight.

A fourth data point on the boundary problem: fixed windows *"suffer from the boundary burst problem
where users can make 2x budget requests"*
([part 2](https://handsonarchitects.com/blog/2025/denial-of-wallet-cost-aware-rate-limiting-part-2/)).
Cloudflare's answer — weighting the previous window's counter to approximate a sliding window with
only two numbers per key, measured at 0.003% error over 400M requests with zero false positives
([Cloudflare](https://medium.com/cloudflare-blog/how-we-built-rate-limiting-capable-of-scaling-to-millions-of-domains-3bcc875e16a6))
— is elegant but solves a problem RecipAI does not have at 2 extractions/day.

### Charging for attempts, not successes

The requirements say all extraction attempts count regardless of outcome. Prior art splits:

- **Reserve-then-refund** is the recommended pattern for LLM calls: reserve an estimated cost from
  input tokens + `max_tokens` up front, then refund the difference once actual output tokens are
  known — *"a request reserving 5 units but costing 3.2 actual units receives a 1.8-unit refund"*
  ([part 2](https://handsonarchitects.com/blog/2025/denial-of-wallet-cost-aware-rate-limiting-part-2/)).
- **GitHub** hit the mirror-image bug: their limiter *"doesn't increment the rate limit value until
  after the request is finished"*, and in retrospect they wanted to increment at request start with
  refunds for `304 Not Modified`
  ([Mosolgo](https://rmosolgo.github.io/ruby/redis/apis/rate%20limiting/2021/03/06/redis-rate-limiter.html)).

RecipAI's "charge on attempt, never refund" is the simplest correct point on this spectrum and is
defensible precisely because the cost is incurred on attempt. Worth recording as a deliberate
choice rather than an omission — it is the strict version of what GitHub wished it had done.

### Lessons from GitHub's Redis limiter (what goes wrong operationally)

[GitHub's rewrite](https://github.blog/engineering/infrastructure/how-we-scaled-github-api-sharded-replicated-rate-limiter-redis/)
and the [engineer's retrospective](https://rmosolgo.github.io/ruby/redis/apis/rate%20limiting/2021/03/06/redis-rate-limiter.html)
are the most candid failure account available:

- They moved off Memcached because eviction *"unpredictably cleared active rate limit data"* and
  per-datacenter instances couldn't share state. Cache-shaped stores lose quota state; that is a
  reason to prefer the database for anything that matters.
- Computing expiry across the app/store boundary broke: *"time passes between the call to `TTL` (in
  Redis) and `Time.now.to_i` (in Ruby)"*, so timestamps drifted by seconds. Fix: **store explicit
  expiration/period-start values** rather than deriving them from two clocks.
- Reads from replicas returned stale windows while writes hit primaries — Redis replicas don't
  expire keys on their own — producing contradictory limit headers.
- All mutation logic ended up in **Lua scripts for atomicity**. The database equivalent is a single
  conditional `UPDATE`.

### Failing open vs. failing closed

*"Never fail open when the counter store is unreachable on a revenue-bearing dimension. Failing
open hands unlimited free usage to your busiest tenants during the outage."* The recommendation is
to degrade to a conservative cached limit for rate checks but **fail closed for seats and hard
volumes**
([Multi-Tenant SaaS Architecture Hub](https://www.multi-tenant-saas.com/tenant-billing-usage-metering/subscription-and-plan-enforcement/)).
For RecipAI this is nearly moot if counters live in the same Postgres as the data — if the store is
down, the create fails anyway. That co-location is itself a meaningful argument against a separate
Redis.

### Making limits changeable without redeploy

The requirement matches standard practice: **GitLab** moved hardcoded rate limits into
database-backed application settings editable via admin UI or the settings API, and treats
"make limit X a configurable application setting" as a recurring class of issue
([instance limits](https://docs.gitlab.com/administration/instance_limits),
[settings API](https://docs.gitlab.com/api/settings/),
[issue 219112](https://gitlab.com/gitlab-org/gitlab/-/issues/219112)). The generalised advice: model
limits *"as a flat structure keyed by a stable identifier, so adding a plan or adjusting a limit is
a data change, not a code change"*, and avoid scattering `if (plan === 'pro')` across handlers,
because *"every pricing change requires code review of dozens of files"*.

Two caching cautions that apply even without tiers: cache resolved limits for ~60s in-process, but
**invalidate explicitly on change** — *"relying on TTL alone leaves a downgraded tenant with premium
access for the full cache window"*. Since RecipAI's changes happen by hand-editing the database
(no webhook, no invalidation hook), the safe answer is to **not cache limits at all** initially; a
single indexed row read per create request is cheap, and it's the only way "takes effect
immediately" holds literally.

### Firebase-specific: the closest architectural analogue

Google's own [*Securing a retail AI endpoint from abuse*](https://firebase.blog/posts/2025/11/securing-ai-endpoints-from-abuse/)
(Nov 2025) covers an AI endpoint behind Firebase auth — the same trust model as RecipAI. Its layered
defence:

1. **App Check** — attests the request came from a genuine build of the app on a real device; tokens
   are consumed server-side so they can't be replayed, limiting *"the amount of requests to a single
   request per valid attestation"*.
2. **Firebase ID token verification** — `uid` extracted server-side, optionally gated on
   `email_verified`.
3. **Per-user quota** — 5 requests/hour, counters stored in **Firestore** (not Redis) under
   `/users/{userId}/rateLimit`, one document per request with a TTL field, counted inside a
   transaction over the last hour.
4. **Restricted input schema** — the endpoint accepts only a `productSku`; the server supplies the
   prompt and images itself, so users cannot repurpose it as a general image generator.

Points 1 and 4 are outside the current requirements but relevant to the same threat. Point 1 in
particular closes the hole that per-user limits leave open: **sybil accounts**. Anonymous or
throwaway Firebase sign-ups reset every quota, and free-trial farming — *"a new email address
generates a new trial"* — is the standard bypass
([vindevs](https://vindevs.com/blog/how-to-prevent-users-from-abusing-free-tiers-and-creating-multiple-accounts-p68/),
[cside](https://cside.com/blog/signup-shield-multi-account-fraud-detection)). Per-user limits bound
the damage *per account*, not in aggregate; a global daily extraction ceiling is the cheap
complement, and App Check is the structural one.

### JIT provisioning: the missing user table

RecipAI has no user table — identity is a JWT claim. The standard resolution is **just-in-time
provisioning**: after validating the token, *"find or create a local User entity based on the
`sub`"*, then attach it to the request
([OneUptime](https://oneuptime.com/blog/post/2026-01-30-just-in-time-provisioning/view),
[WorkOS](https://workos.com/docs/sso/jit-provisioning)). This satisfies the acceptance criterion
"a new user signs up and receives the default limits without any manual setup" without a signup
hook: the row is created on first authenticated request, with limit columns left NULL so they fall
through to the defaults. Two notes — the upsert must be idempotent under concurrent first requests
(`INSERT … ON CONFLICT DO NOTHING` on `sub`), and defaults should be *absent values*, not copied
values, so changing a default retroactively covers everyone who never got an override.

### Telling the user

- The IETF has a draft standard for exposing quota state:
  [`RateLimit` and `RateLimit-Policy` header fields](https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers/),
  which *"allow servers to advertise their quota policies and the current service limits, thereby
  allowing clients to avoid being throttled"*. Still an Internet-Draft as of 2026, and it collapsed
  the older three-header form (`RateLimit-Limit`/`-Remaining`/`-Reset`) into two fields — so treat
  the old triple as a convention, not a standard.
- Status code convention in the surveyed material: **429** for time-window throttles (with
  `Retry-After`), **402/403** for entitlement/volume refusals. RecipAI's existing precedent is **409**
  from `MealPlanLimitExceededException`. Consistency with the existing handler is worth more than
  matching an external convention, but it is worth noting 409 is unusual for this and that the
  distinction between "wait and retry" (429) and "this will never succeed" (402/403) is genuinely
  useful to a mobile client deciding whether to offer a retry button.
- On UI: sources consistently recommend that the client not compute limits locally — enforcement is
  server-side and the client displays what the server reports.

## Pros and cons of the likely design

Consolidating what the sources say about the approach the requirements point at — per-user rows in
Postgres, `COUNT` for stock, counter row for flow, no Redis, no library.

**Arguments for, from the sources**

- Limits and counters are joinable with app data and editable with plain SQL — the stated
  requirement. Bucket4j's serialised bucket state is not.
- One store means no drift, no reconciliation job, and no "fail open vs. closed" dilemma.
- `COUNT(*)` at 3–10 ms on an indexed owner column is explicitly called acceptable for volume checks;
  create endpoints are low-frequency by nature.
- Atomic conditional `UPDATE` gives exact enforcement in one statement, no Lua, no transaction.
- Nothing in the design forecloses the OpenMeter-style grant model later; a `usage_event` ledger can
  be added behind the same service interface if per-request cost attribution is ever needed.

**Arguments against / accepted costs**

- Row-level lock contention on a single counter row per user under concurrency — real at scale
  ([sqlfordevs](https://sqlfordevs.com/concurrent-updates-locking)), irrelevant for a single user
  making 2 extractions a day.
- No audit trail if only a counter is kept. The HN thread's warning about needing raw events for
  disputes applies weakly here (nobody is being billed) but strongly if the developer ever wants to
  answer "what did this user actually do".
- No burst absorption or smoothing; a fixed window has the 2x boundary burst. Acceptable at these
  magnitudes.
- Per-user limits alone don't stop multi-account abuse; the requirements already accept the sharing
  amplifier, and sybil signups are the same shape of hole.
- Every new limited resource is a schema change if the Dub-style column-pair layout is used. A
  narrow `(user, resource, kind, limit, used, period_start)` table avoids that at the cost of losing
  column-level typing — the requirements' "stock or flow, configurable per user per resource" implies
  the row-per-resource shape rather than Dub's column-per-resource shape.

## Open questions / gaps

- **No public case study was found of a Spring Boot app implementing per-user stock+flow limits
  against Postgres.** The Java material is entirely Bucket4j rate limiting; the quota-modelling
  material is entirely Node/Prisma/Go. The design is well-supported in the aggregate but not by a
  single reference implementation in-stack.
- **Nobody publishes the exact stock/flow terminology.** The closest vocabularies are OpenMeter's
  metered/static/boolean and the volume/rate split above. Worth noting the requirements' framing is
  clearer than the prior art's, and no source discusses making the *kind* (stock vs. flow)
  per-user-configurable — that appears to be genuinely unusual, and its cost is that every check
  site must handle both shapes.
- **Blomfield never published which of the 15 suggested mitigations he implemented**, so the most
  on-the-nose case study yields motivation but no design detail.
- **Sentry's quota docs are thin** on refunds and accuracy/performance trade-offs; the source is the
  only real documentation.
- **Lazy-reset-on-read is under-documented** relative to how common it is. No authoritative write-up
  was found comparing it against cron resets; the recommendation here is inferred from the
  requirements' constraints rather than cited.
- **App Check's actual effectiveness** against a determined attacker (vs. raising the bar) is not
  quantified in Google's own post.

## Sources

**Case studies / incidents**

- [Vibecoding a Production App — Tom Blomfield](https://tomblomfield.com/post/778601470234918912/vibecoding-a-production-app) — the $700 OpenAI bill on an AI recipe app; 12,000 repeated generations; no rate limiting.
- [Tom Blomfield's AI recipe app trolled with bogus meals — Sifted](https://sifted.eu/articles/tom-blomfield-ai-recipe-app-trolled) — the content-abuse half of the same incident.
- [How we scaled the GitHub API with a sharded, replicated rate limiter in Redis — GitHub Blog](https://github.blog/engineering/infrastructure/how-we-scaled-github-api-sharded-replicated-rate-limiter-redis/) — Memcached→Redis migration, Lua atomicity, client-side sharding.
- [Lessons learned implementing a sharded, replicated rate limiter with Redis — Robert Mosolgo](https://rmosolgo.github.io/ruby/redis/apis/rate%20limiting/2021/03/06/redis-rate-limiter.html) — the candid retrospective: clock drift, replica staleness, increment-at-start regret.
- [How we built rate limiting capable of scaling to millions of domains — Cloudflare](https://medium.com/cloudflare-blog/how-we-built-rate-limiting-capable-of-scaling-to-millions-of-domains-3bcc875e16a6) — sliding-window approximation with two counters; measured error rates.
- [Securing a retail AI endpoint from abuse — Firebase Blog](https://firebase.blog/posts/2025/11/securing-ai-endpoints-from-abuse/) — the closest analogue: App Check + ID token + Firestore-backed per-user hourly quota + restricted input schema.
- [The $23,000 Vercel Bill — UsageBox](https://usagebox.com/articles/vercel-23000-dollar-bill-usage-based-platform-bill-shock-2026) and [ServerlessHorrors](https://serverlesshorrors.com/) — the bill-shock genre.

**Design discussions**

- [Ask HN: How to design database schema for usage based billing?](https://news.ycombinator.com/item?id=32669344) — ledger vs. running balance, encoding rate with the event, build-vs-buy warnings.
- [Denial of Wallet: Cost-Aware Rate Limiting, part 1](https://handsonarchitects.com/blog/2025/denial-of-wallet-cost-aware-rate-limiting-part-1/) and [part 2](https://handsonarchitects.com/blog/2025/denial-of-wallet-cost-aware-rate-limiting-part-2/) — DoW vs. DoS; budget units; reserve-and-refund; algorithm comparison table with pros/cons.
- [Subscription & Plan Enforcement — Multi-Tenant SaaS Architecture Hub](https://www.multi-tenant-saas.com/tenant-billing-usage-metering/subscription-and-plan-enforcement/) — the single densest source: volume-vs-rate split, atomic reservation, enforcement ordering, fail-closed, five named pitfalls.
- [Best practices for monthly usage resets with annual plans — pay-rails/pay #971](https://github.com/pay-rails/pay/discussions/971) — `UsagePeriod` model and the hourly-cron rationale.
- [Rate limiting APIs at scale — Gravitee](https://www.gravitee.io/blog/rate-limiting-apis-scale-patterns-strategies) and [A Guide to API Gateway Rate Limiting — dotMock](https://dotmock.com/blog/api-gateway-rate-limiting) — gateway vs. application enforcement trade-offs.
- [How to Handle Race Conditions in PostgreSQL — OneUptime](https://oneuptime.com/blog/post/2026-01-25-postgresql-race-conditions/view) and [Preventing Postgres race conditions with SELECT FOR UPDATE — on-systems.tech](https://on-systems.tech/blog/128-preventing-read-committed-sql-concurrency-errors/) — conditional-update vs. row-lock, with performance caveats.
- [Prevent locking issues for updates on counters — sqlfordevs](https://sqlfordevs.com/concurrent-updates-locking) — counter-row contention and fanout.
- [How to Prevent Users from Abusing Free Tiers — vindevs](https://vindevs.com/blog/how-to-prevent-users-from-abusing-free-tiers-and-creating-multiple-accounts-p68/) and [Multi-account fraud detection — cside](https://cside.com/blog/signup-shield-multi-account-fraud-detection) — the sybil bypass of per-user limits.
- [AI API cost protection for free tiers — Freemius](https://freemius.com/blog/ai-api-cost-protection/) and [Avoid surprise bills — Firebase docs](https://firebase.google.com/docs/projects/billing/avoid-surprise-bills) — provider spend caps do not hard-stop.

**Implementations to read**

- [dubinc/dub — workspace.prisma](https://github.com/dubinc/dub) — paired `*Usage`/`*Limit` integer columns; stock resources get limit-only; `billingCycleStart` as day-of-month.
- [OpenMeter entitlements and grants](https://deepwiki.com/openmeterio/openmeter/3.4-entitlements-and-grants) plus [balances/grants/rollovers](https://openmeter.io/blog/launchweek-2-02-balances-grants-and-rollovers) — the full grant/burn-down/reset model and its stated costs.
- [Makerkit credit-based billing](https://makerkit.dev/docs/next-supabase-turbo/billing/credit-based-billing) — `credits` table, `consume_credits()` with row lock, `has_credits()` precheck, 402 on failure.
- [getsentry/sentry — quotas/redis.py](https://github.com/getsentry/sentry/blob/master/src/sentry/quotas/redis.py) and [Sentry quota design docs](https://develop.sentry.dev/backend/application-domains/quotas/) — pluggable `Quota` base class, Lua-script atomicity, 60s windows.
- [bucket4j/bucket4j](https://github.com/bucket4j/bucket4j), [discussion #382 on custom DB tables](https://github.com/bucket4j/bucket4j/discussions/382), [INNOQ walkthrough](https://www.innoq.com/en/blog/2024/03/distributed-rate-limiting-with-spring-boot-and-redis/) — the Java option and why its storage model conflicts with hand-editable limits.
- [GitLab instance limits](https://docs.gitlab.com/administration/instance_limits) / [settings API](https://docs.gitlab.com/api/settings/) / [issue 219112](https://gitlab.com/gitlab-org/gitlab/-/issues/219112) — hardcoded limits migrated to database-backed settings.
- [RateLimit header fields for HTTP — IETF draft](https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers/) — standard-track way to surface remaining quota to clients.
- [Just-in-time provisioning — OneUptime](https://oneuptime.com/blog/post/2026-01-30-just-in-time-provisioning/view) / [WorkOS](https://workos.com/docs/sso/jit-provisioning) — creating the local user row from a validated token on first request.
