# Migrating to v0.2.0

**TL;DR — for framework adopters, v0.2.0 is additive: no breaking changes, no action required to upgrade.**

v0.2.0's headline is a new flagship application (FinCopilot); the library changes that landed are
backward-compatible additions.

## What changed in the library

- **`agent-spring-boot-starter` gained capabilities** (all additive):
  - It now also auto-configures a per-request **streaming-agent factory** (`Function<AgentObserver, Agent>`)
    and a **stream executor**, alongside the governed `Agent` it already configured.
  - It provides shared HTTP plumbing for agent services — `AgentTurns` (request validation, outcome→HTTP
    status mapping, synchronous run, and SSE streaming) and `SseAgentObserver` — so a Spring Boot agent
    endpoint needs little boilerplate.
  - Existing applications that only relied on the auto-configured `Agent` keep working unchanged.
- **`@Internal` marker added** (`dev.vaijanath.aiagent.annotation.Internal`) and a written
  [API stability policy](API-STABILITY.md). No existing type's visibility changed.
- No types were removed or deprecated; no method signatures on the stable seams changed.

## Tool request-context

Tools that need the turn's identity (principal/tenant/deadline) should implement **`ContextualTool`**
and read `ToolInvocation.context()`. This seam already existed in v0.1.x; v0.2.0 simply exercises it (the
FinCopilot Analyst tools use it for per-user data isolation) — no API change.

## New: the FinCopilot reference application

`apps/fincopilot` is a complete, deployable grounded finance copilot built on the framework (Ollama
substrate, governed agent, RAG-grounded advice, React UI, Docker Compose). It consumes the library exactly
as a third-party adopter would, and is the worked example for building a real product on `java-ai-agent`.

## Upgrading

Bump the dependency version to `0.2.0`. Nothing else is required. If you use the Spring Boot starter, you
may optionally adopt the new streaming factory / `AgentTurns` helpers to simplify your own controllers.
