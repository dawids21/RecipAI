# Documentation Standard

## Purpose

Docs exist so an agent can understand a part of the codebase without reading its implementation. This standard covers
`docs/project/`, `docs/backend/` and `docs/mobile/`. `docs/tasks/` and `docs/ADRs/` are exempt: they record history by
design.

## Present Tense

Docs state the current behaviour in the present tense — never how the code used to work or what a change altered ("was
X before", "no longer", "as of this task"), and never reference `docs/tasks/` (task directories, task IDs, plans).

## The Test for a Sentence

Keep a sentence only if it is both:

1. **expensive to derive from code** — reading it saves opening several files, or explains a reason the code can't show;
   and
2. **still true after a routine refactor** — renaming a method, extracting a widget or rewriting a query doesn't make
   it wrong.

- Keep: "Usage is counted against the list, but the quota value is resolved from the list's owner." (a rule spanning
  several classes, survives refactors)
- Drop: "`findAccessibleIds` takes the accessible recipe ids and collection ids as two IN parameters, no join." (one
  look at the repository shows it, and it breaks on the next query rewrite)

## What Docs Contain

- **Purpose and boundary** — what the module is for, what it owns, what it calls, and who calls it.
- **File map** — enough to find your way around (see [File Map Comments](#file-map-comments)).
- **Contracts** — API shapes and status codes, database tables and constraints, facade operations other modules rely
  on.
- **Rules and their reasons** — each stated once.
- **Cross-module flows** — documented in the module that drives the flow; the other modules link to it.
- **Gotchas** — behaviour that surprises a reader of the code or breaks an obvious-looking change.

## What Docs Leave Out

| Leave out                          | Why                                                            | Instead                                                         |
|------------------------------------|----------------------------------------------------------------|-----------------------------------------------------------------|
| Query method names and SQL tactics | Visible in the repository; change on every query rewrite       | State the access rule the query enforces                        |
| Widget implementation details      | Visible in the widget; styling belongs to the theming standard | Describe what the user sees and can do                          |
| Method lists, full or partial      | A partial list implies unlisted methods don't exist            | Describe the operation other modules rely on, in a contract     |
| A rule repeated in a file comment  | Two copies drift apart                                         | Point to the section that states the rule (see below)           |

Examples to avoid: "includes `ownedResources` and `deleteAllByEmail`" (partial method list); "Uses IconButton.outlined",
"uses AnimatedSize", "Error icon (64px)" (widget details).

## Stating Each Fact Once

Each fact has one home — the most specific doc that owns it:

- a file comment, for what a single file is for;
- a section of the module's docs, for a module rule, contract or gotcha;
- the driving module's docs, for a cross-module flow;
- one module's `ui.md`, for a screen, widget or user flow: the module whose folder holds the screen or widget, or
  the module the flow is about; other `ui.md` files link to it;
- a project doc, for a system-wide fact (see [Project Docs](#project-docs)).

Everywhere else, link to it by relative path and section name (e.g. "see `docs/backend/modules/limits/module.md` →
Behaviour") instead of restating it, even in summary.

## File Map Comments

A file map is a tree of the module's files with a comment per file.

- A comment is about **100 characters or fewer**: what the file is for, plus at most one distinguishing fact.
- When the file takes part in a rule stated in a section, point to it ("see Rules") instead of restating it.
- A file whose name says everything (e.g. request/response DTOs, setup files) may have no comment.

## Module Doc Layout

Every module has its own directory of docs. Sections marked *(when applicable)* are omitted rather than left empty;
small modules get short docs.

### Backend — `docs/backend/modules/<module>/`

**`module.md`**

- **Purpose** — 2–3 lines.
- **Boundary** — what the module owns, the facades it calls, and the modules that call its facade.
- **File map** — `backend/src/main/...` sources.
- **Rules** *(when applicable)* — business rules and their reasons.
- **Cross-module Flows** *(when applicable)* — one subsection per flow the module drives.
- **Gotchas** *(when applicable)*.

**`api.md`** *(when the module exposes endpoints)* — per endpoint: method and path, request shape, response shape, and
status codes with their meaning. Shapes shared across modules are documented once and linked.

**`db.md`** *(when the module owns tables)* — **Tables** (columns and constraints), **Relationships**, **Indexes**, plus
seeded data or migration behaviour when a reader needs it.

### Mobile — `docs/mobile/modules/<module>/`

A mobile module is a folder under `mobile/lib/features/`, plus `core` (covering `main.dart`, `core/` and `shared/`).

**`module.md`**

- **Purpose** — 2–3 lines.
- **Boundary** — backend endpoints called (linked to the backend `api.md`, shapes not copied), features this one uses,
  and features that use it.
- **File map** — `mobile/lib/...` and `mobile/test/...` sources.
- **Services and State** — what each service holds, its lifetime (singleton or lazy singleton; screen- or
  session-scoped), and its rules.
- **Cross-module Flows** *(when applicable)* — one subsection per flow the module drives.
- **Gotchas** *(when applicable)*.

**`ui.md`** — screens and widgets, and the flows the user goes through: what the user sees and can do. Service logic
and rules live in `module.md` and are linked from here.

**`data.md`** *(when the module stores data on the device)* — tables, columns and constraints, the schema version and
the rule for changing it, and what survives a restart or sign-out. This data outlives app versions, so a wrong change
breaks installed apps.
