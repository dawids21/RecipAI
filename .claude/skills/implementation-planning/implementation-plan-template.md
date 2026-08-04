# <Task ID>: <Task name> — Implementation Plan

**Date:** <YYYY-MM-DD>

## Required reading

<Everything the implementer must read before touching code. Three groups:
project docs/standards from `docs/INDEX.md`, design sections and ADRs, and
existing source files whose patterns this task should follow.

The design sections and ADRs will overlap with design.md's own "Required
reading for implementation" — that is intentional; pull the subset that
matters for *this task* rather than sending the reader back to design.md
to filter.

**Docs & standards** (from `docs/INDEX.md`)
- `docs/standards/<file>.md` — <one-line reason it's relevant>
- `docs/architecture/<file>.md` — <one-line reason it's relevant>

**Design & ADRs**
- `design.md` > <Section name> — <one-line reason>
- `docs/ADRs/NNNN-<slug>.md` — <one-line reason>

**Code to mirror**
- `path/to/ExistingFile.java` — <pattern or convention to follow>

If a group has no entries, write "_None._" under its heading rather than
deleting the heading.>

## File inventory

<Flat list of every file created, modified, or deleted. One line per file
stating what happens there. Complete — not just the integration points from
design.md. Doubles as the PR checklist.

- **CREATE** `path/to/NewFile.java` — <what it is, in ~10 words>
- **MODIFY** `path/to/ExistingFile.java` — <what changes>
- **DELETE** `path/to/OldFile.java` — <why it goes>
- **CREATE** `src/main/resources/db/migration/V<N>__<name>.sql` — <what the migration does>>

## Step-by-step plan

<Ordered, commit-sized steps. Each step names what changes, which files it
touches, and what goes green (compile, a new test passes, a manual check
succeeds). The implementer should be able to run off this list top to bottom.

1. **<Step name>** — <what changes, in 1–2 sentences>
   - Files: `path/to/File.java`, `path/to/OtherFile.java`
   - Verify: <compile / `./mvnw test -Dtest=FooTest` passes / `curl ...` returns 200>

2. **<Step name>** — <what changes>
   - Files: `...`
   - Verify: `...`

## Test plan

<Concrete test cases per level. Not "write tests" — an actual case list.
Edge cases, error modes, boundary conditions.

**Unit tests**
- `FooServiceTest` — <case>, <case>, <case>
- `BarMapperTest` — <case>

**Integration tests**
- `FooControllerIT` (`@SpringBootTest` + Testcontainers) — <case>
- `FooRepositoryTest` (`@DataJpaTest`) — <case>

**Flutter widget/integration tests** (if applicable)
- `foo_screen_test.dart` — <case>

**Manual verification**
- <Anything that can't be automated cheaply — visual check, cross-device,
  real S3 bucket, etc.>

If a level doesn't apply, write "_N/A — <one-line reason>_" rather than
deleting the heading.>

## Verification checklist

<Pre-merge gate. Tick through before opening the PR.

- [ ] Lint / formatter passes (`./mvnw spotless:check`, `dart analyze`, etc.)
- [ ] All new and existing tests pass
- [ ] Migration runs cleanly up *and* down against a fresh DB
- [ ] `tasks.md` > <Task> "How to verify" succeeds end-to-end
- [ ] Design assumptions listed in `design.md` > Assumptions to verify are
      confirmed (or the remaining ones are documented)
- [ ] No new compiler warnings
- [ ] Logs at `INFO` are clean on the happy path

Tailor per task. Drop items that don't apply; add task-specific ones.>

## Risks surfaced during planning

<Things that came up while working this plan out that `tasks.md` > Risks /
unknowns didn't catch. Format mirrors design.md's "Assumptions to verify":

- **Risk:** <what>
  **Why it matters:** <consequence>
  **Mitigation:** <what to do, or "flag for review before implementing">

If none: "_No additional risks beyond those in tasks.md._" Or delete the
section entirely if tasks.md also had none.>
