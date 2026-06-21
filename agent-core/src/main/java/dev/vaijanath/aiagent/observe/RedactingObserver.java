package dev.vaijanath.aiagent.observe;

import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.guardrail.GuardrailDecision;
import dev.vaijanath.aiagent.guardrail.GuardrailStage;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.util.List;
import java.util.Objects;

/**
 * Wraps another {@link AgentObserver} and forwards every event with content fields replaced by
 * {@code "[redacted]"}, while preserving the metadata a telemetry pipeline needs — token usage,
 * roles, tool and guardrail names, ids, counts, and outcome flags. Use it when observers feed a
 * metrics/tracing backend that should never carry message content, tool arguments, or model output
 * (recall that {@code onModelResponse}/{@code onToolResult} otherwise carry raw, pre-output-guardrail
 * content). Raw streamed tokens are dropped entirely.
 */
public final class RedactingObserver implements AgentObserver {

    private static final String REDACTED = "[redacted]";

    private final AgentObserver delegate;

    public RedactingObserver(AgentObserver delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void onTurnStart(String input) {
        delegate.onTurnStart(REDACTED);
    }

    @Override
    public void onGuardrail(GuardrailStage stage, String guardrailName, GuardrailDecision decision) {
        GuardrailDecision redacted = decision.blocked()
                ? GuardrailDecision.block(REDACTED, decision.reason())
                : GuardrailDecision.allow(REDACTED);
        delegate.onGuardrail(stage, guardrailName, redacted);
    }

    @Override
    public void onModelCall(ModelRequest request) {
        List<Message> messages = request.messages().stream().map(RedactingObserver::redact).toList();
        delegate.onModelCall(new ModelRequest(messages, request.tools()));
    }

    @Override
    public void onModelResponse(ModelResponse response) {
        // Keep usage (for metering) and tool-call structure; redact the text and arguments.
        delegate.onModelResponse(
                new ModelResponse(REDACTED, redactCalls(response.toolCalls()), response.usage()));
    }

    @Override
    public void onToolCall(ToolCall call) {
        delegate.onToolCall(new ToolCall(call.id(), call.name(), REDACTED));
    }

    @Override
    public void onToolResult(String toolName, ToolResult result) {
        delegate.onToolResult(toolName, new ToolResult(REDACTED, result.error()));
    }

    @Override
    public void onTurnEnd(AgentResponse response) {
        delegate.onTurnEnd(new AgentResponse(REDACTED, response.blocked(), response.stopReason()));
    }

    @Override
    public void onToken(String token) {
        // Raw streamed tokens are the most sensitive — drop them entirely.
    }

    @Override
    public void onError(String stage, Throwable error) {
        delegate.onError(stage, error);
    }

    private static Message redact(Message message) {
        return new Message(message.role(), REDACTED, redactCalls(message.toolCalls()),
                message.toolCallId(), message.toolName());
    }

    private static List<ToolCall> redactCalls(List<ToolCall> calls) {
        return calls.stream().map(c -> new ToolCall(c.id(), c.name(), REDACTED)).toList();
    }
}
