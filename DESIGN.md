# java-ai-agent — Design

> The durable architecture and the reasoning behind it. (Strategic/market rationale lives in the
> author's private notes; this document is self-contained for contributors.)

## North star

A **vendor-neutral orchestration + trust layer** for AI agents on the JVM. It does not compete
with LangChain4j / Spring AI / Google ADK — it **depends on them** and provides the runtime,
cognition, and trust layers above them. Success = the author's own products (Mitra, kid-safety
gateway, education tools) run on it, proving it is end-to-end and trustworthy, not a prototype.

## Principles (inherited from Mitra)

1. **Never fake success silently.** Every stub returns an obvious placeholder; failures are
   visible and recoverable.
2. **Real where cheap, stubbed where expensive.** The runtime + trust seams are real now; heavy
   integrations land incrementally behind stable interfaces.
3. **Trust is a default, not a flag.** Guardrails, audit, and oversight are first-class, not opt-in.
4. **Compose, don't wrap-and-replace.** Consume substrate primitives; add only what is genuinely
   above them.

## The layers

- **L0 — Substrate (dependencies, as-is):** LangChain4j / Spring AI / Google ADK provide model
  clients (cloud + local/Ollama), embeddings, vector stores, RAG, tools, MCP, streaming.
- **L1 — Runtime (owned):** the control loop (ReAct / plan-execute), planning, sub-agent
  orchestration via **Loom virtual threads**, the deep-agent workspace (virtual FS / scratchpad).
- **L2 — Cognition (owned):** long-term / episodic / self-editing memory, skills (manifest +
  registry + progressive disclosure), self-improvement (reflection, episodic learning, skill
  acquisition) — **opt-in, versioned, observable, reversible**, never silent drift.
- **L3 — Trust & Ops (owned, the differentiator):** guardrails, permission/capability model +
  tool sandboxing, audit log + deterministic replay, human-in-the-loop checkpoints, cost/rate
  limits, eval harness, observability rollup.

## The three deps sit at different altitudes

| Dependency | What it is | Consumed at |
|---|---|---|
| LangChain4j | LLM toolkit | **L0** model/tool provider (first reference adapter) |
| Spring AI | LLM toolkit for Spring + Advisor chain | **L0** provider + guardrail seam |
| Google ADK | a full agent framework | **agent-as-component** (wrapped as a black box) |

## Unify at standard seams, not at their internals

- **Tools → MCP** (the canonical tool interface across all three).
- **Tracing → OpenTelemetry GenAI conventions.**
- **Models → a tiny `ModelPort`** (chat + tools + stream, embed).
- **Whole agents → one `Agent` interface** (input + context → output + events). This is how ADK
  (and later Embabel / Koog) get wrapped without leaking their types into the core.

## Core seams (Phase 0, implemented)

- `ModelPort` — L0 seam: `chat(ModelRequest) -> ModelResponse`. Implementations: `StubModelPort`
  (core, honest placeholder), `LangChain4jModelPort` (agent-langchain4j).
- `Agent` — the agent-as-component seam: `run(AgentRequest) -> AgentResponse`. Default impl:
  `DefaultAgent` (the L1 runtime walking skeleton: input-guardrails → model/tool loop →
  output-guardrails).
- `Tool` / `ToolSpec` — MCP-aligned (name, description, JSON-schema params). Wired into the loop
  in the next phase.
- `Guardrail` — L3 seam: `check(stage, content) -> GuardrailDecision` (allow / transform / block),
  run on input and output. Reference impl: `KeywordBlocklistGuardrail`; `kidguard` to follow.
- `Memory` — short-term history now (`InMemoryMemory`); long-term/episodic to follow.

## Roadmap

- **Phase 0:** core seams + runnable guardrail-wrapped loop + LangChain4j model adapter +
  stub model + tests. ✅ done.
- **Phase 1:** tool-calling through `ModelPort` (MCP-aligned); real tool execution in the loop.
  ✅ done — verified live (gemma invoked a tool through the LangChain4j↔Ollama adapter).
- **Phase 2:** the safety layer as reference `Guardrail`s — `LlamaGuardGuardrail` (local
  `llama-guard3:1b` via a `ModelPort`, fails closed) + `PiiScrubGuardrail`. ✅ done — verified live
  (S1 content blocked at input). Next: crisis detection + blocklist to complete the `kidguard` suite.
- **Phase 3:** observability & ops — an `AgentObserver` SPI (zero-dep) with built-in token
  accounting, logging, and record/replay (`ReplayModelPort`), plus an OpenTelemetry tracing adapter
  in `agent-observability-otel`. ✅ done — span emission verified via an in-memory exporter.
- **Phase 4:** deep agents — `Planner`/`LlmPlanner`, a `DeepAgent` that plans → fans sub-agents out
  concurrently on virtual threads (Loom; `StructuredTaskScope` once it's non-preview) → synthesizes,
  and a `Workspace` scratchpad. ✅ done — concurrency covered by a test; verified live.
- **Phase 5:** skills + memory + learning. `Skill`/`SkillRegistry`/`SkillSelector` (keyword + LLM)
  with progressive disclosure via `SkillfulAgent`; episodic memory (`EpisodicStore`); and a
  `ReflectiveAgent` that recalls lessons, self-critiques (`Reflector`/`LlmReflector`), and retries —
  learning within a run and across runs. ✅ done — cross-run learning covered by a test.
- **Phase 6:** more substrate + finish `kidguard`. ✅ done — `SpringAiModelPort` (second L0
  substrate; text + usage), an `AdkAgent` that wraps a Google ADK agent via the agent-as-component
  seam (compiles against ADK 1.4.0; reduction logic tested with real ADK objects), and the
  `CrisisGuardrail` + `Guardrails.kidguard(...)` pipeline. Follow-ups: tool-calling parity for the
  Spring AI adapter; live ADK end-to-end needs a configured ADK model.

## Decisions

- **Build:** Gradle (Kotlin DSL), version catalog. **Java baseline: 21** (built on JDK 26 via
  `--release 21`) so any 21+ project can depend on it; **no preview APIs in the public API.**
- **Concurrency:** virtual threads (GA in 21). Structured concurrency only where it does not force
  `--enable-preview` on consumers.
- **`agent-core` has zero framework dependencies** (SLF4J only) — substrate stays optional.
