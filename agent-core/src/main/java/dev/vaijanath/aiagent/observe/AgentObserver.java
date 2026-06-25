package dev.vaijanath.aiagent.observe;

import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.guardrail.GuardrailDecision;
import dev.vaijanath.aiagent.guardrail.GuardrailStage;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.ApprovalRequest;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.time.Duration;

/**
 * The observability seam. The runtime emits these lifecycle events; implementations trace, meter,
 * record, or audit. All methods default to no-op, so an observer overrides only what it needs.
 *
 * <p>Observers must not affect correctness: the runtime isolates each callback, so a throwing
 * observer is logged and ignored, never breaking the run.
 *
 * <p>Content caveat: {@link #onTurnStart} receives post-input-guardrail (e.g. scrubbed) input, but
 * {@link #onModelResponse} and {@link #onToolResult} carry <b>raw, pre-output-guardrail</b> content
 * (tool results are size-capped). Anything that persists these events — e.g. a recorder — holds
 * potentially sensitive data and should be protected accordingly.
 */
public interface AgentObserver {

    default void onTurnStart(String input) {}

    default void onGuardrail(GuardrailStage stage, String guardrailName, GuardrailDecision decision) {}

    default void onModelCall(ModelRequest request) {}

    default void onModelResponse(ModelResponse response) {}

    /**
     * The model responded, with the wall-clock {@code latency} of that call. The runtime invokes this
     * timed variant; it defaults to the untimed {@link #onModelResponse(ModelResponse)}, so existing
     * observers keep working and timing-aware ones override this instead.
     */
    default void onModelResponse(ModelResponse response, Duration latency) {
        onModelResponse(response);
    }

    default void onToolCall(ToolCall call) {}

    default void onToolResult(String toolName, ToolResult result) {}

    /**
     * A tool finished, with the wall-clock {@code latency} of that call. The runtime invokes this timed
     * variant; it defaults to the untimed {@link #onToolResult(String, ToolResult)}.
     */
    default void onToolResult(String toolName, ToolResult result, Duration latency) {
        onToolResult(toolName, result);
    }

    /**
     * A {@link dev.vaijanath.aiagent.tool.StructuredTool} produced a structured JSON payload alongside its
     * {@link #onToolResult} text. For UIs and recorders only — this payload is never sent to the model.
     */
    default void onToolData(String toolName, String dataJson) {}

    /**
     * An effectful tool call requires human approval before it can run (the {@code ToolApprover} denied it
     * and an {@link dev.vaijanath.aiagent.tool.ApprovalHandler} is configured). Emitted just before the
     * runtime blocks on the handler, so a UI can surface the request and resolve it by
     * {@link ApprovalRequest#approvalId()}.
     */
    default void onApprovalRequired(ApprovalRequest request) {}

    default void onTurnEnd(AgentResponse response) {}

    /**
     * The turn ended, with its total wall-clock {@code duration}. The runtime invokes this timed
     * variant; it defaults to the untimed {@link #onTurnEnd(AgentResponse)}.
     */
    default void onTurnEnd(AgentResponse response, Duration duration) {
        onTurnEnd(response);
    }

    /**
     * A raw, <b>pre-guardrail</b> output chunk streamed from the model — unsafe to surface directly,
     * since output guardrails have not run yet. Emitted only when the agent is built with
     * {@code streamRawTokens(true)}; the guarded result always arrives via {@link #onTurnEnd}.
     */
    default void onToken(String token) {}

    /** A recoverable failure during a turn (e.g. the model call threw). The turn ends gracefully. */
    default void onError(String stage, Throwable error) {}
}
