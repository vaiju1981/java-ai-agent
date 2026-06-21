# Changelog

Notable changes to java-ai-agent. Format loosely follows [Keep a Changelog](https://keepachangelog.com);
versioning is [SemVer](https://semver.org). (Commit history has the fine-grained detail.)

## [Unreleased] — 0.1.0-SNAPSHOT

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
- **Reliability** — `ResilientModelPort` (timeout + retry), `WindowedMemory`, graceful
  model-failure handling, **structured output** (`StructuredOutput`, schema-bound JSON; used by the
  planner, reflector, skill selector, and skill synthesizer).
- **Build & quality** — Gradle multi-module (Java 21 baseline, JDK 26); Spotless + JaCoCo gates;
  Maven publication config; GitHub Actions CI.

### Hardening (trust-review remediation)

- **Trust governs the universal `Agent` seam** — new `PolicyEnforcingAgent` / `Trust.govern(agent,
  guardrails…)` enforces input/output guardrails and the request deadline around *any* agent, so
  composed and black-box agents (`DeepAgent`, `AdkAgent`) are governed too, not just `DefaultAgent`.
- **Tool-call timeout** — `DefaultAgent.builder().toolTimeout(Duration)` bounds each tool call on a
  virtual thread, so a hung tool returns an error instead of stalling the turn.
- **Adversarial + concurrency tests** — cross-session leakage under 64 concurrent sessions, a
  prompt-injected tool result that cannot escalate to an effectful tool, hung tools bounded by the
  timeout, and `DeepAgent` propagating tenant/identity to sub-agents. A `downstream-smoke` module
  compiles against each adapter's public entry points using only transitively-exposed types, so a
  regression of an adapter dependency from `api` to `implementation` breaks the build.
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
- Not yet released to Maven Central — publication config is verified locally (see
  [PUBLISHING.md](PUBLISHING.md)).
