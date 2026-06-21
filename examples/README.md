# Examples — simple → complex

A graduated tour of `java-ai-agent`. Each rung adds one capability. Run any of them with:

```bash
./gradlew :examples:run -PmainClass=dev.vaijanath.aiagent.examples.<ClassName>
```

By default examples use an honest **stub model** (offline; obvious placeholder output). For real
answers, point at a local Ollama model:

```bash
export AGENT_MODEL=llama3.2            # any pulled Ollama model (or gemma4:31b-cloud, etc.)
# export OLLAMA_BASE_URL=http://localhost:11434   # optional; this is the default
```

| # | Example | Capability it introduces | Needs a model? |
|---|---------|--------------------------|----------------|
| 1 | [`MinimalAgent`](src/main/java/dev/vaijanath/aiagent/examples/MinimalAgent.java) | the smallest agent — a model + a prompt | optional |
| 2 | [`HelloAgent`](src/main/java/dev/vaijanath/aiagent/examples/HelloAgent.java) | a **tool**, a **guardrail**, and **token accounting** | optional* |
| 3 | [`SafeAgent`](src/main/java/dev/vaijanath/aiagent/examples/SafeAgent.java) | the **local safety layer** — Llama Guard + PII scrub | yes (`ollama pull llama-guard3:1b`) |
| 4 | [`SkilledAgentExample`](src/main/java/dev/vaijanath/aiagent/examples/SkilledAgentExample.java) | **skills** with progressive disclosure (equip per task) | optional |
| 5 | [`DeepResearchAgent`](src/main/java/dev/vaijanath/aiagent/examples/DeepResearchAgent.java) | **deep agents** — plan → parallel sub-agents → synthesize | optional* |
| 6 | [`LearningAgentExample`](src/main/java/dev/vaijanath/aiagent/examples/LearningAgentExample.java) | **learning from mistakes** — episodic memory + reflection | no (deterministic) |

\* *Runs offline with the stub, but the capability (real tool-calling, real planning) only comes
alive with a model.*

## What to look for

- **MinimalAgent** — how little it takes: `DefaultAgent.builder().model(...).build()`.
- **HelloAgent** — the guardrail blocks an unsafe input *before* the model; the final line prints
  model-call count and token usage from the `TokenAccountingObserver`.
- **SafeAgent** — `llama-guard3:1b` classifies input/output locally; harmful requests are blocked
  with a category (e.g. `S1`); the guard fails **closed**.
- **SkilledAgentExample** — the `equipped: [...]` line differs per task: only relevant skills'
  instructions and tools enter context.
- **DeepResearchAgent** — prints the generated `plan.md` (subtasks marked `DONE`) and the synthesized
  answer; sub-agents run concurrently on virtual threads.
- **LearningAgentExample** — Run 1 answers wrong, self-corrects, and **records the lesson**; Run 2
  (no retries) gets it right on the first try by **recalling that lesson** across runs.
