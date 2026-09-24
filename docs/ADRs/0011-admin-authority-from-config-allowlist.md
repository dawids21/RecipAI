# ADR-0011: Administrators are recognised by a configured email allowlist

**Date:** 2026-09-04
**Status:** accepted
**Related ADRs:** [ADR-0009: The Firebase identity is deleted by the mobile client](0009-client-side-firebase-identity-deletion.md)

## Context

Account deletion requests also arrive by email, and whoever handles them needs an endpoint that deletes any
account by address rather than editing the database by hand. That endpoint must be reachable by that person
and by nobody else — which the backend currently has no way to express.

Security today is a single filter chain: the health endpoint is public, the known API prefixes require
authentication, and everything else is denied. There is no authority check anywhere and method security is
switched off. Every controller derives identity the same way, by reading the email claim from the validated
token. Firebase ID tokens carry no scope claim, so the authority collection on an authenticated request is
empty on every request the system serves.

Whatever grants the authority also has to work in the dev profile, where callers are synthesised locally
rather than issued by Firebase — the integration suite authenticates that way, so an admin endpoint that
cannot be reached in the dev profile cannot be tested at all.

Three sources for the flag are plausible: a Firebase custom claim, which arrives inside the token the backend
already validates; a configured allowlist compared against the email claim; or a database table.

The driver is one person handling occasional emailed requests.

## Decision

The set of administrator emails is configuration — supplied by the environment in production, never committed
— and a caller whose token carries one of those addresses is granted an admin authority. The grant happens in
one place, at the seam where the validated token is converted into an authenticated caller, so the source of
truth is a single bean.

Enforcement is a matcher on the admin path in the existing security chain, which keeps the deny-by-default
posture intact and refuses a non-admin caller rather than revealing that the route exists.

## Alternatives considered

- **Firebase custom claims** — arrive in the token for free, but a granted or revoked claim only takes effect
  when the token next refreshes, up to an hour later, unless every request pays for a revocation check against
  Firebase; there is no console UI for editing claims; and setting them requires the Firebase Admin SDK and a
  service account, which is precisely the dependency ADR-0009 removed.
- **A roles table in the database** — the only option supporting runtime changes with no restart, but it means
  a migration, a repository and a lookup per request to introduce the first users table this system has ever
  had, for a single boolean flag.

## Consequences

No new dependency, no new infrastructure, no per-request cost, and nothing is asked of Firebase beyond the
token validation already in place. The dev profile needs only an address in its configuration for the
integration suite to exercise the admin endpoint.

Changing who is an administrator is a configuration change and takes effect on restart, which suits a set that
changes approximately never.

Because enforcement is an authority on the authenticated caller rather than an email comparison inside the
controller, the endpoint, its guard and its tests are unaffected by where the authority comes from. Moving to
custom claims or a table later changes one bean.

The system now has an authority that some callers hold and others do not, where previously every authenticated
caller was equivalent. Anything else that needs privileged access should reuse this authority rather than
inventing a second scheme.
