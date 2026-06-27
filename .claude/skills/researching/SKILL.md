---
name: researching
description: Research a topic using the web and user-specified sources, then save a structured report to a file. Use this skill whenever the user asks to research, investigate, look up, gather information about, or summarize a topic — especially when they specify an output file path or say things like "research X and save to Y", "find information about X", "write a report on X", "look into X and put it in a file". Also trigger when the user provides a list of URLs or sources to read and synthesize. Do NOT use this skill for code lookups, debugging, or tasks that are purely about reading the local codebase.
disable-model-invocation: true
---

# Research skill

Research a topic using the web and any sources the user provides, then write a structured report to a specified file.

## Arguments

The user provides (in any order, in natural language):
- **Topic** — what to research
- **Output path** — where to save the report (file path, absolute or relative to cwd)
- **Sources** (optional) — specific URLs, documents, or domains the user wants included

## Workflow

1. **Parse the request** — identify the topic, output path, and any user-specified sources. If the output path is missing, ask before proceeding.

2. **Research** — search the web broadly, then go deep on the most relevant results. Always include user-specified sources. Prioritize primary sources (official docs, papers, original articles) over secondary summaries. Aim for 5–10 high-quality sources minimum; more for broad or contested topics.

3. **Synthesize** — pull findings into a coherent narrative. Don't just quote or list sources — extract the key facts, tensions, and open questions. Note where sources disagree.

4. **Write the report** — save to the specified path using the structure below. Create parent directories if needed.

## Report structure

```
# [Topic]

## Summary
2–4 sentence executive summary of the most important findings.

## Key findings
Bulleted list of the most important facts, organized thematically if there's a lot.

## Details
Longer-form narrative covering the topic in depth. Use subheadings if it helps.

## Open questions / gaps
What's unclear, contested, or not well covered by available sources.

## Sources
- [Title](URL) — one-line note on what this source contributed
```

Adjust the structure if it doesn't fit — e.g., for a narrow factual topic, "Details" and "Key findings" can merge; for a deep technical topic, add domain-specific sections.

## Quality bar

- Every claim should be traceable to a source in the Sources section.
- Prefer recency for fast-moving topics (tech, current events); prefer authoritative references for stable topics (science, history).
- If the user specified sources, they must appear in the report, not just be consulted silently.
- Write for someone who knows nothing about the topic but is smart — no jargon without explanation, no padding.
