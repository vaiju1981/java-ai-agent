# java-ai-agent

> A **vendor-neutral orchestration + trust layer** for building AI agents on the JVM.
>
> You bring a model (local or cloud) and some tools; `java-ai-agent` gives you a trustworthy,
> long-running **agent** built from them — with planning, memory, skills, guardrails, audit, and
> observability **built in**. It does **not** replace LangChain4j, Spring AI, or Google ADK —
> it **uses them as dependencies** and adds the layer above them that none of them own.

**Status: Phases 0–5 complete.** In place and tested: the core seams and a runnable agent loop;
**tool-calling** through the substrate (verified live); a **real, local safety layer** (Llama Guard
+ PII scrub); an **observability layer** (token/cost accounting, deterministic record/replay,
OpenTelemetry tracing); **deep agents** (planning, sub-agents fanned out concurrently on **virtual
threads**, shared workspace); and **skills + memory + learning** — a skill system with progressive
disclosure, episodic memory, and a reflective agent that **learns from its mistakes** (records
lessons and applies them on retry and across future runs). Following the discipline borrowed from
Mitra: **real where cheap, stubbed where expensive, and the app never fakes success silently** —
every stub returns an obvious placeholder, the safety guard fails *closed*, and learned lessons are
explicit recorded episodes, never silent drift.

---

## Why it exists

The JVM agent ecosystem is fragmenting — LangChain4j, Spring AI, Google ADK, Embabel, Koog — and
none of them interoperate or ship a serious **trust** story (guardrails, sandboxing, audit,
human-in-the-loop, evals). This project turns that fragmentation into its reason to exist:

> Use any of them as the substrate; get **one** trustworthy runtime, observability story, and
> guardrail layer on top.

It is also designed to be **dogfooded**: the author's own products (Mitra, the kid-safety gateway,
education tools) are intended to run on it — which forces it to be genuinely end-to-end, not a demo.

## The layering (see [DESIGN.md](DESIGN.md))

| Layer | Owner | What |
|---|---|---|
| **L0 — Substrate** | LangChain4j / Spring AI / ADK *(deps, as-is)* | models, tools, RAG, MCP, embeddings |
| **L1 — Runtime** | `java-ai-agent` | control loop, planning, sub-agents (Loom), deep-agent workspace |
| **L2 — Cognition** | `java-ai-agent` | long-term/episodic memory, skills, self-improvement |
| **L3 — Trust & Ops** | `java-ai-agent` | guardrails, permissions/sandboxing, audit, replay, HITL, cost, evals |

## Modules

- **`agent-core`** — the SPIs and the runtime. **Zero framework dependencies** (only SLF4J).
- **`agent-langchain4j`** — the first reference L0 adapter: a `ModelPort` backed by LangChain4j
  (incl. local models via Ollama). ADK + Spring AI adapters follow the same seam.
- **`agent-observability-otel`** — optional OpenTelemetry tracing adapter (`OtelAgentObserver`);
  keeps the OTel SDK out of `agent-core`.
- **`examples`** — runnable agents (`HelloAgent`, `SafeAgent`) showing the loop, tools, guardrails,
  and observability.

## Build & run

Requires a JDK (built/tested on 26; **compiles to a Java 21 baseline** so any 21+ project can
depend on it).

```bash
./gradlew build            # compile + test everything
./gradlew :examples:run    # run the hello-world agent
```

By default the example uses a **stub model** (no network, obvious placeholder output). To run it
against a real local model, start [Ollama](https://ollama.com) and set:

```bash
export AGENT_MODEL=llama3.2        # any pulled Ollama model
export OLLAMA_BASE_URL=http://localhost:11434   # optional, this is the default
./gradlew :examples:run
```

**Safety demo** (`SafeAgent`) — PII scrubbing + a local Llama Guard classifier on input and output:

```bash
ollama pull llama-guard3:1b
AGENT_MODEL=gemma4:31b-cloud ./gradlew :examples:run \
  -PmainClass=dev.vaijanath.aiagent.examples.SafeAgent
```

**Deep agent demo** (`DeepResearchAgent`) — plans a task, runs a sub-agent per subtask concurrently
on virtual threads, then synthesizes:

```bash
AGENT_MODEL=gemma4:31b-cloud ./gradlew :examples:run \
  -PmainClass=dev.vaijanath.aiagent.examples.DeepResearchAgent
```

## Roadmap

See [DESIGN.md](DESIGN.md) for the full plan. Near-term: tool-calling through the `ModelPort`,
the `kidguard` safety pipeline as the reference `Guardrail`, OpenTelemetry tracing, and the
`Agent`-as-component adapter for Google ADK.

## License

Apache-2.0 — see [LICENSE](LICENSE).
