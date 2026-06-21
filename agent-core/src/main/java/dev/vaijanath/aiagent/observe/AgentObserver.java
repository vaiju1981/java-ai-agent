package dev.vaijanath.aiagent.observe;

import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.guardrail.GuardrailDecision;
import dev.vaijanath.aiagent.guardrail.GuardrailStage;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.ToolResult;

/**
 * The observability seam. The runtime emits these lifecycle events; implementations trace, meter,
 * record, or audit. All methods default to no-op, so an observer overrides only what it needs.
 *
 * <p>Observers must not affect correctness: the runtime isolates each callback, so a throwing
 * observer is logged and ignored, never breaking the run.
 */
public interface AgentObserver {

    default void onTurnStart(String input) {}

    default void onGuardrail(GuardrailStage stage, String guardrailName, GuardrailDecision decision) {}

    default void onModelCall(ModelRequest request) {}

    default void onModelResponse(ModelResponse response) {}

    default void onToolCall(ToolCall call) {}

    default void onToolResult(String toolName, ToolResult result) {}

    default void onTurnEnd(AgentResponse response) {}

    /**
     * A raw, <b>pre-guardrail</b> output chunk streamed from the model — unsafe to surface directly,
     * since output guardrails have not run yet. Emitted only when the agent is built with
     * {@code streamRawTokens(true)}; the guarded result always arrives via {@link #onTurnEnd}.
     */
    default void onToken(String token) {}

    /** A recoverable failure during a turn (e.g. the model call threw). The turn ends gracefully. */
    default void onError(String stage, Throwable error) {}
}
