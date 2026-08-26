---
name: docs-updating
description: After a feature is implemented, check the project's documentation for staleness and update whatever no longer describes the project accurately — scoped to what the project's `CLAUDE.md` defines as documentation. Use whenever the user says the docs are probably out of date, wants to refresh/sync documentation after building something, or asks to bring docs in line with what was just implemented in a `docs/tasks/<date>-<name>/` folder. Triggers on phrasings like "update the docs now that T2 is done", "the README is probably stale after this change", "sync the documentation for docs/tasks/2026-04-18-fix-auth-redirect", "make sure CLAUDE.md still matches reality". Do NOT use this to write code (that's `/implementing`), to produce a plan, or to edit inline code comments and docstrings unless `CLAUDE.md` lists them as documentation.
disable-model-invocation: true
---

# Docs Updating

Bring the project's documentation back in line with reality after a feature
lands. On either track of the workflow this runs once `/implementing` is done and
before `/reviewing` gates the commit — how much planning happened upstream
doesn't change the job here. The code just changed; the docs that describe it
may now be wrong, incomplete, or misleading. Your job is to find that drift and
fix it — and *only* the documentation the project actually recognizes as
documentation, not every prose file you can find.

## Inputs

- The project's **`CLAUDE.md`** — **required.** It defines what counts as
  documentation for *this* project (which files, which directories). This is the
  scope boundary for everything below. If `CLAUDE.md` doesn't name any
  documentation, stop and ask the user what to check — don't guess from filenames
  like `README.md`, since "looks like docs" isn't the same as "is in scope here".
- A **task directory path** (`docs/tasks/<date>-<name>/`) — so you can see what
  was built and compare the docs against it. If the user doesn't give one, ask
  which feature/change the docs should be updated for before proceeding.

## Workflow

### 1. Establish what counts as documentation

Read the project's `CLAUDE.md` and pull out the concrete list of documentation
files and locations it defines. That list is your scope for the rest of the run —
nothing outside it gets touched. If the list is empty or `CLAUDE.md` is silent on
the matter, stop here and ask the user what they consider documentation; updating
the wrong files is worse than asking.

### 2. Understand what actually changed

Two sources, and you want both:

- **Intent** — read the artifacts in the task folder (`implementation-plan.md` or
  `plans/T<N>-implementation-plan.md`, the task design, the HLD as needed). These
  tell you what the change was *meant* to do and which behaviors/interfaces it
  touched.
- **Reality** — look at the actual diff (`git diff`, the changed files, new or
  removed commands, config, flags, endpoints). Docs describe the code as it is, so
  the code is the source of truth where intent and reality disagree.

You're building a mental model of *what a reader of the docs would now find
wrong*: renamed commands, changed defaults, new features that go unmentioned,
removed behavior still documented as present.

### 3. Compare each doc against that model

Read every in-scope documentation file and, for each, note specifically what is:

- **Stale** — describes behavior, names, or interfaces that the change altered.
- **Missing** — new behavior or surface area the change introduced that the doc
  should mention but doesn't.
- **Incorrect** — was already wrong, or became wrong, in a way this change makes
  it sensible to fix.

Be concrete: "the install section still references the old `setup.sh` script
removed in this change" beats "install section is outdated". If a file is fully
in sync, say so and leave it alone — don't invent edits to look busy.

### 4. Present proposed changes before editing

Give the user a summary, grouped by file: what you found stale/missing/incorrect
and the specific edit you propose for each. This is a checkpoint, not a
formality — the user may know a doc is intentionally aspirational, or that a
"stale"-looking line is deliberate. Wait for their go-ahead.

### 5. Apply the updates

Once confirmed, make the edits. Match each document's existing voice, structure,
and formatting — a docs update should read as if the original author wrote it,
not as a bolted-on patch. Then report what you changed, file by file, and flag
anything you deliberately left alone and why.

## Scope boundaries

- **Only what `CLAUDE.md` defines as documentation.** Inline code comments and
  docstrings are out of scope unless `CLAUDE.md` explicitly lists them.
- **Documentation, not code.** If you notice the *code* is wrong while reading,
  surface it to the user — don't fix it here. This step trusts the implementation
  and aligns the prose to it.
- **Drift only.** Don't rewrite docs that are already accurate just because you'd
  have phrased them differently.
- **The current state, not the change.** The docs describe the system as it is
  now, not what this change did to it, and they never reference anything under
  `docs/tasks/` — no task directories, task IDs or plans. `CLAUDE.md` carries the
  full rule and the phrasings that give it away; watch for them in your own edits,
  where you have just read the diff and the docs haven't.
