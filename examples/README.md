# Examples — simple → complex

> **Starting a new project?** [`create-agent/`](create-agent/) is a copy-out starter — a standalone
> mini-project (its own build, a ~20-line `Main`) that depends on the **published** artifacts. Copy the
> folder, set an API key, `gradle run`. The examples below instead run *inside* this repo against the
> source.

A small, canonical tour of `java-ai-agent`. Run any with:

```bash
./gradlew :examples:run -PmainClass=dev.vaijanath.aiagent.examples.<ClassName>
```

Most examples are only meaningful with a **real model**. Point at a local Ollama model:

```bash
export AGENT_MODEL=gemma4:31b-cloud   # any pulled Ollama model
# export OLLAMA_BASE_URL=http://localhost:11434   # optional; this is the default
```

Without `AGENT_MODEL` they fall back to an honest **stub** (obvious placeholder output) so they
still run, but the real behavior (tool orchestration, streaming) needs a model.

| Example | Shows | Model |
|---------|-------|-------|
| `MinimalAgent` | the smallest agent: model + prompt | optional |
| `ToolUsingAssistant` | **multi-tool orchestration** — the model chains `convert` (mi→km) then `math` (add) to answer one question; the trace shows each tool call | needs a model |
| `SafeAgent` | the local safety layer — Llama Guard + PII scrub | needs `llama-guard3:1b` |
| `MemoryAcrossSessions` | **cross-session memory** — learnings persist to a file via `FileEpisodicStore`; run it twice and the second (separate) process already knows the lesson | needs a model |
| `StreamingChat` | **streaming** — prints the model's reply token-by-token as it's generated | needs a model |
| `LearningAgent` | **self-learning** — a `ReflectiveAgent` slips on the first task, records the lesson, and recalls it on a later similar task (2 attempts → 1) | none (deterministic) |

## What to look for

- **ToolUsingAssistant** — DEBUG trace: `tool 'convert' -> ok`, `tool 'math' -> ok`; the model never
  computes in its head. Token usage printed at the end.
- **SafeAgent** — a harmful prompt is replaced by a safe response; PII in the input is scrubbed before
  it reaches the model. Guardrails fail closed.
- **MemoryAcrossSessions** — the recorded lesson is an `Episode` persisted to disk, so the learning is
  auditable and survives a restart.

> More elaborate showcases (multi-agent orchestration, skills, deep-research planning, learning,
> evaluation) live outside this repo to keep the core lean — see the cookbook ([docs/COOKBOOK.md](../docs/COOKBOOK.md))
> for the corresponding patterns.
