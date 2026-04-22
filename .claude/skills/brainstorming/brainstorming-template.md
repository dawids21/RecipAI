# <Task name> — solution brainstorming

**Date:** <YYYY-MM-DD>
**Status:** brainstorming

## Summary

<One or two sentences: what we're trying to build (from requirements) and
what this doc is doing — exploring N distinct approaches and recommending
one (or deferring).>

## Approaches considered

### Approach 1: <Name>

**Sketch.** <2–4 sentences on how this actually works in this codebase.
Reference concrete modules, files, or systems where relevant. Stay at the
level of shape, not implementation detail.>

**Trade-offs.**
- <Honest cost or benefit>
- <Honest cost or benefit>
- ...

**When it's the right choice.** <The conditions under which this approach
clearly wins.>

**Main risk.** <The single thing most likely to go wrong.>

### Approach 2: <Name>

<Same structure.>

### Approach 3: <Name>

<Same structure.>

<Add Approach 4 / 5 as needed. Drop the header if fewer approaches.>

## At a glance

<Comparison table on the 2–3 dimensions that actually discriminate between
these options for this task. Pick the dimensions that matter — not a generic
template. Examples: implementation cost, latency, revocability, migration
burden, operational cost, coupling to existing modules.>

| Approach | <Dim 1> | <Dim 2> | <Dim 3> |
|----------|---------|---------|---------|
| 1. ...   | ...     | ...     | ...     |
| 2. ...   | ...     | ...     | ...     |

## Recommendation

<One of:>

<**Chosen: Approach N.** One paragraph on why, referencing the specific
trade-offs that decided it — not generic best-practice language. Mention
explicitly what this choice gives up relative to the runners-up.>

<**Deferred.** What's deferred is the recommendation, not the exploration.
State clearly what information is missing, what would resolve it (a spike,
a benchmark, a conversation, a production data check), and who/what can
provide it. Include which approaches are still live and which have been
ruled out.>

## Questions for design

<Questions that only matter once an approach is chosen — for the designing
step to resolve. Different from deferred brainstorming questions, which
block the choice itself. If none, delete this section.>

- ...
- ...
