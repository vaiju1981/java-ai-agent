# Examples — simple → complex

A tour of `java-ai-agent`. Run any with:

```bash
./gradlew :examples:run -PmainClass=dev.vaijanath.aiagent.examples.<ClassName>
```

Most examples are only meaningful with a **real model**. Point at a local Ollama model:

```bash
export AGENT_MODEL=gemma4:31b-cloud   # any pulled Ollama model
# export OLLAMA_BASE_URL=http://localhost:11434   # optional; this is the default
```

Without `AGENT_MODEL` they fall back to an honest **stub** (obvious placeholder output) so they
still run, but the real behavior (tool orchestration, skill selection, self-correction) needs a model.

## Basics

| # | Example | Shows | Model |
|---|---------|-------|-------|
| 1 | `MinimalAgent` | the smallest agent: model + prompt | optional |
| 2 | `HelloAgent` | a tool, a keyword guardrail, token accounting | optional |
| 3 | `SafeAgent` | the local safety layer — Llama Guard + PII scrub | needs `llama-guard3:1b` |

## Substantial (run these with a model)

| # | Example | What it really demonstrates |
|---|---------|------------------------------|
| 4 | `ToolUsingAssistant` | **multi-tool orchestration** — the model chains `convert` (mi→km) then `math` (add) to answer one question; the trace shows each tool call |
| 5 | `SkilledAgentExample` | **skills, model-selected** — an `LlmSkillSelector` picks `math-tutor` (with the math tool) vs `french-translator` per task; the equipped skill's instructions + tools steer the answer |
| 6 | `ResearchAssistant` | **deep agent** — plans a CTO briefing into sections, writes each with a concurrent sub-agent (virtual threads), and synthesizes; prints the plan, the per-section **workspace artifacts**, and the final briefing |
| 7 | `LearningAgentExample` | **learning from real mistakes** — a deterministic verifier rejects banned words; the model slips, the agent records the lesson, retries, and self-corrects (watch the INFO log show each attempt) |
| 8 | `MemoryAcrossSessions` | **cross-session memory** — learnings are persisted to a file with `FileEpisodicStore`; run it twice and the second (separate) process already knows the lesson |
| 9 | `PermissionedAgent` | **tool authorization** — an allow-list (or human approval) gates tool calls; a sensitive tool is denied while a safe one runs |

(`DeepResearchAgent` is a smaller deep-agent demo kept as a stepping stone to `ResearchAssistant`.)

## Capstone — everything at once

| Example | Composes |
|---------|----------|
| `StudyBuddy` | **kidguard guardrails + skills + tools + deep agent + learning + observability**, all through the one `Agent` seam |

`StudyBuddy` runs four turns on one safe, skilled, observed worker: a homework question (skill + tool),
a harmful request (blocked by Llama Guard), an encouragement that must be signed "Mitra" (self-corrects
on the second attempt), and a "why is the sky blue" explainer answered by a deep agent whose
sub-agents are themselves safe and skilled — then prints total token usage. Best with
`AGENT_MODEL` set and `ollama pull llama-guard3:1b`.

```bash
ollama pull llama-guard3:1b
AGENT_MODEL=gemma4:31b-cloud ./gradlew :examples:run \
  -PmainClass=dev.vaijanath.aiagent.examples.StudyBuddy
```

## What to look for

- **ToolUsingAssistant** — DEBUG trace: `tool 'convert' -> ok`, `tool 'math' -> ok`; the model never
  computes in its head. Token usage printed at the end.
- **SkilledAgentExample** — the math task ends up using the `math` tool and explaining steps; the
  translation task returns French only. Different skills, selected by the model, change behavior.
- **ResearchAssistant** — `plan.md` lists subtasks marked `DONE`; each `step-N.txt` is a real section
  draft produced by a concurrent sub-agent; the final briefing synthesizes them.
- **LearningAgentExample** — attempt 1 typically uses a banned word → the verifier teaches a lesson →
  a later attempt avoids it. The lesson is a recorded `Episode`, so the learning is auditable.
