---
name: committing
description: Write RecipAI commit messages in Conventional Commits form. Use whenever you are about to run `git commit`, are asked to commit, stage-and-commit, amend, reword, or squash work, or are asked what a commit message for a change should say. Triggers on phrasings like "commit this", "write the commit message", "fix up the commit subject", "is this message right?".
---

# Committing

RecipAI follows [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).

## Format

```
type(scope): subject          ← under 50 chars, 72 hard ceiling

body, hard-wrapped at 72      ← at most 3 short paragraphs
columns

Footer-Key: value
```

Exactly one blank line between subject and body, and between paragraphs.
The blank line is what makes `git log --oneline`, `git shortlog` and every
review tool treat the first line as the subject — without it the whole
message collapses into one unreadable subject.

## Type

One of: `feat`, `fix`, `docs`, `chore`, `refactor`, `build`, `test`, `style`.

Pick by what the change *is*, not where it lands — tests added alongside a
feature are part of the `feat`, not a separate `test`.

## Scope

Only `mobile` or `backend`, and only when the change touches that side alone.

- Touches `mobile/` only → `feat(mobile): …`
- Touches `backend/` only → `feat(backend): …`
- Touches both, or neither (docs, root config, scripts, CI) → no scope, no
  parentheses: `docs: …`

Never invent other scopes (module, feature, layer names).

## Subject

Imperative mood, lowercase start, no trailing period.

**Length: aim under 50 characters; 72 is a hard ceiling.** The 50 is a
target because `git log --oneline`, GitHub's commit list and `git blame`
all truncate around there, and a subject that survives untruncated is the
one people can actually scan. 72 is the point where truncation is certain,
so a subject over it is simply wrong — split the detail into the body.

Count the full line, `type(scope): ` prefix included — that prefix alone
eats 15-17 characters.

Say what the change does for the reader, not which files moved. When a
subject runs long it is usually listing things; name the shared idea
instead and let the body enumerate.

Too long (78): `feat(backend): enforce stock limits on recipes, collections and shopping lists`
Better (46): `feat(backend): cap the resources a user can own`

## Body

Optional but preferred for anything non-trivial. Explain *why* and what the
change implies — the diff already shows what. Skip it for genuinely
self-evident commits.

**Wrap: hard newlines at 72 columns.** Git does not wrap for you, so an
unwrapped paragraph is one enormous line that terminals and `git log`'s
four-space indent render as a horizontal scroll. Write real line breaks,
do not rely on soft wrapping in your editor.

**Length: at most 3 paragraphs, each at most 5 lines — roughly 15 lines
total.** A commit message is a summary read in a log, not a design
document. When the reasoning genuinely needs more room it belongs in
`docs/` and the body should point at it, because that is where the reader
will look for it in six months and where it can be kept current. Lead with
the single most important paragraph, so a reader who stops after it still
has the point.

## Footers

Trailers (`Co-Authored-By:`, `Claude-Session:`, `Refs:`) go after one blank
line following the body. They are `Key: value` pairs parsed by tooling, so
never wrap them — a long URL stays on one line, exempt from the 72-column
rule.

## Writing the message

Compose the message in a file and pass it with `git commit -F`, or use a
quoted heredoc:

```bash
git commit -F - <<'EOF'
feat(mobile): show remaining quota on recipe form
...
EOF
```

Repeated `-m` flags cannot express hard-wrapped paragraphs — each one
becomes its own unwrapped paragraph — so avoid them for anything with a
body.

Before committing, verify the mechanics rather than eyeballing them:

```bash
git log -1 --format=%B | awk '
  NR==1 { printf "subject %d chars%s\n", length, (length>72 ? " — OVER 72" : (length>=50 ? " — over 50" : "")) }
  NR==2 && $0!="" { print "MISSING blank line after subject" }
  NR>1 && length>72 && !/^[A-Za-z-]+: / { printf "line %d is %d chars\n", NR, length }
'
```

## Examples

Subject only — the change explains itself:

```
docs: fix the meal plan cap link in INDEX.md
```

Subject and body — why, not what:

```
feat(mobile): discard item creates refused at cap

A 429 on create becomes a permanent discard rather than a transient
failure: the cap is a stock limit, so waiting resolves nothing and
retrying would jam the list's outbox behind an entry that can never
succeed.

A burst of refusals raises one snackbar rather than a queue of them —
ten items added offline to a full list would otherwise bury the undo
bar behind them.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

Detail deferred to docs rather than inflating the body:

```
feat(backend): move the meal plan cap onto limits

Creation reserves one MEAL_PLAN unit keyed by the owner before anything
is written, and deletion releases it. The cap now lives in limit_config,
so raising it is SQL rather than a redeploy.

V17 seeds the default and the repeatable recompute gains a fourth block,
seeding existing owners at rollout. Rollout order and the refusal
contract are in docs/backend/modules/limits/.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```
