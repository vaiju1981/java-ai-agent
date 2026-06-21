# java-ai-agent — Design

> The durable architecture and the reasoning behind it. For the **current capability list and
> status**, see the [README](README.md). This document is the timeless *why / how it's built*, so the
> two don't drift: capabilities live in the README, history lives in git, architecture lives here.

## North star

A **vendor-neutral orchestration + trust layer** for AI agents on the JVM. It does not compete with
LangChain4j / Spring AI / Google ADK — it **depends on them** and adds the runtime, cognition, and
trust layers above. Success = the author's own products (Mitra, the kid-safety gateway, education
tools) run on it.

## Principles (inherited from Mitra)

1. **Never fake success silently** — stubs return obvious placeholders; failures are visible and
   recoverable; the safety guard fails *closed*.
2. **Real where cheap, stubbed where expensive** — seams are real; heavy integrations land behind
   stable interfaces.
3. **Trust is a default, not a flag** — guardrails, tool authorization, audit, and oversight are
   first-class.
4. **Compose, don't wrap-and-replace** — consume substrate primitives; add only what is genuinely
   above them.
5. **Prefer structured output over parsing** — where a specific shape is needed, request schema-bound
   JSON and bind it; don't hand-roll a parser.

## The layers

- **L0 — Substrate (dependencies, as-is):** LangChain4j / Spring AI (model clients, tools, RAG,
  embeddings, streaming); Google ADK; MCP servers (via the MCP adapter).
- **L1 — Runtime (owned):** the control loop, planning, sub-agent orchestration on **Loom virtual
  threads**, the deep-agent workspace.
- **L2 — Cognition (owned):** conversation + episodic memory (in-memory / persistent / semantic),
  skills (registry + progressive disclosure + acquisition), learning (reflection + cross-session
  lessons).
- **L3 — Trust & Ops (owned, the differentiator):** guardrails (kidguard), tool authorization +
  human-in-the-loop, observability (tokens, replay, OpenTelemetry), eval + budget enforcement.

## The dependencies sit at different altitudes

| Dependency | What it is | Consumed at |
|---|---|---|
| LangChain4j | LLM toolkit | **L0** model + tool provider |
| Spring AI | LLM toolkit for Spring | **L0** model + tool provider |
| Google ADK | a full agent framework | **agent-as-component** (wrapped as a black box) |
| MCP servers | external tool servers | **L0** tool provider (via `agent-mcp`) |

## Unify at standard seams, not their internals

- **Tools → MCP-aligned** `ToolSpec`; the MCP adapter consumes real MCP servers.
- **Tracing → OpenTelemetry.**
- **Models → a small `ModelPort`** (+ `StreamingModelPort`, `StructuredOutput`).
- **Whole agents → one `Agent` interface** — how ADK is wrapped, and later Embabel / Koog, without
  leaking their types into the core.

## The seams

- **`ModelPort`** — `chat(ModelRequest) → ModelResponse`. Decorators: `ResilientModelPort`
  (timeout + retry), `BudgetModelPort` (token ceiling). Variants: `StreamingModelPort`,
  `StructuredOutput` (typed JSON). Impls: `StubModelPort`, `LangChain4jModelPort`, `SpringAiModelPort`.
- **`Agent`** — `run(AgentRequest) → AgentResponse`, the universal seam. `AgentRequest` carries a
  **`RequestContext`** (session, principal, tenant, trace, deadline), so identity and governance
  travel with every turn and propagate to sub-agents. Impls: `DefaultAgent` (guardrail-wrapped
  model/tool loop, **stateless across calls**), `DeepAgent` (plan → sub-agents → synthesize),
  `ReflectiveAgent` (learn from mistakes), `SkilledAgent`, `SkillAcquiringAgent`, `AdkAgent`. Any
  `Agent` can be a sub-agent or worker of another — composition needs no extra wiring.
- **`Tool` / `ToolSpec`** (MCP-aligned; each tool declares a `ToolEffect` — `READ_ONLY` or
  `EFFECTFUL`, defaulting to effectful) + **`ToolApprover`** — authorization runs before execution and
  sees a `ToolCallContext` (the spec/effect, arguments, principal, tenant), so policies decide by
  capability or identity. `ToolApprovers.denyEffectful()` (read-only runs, effectful denied),
  `allowList`, or `ConsoleToolApprover` for HITL. The selector is enforced too: a tool not presented
  this turn cannot run, even if the model names it.
- **`Guardrail`** — `check(stage, content) → allow / transform / block`; `Guardrails.kidguard(...)`
  is the ordered crisis → PII → Llama Guard pipeline.
- **`PolicyEnforcingAgent` / `Trust.govern(agent, …)`** — wraps any `Agent` so input/output guardrails
  and the request deadline are enforced at the universal seam, governing composed and black-box agents
  (`DeepAgent`, `AdkAgent`) uniformly. Trust is a wrapper over the seam, not per-implementation
  configuration.
- **`Memory`** — short-term (`InMemoryMemory` / `WindowedMemory`), scoped per session by a
  **`ConversationStore`** (`InMemoryConversationStore`) so one agent serves many sessions without
  interleaving; **`EpisodicStore`** for long-term, cross-session learning (in-memory, file-persistent,
  or semantic/embedding-based).
- **`AgentObserver`** — lifecycle events; `LoggingObserver`, `TokenAccountingObserver`,
  `RecordingObserver` (+ `ReplayModelPort` for deterministic replay), `OtelAgentObserver`.
- **`Planner` / `Reflector` / `SkillSelector` / `SkillSynthesizer`** — LLM-driven helpers, each
  preferring `StructuredOutput` with a free-text fallback.

## A turn, step by step

```mermaid
sequenceDiagram
    participant U as Caller
    participant A as DefaultAgent
    participant G as Guardrails
    participant M as ModelPort
    participant T as Tool (+ ToolApprover)
    U->>A: run(input)
    A->>G: input guardrails
    G-->>A: allow / transform / block
    loop until a final answer (≤ maxSteps)
        A->>M: chat(history, tools)  (raw token stream is opt-in; off by default)
        M-->>A: text, or tool calls
        opt tool calls
            A->>T: authorize, then invoke
            T-->>A: result (fed back into history)
        end
    end
    A->>G: output guardrails
    G-->>A: allow / transform / block
    A-->>U: AgentResponse
```

## Decisions

- **Build:** Gradle (Kotlin DSL) + version catalog. **Java 21 baseline** (built on a newer JDK via
  `--release 21`); **no preview APIs in the public API.**
- **Concurrency:** virtual threads (GA in 21). `StructuredTaskScope` only once it is non-preview.
- **`agent-core` has zero framework dependencies** (SLF4J only) — every substrate is optional.
- **Modules:** `agent-core` + adapters (`agent-langchain4j`, `agent-spring-ai`, `agent-adk`,
  `agent-mcp`, `agent-observability-otel`) + `examples`.

## Known gaps (need external systems, not design changes)

- Live **ADK** and **MCP** end-to-end need a configured ADK model / a running MCP server; their
  adapters are built against the real APIs and unit-tested, but only compile/logic-verified here.
- **MCP** parameter schemas are advertised permissively (the server validates args); richer schema
  propagation is a follow-up.
- **Maven Central** release: the publication config is done and verified with `publishToMavenLocal`;
  the upload needs the author's account + signing key — see [PUBLISHING.md](PUBLISHING.md).
