---
name: committing
description: Write RecipAI commit messages in Conventional Commits form. Use whenever you are about to run `git commit`, are asked to commit, stage-and-commit, amend, reword, or squash work, or are asked what a commit message for a change should say. Triggers on phrasings like "commit this", "write the commit message", "fix up the commit subject", "is this message right?".
---

# Committing

RecipAI follows [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).

## Format

```
type(scope): subject

body

footer
```

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

Imperative mood, lowercase start, no trailing period, ideally ≤ 72 chars.
Say what the change does for the reader, not which files moved.

## Body

Optional but preferred for anything non-trivial. Wrap at ~72 chars. Explain
*why* and what the change implies — the diff already shows what. Skip it for
genuinely self-evident commits.

## Examples

```
feat(backend): cap items per shopping list
feat(mobile): map plan limit refusal to 429
docs: document the meal plan cap migration
```
