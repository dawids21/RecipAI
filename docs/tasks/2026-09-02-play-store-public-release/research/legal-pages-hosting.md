# Hosting the privacy policy and account-deletion pages

## Summary

Both options clear Google's bar — Play only demands a working, public, non-geofenced HTTPS URL that is not a
PDF, is not user-editable, and names the app or developer as the store listing does. The deciding factor is
therefore not compliance but **failure domain and operating cost**. GitHub Pages wins: the account-deletion
URL is a standing obligation to Google that should not go down when the RecipAI VPS does, and static legal
text needs none of the control a Dokku app buys you. Point a subdomain of the domain you already own at a
public GitHub repo and the ongoing maintenance is zero. Dokku is the better answer only if these pages must
one day *do* something — run a real deletion form against the backend rather than describe the process.

## Key findings

**What Play actually requires**

- Privacy policy: a link in Play Console *and* reachable inside the app. The URL must be active, public,
  non-geofenced, readable in a standard browser without plugins, non-editable, not a PDF, and must reference
  the entity named in the store listing.
- Account deletion: an in-app path *plus* a separate public web URL. Google's own wording asks that it be
  "functional" (loads without error), "relevant" (the deletion pathway is prominent and easily discoverable),
  and "identifiable" (references the app or developer name as it appears on the listing).
- The deletion page does **not** need to perform the deletion. Google accepts a dedicated link, a customer
  service email, or a submission form. Static instructions plus a support address are compliant — which is
  what makes a static host sufficient here. Secondary sources add that the URL must not sit behind a login
  wall or resolve to a generic marketing page; that follows from "relevant" but is not spelled out in the
  help page itself.
- Both pages are then referenced from the Data safety form.

**GitHub Pages**

- Free TLS via Let's Encrypt, provisioned and renewed automatically. Custom domains are free on every plan —
  a CNAME from e.g. `recipai.stasiak.xyz` to `<user>.github.io` is enough.
- On a free personal account, Pages only publishes from a **public** repository. Not a problem for content
  that is public by definition, but it does mean a second repo separate from RecipAI (likely private).
- Limits are far beyond this use case: 1 GB site, 100 GB/month soft bandwidth, 10 builds/hour soft cap,
  one site per repo.
- The one clause worth reading twice: GitHub states Pages "isn't intended for... commercial purposes",
  naming online businesses, e-commerce, and SaaS. A privacy policy and a deletion-instructions page are
  not primarily directed at facilitating commercial transactions, so this reads as out of scope today. It
  becomes a live question only if the site grows into RecipAI's paid-product marketing front.
- Availability is strong but not independent of GitHub's wider platform: Pages reports 100% uptime over
  recent 90-day windows, though a 6 August 2026 capacity incident degraded Pages *deployments* for roughly
  10 hours, and a February 2026 Azure-related incident touched Pages alongside Actions. Serving already
  published content is the resilient part; publishing changes is the part that occasionally stalls.

**Dokku static app on the existing VPS**

- Mechanically simple. Drop an empty `.static` file in the repo root and Dokku selects its nginx buildpack;
  with an `index.html` at the root, that file is the entire configuration. `NGINX_ROOT` redirects the web
  root if a generator outputs to a subdirectory.
- TLS is one plugin away: `dokku letsencrypt:set --global email …`, `dokku domains:set`,
  `dokku letsencrypt:enable <app>`, then `dokku letsencrypt:cron-job --add` for renewal. The app must be
  reachable over plain HTTP first so the HTTP-01 challenge resolves.
- Still a new git repo, a new DNS record, a new container consuming RAM, and a new thing to remember during
  OS and Dokku upgrades.
- The real cost is architectural: the Play-mandated deletion URL would share a failure domain with the API.
  A VPS reboot, a botched Dokku upgrade, or a full disk takes down the compliance page at the same moment it
  takes down the app — the moment users are most likely to go looking for it.

**Third option, for completeness: a static route on the Spring Boot backend**

Cheapest to stand up (no new repo, no new DNS, no new container) and worst on every other axis. It couples
legal-text edits to backend releases and puts the deletion page behind the same process that serves the API.
Not recommended.

## Details

### Why the failure-domain argument dominates

The privacy policy and the deletion page differ in kind from the rest of the deployment. They are not
features; they are continuing representations to Google, checked at review time and reachable by users who
have already uninstalled the app. A user who deleted RecipAI from their phone and later wants their data
gone reaches that URL with nothing else of yours running. Serving it from infrastructure that is
independent of the API is the correct default, and it is the axis on which the two options genuinely differ
— everything else (cost, TLS, custom domain) is roughly a tie.

The counter-argument is that GitHub is also a third party that can fail, and it did in August 2026. But
that incident degraded *deployments*, not the serving of already-published pages, and a static legal page is
deployed once and read thereafter. The asymmetry favours Pages.

### What the choice does not hinge on

- **Cost.** The VPS is already paid for and a static container is nearly free; GitHub Pages is free. No
  meaningful difference.
- **Professional appearance.** Both can serve from a subdomain you own. A bare `<user>.github.io` URL looks
  worse than `recipai.stasiak.xyz`, but that is a custom-domain decision, not a hosting-provider decision.
  Set the custom domain either way.
- **Play acceptance.** No source found suggests Google discriminates by host. Rejections cluster around
  broken links, PDFs, editable documents, geofenced URLs, and pages that fail to name the app or developer.

### Where Dokku would win

If the deletion URL should eventually run a genuine self-service flow — enter your email, receive a
confirmation link, backend deletes the account — that page stops being static and needs to reach the API.
At that point it belongs on infrastructure that can talk to the backend, and the argument reverses. Worth
deciding now which of the two you are building, because Google accepts the simpler one: the backend has no
account-deletion endpoint yet, and the in-app path plus a documented email request satisfies the policy
without one.

### Practical shape of the recommended setup

A single public repo holding two pages plus an index, published to a subdomain of the domain you already
control. Two stable, direct URLs — one per purpose, as Play expects — and a CNAME record. The content is
plain HTML; no generator, no build step, so the 10-builds-per-hour and deployment-outage concerns are
irrelevant. Changes are a commit.

## Open questions / gaps

- **Does RecipAI have monetisation plans?** No pricing, subscription, or paid-tier language appears in the
  PRD, and the usage-limit work reads as abuse control rather than a paywall. If a paid tier is coming and
  this site later grows into the product's marketing front, revisit the GitHub Pages commercial-use clause.
- **Which domain?** `stasiak.xyz` is named in the earlier publishing-requirements research as a candidate,
  but no domain is recorded anywhere in the repo or project docs, and nothing confirms what the API's
  hostname is today. Confirm before writing the DNS record.
- **Static instructions or a working deletion form?** Decides whether this recommendation holds. See above.
- **In-app privacy policy link.** Play requires the policy be reachable from inside the app as well as from
  the listing. Where that link goes in the Flutter UI is not covered here.
- **Whether the login-wall prohibition is official.** Multiple secondary sources state the deletion URL must
  not require sign-in; Google's help page implies it via "relevant" but does not say it outright. Treat the
  stricter reading as binding — it costs nothing.

## Sources

- [Provide a way for users to request app account deletion — Play Console Help](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en) — the functional/relevant/identifiable criteria for the web URL, the accepted request methods (link, email, form), and the non-mobile-surface exemption.
- [GitHub Pages limits — GitHub Docs](https://docs.github.com/en/pages/getting-started-with-github-pages/github-pages-limits) — 1 GB site, 100 GB/month bandwidth, 10 builds/hour, 10-minute deployment timeout, and the "not intended for commercial purposes" prohibition.
- [What is GitHub Pages — GitHub Docs](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages) — one site per account/repo; confirms the docs make no uptime or SLA promise.
- [GitHub Pages with Free Account: Private Repos, Static Content — GitHub Community](https://github.com/orgs/community/discussions/167331) — Pages publishes from private repos only on paid plans; free accounts need a public repo.
- [GitHub Pages Free Hosting 2026: Custom Domain, HTTPS & Setup Guide](https://www.devian.in/blogs/github-pages-free-hosting) — custom domains free on all plans, CNAME/A-record setup, automatic Let's Encrypt certificates.
- [Build and Deploy a Static Site with Dokku — JohnFraney.ca](https://johnfraney.ca/blog/build-deploy-static-site-dokku/) — the `.static` marker file, the nginx buildpack, and `NGINX_ROOT` for generator output directories.
- [dokku/dokku-letsencrypt — GitHub](https://github.com/dokku/dokku-letsencrypt) — plugin install, global email, `letsencrypt:enable`, the cron renewal job, and the HTTP-01 reachability prerequisite.
- [Invalid Privacy Policy URL Rejection for Google Play Store — TermsFeed](https://www.termsfeed.com/blog/invalid-privacy-policy-url-google/) — the active/non-geofenced/non-editable/no-PDF criteria and the requirement to reference the listed entity.
- [Google Play Account Deletion URL: How to Comply Without Building It — Applander](https://www.applander.io/blog/google-play-account-deletion-url) — the HTTPS, no-login-wall, no-marketing-page reading of the deletion URL rules and what the page should state.
- [GitHub Outages 2025–2026: Reliability Analysis and Outage History — IncidentHub](https://blog.incidenthub.cloud/github-reliability-outage-history-2025-2026) — the 6 August 2026 Pages deployment incident (~10h43m) and the February 2026 Azure-related degradation.
- [App Store Support URL & Privacy Policy Requirements (2026) — AppLaunchFlow](https://www.applaunchflow.com/blog/app-store-support-url-privacy-policy-requirements-2026) — that one domain can host policy/support/terms pages provided each has a stable direct URL, and the own-domain-vs-github.io tradeoff.
