# java-ai-agent

> A **vendor-neutral orchestration + trust layer** for building AI agents on the JVM.
>
> You bring a model (local or cloud) and some tools; `java-ai-agent` gives you a trustworthy,
> long-running **agent** built from them — with planning, memory, skills, guardrails, audit, and
> observability **built in**. It does **not** replace LangChain4j, Spring AI, or Google ADK —
> it **uses them as dependencies** and adds the layer above them that none of them own.

**Status:** a working, tested framework — `./gradlew build` is green and most capabilities are
verified live against a local model.

- **Runtime** — a guardrail-wrapped agent loop; **deep agents** (plan → parallel sub-agents on
  virtual threads → synthesize); **streaming**.
- **Substrate** — LangChain4j and Spring AI as `ModelPort`s (both with **tool-calling**); Google ADK
  wrapped as an `Agent`; MCP servers' tools as `Tool`s.
- **Trust & ops** — governance at the universal `Agent` seam (`Trust.govern`) so guardrails and the
  deadline apply to any agent (incl. composed/black-box); kidguard guardrails (crisis · PII · local
  **Llama Guard**, fails *closed*); **capability-based tool authorization** (`denyEffectful`:
  read-only runs, effectful denied) + human-in-the-loop; observability (token accounting,
  deterministic replay, **OpenTelemetry**); an **eval harness** + token-**budget** enforcement.
- **Cognition** — episodic memory that is in-memory, **persistent (cross-session)**, or **semantic**;
  skills with progressive disclosure + **acquisition**; a reflective agent that **learns from its
  mistakes** and applies lessons in later sessions.
- **Reliability** — per-call model timeouts + retries and a **per-tool-call timeout**, bounded
  context, graceful model-failure handling, **side-effect-free replay**; **structured output**
  (schema-bound JSON, no fragile parsers).

Discipline borrowed from Mitra: real where cheap, stubbed where expensive, never fake success; trust
is a default; **`agent-core` has zero framework dependencies**.

---

## Why it exists

The JVM agent ecosystem is fragmenting — LangChain4j, Spring AI, Google ADK, Embabel, Koog — and
none of them interoperate or ship a serious **trust** story (guardrails, sandboxing, audit,
human-in-the-loop, evals). This project turns that fragmentation into its reason to exist:

> Use any of them as the substrate; get **one** trustworthy runtime, observability story, and
> guardrail layer on top.

It is also designed to be **dogfooded**: the author's own products (Mitra, the kid-safety gateway,
education tools) are intended to run on it — which forces it to be genuinely end-to-end, not a demo.

## Reference application: FinCopilot

[`apps/fincopilot`](apps/fincopilot/README.md) is a complete, deployable product built on this framework
— a **grounded finance copilot** for individuals and small businesses: per-user accounts & transactions,
an Analyst that answers from your real data, a knowledge-grounded Advisor (with citations + disclaimers),
a React dashboard, usage quotas, and data export/delete — on an Ollama substrate, governed end to end. It
consumes the library exactly as a third-party adopter would.

API stability and upgrades are documented in [docs/API-STABILITY.md](docs/API-STABILITY.md) and the
per-release migration notes (e.g. [docs/MIGRATION-0.2.md](docs/MIGRATION-0.2.md)).

## Architecture (see [DESIGN.md](DESIGN.md))

> Looking for code? The **[Cookbook](docs/COOKBOOK.md)** has copy-pasteable recipes — tools, RAG,
> structured output, multi-agent routing, streaming, and the Spring Boot starter.

**Four layers** — the substrate you depend on, plus the runtime, cognition, and trust layers this
project owns on top:

| Layer | Owner | What |
|---|---|---|
| **L0 — Substrate** | LangChain4j / Spring AI / ADK *(deps, as-is)* | models, tools, RAG, MCP, embeddings |
| **L1 — Runtime** | `java-ai-agent` | control loop, planning, sub-agents (Loom), deep-agent workspace |
| **L2 — Cognition** | `java-ai-agent` | long-term/episodic memory, skills, self-improvement |
| **L3 — Trust & Ops** | `java-ai-agent` | guardrails, permissions/sandboxing, audit, replay, HITL, cost, evals |

```mermaid
flowchart TB
    subgraph L3["L3 · Trust &amp; Ops (owned)"]
        direction LR
        g["Guardrails<br/>(crisis · PII · Llama Guard)"]
        perm["Tool permissions<br/>+ human-in-the-loop"]
        obs["Observability<br/>(tokens · replay · OTel)"]
        ev["Eval + budget"]
    end
    subgraph L2["L2 · Cognition (owned)"]
        direction LR
        mem["Memory<br/>(episodic · persistent)"]
        sk["Skills<br/>(+ acquisition)"]
        learn["Learning<br/>(reflection)"]
    end
    subgraph L1["L1 · Runtime (owned)"]
        direction LR
        da["DefaultAgent loop"]
        deep["DeepAgent<br/>(plan + sub-agents)"]
        str["Streaming"]
    end
    subgraph L0["L0 · Substrate (dependencies, as-is)"]
        direction LR
        lc["LangChain4j"]
        sp["Spring AI"]
        adk["Google ADK"]
    end
    L3 --> L1
    L2 --> L1
    L1 --> L0
```

### How a request flows

One turn through `DefaultAgent`: input guardrails, then a reason-and-act loop over a (decorated)
`ModelPort` and authorized tools, then output guardrails. The rows of a database, an MCP server's
tools, or a cloud model all sit *outside* the core, reached only through a seam — so `agent-core`
stays dependency-free, and memory, skills, and observers attach without touching the loop.

```mermaid
flowchart TB
    req([AgentRequest]) --> IG["Input guardrails<br/>crisis · PII · Llama Guard · fail-closed"]
    IG --> LOOP

    subgraph CORE["agent-core · DefaultAgent loop — reason and act, up to maxSteps"]
      direction LR
      LOOP["agent loop"]
      LOOP -->|chat| MODEL["ModelPort<br/>Resilient ▸ Budget ▸ Observing"]
      MODEL -->|text or tool calls| LOOP
      LOOP -->|tool call| TOOLS["Tools<br/>ToolSelector ▸ ToolApprover ▸ invoke"]
      TOOLS -->|result| LOOP
    end

    MODEL --> ADP["L0 adapter · LangChain4j / Spring AI"] --> LLM[("LLM · Ollama / cloud")]
    TOOLS --> EXT["local tools · MCP servers · wrapped ADK agent"]

    LOOP -. recall · store .-> COG["Memory and skills<br/>episodic · persistent · semantic · reflection"]
    LOOP -. events .-> OBS["Observers<br/>tokens · replay · OpenTelemetry"]

    LOOP -->|final answer| OG["Output guardrails"] --> resp([AgentResponse])
```

### Deep agents — plan, fan out, synthesize

A `DeepAgent` plans a task into steps, runs a sub-agent per step **concurrently on Loom virtual
threads** writing to a shared workspace, then synthesizes the result. Each sub-agent is itself an
`Agent`, so any agent — even another `DeepAgent` — composes as a sub-agent with no extra wiring.

```mermaid
flowchart LR
    task([task]) --> P["LlmPlanner<br/>structured output"]
    P --> plan["Plan · steps 1..n"]
    plan --> W1["sub-agent 1"]
    plan --> W2["sub-agent 2"]
    plan --> Wn["sub-agent n"]
    W1 --> WS[("shared Workspace")]
    W2 --> WS
    Wn --> WS
    WS --> SYN["synthesize"] --> out([result])
```

## How it compares

It is **not** a competitor to the substrate frameworks — it's the trust + orchestration layer that
runs *on top* of them.

| | java-ai-agent | LangChain4j / Spring AI | Embabel / ADK |
|---|---|---|---|
| Role | trust + orchestration layer **on top** | LLM toolkits | agent frameworks |
| Consumes the others | **yes, as dependencies** | — | wrapped via the `Agent` seam |
| Local-first by default | **yes** | partial | cloud-leaning |
| Built-in safety guardrails | **yes** (Llama Guard, PII, crisis) | no | no |
| Tool authorization + HITL | **yes** | no | no |
| Cross-session learning (persistent) | **yes** | no | no |
| Eval harness + budget enforcement | **yes** | partial | no |
| Zero-dependency core | **yes** | no | no |

## Demos

For the deployment-shaped golden path—not a demo—see the
[`production-reference`](production-reference/README.md) service. It wires the production runtime
to PostgreSQL/Flyway, HikariCP, durable audit, bounded requests, health probes, and Ollama.

The [`demos`](demos/README.md) module is a small set of **deep, production-shaped applications** —
each runs through the governed runtime and is verified live against a local model:

```bash
export AGENT_MODEL=gemma4:31b-cloud   # any pulled, tool-capable Ollama model
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.<group>.<Demo>
```

| Demo | Package | What it shows |
|---|---|---|
| **GovernedSupportDeskDemo** | `flagship` | a deterministic (no-model) tour of the whole trust layer: durable restart, deny-by-default → allow-list, guardrail block + PII scrub, token-budget stop, tenant isolation, audit |
| **DataAnalystDemo** | `data` | a real EDA agent over a multi-table warehouse — profiling, histograms, IQR outliers, correlation, segmentation, time-series, **driver analysis** — writing a persisted report |
| **FraudInvestigationDemo** | `fraud` | analysis **plus governed, effectful action**: investigates planted fraud, then `flag_for_review` (allowed) and `freeze_account` (**denied without authorization**), idempotent and audited |
| **PersonalFinanceDemo** | `finance` | a financial advisor over a year of income + expenses: cash-flow, savings-rate trend, budget variance + forecast, subscription/anomaly detection, goal projection → a persisted plan |

See [demos/README.md](demos/README.md) for what each one does and sample output.

## Modules

- **`agent-core`** — the SPIs and the runtime. **Zero framework dependencies** (only SLF4J).
- **`agent-langchain4j`** — the first reference L0 adapter: a `ModelPort` backed by LangChain4j
  (incl. local models via Ollama), with tool-calling.
- **`agent-spring-ai`** — a second L0 adapter: a `ModelPort` backed by any Spring AI `ChatModel`,
  proving the runtime is vendor-neutral.
- **`agent-adk`** — wraps a Google ADK agent as an `Agent` (agent-as-component): ADK is a full
  framework, so it's consumed one level up — the same seam later admits Embabel / Koog.
- **`agent-mcp`** — exposes a Model Context Protocol server's tools as `Tool`s (`McpTools.from(client)`).
- **`agent-observability-otel`** — optional OpenTelemetry tracing adapter (`OtelAgentObserver`);
  keeps the OTel SDK out of `agent-core`.
- **`agent-store-jdbc`** — a durable, queryable `ConversationStore` for SQLite and PostgreSQL:
  complete turns persist transactionally to relational tables that survive restarts and
  supports SQL analytics; see [agent-store-jdbc/README.md](agent-store-jdbc/README.md).
- **`agent-tools-jsonschema`** — a `ToolArgumentValidator` that validates tool arguments against their
  JSON Schema before the tool runs, so malformed calls are rejected without side effects.
- **`examples`** — a graduated set of runnable agents, from `MinimalAgent` to the `StudyBuddy`
  capstone (which composes everything); see [examples/README.md](examples/README.md).
- **`demos`** — deep, production-shaped applications (a flagship trust-layer tour, a data analyst, a
  fraud investigator, a financial advisor), each governed and verified live; see [demos/README.md](demos/README.md).
- **`production-reference`** — a deployable Spring Boot/PostgreSQL golden path with migrations,
  pooling, durable audit, safe runtime presets, health probes, and bounded multi-tenant requests.

## Extending it

Everything is an interface; implement the seam you need.

- **A model provider** — implement `ModelPort.chat(ModelRequest) -> ModelResponse` (see
  `LangChain4jModelPort`, `SpringAiModelPort`). Wrap any port in `ResilientModelPort` for
  timeouts/retries.
- **A tool** — implement `Tool` for read-only utilities, or `ContextualTool` for effectful production
  operations that need tenant, principal, deadline, trace, and an idempotency key.
- **A guardrail** — implement `Guardrail.check(stage, content) -> GuardrailDecision` (allow /
  transform / block). Compose them; `Guardrails.kidguard(guardModel)` returns the ordered pipeline.
- **A skill** — `Skill.of(name, description, instructions, tools)`, register it, and a
  `SkillfulAgent` equips it on demand.
- **Memory** — pick an `EpisodicStore`: `InMemoryEpisodicStore`, `FileEpisodicStore` (persistent,
  cross-session), or `LangChain4jEpisodicStore` (semantic, embedding-based recall).
- **Structured output** — declare a result `record` and use `StructuredOutput.generate(request, T.class)`
  (e.g. `OllamaModelPorts.ollamaStructured(...)`); the model returns JSON bound straight to your type,
  so you never write a parser. `LlmPlanner(StructuredOutput)` uses this.
- **An observer** — implement `AgentObserver` (trace/meter/record); failures are isolated.
- **A tool policy** — implement `ToolApprover.authorize(ToolCallContext)` (use `ToolApprovers.allowList(...)`,
  or `ConsoleToolApprover` for human-in-the-loop); wire it via `DefaultAgent.builder().toolApprover(...)`.
- **Evaluate & cap cost** — `Evaluator.run(agent, cases)` reports a pass rate; wrap a `ModelPort` in
  `BudgetModelPort(port, new TokenBudget(n))` to enforce a token ceiling.
- **Stream tokens** — `ModelPorts.stream(port, request, System.out::print)`; a `StreamingModelPort`
  (e.g. `OllamaModelPorts.ollamaStreaming(...)`) forwards tokens live, others fall back to a single chunk.
- **An agent** — implement `Agent.run(AgentRequest) -> AgentResponse`. Because everything is an
  `Agent`, your implementation can be a sub-agent of a `DeepAgent` or the worker of a
  `ReflectiveAgent` with no extra wiring.

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

## Status & roadmap

Current capabilities are listed at the top; project history is in git. Remaining work needs external
systems or accounts, not design changes: live **ADK** / **MCP** end-to-end (a configured ADK model /
a running MCP server), richer MCP parameter schemas, and the **Maven Central** release (publication
config is done and verified locally — see [PUBLISHING.md](PUBLISHING.md)). Architecture and rationale
are in [DESIGN.md](DESIGN.md).

## License

Apache-2.0 — see [LICENSE](LICENSE).
