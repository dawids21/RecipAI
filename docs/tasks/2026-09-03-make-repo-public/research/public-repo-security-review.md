# Pre-publication security review of the RecipAI repository

> This file will itself become public. Every secret it discusses is referred to by location, never by
> value.

## Summary

The repository is in good shape for publication: no credential has ever been committed to git history.
The backend takes every secret from environment variables, the release scripts read the Play service
account from a gitignored path, and a scan of all 2,653 blobs across all 452 commits on every branch
turned up no private keys, no bearer tokens, and no cloud credentials.

Two things must be dealt with before flipping the switch. A **live Google Gemini API key sits in
`.vscode/launch.json`**, and that file is protected only by the author's personal machine-local
gitignore — nothing in the repository itself keeps it out. And **both Android signing keystores are
committed** as GPG-symmetric ciphertext, which changes from "encrypted at rest in a private repo" to
"offline-crackable by anyone" the moment the repo is public.

Everything else is hardening, licensing, or hygiene.

---

## Findings by priority

### 1. Live Gemini API key protected only by a machine-local gitignore — **fix before publishing**

`.vscode/launch.json` (untracked, present on disk) sets `SPRING_AI_API_KEY` to a real, billable Google
Gemini key as a literal string in the Spring Boot launch configuration.

The file has never been committed — verified against full history. But the only reason it is ignored is
`~/.gitexclude`, the author's **global** git excludes file:

```
$ git check-ignore -v .vscode/launch.json
/home/dawid/.gitexclude:3:.vscode    .vscode/launch.json
```

The repository's own `.gitignore` does not mention `.vscode/`. (`backend/.gitignore` does, but that rule
only applies inside `backend/`.) So the key is one changed global config, one fresh clone, or one
`git add -f` away from being published — and once a key is pushed to a public repo it is scraped within
minutes.

The same launch config points at `envFile: ${workspaceFolder}/.env`. No `.env` exists today, and the root
`.gitignore` does not cover that name either.

Do all three:

- **Rotate the key** in Google AI Studio. It has been sitting in a plaintext file in a working tree;
  treat it as spent regardless of whether it ever left the machine.
- **Move it out of `launch.json`** into the `.env` file the config already references, so the secret and
  the committed-by-default editor config are never the same file.
- **Add `.vscode/` and `.env` to the repository's own `.gitignore`**, so protection travels with the repo
  instead of living on one machine.

### 2. Android signing keystores are committed — **decide before publishing**

`scripts/keystores/upload_keystore.jks.gpg` and `debug_keystore.jks.gpg` are tracked, added in a single
commit (`056a2a1`), with no earlier versions in history to purge.

The encryption itself is done properly — AES-256, S2K mode 3, SHA-512, iteration count 65,011,712
(GPG's maximum):

```
:symkey enc packet: version 4, cipher 9, aead 0, s2k 3, hash 10
    salt ..., count 65011712 (255)
```

That is a strong KDF, and it is the *only* thing standing between a stranger and the signing keys. The
JKS password inside is not a second real layer — JKS's own password hashing is SHA-1-based and cracks
trivially once the GPG layer is off. So the security of both keys reduces entirely to the strength of one
symmetric passphrase, now published for unlimited offline attack.

What an attacker actually gains differs per key:

- **Upload keystore** — cannot publish on its own; uploading to Play also requires Play Console access or
  the service account key (which is correctly gitignored). Defence-in-depth loss rather than a direct
  break. If it is ever compromised, Play's upload-key reset is the recovery path, and Play App Signing
  means the real app signing key is held by Google and is unaffected.
- **Debug keystore** — `mobile/android/app/google-services.json` binds two OAuth clients to the package
  name `xyz.stasiak.recipai` plus specific certificate SHA-1 hashes. Android OAuth client identity *is*
  package name + signing certificate, so anyone holding a keystore matching one of those hashes can build
  an app that Google Sign-In treats as RecipAI. The gain is consent-screen impersonation — a phishing app
  that asks for Google account access under this project's identity. It is **not** backend access: the API
  trusts any Firebase token issued for `recipai-751ae`, and anyone can obtain one by signing up through
  the real app. Rotate it because it is free to rotate, not because it is urgent.

**Rotate both keys, and move the replacements out of the repository.** Do not rely on a history rewrite
for this.

A rewrite makes the committed ciphertext harder to reach; rotation makes it worthless. Only the second is
durable. Both keys have supported rotation paths, which is what makes this the easy choice:

- **Upload keystore** — Play supports upload key reset: generate a new key, register its certificate, and
  Google switches which key it accepts for uploads. The app signing key is held by Google under Play App
  Signing (indicated by the `upload_keystore.jks` / `keyAlias=upload` split) and is unaffected throughout.
  Confirm enrolment in the Play Console before relying on this.
- **Debug keystore** — generate a new one and replace its SHA-1 in the Firebase OAuth client
  configuration. Once the old hash is deregistered, a keystore recovered from history matches no OAuth
  client and grants nothing.

Then change the workflow so the new keystores live outside the repository — a password manager or a
private location, with `recipai.sh setup` / `build-mobile` reading from there. Rotation alone is not
enough: re-encrypting and re-committing puts you back in the same position, except the next time the
ciphertext is public from its first commit with no private-repo grace period.

Why not simply rewrite history instead: `056a2a1` has 54 commits after it on `main` and is an ancestor of
every branch, so the rewrite churns 54+ commits across 6 remote branches, changes every subsequent commit
SHA, and reintroduces the blob if any branch is missed. More importantly, GitHub retains unreachable
objects addressable by SHA after a force-push — which is why the standard guidance for a committed secret
is to treat it as compromised and rotate regardless. A rewrite that leaks leaves you with a live key; a
rotated key that leaks is inert.

Once both keys are rotated and relocated, the blobs in history are dead ciphertext protecting dead keys.
At 2.6 KB each there is no size argument either, so no rewrite is warranted on this finding's account.

### 3. `/actuator/**` is unauthenticated — hardening

`SecurityConfig.java:26` permits all requests to `/actuator/**`, and `Dockerfile:36` relies on that for
its healthcheck.

Exposure today is genuinely small: nothing sets `management.endpoints.web.exposure.include`, so Spring
Boot's defaults apply — `health` only, with `show-details: never`. There is no `/actuator/env`,
`/actuator/heapdump`, or `/actuator/configprops` to reach.

The risk is that the permit rule is broad and the exposure list is implicit. Anyone who later adds an
endpoint for debugging exposes it publicly by default, and a public repo means the gap is documented for
anyone who cares to look. Two cheap fixes: narrow the matcher to `/actuator/health`, and pin the exposure
list explicitly in `application.yml` rather than depending on the framework default.

### 4. Firebase Android API key is committed — expected, but verify restrictions

The same key appears in `mobile/android/app/google-services.json:39` and
`mobile/lib/firebase_options.dart:56`, and in 8 historical blobs.

This is normal and by design — Firebase Android API keys are shipped inside every APK and are not
secrets. They identify the project; they do not authorise anything on their own. Publishing the repo adds
no exposure beyond what shipping on Google Play already does.

Worth doing anyway, since publication makes the project trivially discoverable: confirm in the Google
Cloud console that the key is restricted to the Android app (package name + certificate hashes) and to
only the APIs the app actually calls, so it cannot be lifted into someone else's project.

### 5. Third-party copyrighted content in test fixtures — licensing, not security

`backend/src/test/resources/recipe_sources/` contains full-resolution captures of other people's work:

| File | What it is |
|---|---|
| `instagram.jpg` (1.3 MB) | A complete Instagram post — creator's handle, their photograph, their recipe text, a paid brand partnership and discount code. The account avatar of the logged-in viewer is visible in the bottom nav bar. |
| `ania_gotuje.jpg` / `.pdf` (4.5 MB) | Capture of the ania-gotuje.pl recipe site |
| `kwestia_smaku.jpg` / `.pdf` (2.3 MB) | Capture of the kwestiasmaku.com recipe site |

Republishing these under a public repo is a copyright question rather than a security one, and the
Instagram capture also puts a named private individual's content and a fragment of the author's own
profile picture into a public repository.

The extraction tests need *representative* input, not *this* input. Replacing them with hand-written
recipe images and text authored for the project removes the issue entirely and drops ~8 MB from the repo.
If they are replaced, purge them from history too — otherwise the clone still carries them.

### 6. Infrastructure detail that becomes public — reviewed, acceptable

For completeness, these are disclosed by publication and all appear fine to leave:

- Production hostnames (`recipai-api.stasiak.xyz`, `api.recipai.stasiak.xyz`,
  `recipai-local.stasiak.xyz`) in `recipai.sh:147` and `.vscode/launch.json`
- Firebase project `recipai-751ae`, S3 bucket `recipai-data` in `eu-central-1`
- The deployment topology diagram in `docs/project/architecture.md:198`
- The dev-profile auth bypass (`DevAuthConfig.java`), correctly gated behind `@Profile("dev")` while
  production defaults to `prod` in `application.yml`

None of these are secrets, and the API hostname is already extractable from the published Play app.
Worth noting only that publication makes the API discoverable to a wider audience — the `EXTRACTION`
quota (`V15__limits_schema.sql:24`, 2 per flow) is what stands between a stranger with a Firebase account
and your Gemini bill, so it is worth confirming that limits are actually enabled in production
(`recipai.limits.enabled: true` in `application.yml` — yes) and that the default is tight enough.

### 7. Hygiene

- **No `LICENSE` file.** Without one, a public repo is all-rights-reserved by default: nobody may legally
  use, modify, or redistribute it. If the intent is for people to be able to read *and* use it, add a
  license. If the intent is only to make the code readable, the current state already says that — but say
  it deliberately rather than by omission.
- **No root `README.md`.** The only README is `mobile/README.md` (the Flutter default). The landing page
  of a public repo is worth writing: what RecipAI is, the two-app layout, how to run it. `docs/INDEX.md`
  and `docs/project/` already contain the material.
- **`mobile/2025-10-04_13-27.png`** is a stray screenshot of the app icon on a home screen, committed at
  the repo root of `mobile/`. Harmless, but it is cruft in the first thing visitors see.
- **User emails are logged at INFO/WARN** throughout `permissions/` (e.g. `InviteService.java:45`,
  `PermissionService.java:62`). Not a repository-publication issue at all, but publication invites
  scrutiny, and email addresses in retained logs are personal data under GDPR. Consider logging the
  Firebase UID instead of the email at INFO level.
- **`docs/tasks/`** (13 task directories) and `.claude/skills/` become public. Nothing sensitive in them —
  reviewed for credentials and found clean. Publishing them is a deliberate choice about how much of the
  development process to show, not a risk.

---

## What was checked and found clean

- **Full git history**: 452 commits, all branches, 2,653 blobs under 2 MB each, scanned for Google API
  keys, OpenAI keys, GitHub tokens, PEM private keys, AWS access key IDs, Slack tokens, bearer tokens and
  JWTs. Only the Firebase Android key (finding 4) matched.
- **Backend configuration**: `application.yml`, `application-prod.yml` and `application-dev.yml` take
  every secret via `${ENV_VAR}` placeholders — datasource URL/username/password, `SPRING_AI_API_KEY`.
  `application-test.yml` uses a literal `test-key`, which is correct.
- **`backend/compose.yaml`** hardcodes `POSTGRES_PASSWORD=secret`, which is fine — it is a throwaway local
  container on an ephemeral port, not a production credential.
- **CI**: `.github/workflows/docker-build-api.yml` uses only the auto-provisioned `secrets.GITHUB_TOKEN`
  with `contents: read` / `packages: write`, and is `workflow_dispatch`-only. No injectable expressions in
  `run:` blocks.
- **Release tooling**: `scripts/play_publish.py` reads the service account from
  `scripts/play-service-account.json`, which is gitignored and confirmed never committed.
  `recipai.sh:131` prompts for the keystore password interactively and writes `upload-key.properties`
  at mode 600; `**/*-key.properties` and `**/*.jks` are gitignored.
- **Build output**: no `backend/target/`, `mobile/build/`, `.dart_tool/`, or `scripts/.venv/` content is
  tracked.
- **Committed images**: reviewed. `mobile/assets/graphics/screenshot_*.jpg` are clean UI captures with no
  personal data. App icons and the feature graphic are original artwork.
- **Git identity**: a single author, `Dawid Stasiak <dawid.stasiak21@gmail.com>` — a personal address that
  becomes publicly associated with the repo. Expected for a personal project; flagged only so it is a
  choice. GitHub's `noreply` address is the alternative, and would require a history rewrite.
- **Credential-shaped strings** across the tracked tree (`password|secret|token|api_key = "..."`): only
  the interactive prompt in `recipai.sh`.
- **Token and secret logging**: no bearer token, JWT, or password is written to logs on either side.

---

## Recommended order of work

1. Rotate the Gemini key; move it into `.env`; add `.vscode/` and `.env` to the repo `.gitignore`.
2. Rotate both signing keys (Play upload key reset; new debug keystore + updated SHA-1 in Firebase), and
   move the replacements out of the repository. No history rewrite needed.
3. Replace the third-party test fixtures with original material.
4. Narrow the actuator matcher and pin the exposure list.
5. Verify Firebase API key restrictions in the Google Cloud console.
6. Add `LICENSE` and a root `README.md`; delete `mobile/2025-10-04_13-27.png`.
7. Flip the repository to public.

Steps 1 and 2 are both rotations, and neither depends on touching history — that is deliberate. Rotation
removes the value of the exposed artefact, which is a durable property; a rewrite only reduces its
reachability, which is not.

Step 3 is the one finding a rewrite could actually help with, because copyrighted material cannot be
rotated — if the goal is for those images to be absent from a clone rather than merely absent from the
tip, `git filter-repo` is the only lever. That is a copyright judgement, not a security one, and it should
be decided on its own merits rather than bundled with the keystores. If it is done, force-push every
branch and prefer pushing the cleaned history to a fresh repository over rewriting the existing one in
place, since GitHub keeps unreferenced objects addressable by SHA.
