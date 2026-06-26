# Changelog

Notable changes to java-ai-agent. Format loosely follows [Keep a Changelog](https://keepachangelog.com);
versioning is [SemVer](https://semver.org). (Commit history has the fine-grained detail.)

## [Unreleased]

The **self-learning** line: production-grade learning from past mistakes.

### Added

- **`JdbcEpisodicStore`** (`agent-store-jdbc`) — a durable, **semantic** `EpisodicStore` for SQLite/
  PostgreSQL. Episodes are stored with their task+lesson embedded (via the `rag.Embedder` seam) and
  recalled by cosine similarity, so a `ReflectiveAgent`'s learning persists across restarts and is shared
  across instances — production "RAG over past mistakes". Previously, semantic recall existed only in the
  in-memory `LangChain4jEpisodicStore`.

### Changed

- **(Breaking)** `agent-store-jdbc` now ships its Flyway migrations under its **own** location,
  `classpath:db/agent-store-jdbc/` — **not** the default `classpath:db/migration` — so the library no
  longer squats a consumer's migration version space (the cause of the cross-repo `V2` collision). Add it
  to your Flyway locations (`spring.flyway.locations=classpath:db/agent-store-jdbc,classpath:db/migration`).
  `fromJdbcUrl(...)` still self-creates the schema. See [docs/MIGRATION-0.5.md](docs/MIGRATION-0.5.md).

## [0.4.0] — 2026-06-25

The **hardening** release: production-rigor and observability across the runtime and the reference,
driven by a pair of code reviews. Almost entirely additive — the one breaking change is a new component
on the A2A request record (see [docs/MIGRATION-0.4.md](docs/MIGRATION-0.4.md)).

### Added

- **Tool-execution safety** (`agent-core`) — `DefaultAgent.maxToolCallsPerStep` bounds a step's tool
  fan-out (`0` = unlimited; `ProductionAgentRuntime` defaults to 16). Beyond the ceiling each call still
  comes back as an error result, so the transcript stays valid. Policy-denied tools now log a **WARN**
  with the fix instead of a silent INFO.
- **Observer timing** (`agent-core`, `agent-spring-boot-starter`) — additive `AgentObserver` default
  methods carry latency: `onModelResponse` / `onToolResult` / `onTurnEnd(…, Duration)`.
  `MicrometerAgentObserver` records `agent.{model,tool,turn}.latency` timers.
- **Per-model token accounting & cost** (`agent-core`) — `AgentObserver.onUsage(model, usage)`;
  `TokenAccountingObserver.tokensByModel()` for a per-model breakdown; and a **bring-your-own**
  `TokenPrice` + `Pricing` to turn tokens into cost. No price table is bundled (list prices go stale;
  unpriced models are free, the right default for local models).
- **Error taxonomy** (`agent-core`) — `StopReason`, a closed enum (`Category` + `retryable()`), plus
  `AgentResponse.reason()` / `retryable()`. A distinct `BUDGET_EXCEEDED` reports a hit token budget
  separately from a model outage (`model_error`).
- **Turn-level idempotency** (`agent-core`, `agent-store-jdbc`) — an `IdempotencyStore` seam +
  `IdempotentAgent` that replays a key's non-retryable result instead of re-running the turn, with an
  `InMemoryIdempotencyStore` and a durable `JdbcIdempotencyStore`.
- **Tracing context across virtual threads** — MDC (traceId/tenant) now propagates across the runtime's
  virtual-thread boundaries, so parallel/async work keeps its log context. `OtelAgentObserver` makes the
  turn span current (proper nesting) and sets ERROR status + records the exception on a failed turn.
- **A2A deadline propagation** (`agent-a2a`) — `A2aRequest` carries the caller's remaining deadline so a
  remote turn is bounded server-side rather than running on after the caller has timed out. (See
  **Changed**.)

### Changed

- **`production-reference` hardening:**
  - **Fail-fast** under the `prod` profile on insecure configuration (no `agent.api-keys`, the default DB
    password, no guard model) — an unsafe prod deployment refuses to start, rather than only warning.
  - **Tenant is bound to the API key**, not trusted from the `X-Tenant-Id` header, closing a
    tenant-spoofing gap when authentication is enabled.
  - **Rate limiter** is bounded (LRU, no unbounded growth), keyed by credential/address, and requires
    `Content-Length` (returns 411 otherwise).
  - **Turn idempotency is now enforced** — the `Idempotency-Key` header (already accepted) deduplicates
    via `IdempotentAgent` + `JdbcIdempotencyStore` (new `V2__agent_idempotency.sql` migration).
- **Ops / DX** — Trivy gates PRs on fixable HIGH/CRITICAL findings; `AnthropicModelPort.fromEnv()` /
  `OpenAiModelPort.fromEnv()` give a friendly, actionable error on a missing key; the cookbook gains a
  "which builder?" table and a "token accounting & cost" section.
- **(Breaking)** `A2aRequest` gained a `deadlineEpochMillis` component, changing its **canonical
  constructor**. Prefer `A2aRequest.of(input)` or add the trailing argument; callers that use
  `RemoteAgent` / `A2aServer` are unaffected. Excluded from the japicmp gate for this release. See
  [docs/MIGRATION-0.4.md](docs/MIGRATION-0.4.md).
- The **FinCopilot** reference application moved to its own repository
  ([vaiju1981/fincopilot](https://github.com/vaiju1981/fincopilot)) and now consumes the published
  `io.github.vaiju1981:agent-*` artifacts. This repository is now library-only (plus the runnable
  `examples/` and `demos/`).

### Fixed

- **Tool idempotency key now always includes the call arguments.** `ToolCallContext.idempotencyKey()`
  dropped the arguments whenever a client idempotency key was present (0.3.0), so two distinct calls to
  the same tool within one idempotent request (e.g. `pay({"amount":5})` and `pay({"amount":6})`) hashed
  to the same key — an effectful tool deduplicating by it could skip a real operation. Arguments are now
  folded into the key on every path; a retry of the *same* call still deduplicates.

### Notes

- The `demos` module was removed and `examples` trimmed to a canonical set (minimal agent, tool
  orchestration, safety layer, cross-session memory, streaming) to keep the repo focused on the library;
  the removed showcases remain in git history. No published library module changed.
- `DefaultAgent.converse` / `executeCalls` were refactored below the cognitive-complexity gate — a pure,
  behavior-preserving change with no API or semantic impact.
- Every change above is additive against the 0.3.0 API except the `A2aRequest` constructor; the japicmp
  baseline remains 0.3.0 for this release.

## [0.3.0] — 2026-06-25

The multi-agent + multimodal release: the library now drives end-to-end, **multi-agent** applications
— agents that route, hand off, chat, and call one another in- and cross-process — and accepts
**images**.

### Added

- **Multi-agent orchestration** (`agent-core`), all composing on the one `Agent` seam:
  - `Agents.asTool(...)` — wrap any agent as a `Tool`, so a model invokes peers as tools.
  - `HandoffAgent` — the **Swarm** pattern: peers hand control to one another, no central manager.
  - `GroupChatAgent` — **AutoGen-style** multi-agent conversation with a speaker selector
    (round-robin or LLM-chosen).
  - `GraphAgent` — a **LangGraph-style** workflow graph: named nodes joined by conditional edges,
    with cycles and optional crash-resume via a `CheckpointStore`.
- **`agent-openai`** — a first-party `ModelPort` over the official OpenAI Java SDK (Chat Completions),
  alongside the existing `agent-anthropic`.
- **Parallel tool calls** — `DefaultAgent` runs a turn's tool calls concurrently on virtual threads
  (`parallelToolCalls`, on by default).
- **`@AiService`-style facade** — `AiServices.create(MyInterface.class, agent)` turns a plain Java
  interface (with `@UserMessage` / `@V` templates) into an agent-backed implementation.
- **Multimodal input** — a `Media` type (image/audio; inline base64 or URL) and
  `Message.user(text, media)`; the OpenAI and Anthropic adapters send images to vision models. See
  **Changed** below.
- **RAG ingestion** (`agent-core`) — `DocumentSplitter` (boundary-aware, overlapping chunks) and
  `Ingestor`, writing to any `ChunkStore` (e.g. `InMemoryVectorStore`, `JdbcVectorStore`).
- **`agent-a2a`** — Agent-to-Agent over HTTP: `A2aServer` exposes an `Agent`; `RemoteAgent` (itself an
  `Agent`) calls a remote one, so distributed agents compose like local ones. Dependency-light (JDK
  sockets + `java.net.http`).
- **`create-agent` starter** — a copy-out quickstart project under `examples/create-agent`.

### Changed

- **(Breaking)** `Message` gained a `media` component for multimodal input, changing its canonical
  constructor. Construction via the factory methods (`Message.user(...)`, etc.) is unaffected; only
  direct `new Message(...)` callers need the trailing `media` argument. See
  [docs/MIGRATION-0.3.md](docs/MIGRATION-0.3.md).

## [0.2.0] — 2026-06-22

The first real product on the framework, built end to end.

### Added

- **FinCopilot** (`apps/fincopilot`) — a complete, deployable grounded finance copilot: an Analyst
  (per-user data; an effectful savings-goal tool with human-in-the-loop approval) and a RAG-grounded
  Advisor, a React UI, Docker Compose, and an Ollama substrate — the worked example for building a
  real product on `java-ai-agent`.
- **`agent-spring-boot-starter` capabilities** (additive) — also auto-configures a per-request
  streaming-agent factory and a stream executor, and provides shared HTTP plumbing (`AgentTurns`,
  `SseAgentObserver`) so a Spring Boot agent endpoint needs little boilerplate.
- **API-stability policy** — an `@Internal` marker (`dev.vaijanath.aiagent.annotation.Internal`) and a
  written [API stability policy](docs/API-STABILITY.md), plus a per-module japicmp binary-compatibility
  gate. No existing type's visibility changed. See [docs/MIGRATION-0.2.md](docs/MIGRATION-0.2.md).

## [0.1.0] — 2026-06-22

Initial public release — the agent runtime, trust layer, adapters, cognition, and a durable
conversation store — published to Maven Central under `io.github.vaiju1981`. (Patch releases 0.1.1 and
0.1.2 followed the same day with CI/publishing fixes and no API changes.)

### Added

- **Runtime** — guardrail-wrapped agent loop (`DefaultAgent`); deep agents (`DeepAgent`: plan →
  parallel sub-agents on virtual threads → synthesize); token streaming through the loop.
- **Substrate adapters** — LangChain4j and Spring AI as `ModelPort`s (both with tool-calling);
  Google ADK wrapped as an `Agent`; MCP server tools via `agent-mcp`.
- **Trust & ops** — kidguard guardrails (crisis · PII · Llama Guard; fail-closed); tool
  authorization + human-in-the-loop (`ToolApprover`); observability (`AgentObserver`, token
  accounting, deterministic record/replay, OpenTelemetry); eval harness + token-budget enforcement.
- **Cognition** — episodic memory (in-memory, file-persistent/cross-session, semantic/embedding);
  skills (registry, progressive disclosure, acquisition); reflective learning within a run and
  across sessions.
- **Durable conversation store** — `agent-store-jdbc`: a `JdbcConversationStore`
  (SQLite/PostgreSQL/MySQL) that persists each message to a queryable `agent_messages` table, so
  conversations survive restarts and support SQL analytics; drops into
  `DefaultAgent.builder().conversationStore(...)`, with optional windowing and faithful tool-call
  persistence.
- **Reliability** — `ResilientModelPort` (timeout + retry), `WindowedMemory`, graceful
  model-failure handling, **structured output** (`StructuredOutput`, schema-bound JSON; used by the
  planner, reflector, skill selector, and skill synthesizer).
- **Build & quality** — Gradle multi-module (Java 21 baseline, JDK 26); Spotless + JaCoCo gates;
  Maven publication config; GitHub Actions CI.

### Hardening (trust-review remediation)

- **Trust governs the universal `Agent` seam** — new `PolicyEnforcingAgent` / `Trust.govern(agent,
  guardrails…)` enforces input/output guardrails and the request deadline around *any* agent, so
  composed and black-box agents (`DeepAgent`, `AdkAgent`) are governed too, not just `DefaultAgent`.
- **Observability redaction and audit hardening** — input guardrails run before `onTurnStart`, so
  observers and the audit trail see post-guardrail (e.g. PII-scrubbed) content, never the raw input,
  and `LoggingObserver` no longer logs raw tool arguments. A throwing `AuditSink` is isolated (logged
  and dropped) so it can't break a run; `DefaultAgent` now emits `turn.end` on every exit
  (completed/blocked/max_steps/model_error/deadline) plus a `tool.result` event. `FileAuditSink`
  fsyncs each event, sanitizes identity fields and Base64-encodes detail so no field can corrupt the
  line, and never throws into the caller.
- **Tool boundary hardened** — `ToolCallContext` now also carries traceId, sessionId, and deadline,
  so policies can decide by correlation or remaining time, not just identity; tool results fed back to
  the model are capped (`maxToolResultChars`, default 8192) so a tool can't flood or poison the
  context; tool exception detail is logged but no longer leaks into the model context. (Still open:
  hard cancellation of interruption-ignoring tools — a JVM limitation.)
- **Tool-argument validation** — a pluggable `ToolArgumentValidator` runs before a tool executes, so
  a malformed or incomplete call is rejected without side effects; `agent-tools-jsonschema` provides a
  JSON-schema validator (required fields + types, recursing into nested objects). The default is
  no-op, keeping `agent-core` dependency-free.
- **Untrusted-result framing + idempotency key** — tool results fed to the model are framed as
  untrusted data by default (resists prompt injection; opt out with `frameToolResults(false)`), and
  `ToolCallContext.idempotencyKey()` provides a stable key (tenant + session + tool + arguments) that
  an effectful `ToolApprover` or tool can use to make a repeated operation idempotent.
- **Telemetry content redaction** — `RedactingObserver` wraps any observer and forwards events with
  content fields blanked (`"[redacted]"`) while preserving metering metadata (token usage, roles,
  tool/guardrail names, ids, outcome flags); raw streamed tokens are dropped. This lets a
  metrics/tracing backend avoid carrying message content, tool arguments, or model output — which
  `onModelResponse`/`onToolResult` otherwise expose raw (pre-output-guardrail) by design.
- **Off-request-path audit delivery** — `AsyncAuditSink` delivers events to a delegate sink on a
  background thread, so a slow or hanging sink (e.g. an fsync) can't delay the request beyond its
  deadline — the audit writes around the bounded turn no longer count against it. `record` is
  non-blocking and drops under sustained backpressure; wrap a durable sink (e.g. `FileAuditSink`) to
  keep durability, and `close()` to flush.
- **Whole-turn deadline and exactly-once seam lifecycle** — `PolicyEnforcingAgent` bounds the entire
  turn — input guardrails, the delegate, and output guardrails — under the deadline, so even a hanging
  model-backed guardrail cannot exceed it. `turn.end` is recorded **exactly once** in a `finally`
  (covering completion, blocks, the deadline, and exceptions) and only by the caller, so a thrown
  guardrail/delegate still closes the lifecycle and a late interruption-ignoring worker cannot
  double-record. (Cancellation remains cooperative — a JVM thread cannot be force-stopped.)
- **Hard deadline and unbypassable output policy** — `PolicyEnforcingAgent` runs the delegate
  bounded by the remaining deadline (cancelling on expiry) and re-checks before delivering, so a
  blocking or late delegate cannot return a result past the deadline; output guardrails now run even
  when the delegate marks its result blocked, so a black-box agent cannot dodge the policy by
  self-labelling unsafe output as "blocked".
- **Deny-effectful is the runtime default** — `DefaultAgent` (and `SkillfulAgent`, which now exposes
  a `toolApprover`) deny effectful tools unless allow-listed; opt into `ToolApprovers.allowAll()`
  explicitly for dev/test. Read-only demo, example, and built-in tools are classified `READ_ONLY` so
  they keep running under the safe default; tools of unspecified effect (e.g. MCP server tools) are
  treated as effectful and denied until allow-listed.
- **Tool-call timeout** — `DefaultAgent.builder().toolTimeout(Duration)` bounds each tool call on a
  virtual thread, so a hung tool returns an error instead of stalling the turn.
- **Adversarial + concurrency tests** — cross-session leakage under 64 concurrent sessions, a
  prompt-injected tool result that cannot escalate to an effectful tool, hung tools bounded by the
  timeout, and `DeepAgent` propagating tenant/identity to sub-agents. A `downstream-smoke` module
  compiles against each adapter's public entry points using only transitively-exposed types, so a
  regression of an adapter dependency from `api` to `implementation` breaks the build.
- **Bounded, atomically-leased conversation store** — `InMemoryConversationStore` evicts
  least-recently-used sessions beyond a cap (default 10,000), so a long-running service no longer
  leaks memory as ephemeral sessions accumulate. Access is via `withMemory(tenant, session, action)`,
  which **pins** the entry for the duration so an in-flight session can't be evicted mid-turn and
  concurrent same-session requests serialize on one memory (no split sessions). The key is a record,
  so ids may contain any characters.
- **Per-tenant skill governance** — `SkillQuarantine` is now scoped per tenant: pending candidates,
  version history, and each tenant's active registry are keyed by `(tenant, name)`, so a skill
  approved for one tenant is invisible to another. `SkillAcquiringAgent` quarantines and approves
  under the request's tenant.
- **Per-tenant episodic lessons** — `Episode` carries a tenant and `EpisodicStore.recall(tenant,
  query, limit)` is scoped to it, so a lesson learned for one tenant is never recalled for another;
  `ReflectiveAgent` records and recalls under the request's tenant. (A back-compat `recall(query,
  limit)` and 4-arg `Episode` default to the `"default"` tenant; the file store reads legacy rows.)
- **Skill governance sealed and thread-safe** — `SkillRegistry` is now synchronized, and
  `SkillQuarantine` owns its registry and exposes only a read-only `SkillCatalog` (selectors and
  `SkillfulAgent` take `SkillCatalog`), so a skill can no longer be activated by calling `register()`
  directly — it must pass submit → approve. (Per-tenant skill isolation remains a follow-up;
  provenance already records the tenant.)
- **Governed skill acquisition** — a synthesized skill is quarantined with provenance (source task,
  author, tenant, version) and stays pending until a `SkillApprover` approves it; only then does it
  enter the active registry. `SkillAcquiringAgent` no longer activates skills directly (default
  approver is manual — nothing auto-activates), and promotions are versioned and reversible via
  `SkillQuarantine.rollback`.
- **Durable audit trail** — an `AuditEvent` (id, timestamp, traceId, session, principal, tenant,
  type, detail) emitted by the runtime for the turn lifecycle, tool-authorization decisions, and
  guardrail blocks; an `AuditSink` with `InMemoryAuditSink` and a flushed-per-event `FileAuditSink`.
  Wired into `DefaultAgent.builder().auditSink(...)` and `PolicyEnforcingAgent`
  (`Trust.govern(agent, sink, guardrails)`). Details are kept non-sensitive (names, decisions,
  reasons), never raw content or arguments.
- **Capability-based tool authorization** (breaking SPI) — tools declare a `ToolEffect`
  (`READ_ONLY`/`EFFECTFUL`, default effectful); `ToolApprover` now receives a `ToolCallContext`
  (spec/effect, arguments, principal, tenant) instead of `(name, json)`; `ToolApprovers.denyEffectful()`
  runs read-only tools and denies effectful ones by default.
- **Stateless runtime with a request context** — `AgentRequest` now carries a `RequestContext`
  (session, principal, tenant, trace, deadline). `DefaultAgent` holds no per-conversation state;
  memory is scoped per session via `ConversationStore`, so one instance serves many concurrent
  users/tenants without interleaving histories, and a turn past its deadline stops cleanly. Sub-agent
  calls (e.g. `DeepAgent` workers) inherit identity/tenant/trace with their own session.
- **Side-effect-free replay** — `RecordingObserver` now records tool results as well as model
  responses, and a new `ReplayToolExecutor` returns them in order during replay instead of re-running
  the real tools (`DefaultAgent.builder().toolExecutor(...)`). A recorded run reproduces
  deterministically without repeating writes, payments, or emails.
- **Streaming no longer bypasses output safety** — raw, pre-guardrail tokens are not streamed by
  default; `AgentObserver.onToken` is documented as an explicitly unsafe/raw channel, opt-in via
  `streamRawTokens(true)`. The guarded result is delivered only after output guardrails run.
- **Tool dispatch enforces the selector** — a tool not presented to the model this turn can no longer
  be invoked, even if the model names it (hallucination or prompt injection).
- **Skill acquisition is gated on genuine success** — blocked, errored, and step-exhausted turns no
  longer teach a skill.
- **`ResilientModelPort` holds no long-lived executor** — each attempt runs on a fresh virtual thread,
  so there is nothing to leak or shut down.
- **Adapter dependency scopes corrected** — types exposed on public entry points (LangChain4j /
  Spring AI `ChatModel`, ADK `BaseAgent`/`Runner`, the MCP client, OTel `Tracer`) are now `api`, so
  generated POMs declare them at `compile` and downstream code compiles against the public API
  without hunting for transitive deps.

### Notes

- Live ADK/MCP end-to-end need a configured ADK model / a running MCP server; their adapters are
  built against the real APIs and unit-tested.
