---
name: brainstorming
description: Run a Socratic brainstorming conversation to scope a programming task before implementation, then save the result as a structured brainstorming document. Use this skill whenever the user describes a new feature or a refactor they're considering — especially if they mention "brainstorm", "scope", "think through", "plan", "flesh out", or present a task description without yet asking for code. Use it even when the user's description seems complete; the skill's job is to surface the gaps. Do NOT use this skill for bug investigation (bugs need a separate reproduction-first workflow), when the user has already decided on an approach and is asking for implementation help, or when they're asking a narrow technical question with no design component.
disable-model-invocation: true
---

# Task brainstorming

Guide the user through a Socratic-method conversation to scope a programming task (a new feature or a refactor) across seven dimensions, then write a structured brainstorming document the user can hand off to implementation.

This skill is a **pre-planning** step. It produces context, not code. The output is meant to feed into subsequent planning (PRDs, ADRs, API plans) or directly into an implementation agent.

**Not for bugs.** Bug investigation needs a different workflow — reproduction, isolation, root-cause analysis — and should be handled by a separate skill. If the user brings a bug to this skill, say so and decline rather than forcing it into the brainstorming frame.

## The seven dimensions

Every brainstorming conversation should cover these areas. Not every area needs equal depth — a small feature tweak won't have much to say about integration points, and a pure refactor won't have much context to gather — but each should be touched.

1. **Context** — Why does this need to happen? What problem does it solve? What's the motivation?
2. **Requirements** — How should the system behave after this is done? Concrete, observable behavior.
3. **Anti-requirements** — What are we explicitly *not* doing? What's out of scope?
4. **Constraints & assumptions** — Technical limits, dependencies, things we're taking for granted.
5. **Acceptance criteria** — How will we know it's done? What's testable?
6. **Edge cases** — Unusual scenarios, failure modes, boundary conditions.
7. **Integration points** — Where in the existing system does this live? What are the entry points?

## Conversation flow

The conversation has three phases: **foundation**, **deep dive**, **recap**. Do not skip phases.

### Phase 1: Foundation (one turn)

After the user describes the task, respond with a single batch of **5–8 questions** covering the seven dimensions at a high level. Aim for roughly one question per dimension, adjusted for the task type:

- For **features**: weight toward requirements, acceptance criteria, integration points.
- For **refactors**: weight toward anti-requirements (what behavior stays the same), constraints, acceptance criteria.

Format the batch as a numbered list so the user can answer question-by-question. Group related questions if helpful.

**Skip obvious questions.** If the user's initial description already answers something, don't ask it again — acknowledge it briefly and move on.

### Phase 2: Deep dive (multiple turns)

Based on the user's answers, ask **one or two follow-up questions at a time**. Be Socratic — your job is to surface what the user hasn't articulated yet, not to walk a checklist.

Good deep-dive questions:
- Probe vague answers ("You said 'reasonable performance' — what's the actual threshold?")
- Challenge assumptions ("You're assuming the existing auth will work here. Have you verified that?")
- Force explicit trade-offs ("If we have to choose between X and Y, which wins?")
- Surface implicit scope ("Does this apply to imported recipes too, or only user-created ones?")
- Test edge cases with concrete scenarios ("What happens if the upload succeeds but the DB write fails?")

Avoid:
- Yes/no questions (they rarely surface new info)
- Questions that just re-ask something already answered
- Implementation-level questions ("which library should we use?") — those belong in a later planning step

Return to dimensions that feel under-explored. If the user gave a thin answer to "anti-requirements" in the foundation round, come back to it.

### Phase 3: Recap (one turn, before writing the file)

When the stopping criteria (below) are met, do a recap turn:

1. Summarize your understanding across all seven dimensions in concise prose or bullets.
2. Explicitly ask: **"Does this capture it? Anything missing, wrong, or worth adding before I write the doc?"**
3. Propose a filename: `docs/tasks/<YYYY-MM-DD>-<task-name>/brainstorming.md` with `<task-name>` in kebab-case. Ask the user to confirm or adjust.

Only after the user confirms do you write the file.

## When to stop asking questions

End the deep dive and move to the recap when **any** of these is true:

- **Coverage**: all seven dimensions have enough material to fill the corresponding template sections without leaving them blank or hand-wavy.
- **User signals done**: the user says "that's enough", "let's wrap up", "write the doc", "I don't know, let's figure it out later", or similar.
- **Diminishing returns**: the user's last two or three answers have been short, uncertain, or variations of "I don't know yet". Further questions are unlikely to yield new scoping information and belong in the **Open questions** section of the doc.
- **Drifting to implementation**: your next question would be about *how to build it* rather than *what we're building and why*. That's outside this skill's scope — stop and recap.
- **Turn budget**: you've done 3+ full rounds of deep-dive questions. Beyond this, returns drop sharply. Recap and capture remaining uncertainty as open questions.

When in doubt, err toward stopping. A brainstorming doc with a populated **Open questions** section is more useful than one dragged out by forced questioning.

## Writing the file

After the user confirms the recap:

1. Read the template at [brainstorming-template.md](brainstorming-template.md)
2. Create the directory `docs/tasks/<YYYY-MM-DD>-<task-name>/`.
3. Write `brainstorming.md` inside it, filling in each section from the conversation.
4. Use today's date. Use the task name the user confirmed.
5. If a section genuinely has no content (e.g., no edge cases came up for a trivial task), write a short honest note like "None identified during brainstorming" rather than leaving it blank or inventing filler.
6. Populate **Open questions** with any unresolved items from the conversation. If there are none, delete the section entirely.

Tell the user where the file was written.

## Style guidance

- **Be direct.** Socratic questioning is not the same as being indirect or coy. Ask clear, specific questions.
- **One question should do real work.** If a question could be answered with "yes" and leave no new information, rephrase it.
- **Quote the user's own words back to them** when probing. It's easier to refine "a fast response" than to answer "what are the performance requirements?" in the abstract.
- **Don't lecture.** Your role is to draw out the user's thinking, not to explain best practices. If you have a concern, phrase it as a question.
- **Respect the user's time.** If the task is small, the brainstorming should be short. A tiny copy change or a one-line config tweak does not need 20 questions.

## Example opening (feature)

> User: I want to add a "share recipe" feature to RecipAI where users can generate a public link to a recipe.
>
> Agent: Good, let me get the foundation down first — a batch of questions across the main dimensions, then we'll dig in:
>
> 1. **Context**: is this driven by a specific user request, a competitor feature, or your own thinking? Any constraint on timing?
> 2. **Requirements**: when a user clicks "share", what's the exact flow — modal, new page, copy-to-clipboard? And what does the recipient see when they open the link?
> 3. **Anti-requirements**: is this read-only sharing, or are we also thinking about collaborative editing? Any auth model for recipients (anonymous vs. account-required)?
> 4. **Constraints**: does the shared link need to work if the original recipe is later edited or deleted? Should it respect the existing S3 image encryption setup?
> 5. **Acceptance criteria**: what's the demo you'd run to say "this is done"?
> 6. **Edge cases**: what about recipes with images that failed to upload, or recipes mid-edit?
> 7. **Integration points**: does this live as a new endpoint on the existing recipe controller, or a separate sharing service?

After the user answers, move into deep-dive mode — one or two sharper follow-ups at a time.
