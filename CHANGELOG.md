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

- **Stateless runtime with a request context** — `AgentRequest` now carries a `RequestContext`
  (session, principal, tenant, trace, deadline). `DefaultAgent` holds no per-conversation state;
  memory is scoped per session via `ConversationStore`, so one instance serves many concurrent
  users/tenants without interleaving histories, and a turn past its deadline stops cleanly. Sub-agent
  calls (e.g. `DeepAgent` workers) inherit identity/tenant/trace with their own session.
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
