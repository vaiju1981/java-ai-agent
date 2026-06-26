# API stability policy

This document defines what `java-ai-agent` considers **stable public API** versus **internal**, and how
changes are managed across releases. It applies to the published library modules (`agent-*`); the
`examples/` module and `production-reference` are example applications, not API.

## Stable public API

The supported surface is the set of types and members an application is expected to use directly:

- The core **seams**: `Agent`, `ModelPort` (and `StreamingModelPort`, `StructuredOutput`), `Tool`
  (and `ContextualTool`), `Guardrail`, `Retriever`, `Embedder`, `ConversationStore`, `AuditSink`,
  `ToolApprover`, `Router`.
- The **assembly** entry points: `ProductionAgentRuntime.builder()`, `Trust.govern(...)`,
  `SupervisorAgent.builder()`, `RetrievalAugmentedAgent`, and the request/response records
  (`AgentRequest`, `AgentResponse`, `RequestContext`, `ToolSpec`, `ToolResult`, `ToolInvocation`, …).
- The adapter factories (e.g. `OllamaModelPorts`, the Anthropic/Spring AI ports) and the
  `agent-spring-boot-starter` auto-configuration + `agent.*` properties.

For stable API: no breaking changes in a **minor** or **patch** release. Breaking changes happen only in
a **major** release, and only after a deprecation period (see below).

## Internal

Anything marked [`@Internal`](../agent-core/src/main/java/dev/vaijanath/aiagent/annotation/Internal.java)
is not API and may change or be removed in **any** release without notice. In addition, a type that is
plainly an implementation detail behind a seam (for example a `Default*` class) should be treated as
internal even if not yet annotated — prefer the documented interfaces and builders. The annotation is
applied incrementally.

## Deprecation lifecycle

When a stable element is replaced, it is **deprecated, not removed**: annotated
`@Deprecated(forRemoval = true, since = "<version>")` with a working replacement, kept for at least one
minor release, and removed no earlier than the next major. Each release with deprecations or removals
ships a `docs/MIGRATION-<version>.md` note.

## Pre-1.0 note

While the project is `0.x`, the public API is still stabilizing. We minimize breaking changes, apply the
deprecation lifecycle where practical, and document anything notable in the per-release migration notes.

## Enforcement

Every library module runs a **binary-compatibility check (japicmp)** as part of `check` (so it runs in
CI on every PR): the freshly built jar is diffed against the module's last published release on Maven
Central, and a **binary-incompatible change to non-`@Internal`, public API fails the build**. This is the
enforcement behind "deprecate, don't remove" — an accidental removal or signature change is caught before
it ships, not after.

- **Run it locally:** `./gradlew japicmpCheck` (or `:<module>:japicmpCheck`). A text + HTML report is
  written to each module's `build/reports/japicmp/`.
- **Baseline:** the last released version (currently `0.1.2`). Override with `-PjapicmpBaseline=<version>`;
  **bump the default in the root build after each release** once the artifacts are live on Central.
- **Scope:** only `public` API is compared, and elements annotated `@Internal` are excluded — so internal
  evolution never trips the gate. Additive changes (new types/methods) are always compatible.
- **Intentional break (major release only):** when a removal is deliberate, perform it in a major version
  and bump the baseline; the deprecation lifecycle above must have run first.
